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
     * Synchronizes local markdown notes and attachments with the user's hidden Google Drive appDataFolder
     * with client-side AES-256-GCM End-to-End Encryption (E2EE).
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

            var uploaded = 0
            var downloaded = 0

            // 1. Fetch remote files in appDataFolder
            onProgress("Checking Google Drive remote files...")
            val remoteFiles = GoogleDriveClient.listAppDataFiles(token)
            val remoteMap = mutableMapOf<String, GoogleDriveFile>()
            for (rf in remoteFiles) {
                remoteMap[rf.name.lowercase()] = rf
            }

            // 2. Pull remote updates to local vault
            onProgress("Syncing remote notes...")
            for (remoteFile in remoteFiles) {
                val cleanName = if (remoteFile.name.endsWith(".enc", ignoreCase = true)) {
                    remoteFile.name.dropLast(4)
                } else {
                    remoteFile.name
                }

                if (!cleanName.endsWith(".md", ignoreCase = true) && !cleanName.startsWith("img_") && !cleanName.startsWith("audio_") && !cleanName.startsWith("canvas_")) {
                    continue
                }

                val localTarget = File(vaultDir, cleanName)
                val needsDownload = !localTarget.exists() || (remoteFile.modifiedTime > (localTarget.lastModified() + 2000L))

                if (needsDownload) {
                    onProgress("Downloading $cleanName...")
                    val rawBytes = GoogleDriveClient.downloadFile(token, remoteFile.id)
                    val finalBytes = if (VaultCrypto.isEncrypted(rawBytes)) {
                        try {
                            VaultCrypto.decrypt(rawBytes, passphrase)
                        } catch (e: Exception) {
                            // If decryption fails with current passphrase, keep encrypted file
                            rawBytes
                        }
                    } else {
                        rawBytes
                    }

                    localTarget.parentFile?.mkdirs()
                    localTarget.writeBytes(finalBytes)
                    localTarget.setLastModified(remoteFile.modifiedTime)
                    downloaded++
                }
            }

            // 3. Push local changes to Google Drive
            onProgress("Checking local notes for upload...")
            val localFiles = mutableListOf<File>()
            vaultDir.walkTopDown()
                .filter { it.isFile }
                .filter { !it.name.startsWith(".") }
                .filter { !it.absolutePath.contains("/.trash/") }
                .filter { !it.absolutePath.contains("/exports/") }
                .forEach { localFiles.add(it) }

            for (localFile in localFiles) {
                val baseName = localFile.name
                val remoteEncName = "$baseName.enc"

                val remoteMatch = remoteMap[remoteEncName.lowercase()] ?: remoteMap[baseName.lowercase()]
                val needsUpload = remoteMatch == null || (localFile.lastModified() > (remoteMatch.modifiedTime + 2000L))

                if (needsUpload) {
                    onProgress("Encrypting & uploading $baseName...")
                    val plainBytes = localFile.readBytes()
                    val encryptedBytes = VaultCrypto.encrypt(plainBytes, passphrase)

                    if (remoteMatch != null) {
                        GoogleDriveClient.updateExistingFile(token, remoteMatch.id, encryptedBytes)
                    } else {
                        val newId = GoogleDriveClient.uploadNewFile(token, remoteEncName, encryptedBytes)
                        remoteMap[remoteEncName.lowercase()] = GoogleDriveFile(
                            id = newId,
                            name = remoteEncName,
                            modifiedTime = System.currentTimeMillis(),
                            size = encryptedBytes.size.toLong()
                        )
                    }
                    uploaded++
                }
            }

            // 4. Handle deleted / trashed notes
            val trashDir = File(vaultDir, ".trash")
            if (trashDir.exists() && trashDir.isDirectory) {
                val trashedFiles = trashDir.listFiles() ?: emptyArray()
                for (trashed in trashedFiles) {
                    val remoteEnc = "${trashed.name}.enc".lowercase()
                    val remotePlain = trashed.name.lowercase()
                    val toDelete = remoteMap[remoteEnc] ?: remoteMap[remotePlain]
                    if (toDelete != null) {
                        onProgress("Removing deleted note from Drive: ${trashed.name}...")
                        GoogleDriveClient.deleteFile(token, toDelete.id)
                        remoteMap.remove(toDelete.name.lowercase())
                    }
                }
            }

            val summary = when {
                uploaded == 0 && downloaded == 0 -> "Google Drive vault is up to date"
                else -> "Google Drive synced: $uploaded uploaded, $downloaded downloaded"
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
