package com.monolith.notes.data.sync

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object GoogleDriveSyncEngine {

    /**
     * Synchronizes local markdown notes and attachments with a connected Google Drive / Cloud folder
     * using the Android Storage Access Framework (SAF).
     *
     * Zero API keys required, zero Google Cloud Console registration, works with user's personal Google Drive.
     */
    suspend fun sync(
        vaultDir: File,
        treeUri: Uri,
        context: Context,
        onProgress: (String) -> Unit = {}
    ): SyncResult = withContext(Dispatchers.IO) {
        try {
            onProgress("Connecting to Google Drive folder...")
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
            if (rootDoc == null || !rootDoc.exists() || !rootDoc.canWrite()) {
                return@withContext SyncResult(
                    isSuccess = false,
                    message = "Cannot access Google Drive folder. Please re-connect."
                )
            }

            // Ensure notes are stored in a dedicated 'MonolithNotes' folder in Google Drive
            val syncFolder = if (rootDoc.name.equals("MonolithNotes", ignoreCase = true)) {
                rootDoc
            } else {
                rootDoc.findFile("MonolithNotes") ?: rootDoc.createDirectory("MonolithNotes") ?: rootDoc
            }

            var uploaded = 0
            var downloaded = 0

            // 1. Collect local syncable files
            val localFiles = mutableListOf<File>()
            vaultDir.walkTopDown()
                .filter { it.isFile }
                .filter { !it.name.startsWith(".") }
                .filter { !it.absolutePath.contains("/.trash/") }
                .filter { !it.absolutePath.contains("/exports/") }
                .forEach { localFiles.add(it) }

            // 2. Map remote files in Google Drive folder
            onProgress("Listing files in Google Drive...")
            val remoteDocs = syncFolder.listFiles()
            val remoteMap = mutableMapOf<String, DocumentFile>()
            for (doc in remoteDocs) {
                if (doc.isFile && doc.name != null) {
                    remoteMap[doc.name!!.lowercase()] = doc
                }
            }

            // 3. Local -> Google Drive (Upload new or updated notes)
            for (localFile in localFiles) {
                val fileName = localFile.name
                val remoteDoc = remoteMap[fileName.lowercase()]

                val needsUpload = remoteDoc == null || (localFile.lastModified() > (remoteDoc.lastModified() + 2000L))

                if (needsUpload) {
                    onProgress("Syncing to Drive: $fileName...")
                    val mimeType = when {
                        fileName.endsWith(".md", ignoreCase = true) -> "text/markdown"
                        fileName.endsWith(".png", ignoreCase = true) -> "image/png"
                        fileName.endsWith(".jpg", ignoreCase = true) || fileName.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
                        else -> "application/octet-stream"
                    }

                    val targetDoc = remoteDoc ?: syncFolder.createFile(mimeType, fileName)
                    if (targetDoc != null) {
                        context.contentResolver.openOutputStream(targetDoc.uri, "wt")?.use { outStream ->
                            localFile.inputStream().use { inStream ->
                                inStream.copyTo(outStream)
                            }
                        }
                        remoteMap[fileName.lowercase()] = targetDoc
                        uploaded++
                    }
                }
            }

            // 4. Google Drive -> Local (Download new or updated notes from Drive)
            for ((_, remoteDoc) in remoteMap) {
                val docName = remoteDoc.name ?: continue
                if (!docName.endsWith(".md", ignoreCase = true) && !docName.startsWith("img_")) continue

                val localTarget = File(vaultDir, docName)
                val needsDownload = !localTarget.exists() || (remoteDoc.lastModified() > (localTarget.lastModified() + 2000L))

                if (needsDownload) {
                    onProgress("Downloading from Drive: $docName...")
                    context.contentResolver.openInputStream(remoteDoc.uri)?.use { inStream ->
                        localTarget.outputStream().use { outStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                    if (remoteDoc.lastModified() > 0) {
                        localTarget.setLastModified(remoteDoc.lastModified())
                    }
                    downloaded++
                }
            }

            val summary = when {
                uploaded == 0 && downloaded == 0 -> "Google Drive vault is up to date"
                else -> "Synced with Google Drive: $uploaded uploaded, $downloaded downloaded"
            }
            onProgress(summary)
            SyncResult(isSuccess = true, uploadedCount = uploaded, downloadedCount = downloaded, message = summary)
        } catch (e: Exception) {
            SyncResult(isSuccess = false, message = "Google Drive sync error: ${e.message}")
        }
    }
}
