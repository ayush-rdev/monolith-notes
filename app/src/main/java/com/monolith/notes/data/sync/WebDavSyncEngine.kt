package com.monolith.notes.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class SyncResult(
    val isSuccess: Boolean,
    val uploadedCount: Int = 0,
    val downloadedCount: Int = 0,
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

object WebDavSyncEngine {

    /**
     * Executes two-way delta sync between local vault directory and remote WebDAV server.
     * Supports Zero-Knowledge client-side encryption (E2EE).
     */
    suspend fun sync(
        vaultDir: File,
        config: WebDavConfig,
        onProgress: (String) -> Unit = {}
    ): SyncResult = withContext(Dispatchers.IO) {
        if (!config.isConfigured) {
            return@withContext SyncResult(isSuccess = false, message = "WebDAV server is not configured")
        }

        val client = WebDavClient(config)

        try {
            onProgress("Connecting to WebDAV server...")
            val connResult = client.testConnection()
            if (connResult.isFailure) {
                return@withContext SyncResult(
                    isSuccess = false,
                    message = "Connection failed: ${connResult.exceptionOrNull()?.message}"
                )
            }

            var uploaded = 0
            var downloaded = 0

            // 1. Gather all local syncable files (.md notes and attachments)
            val localFiles = mutableListOf<File>()
            vaultDir.walkTopDown()
                .filter { it.isFile }
                .filter { !it.name.startsWith(".") }
                .filter { !it.absolutePath.contains("/.trash/") }
                .filter { !it.absolutePath.contains("/exports/") }
                .forEach { localFiles.add(it) }

            // 2. Fetch remote files (root and subdirectories)
            onProgress("Inspecting remote files...")
            val remoteFilesMap = mutableMapOf<String, RemoteDavFile>()
            fetchRemoteFilesRecursively(client, "", remoteFilesMap)

            // 3. Sync Local -> Remote (Upload new/modified local files)
            for (localFile in localFiles) {
                val relativePath = localFile.relativeTo(vaultDir).path.replace('\\', '/')
                val remoteFile = remoteFilesMap[relativePath]

                val needsUpload = remoteFile == null || (localFile.lastModified() > (remoteFile.lastModifiedMillis + 2000L))

                if (needsUpload) {
                    onProgress("Uploading $relativePath...")
                    // Ensure remote parent directories exist
                    val parentRel = File(relativePath).parent?.replace('\\', '/')
                    if (parentRel != null && parentRel.isNotBlank()) {
                        ensureRemoteDirectoryStructure(client, parentRel)
                    }

                    val localBytes = localFile.readBytes()
                    val payload = if (config.e2eeEnabled && config.encryptionPassphrase.isNotBlank()) {
                        VaultCrypto.encrypt(localBytes, config.encryptionPassphrase)
                    } else {
                        localBytes
                    }

                    val success = client.uploadFile(relativePath, payload)
                    if (success) uploaded++
                }
            }

            // 4. Sync Remote -> Local (Download new/modified remote files)
            for ((relativePath, remoteFile) in remoteFilesMap) {
                if (remoteFile.isDirectory) continue

                val localTarget = File(vaultDir, relativePath)
                val needsDownload = !localTarget.exists() || (remoteFile.lastModifiedMillis > (localTarget.lastModified() + 2000L))

                if (needsDownload) {
                    onProgress("Downloading $relativePath...")
                    val remoteBytes = client.downloadFile(relativePath)
                    if (remoteBytes != null) {
                        localTarget.parentFile?.mkdirs()

                        val finalBytes = if (VaultCrypto.isEncrypted(remoteBytes)) {
                            if (config.encryptionPassphrase.isNotBlank()) {
                                try {
                                    VaultCrypto.decrypt(remoteBytes, config.encryptionPassphrase)
                                } catch (e: Exception) {
                                    throw IllegalStateException("Decryption failed for $relativePath: Invalid passphrase or corrupted payload", e)
                                }
                            } else {
                                throw IllegalStateException("Cannot download encrypted note $relativePath: No passphrase configured")
                            }
                        } else {
                            remoteBytes
                        }

                        localTarget.writeBytes(finalBytes)
                        if (remoteFile.lastModifiedMillis > 0) {
                            localTarget.setLastModified(remoteFile.lastModifiedMillis)
                        }
                        downloaded++
                    }
                }
            }

            val summary = when {
                uploaded == 0 && downloaded == 0 -> "Vault is already up to date"
                else -> "Synced: $uploaded uploaded, $downloaded downloaded"
            }
            onProgress(summary)
            SyncResult(isSuccess = true, uploadedCount = uploaded, downloadedCount = downloaded, message = summary)
        } catch (e: Exception) {
            SyncResult(isSuccess = false, message = "Sync error: ${e.message}")
        }
    }

    private fun fetchRemoteFilesRecursively(
        client: WebDavClient,
        subPath: String,
        outMap: MutableMap<String, RemoteDavFile>
    ) {
        val list = client.listDirectory(subPath)
        for (item in list) {
            val relPath = if (subPath.isEmpty()) item.name else "$subPath/${item.name}"
            outMap[relPath] = item
            if (item.isDirectory && !item.name.startsWith(".")) {
                fetchRemoteFilesRecursively(client, relPath, outMap)
            }
        }
    }

    private fun ensureRemoteDirectoryStructure(client: WebDavClient, dirPath: String) {
        val parts = dirPath.split('/').filter { it.isNotBlank() }
        var current = ""
        for (part in parts) {
            current = if (current.isEmpty()) part else "$current/$part"
            client.createDirectory(current)
        }
    }
}
