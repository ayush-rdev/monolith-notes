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
     * Intelligent, content-hashed, conflict-safe synchronization engine for Google Drive.
     * Uses SHA-256 delta hashes and tombstones to prevent redundant uploads/downloads,
     * detect cross-device deletions, and protect user data during concurrent edits.
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

            // 1. Fetch remote files in appDataFolder
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

            // 2. Process Local Tombstones / Trash (Delete remote files that user deleted locally)
            val trashDir = File(vaultDir, ".trash").apply { if (!exists()) mkdirs() }
            val trashedFiles = trashDir.listFiles()?.filter { it.isFile } ?: emptyList()
            for (trashed in trashedFiles) {
                journal.addTombstone(trashed.name)
            }

            for ((tombstoneName, _) in journal.getAllTombstones()) {
                val remoteMatch = remoteMap[tombstoneName.lowercase()]
                if (remoteMatch != null) {
                    onProgress("Syncing deletion of $tombstoneName...")
                    try {
                        GoogleDriveClient.deleteFile(token, remoteMatch.id)
                        remoteMap.remove(tombstoneName.lowercase())
                        deletedCount++
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // 3. Map Local Vault Files
            val localFiles = mutableListOf<File>()
            vaultDir.walkTopDown()
                .filter { it.isFile }
                .filter { !it.name.startsWith(".") }
                .filter { !it.absolutePath.contains("/.trash/") }
                .filter { !it.absolutePath.contains("/exports/") }
                .forEach { localFiles.add(it) }

            val localMap = mutableMapOf<String, File>()
            for (lf in localFiles) {
                localMap[lf.name.lowercase()] = lf
                journal.removeTombstone(lf.name)
            }

            // 4. Remote -> Local (Intelligent Pull with Conflict Detection)
            onProgress("Evaluating delta updates...")
            for (remoteFile in remoteFiles) {
                val cleanName = if (remoteFile.name.endsWith(".enc", ignoreCase = true)) {
                    remoteFile.name.dropLast(4)
                } else {
                    remoteFile.name
                }

                if (!cleanName.endsWith(".md", ignoreCase = true) &&
                    !cleanName.startsWith("img_") &&
                    !cleanName.startsWith("audio_") &&
                    !cleanName.startsWith("canvas_")
                ) {
                    continue
                }

                if (journal.isTombstoned(cleanName)) {
                    // Local user previously deleted this note; remove remote copy if still there
                    GoogleDriveClient.deleteFile(token, remoteFile.id)
                    continue
                }

                val localFile = localMap[cleanName.lowercase()]
                val record = journal.getRecord(cleanName)

                var needsDownload = false

                if (localFile == null) {
                    // Brand new note from another device!
                    needsDownload = true
                } else {
                    val localHash = SyncJournal.computeHash(localFile)
                    val localChanged = record == null || localHash != record.contentHash
                    val remoteChanged = record == null || remoteFile.modifiedTime > record.remoteModifiedTime

                    if (remoteChanged && !localChanged) {
                        // Remote updated, local untouched -> clean download
                        needsDownload = true
                    } else if (remoteChanged && localChanged) {
                        // Both edited -> potential conflict!
                        val rawBytes = GoogleDriveClient.downloadFile(token, remoteFile.id)
                        val decrypted = if (VaultCrypto.isEncrypted(rawBytes)) {
                            try { VaultCrypto.decrypt(rawBytes, passphrase) } catch (_: Exception) { rawBytes }
                        } else {
                            rawBytes
                        }
                        val remoteHash = SyncJournal.computeHash(decrypted)
                        if (remoteHash != localHash) {
                            // Genuine conflict: preserve local as conflict copy!
                            val conflictName = "${localFile.nameWithoutExtension}_Conflict_${System.currentTimeMillis()}.md"
                            val conflictFile = File(localFile.parentFile ?: vaultDir, conflictName)
                            localFile.copyTo(conflictFile, overwrite = true)
                            conflicts++
                        }
                        localFile.writeBytes(decrypted)
                        journal.setRecord(cleanName, FileSyncRecord(remoteHash, remoteFile.id, remoteFile.modifiedTime, localFile.lastModified()))
                        downloaded++
                        continue
                    }
                }

                if (needsDownload) {
                    onProgress("Downloading $cleanName...")
                    val rawBytes = GoogleDriveClient.downloadFile(token, remoteFile.id)
                    val finalBytes = if (VaultCrypto.isEncrypted(rawBytes)) {
                        try { VaultCrypto.decrypt(rawBytes, passphrase) } catch (_: Exception) { rawBytes }
                    } else {
                        rawBytes
                    }

                    val targetFile = localFile ?: if (cleanName.startsWith("img_") || cleanName.startsWith("audio_") || cleanName.startsWith("canvas_")) {
                        val attDir = File(vaultDir, "attachments").apply { if (!exists()) mkdirs() }
                        File(attDir, cleanName)
                    } else {
                        File(vaultDir, cleanName)
                    }
                    targetFile.parentFile?.mkdirs()
                    targetFile.writeBytes(finalBytes)
                    val newHash = SyncJournal.computeHash(finalBytes)
                    journal.setRecord(cleanName, FileSyncRecord(newHash, remoteFile.id, remoteFile.modifiedTime, targetFile.lastModified()))
                    localMap[cleanName.lowercase()] = targetFile
                    downloaded++
                }
            }

            // 5. Local -> Remote (Intelligent Push with Content-Hash Delta Skip)
            for (localFile in localFiles) {
                val baseName = localFile.name
                val remoteEncName = "$baseName.enc"
                val remoteMatch = remoteMap[baseName.lowercase()]

                val record = journal.getRecord(baseName)
                val currentLocalHash = SyncJournal.computeHash(localFile)

                // If content hash matches last sync AND remote has the file -> SKIP! 0 bytes uploaded!
                if (record != null && record.contentHash == currentLocalHash && remoteMatch != null) {
                    continue
                }

                onProgress("Encrypting & uploading $baseName...")
                val plainBytes = localFile.readBytes()
                val encryptedBytes = VaultCrypto.encrypt(plainBytes, passphrase)

                if (remoteMatch != null) {
                    GoogleDriveClient.updateExistingFile(token, remoteMatch.id, encryptedBytes)
                    journal.setRecord(baseName, FileSyncRecord(currentLocalHash, remoteMatch.id, System.currentTimeMillis(), localFile.lastModified()))
                } else {
                    val newId = GoogleDriveClient.uploadNewFile(token, remoteEncName, encryptedBytes)
                    journal.setRecord(baseName, FileSyncRecord(currentLocalHash, newId, System.currentTimeMillis(), localFile.lastModified()))
                }
                uploaded++
            }

            // 6. Detect Remote Deletions on Local Device
            // If tracked in journal, but completely gone from Google Drive and not modified locally:
            for ((trackedName, record) in journal.getAllRecords()) {
                val localFile = localMap[trackedName.lowercase()]
                val inRemote = remoteMap.containsKey(trackedName.lowercase())

                if (!inRemote && localFile != null && localFile.exists()) {
                    val currentHash = SyncJournal.computeHash(localFile)
                    if (currentHash == record.contentHash) {
                        // Deleted on another device! Move to local trash.
                        onProgress("Syncing remote deletion of $trackedName...")
                        val trashTarget = File(trashDir, localFile.name)
                        localFile.renameTo(trashTarget)
                        journal.addTombstone(trackedName)
                        deletedCount++
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
                    if (conflicts > 0) parts.add("$conflicts conflicts resolved")
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
