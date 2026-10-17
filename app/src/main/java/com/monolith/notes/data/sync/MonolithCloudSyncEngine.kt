package com.monolith.notes.data.sync

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object MonolithCloudSyncEngine {

    suspend fun sync(
        vaultDir: File,
        config: MonolithCloudConfig,
        context: Context? = null,
        onProgress: (String) -> Unit = {}
    ): SyncResult = withContext(Dispatchers.IO) {
        if (!config.isLoggedIn) {
            return@withContext SyncResult(isSuccess = false, message = "Not signed in to Monolith Cloud Vault")
        }

        try {
            onProgress("Connecting to Monolith Cloud Vault...")
            var uploaded = 0
            var downloaded = 0

            // 1. Pull remote updates from server
            onProgress("Checking for remote updates...")
            val pullResult = MonolithCloudClient.pull(
                serverUrl = config.serverUrl,
                token = config.token,
                since = config.lastServerSyncTime
            )

            if (!pullResult.isSuccess) {
                return@withContext SyncResult(isSuccess = false, message = pullResult.message)
            }

            // Apply pulled updates locally
            for (remoteItem in pullResult.items) {
                val targetFile = File(vaultDir, remoteItem.fileName)

                if (remoteItem.isDeleted) {
                    if (targetFile.exists()) {
                        val trashDir = File(vaultDir, ".trash").also { if (!it.exists()) it.mkdirs() }
                        targetFile.renameTo(File(trashDir, targetFile.name))
                    }
                    continue
                }

                // Check if remote item is newer than local
                val localModified = if (targetFile.exists()) targetFile.lastModified() else 0L
                if (!targetFile.exists() || remoteItem.clientModified > (localModified + 2000L)) {
                    onProgress("Decrypting & downloading: ${remoteItem.fileName}...")
                    val rawBytes = Base64.decode(remoteItem.dataBase64, Base64.NO_WRAP)
                    val plaintextBytes = if (config.encryptionKey.isNotBlank() && VaultCrypto.isEncrypted(rawBytes)) {
                        try {
                            VaultCrypto.decrypt(rawBytes, config.encryptionKey)
                        } catch (e: Exception) {
                            throw IllegalStateException("Decryption failed for ${remoteItem.fileName}: Invalid encryption key or corrupted data", e)
                        }
                    } else {
                        rawBytes
                    }

                    targetFile.outputStream().use { it.write(plaintextBytes) }
                    if (remoteItem.clientModified > 0) {
                        targetFile.setLastModified(remoteItem.clientModified)
                    }
                    downloaded++
                }
            }

            // 2. Collect local files and tombstones for push
            val localFiles = mutableListOf<File>()
            vaultDir.walkTopDown()
                .filter { it.isFile }
                .filter { !it.name.startsWith(".") }
                .filter { !it.absolutePath.contains("/.trash/") }
                .filter { !it.absolutePath.contains("/exports/") }
                .forEach { localFiles.add(it) }

            val itemsToPush = mutableListOf<CloudSyncItem>()

            for (localFile in localFiles) {
                // If local file was modified after last sync timestamp, queue it for push
                if (localFile.lastModified() > (config.lastServerSyncTime + 2000L) || config.lastServerSyncTime == 0L) {
                    val rawBytes = localFile.readBytes()
                    val encryptedBytes = if (config.encryptionKey.isNotBlank()) {
                        VaultCrypto.encrypt(rawBytes, config.encryptionKey)
                    } else {
                        rawBytes
                    }

                    val base64Data = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
                    itemsToPush.add(
                        CloudSyncItem(
                            fileName = localFile.name,
                            dataBase64 = base64Data,
                            clientModified = localFile.lastModified(),
                            isDeleted = false
                        )
                    )
                }
            }

            // Also push local deletions (tombstones) so cloud server deletes them
            val journal = context?.let { SyncJournal(it) }
            val tombstones = journal?.getAllTombstones() ?: emptyMap()
            for ((tombstoneName, _) in tombstones) {
                itemsToPush.add(
                    CloudSyncItem(
                        fileName = tombstoneName,
                        dataBase64 = "",
                        clientModified = System.currentTimeMillis(),
                        isDeleted = true
                    )
                )
            }

            // 3. Push local changes to server
            if (itemsToPush.isNotEmpty()) {
                onProgress("Encrypting and uploading ${itemsToPush.size} change(s)...")
                val pushResult = MonolithCloudClient.push(
                    serverUrl = config.serverUrl,
                    token = config.token,
                    items = itemsToPush
                )

                if (!pushResult.isSuccess) {
                    return@withContext SyncResult(isSuccess = false, message = pushResult.message)
                }
                uploaded = pushResult.pushedCount

                // Purge consumed tombstones
                if (journal != null && tombstones.isNotEmpty()) {
                    for ((tombstoneName, _) in tombstones) {
                        journal.removeTombstone(tombstoneName)
                    }
                    journal.save()
                }
            }

            val newServerTime = if (pullResult.serverTime > 0) pullResult.serverTime else System.currentTimeMillis()
            val summary = when {
                uploaded == 0 && downloaded == 0 -> "Cloud vault is up to date"
                else -> "Cloud vault synced: $uploaded uploaded, $downloaded downloaded"
            }

            onProgress(summary)
            SyncResult(
                isSuccess = true,
                uploadedCount = uploaded,
                downloadedCount = downloaded,
                message = summary,
                timestamp = newServerTime
            )
        } catch (e: Exception) {
            SyncResult(isSuccess = false, message = "Cloud sync failed: ${e.message}")
        }
    }
}
