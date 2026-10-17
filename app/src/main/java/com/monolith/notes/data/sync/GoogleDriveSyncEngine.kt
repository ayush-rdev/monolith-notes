package com.monolith.notes.data.sync

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.monolith.notes.data.GoogleDriveConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object GoogleDriveSyncEngine {

    /**
     * Canonical 3-Way State Machine Synchronization Engine for Google Drive.
     * Follows modern distributed sync principles (Dropbox / Syncthing / Git):
     * 1. Local existence & modification ALWAYS trumps deletion.
     * 2. Tombstones are single-use transition events consumed upon propagation, never immortal blocklists.
     * 3. .trash/ is a local recycle bin, NOT an active deletion generator.
     * 4. Zero data loss: concurrent edits produce safe conflict copies rather than overwriting.
     */
    suspend fun sync(
        context: Context,
        vaultDir: File,
        config: GoogleDriveConfig,
        onProgress: (String) -> Unit = {}
    ): SyncResult = withContext(Dispatchers.IO) {
        if (!config.isConnected) {
            return@withContext SyncResult(isSuccess = false, message = "Google Drive is not connected")
        }

        try {
            onProgress("Connecting to Google Drive...")

            val signedInAccount = GoogleSignIn.getLastSignedInAccount(context)
            if (signedInAccount == null) {
                return@withContext SyncResult(
                    isSuccess = false,
                    message = "Google Sign-In session expired. Please tap Connect again."
                )
            }

            val account = signedInAccount.account ?: Account(config.email, "com.google")
            onProgress("Authenticating with Google...")
            val token = GoogleDriveClient.fetchAccessToken(context, account)
                ?: return@withContext SyncResult(
                    isSuccess = false,
                    message = "Could not authenticate Google Drive token. Please reconnect."
                )

            val passphrase = config.encryptionKey.ifBlank { "MonolithGDrive:" + config.email }
            val journal = SyncJournal(context)

            var uploaded = 0
            var downloaded = 0
            var conflicts = 0
            var deletedCount = 0

            val trashDir = File(vaultDir, ".trash").apply { if (!exists()) mkdirs() }

            // 1. Scan Local Active Vault (Excludes .trash and hidden folders)
            val localFiles = mutableListOf<File>()
            vaultDir.walkTopDown()
                .filter { it.isFile }
                .filter { !it.name.startsWith(".") }
                .filter { !it.absolutePath.contains("/.trash/") }
                .filter { !it.absolutePath.contains("/exports/") }
                .forEach { localFiles.add(it) }

            val localMap = mutableMapOf<String, File>()
            for (lf in localFiles) {
                val key = lf.name.lowercase()
                localMap[key] = lf
                // RULE 1: If a file exists in the active vault, it is ALIVE.
                // Clear any lingering or stale tombstones immediately.
                journal.removeTombstone(lf.name)
            }

            // 2. Query Remote Google Drive Metadata
            onProgress("Querying cloud metadata...")
            val remoteFiles = GoogleDriveClient.listAppDataFiles(token)
            val remoteMap = mutableMapOf<String, GoogleDriveFile>()
            for (rf in remoteFiles) {
                val cleanName = if (rf.name.endsWith(".enc", ignoreCase = true)) {
                    rf.name.dropLast(4)
                } else {
                    rf.name
                }
                remoteMap[cleanName.lowercase()] = rf
            }

            // 3. Process Explicit Local Deletions (Tombstones recorded by user action)
            val activeTombstones = journal.getAllTombstones()
            for ((tombstoneName, _) in activeTombstones) {
                val key = tombstoneName.lowercase()
                // Never delete if the file is currently present in active vault (e.g. restored!)
                if (localMap.containsKey(key)) {
                    journal.removeTombstone(tombstoneName)
                    continue
                }

                val remoteMatch = remoteMap[key]
                if (remoteMatch != null) {
                    onProgress("Syncing deletion of $tombstoneName...")
                    try {
                        GoogleDriveClient.deleteFile(token, remoteMatch.id)
                        remoteMap.remove(key)
                        deletedCount++
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                // Tombstone is satisfied and consumed
                journal.removeTombstone(tombstoneName)
                journal.removeRecord(tombstoneName)
            }

            // 4. Form Canonical Union of Candidate Files
            val candidateKeys = (localMap.keys + remoteMap.keys).filter { key ->
                key.endsWith(".md", ignoreCase = true) ||
                key.startsWith("img_") ||
                key.startsWith("audio_") ||
                key.startsWith("canvas_")
            }.toSet()

            onProgress("Reconciling vault files...")

            for (key in candidateKeys) {
                val localFile = localMap[key]
                val remoteFile = remoteMap[key]
                val base = journal.getRecord(key)

                // ── CASE A: Exists ONLY locally (New note or remote was deleted) ──
                if (localFile != null && remoteFile == null) {
                    val currentHash = SyncJournal.computeHash(localFile)
                    val wasPreviouslyRemote = base != null && base.remoteFileId.isNotBlank()

                    if (wasPreviouslyRemote && currentHash == base?.contentHash) {
                        // Deleted on another device while local was untouched! Move to local trash.
                        onProgress("Syncing remote deletion of ${localFile.name}...")
                        val trashTarget = File(trashDir, localFile.name)
                        try {
                            localFile.copyTo(trashTarget, overwrite = true)
                            localFile.delete()
                        } catch (_: Exception) {
                            localFile.renameTo(trashTarget)
                        }
                        journal.removeRecord(key)
                        deletedCount++
                    } else {
                        // Brand new note, or user edited locally after remote deletion!
                        // Modification wins: Upload to Google Drive!
                        onProgress("Encrypting & uploading ${localFile.name}...")
                        val plainBytes = localFile.readBytes()
                        val encryptedBytes = VaultCrypto.encrypt(plainBytes, passphrase)
                        val remoteEncName = "${localFile.name}.enc"
                        val newId = GoogleDriveClient.uploadNewFile(token, remoteEncName, encryptedBytes)
                        journal.setRecord(
                            localFile.name,
                            FileSyncRecord(currentHash, newId, System.currentTimeMillis(), localFile.lastModified())
                        )
                        remoteMap[key] = GoogleDriveFile(newId, remoteEncName, System.currentTimeMillis(), encryptedBytes.size.toLong())
                        uploaded++
                    }
                    continue
                }

                // ── CASE B: Exists ONLY on remote (New remote note or locally deleted) ──
                if (localFile == null && remoteFile != null) {
                    val wasPreviouslyLocal = base != null && base.contentHash.isNotBlank()

                    if (wasPreviouslyLocal) {
                        // User deleted it locally earlier, but tombstone wasn't pending:
                        // Delete remote file to keep in sync.
                        onProgress("Syncing local deletion of ${remoteFile.name}...")
                        try {
                            GoogleDriveClient.deleteFile(token, remoteFile.id)
                            deletedCount++
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        journal.removeRecord(key)
                    } else {
                        // Brand new note created on another device! Download it!
                        val cleanName = if (remoteFile.name.endsWith(".enc", ignoreCase = true)) {
                            remoteFile.name.dropLast(4)
                        } else {
                            remoteFile.name
                        }
                        onProgress("Downloading $cleanName...")
                        val rawBytes = GoogleDriveClient.downloadFile(token, remoteFile.id)
                        val decrypted = if (VaultCrypto.isEncrypted(rawBytes)) {
                            try { VaultCrypto.decrypt(rawBytes, passphrase) } catch (_: Exception) { rawBytes }
                        } else {
                            rawBytes
                        }
                        val targetFile = if (cleanName.startsWith("img_") || cleanName.startsWith("audio_") || cleanName.startsWith("canvas_")) {
                            val attDir = File(vaultDir, "attachments").apply { if (!exists()) mkdirs() }
                            File(attDir, cleanName)
                        } else {
                            File(vaultDir, cleanName)
                        }
                        targetFile.parentFile?.mkdirs()
                        targetFile.writeBytes(decrypted)
                        val newHash = SyncJournal.computeHash(decrypted)
                        journal.setRecord(
                            cleanName,
                            FileSyncRecord(newHash, remoteFile.id, remoteFile.modifiedTime, targetFile.lastModified())
                        )
                        localMap[key] = targetFile
                        downloaded++
                    }
                    continue
                }

                // ── CASE C: Exists on BOTH local and remote ──
                if (localFile != null && remoteFile != null) {
                    val localHash = SyncJournal.computeHash(localFile)
                    val localChanged = base == null || localHash != base.contentHash
                    val remoteChanged = base == null || remoteFile.modifiedTime > (base.remoteModifiedTime + 1000L)

                    // Subcase C1: Neither changed -> 0 bytes, skip!
                    if (!localChanged && !remoteChanged) {
                        continue
                    }

                    // Subcase C2: Only local changed -> Upload
                    if (localChanged && !remoteChanged) {
                        onProgress("Uploading updated ${localFile.name}...")
                        val plainBytes = localFile.readBytes()
                        val encryptedBytes = VaultCrypto.encrypt(plainBytes, passphrase)
                        GoogleDriveClient.updateExistingFile(token, remoteFile.id, encryptedBytes)
                        journal.setRecord(
                            localFile.name,
                            FileSyncRecord(localHash, remoteFile.id, System.currentTimeMillis(), localFile.lastModified())
                        )
                        uploaded++
                        continue
                    }

                    // Subcase C3: Only remote changed -> Download
                    if (!localChanged && remoteChanged) {
                        onProgress("Downloading updated ${localFile.name}...")
                        val rawBytes = GoogleDriveClient.downloadFile(token, remoteFile.id)
                        val decrypted = if (VaultCrypto.isEncrypted(rawBytes)) {
                            try { VaultCrypto.decrypt(rawBytes, passphrase) } catch (_: Exception) { rawBytes }
                        } else {
                            rawBytes
                        }
                        localFile.writeBytes(decrypted)
                        val newHash = SyncJournal.computeHash(decrypted)
                        journal.setRecord(
                            localFile.name,
                            FileSyncRecord(newHash, remoteFile.id, remoteFile.modifiedTime, localFile.lastModified())
                        )
                        downloaded++
                        continue
                    }

                    // Subcase C4: BOTH changed -> Potential Conflict!
                    if (localChanged && remoteChanged) {
                        val rawBytes = GoogleDriveClient.downloadFile(token, remoteFile.id)
                        val decrypted = if (VaultCrypto.isEncrypted(rawBytes)) {
                            try { VaultCrypto.decrypt(rawBytes, passphrase) } catch (_: Exception) { rawBytes }
                        } else {
                            rawBytes
                        }
                        val remoteHash = SyncJournal.computeHash(decrypted)

                        if (remoteHash == localHash) {
                            // Identical content!
                            journal.setRecord(
                                localFile.name,
                                FileSyncRecord(localHash, remoteFile.id, remoteFile.modifiedTime, localFile.lastModified())
                            )
                        } else {
                            // True conflict! Never destroy data!
                            onProgress("Resolving conflict for ${localFile.name}...")
                            val conflictName = "${localFile.nameWithoutExtension}_Conflict_${System.currentTimeMillis()}.md"
                            val conflictFile = File(localFile.parentFile ?: vaultDir, conflictName)
                            localFile.copyTo(conflictFile, overwrite = true)

                            // Apply remote to primary file
                            localFile.writeBytes(decrypted)
                            journal.setRecord(
                                localFile.name,
                                FileSyncRecord(remoteHash, remoteFile.id, remoteFile.modifiedTime, localFile.lastModified())
                            )

                            // Upload conflict copy to Drive so all devices have it
                            val conflictEncBytes = VaultCrypto.encrypt(conflictFile.readBytes(), passphrase)
                            val conflictRemoteId = GoogleDriveClient.uploadNewFile(token, "${conflictName}.enc", conflictEncBytes)
                            journal.setRecord(
                                conflictName,
                                FileSyncRecord(localHash, conflictRemoteId, System.currentTimeMillis(), conflictFile.lastModified())
                            )
                            conflicts++
                        }
                    }
                }
            }

            journal.save()

            val summary = when {
                uploaded == 0 && downloaded == 0 && deletedCount == 0 && conflicts == 0 -> "Vault is up to date"
                else -> {
                    val parts = mutableListOf<String>()
                    if (uploaded > 0) parts.add("$uploaded uploaded")
                    if (downloaded > 0) parts.add("$downloaded downloaded")
                    if (deletedCount > 0) parts.add("$deletedCount deleted")
                    if (conflicts > 0) parts.add("$conflicts conflicts preserved")
                    "Synced: " + parts.joinToString(", ")
                }
            }

            onProgress(summary)
            SyncResult(
                isSuccess = true,
                uploadedCount = uploaded,
                downloadedCount = downloaded,
                message = summary,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            SyncResult(isSuccess = false, message = "Google Drive sync error: ${e.message}")
        }
    }
}
