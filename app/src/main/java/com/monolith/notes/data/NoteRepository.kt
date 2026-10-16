package com.monolith.notes.data

import android.content.Context
import android.os.Environment
import com.monolith.notes.model.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class NoteSortOrder {
    DATE_EDITED,
    DATE_CREATED,
    TITLE
}

class NoteRepository(private val context: Context) {

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    private val _trashNotes = MutableStateFlow<List<Note>>(emptyList())
    val trashNotes: StateFlow<List<Note>> = _trashNotes.asStateFlow()

    private val _folders = MutableStateFlow<List<String>>(listOf("Notes"))
    val folders: StateFlow<List<String>> = _folders.asStateFlow()

    private var currentSortOrder = NoteSortOrder.DATE_EDITED
    private val noteFileMap = ConcurrentHashMap<String, File>()

    val trashDir: File by lazy {
        File(notesDir, ".trash").apply { if (!exists()) mkdirs() }
    }

    val notesDir: File by lazy {
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val vaultDir = File(docsDir, "MonolithNotes")
        if (!vaultDir.exists()) {
            vaultDir.mkdirs()
        }
        if (vaultDir.canWrite()) {
            vaultDir
        } else {
            File(context.getExternalFilesDir(null), "Notes").apply { if (!exists()) mkdirs() }
        }
    }

    val attachmentsDir: File by lazy {
        File(notesDir, "attachments").apply { if (!exists()) mkdirs() }
    }

    suspend fun saveAttachment(uri: android.net.Uri): String? = withContext(Dispatchers.IO) {
        try {
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val extension = when {
                mimeType.contains("png", ignoreCase = true) -> "png"
                mimeType.contains("webp", ignoreCase = true) -> "webp"
                mimeType.contains("gif", ignoreCase = true) -> "gif"
                else -> "jpg"
            }
            val timestamp = System.currentTimeMillis()
            val targetFile = File(attachmentsDir, "img_${timestamp}.$extension")
            context.contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            "attachments/${targetFile.name}"
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun refreshFolders() {
        val subDirs = notesDir.listFiles { file ->
            file.isDirectory && !file.name.startsWith(".") && file.name != "attachments"
        }?.map { it.name }?.sorted() ?: emptyList()
        _folders.value = listOf("Notes") + subDirs
    }

    private fun collectMarkdownFiles(dir: File): List<File> {
        val result = mutableListOf<File>()
        val files = dir.listFiles() ?: return result
        for (file in files) {
            if (file.name.startsWith(".")) continue
            if (file.isDirectory) {
                result.addAll(collectMarkdownFiles(file))
            } else if (file.isFile && file.extension.equals("md", ignoreCase = true)) {
                result.add(file)
            }
        }
        return result
    }

    suspend fun loadNotes() = withContext(Dispatchers.IO) {
        refreshFolders()
        val allFiles = collectMarkdownFiles(notesDir)
        val sortedFiles = allFiles.sortedByDescending { it.lastModified() }

        // Multi-threaded parallel I/O: read and parse files concurrently across CPU cores
        val parsedEntries = coroutineScope {
            sortedFiles.map { file ->
                async(Dispatchers.IO) {
                    val raw = try { file.readText() } catch (e: Exception) { "" }
                    file to parseNoteFromFile(file, raw)
                }
            }.awaitAll()
        }

        // Deduplicate any ghost drafts or twin files accidentally created by double saves
        val loaded = mutableListOf<Note>()
        val seenTitles = mutableMapOf<String, Pair<File, Long>>()

        for ((file, parsed) in parsedEntries) {
            val normalizedTitle = parsed.displayTitle.trim().lowercase()

            if (normalizedTitle.isNotBlank() && !file.name.contains("Copy", ignoreCase = true)) {
                val existing = seenTitles[normalizedTitle]
                if (existing != null) {
                    val (existingFile, existingLength) = existing
                    val timeDiff = kotlin.math.abs(existingFile.lastModified() - file.lastModified())
                    // If created within 15 min or identical file length, it's an accidental ghost draft: delete older
                    if (timeDiff < 15 * 60 * 1000L || existingLength == file.length()) {
                        try { file.delete() } catch (_: Exception) {}
                        continue
                    }
                } else {
                    seenTitles[normalizedTitle] = file to file.length()
                }
            }
            noteFileMap[parsed.id] = file
            loaded.add(parsed)
        }

        val welcomeFile = File(notesDir, "Welcome_to_Notes.md")
        if (welcomeFile.exists()) {
            val raw = try { welcomeFile.readText() } catch (_: Exception) { "" }
            if (raw.contains("The official Apple Notes experience on Android.") && !raw.contains("ayush-rdev")) {
                try { welcomeFile.writeText(WELCOME_MARKDOWN) } catch (_: Exception) {}
            }
        }

        if (loaded.isEmpty()) {
            // Seed default welcome note with Apple Notes styling
            try { welcomeFile.writeText(WELCOME_MARKDOWN) } catch (_: Exception) {}
            val welcomeNote = parseNoteFromFile(welcomeFile, WELCOME_MARKDOWN)
            noteFileMap[welcomeNote.id] = welcomeFile
            _notes.value = listOf(welcomeNote)
        } else {
            _notes.value = sortNotesList(loaded, currentSortOrder)
        }
        loadTrashNotes()
    }

    suspend fun saveNote(note: Note): Note = withContext(Dispatchers.IO) {
        if (note.title.isBlank() && note.content.isBlank()) {
            return@withContext note
        }

        val targetFolderDir = if (note.folder != "Notes" && note.folder.isNotBlank()) {
            File(notesDir, note.folder).apply { if (!exists()) mkdirs() }
        } else {
            notesDir
        }

        // Resolves target file: check note.file first, then noteFileMap by note.id, then create new file
        val targetFile = note.file ?: noteFileMap[note.id] ?: run {
            val safeTitle = note.title.ifBlank { "Note" }
                .replace("[^a-zA-Z0-9_-]".toRegex(), "_")
                .take(30)
            val shortId = note.id.replace("-", "").take(8)
            File(targetFolderDir, "${safeTitle}_$shortId.md")
        }

        // Anchor the mapping so any subsequent or concurrent save to this note ID writes to the SAME file
        noteFileMap[note.id] = targetFile
        noteFileMap[targetFile.absolutePath] = targetFile

        // Convert Apple Notes UI text back to standard Obsidian Markdown
        val markdownContent = MarkdownConverter.toMarkdown(note.title, note.content, isPinned = note.isPinned)

        try {
            targetFile.writeText(markdownContent)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val parsed = parseNoteFromFile(targetFile, markdownContent)
        // Keep note.id stable so Composable state isn't reset
        val updatedNote = parsed.copy(id = note.id, file = targetFile, folder = parsed.folder)

        val currentList = _notes.value.toMutableList()
        val index = currentList.indexOfFirst {
            it.id == note.id || it.id == targetFile.absolutePath || it.file?.path == targetFile.path
        }
        if (index >= 0) {
            currentList[index] = updatedNote
        } else {
            currentList.add(0, updatedNote)
        }
        _notes.value = sortNotesList(currentList, currentSortOrder)
        refreshFolders()
        updatedNote
    }

    suspend fun moveNoteToFolder(note: Note, targetFolderName: String): Note = withContext(Dispatchers.IO) {
        val currentFile = note.file ?: noteFileMap[note.id] ?: return@withContext note
        val targetDir = if (targetFolderName == "Notes") {
            notesDir
        } else {
            File(notesDir, targetFolderName).apply { if (!exists()) mkdirs() }
        }

        val newFile = File(targetDir, currentFile.name)
        val finalTargetFile = if (newFile.exists() && newFile.absolutePath != currentFile.absolutePath) {
            val timestamp = System.currentTimeMillis()
            File(targetDir, "${currentFile.nameWithoutExtension}_$timestamp.md")
        } else {
            newFile
        }

        if (currentFile.absolutePath != finalTargetFile.absolutePath) {
            currentFile.renameTo(finalTargetFile)
            noteFileMap.remove(note.id)
            noteFileMap.remove(currentFile.absolutePath)
            noteFileMap[finalTargetFile.absolutePath] = finalTargetFile
        }

        val raw = try { finalTargetFile.readText() } catch (_: Exception) { "" }
        val updated = parseNoteFromFile(finalTargetFile, raw).copy(id = note.id, folder = targetFolderName)
        noteFileMap[note.id] = finalTargetFile

        val currentList = _notes.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == note.id || it.file?.path == currentFile.path }
        if (index >= 0) {
            currentList[index] = updated
        } else {
            currentList.add(0, updated)
        }
        _notes.value = sortNotesList(currentList, currentSortOrder)
        refreshFolders()
        updated
    }

    suspend fun createFolder(folderName: String): Boolean = withContext(Dispatchers.IO) {
        val cleanName = folderName.trim().replace("[\\\\/:*?\"<>|]".toRegex(), "")
        if (cleanName.isBlank() || cleanName.equals("Notes", ignoreCase = true)) return@withContext false
        val dir = File(notesDir, cleanName)
        val created = if (!dir.exists()) dir.mkdirs() else true
        refreshFolders()
        created
    }

    suspend fun deleteFolder(folderName: String): Boolean = withContext(Dispatchers.IO) {
        if (folderName.equals("Notes", ignoreCase = true)) return@withContext false
        val dir = File(notesDir, folderName)
        if (!dir.exists() || !dir.isDirectory) return@withContext false

        // Move all notes in this folder to Trash
        val folderNotes = _notes.value.filter { it.folderName.equals(folderName, ignoreCase = true) }
        for (note in folderNotes) {
            moveToTrash(note)
        }
        val deleted = dir.deleteRecursively()
        refreshFolders()
        deleted
    }

    suspend fun togglePin(note: Note) = withContext(Dispatchers.IO) {
        val updated = note.copy(isPinned = !note.isPinned)
        saveNote(updated)
    }

    suspend fun setSortOrder(order: NoteSortOrder) = withContext(Dispatchers.IO) {
        currentSortOrder = order
        _notes.value = sortNotesList(_notes.value, currentSortOrder)
    }

    suspend fun loadTrashNotes() = withContext(Dispatchers.IO) {
        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        val files = trashDir.listFiles { file -> file.isFile && file.extension.equals("md", ignoreCase = true) }
            ?: emptyArray()

        val activeTrash = mutableListOf<Note>()
        for (file in files) {
            if (file.lastModified() < thirtyDaysAgo) {
                try { file.delete() } catch (_: Exception) {}
            } else {
                val raw = try { file.readText() } catch (e: Exception) { "" }
                activeTrash.add(parseNoteFromFile(file, raw))
            }
        }
        _trashNotes.value = activeTrash.sortedByDescending { it.lastModified }
    }

    suspend fun moveToTrash(note: Note) = withContext(Dispatchers.IO) {
        val fileToDelete = note.file ?: noteFileMap[note.id]
        if (fileToDelete != null && fileToDelete.exists()) {
            val destination = File(trashDir, fileToDelete.name)
            val targetTrashFile = if (destination.exists()) {
                val timestamp = System.currentTimeMillis()
                File(trashDir, "${fileToDelete.nameWithoutExtension}_$timestamp.md")
            } else {
                destination
            }
            fileToDelete.renameTo(targetTrashFile)
        }
        noteFileMap.remove(note.id)
        if (fileToDelete != null) {
            noteFileMap.remove(fileToDelete.absolutePath)
        }
        _notes.value = _notes.value.filter {
            it.id != note.id && it.file?.path != fileToDelete?.path
        }
        loadTrashNotes()
        refreshFolders()
    }

    suspend fun deleteNote(note: Note) = moveToTrash(note)

    suspend fun restoreNote(note: Note): Note = withContext(Dispatchers.IO) {
        val trashFile = note.file ?: File(trashDir, "${note.displayTitle}.md")
        val destination = File(notesDir, trashFile.name)
        val targetFile = if (destination.exists()) {
            val timestamp = System.currentTimeMillis()
            File(notesDir, "${trashFile.nameWithoutExtension}_restored_$timestamp.md")
        } else {
            destination
        }
        trashFile.renameTo(targetFile)
        val raw = try { targetFile.readText() } catch (e: Exception) { "" }
        val restored = parseNoteFromFile(targetFile, raw)
        noteFileMap[restored.id] = targetFile

        _trashNotes.value = _trashNotes.value.filter { it.id != note.id }
        val currentList = _notes.value.toMutableList()
        currentList.add(0, restored)
        _notes.value = sortNotesList(currentList, currentSortOrder)
        refreshFolders()
        restored
    }

    suspend fun permanentlyDelete(note: Note) = withContext(Dispatchers.IO) {
        val trashFile = note.file ?: File(trashDir, note.id)
        try { trashFile.delete() } catch (_: Exception) {}
        _trashNotes.value = _trashNotes.value.filter { it.id != note.id }
    }

    suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        try {
            trashDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _trashNotes.value = emptyList()
    }

    suspend fun duplicateNote(note: Note): Note = withContext(Dispatchers.IO) {
        val originalFile = note.file ?: noteFileMap[note.id]
        val targetDir = originalFile?.parentFile ?: notesDir
        val safeTitle = note.title.ifBlank { "Note" }
        val newTitle = "$safeTitle Copy"
        val timestamp = System.currentTimeMillis()
        val newFile = File(targetDir, "${newTitle.replace("[^a-zA-Z0-9_-]".toRegex(), "_")}_$timestamp.md")

        val duplicated = note.copy(
            id = newFile.absolutePath,
            file = newFile,
            title = newTitle,
            lastModified = System.currentTimeMillis()
        )
        noteFileMap[duplicated.id] = newFile
        saveNote(duplicated)
    }

    suspend fun exportVaultToZip(): File? = withContext(Dispatchers.IO) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val exportFile = File(notesDir, "MonolithNotes_Backup_$timestamp.zip")
            java.util.zip.ZipOutputStream(java.io.FileOutputStream(exportFile)).use { zipOut ->
                fun addDirToZip(currentDir: File, baseDir: File) {
                    val files = currentDir.listFiles() ?: return
                    for (file in files) {
                        if (file.name.startsWith(".") || file.name.endsWith(".zip")) continue
                        if (file.isDirectory) {
                            addDirToZip(file, baseDir)
                        } else if (file.isFile && file.extension.equals("md", ignoreCase = true)) {
                            val relativePath = baseDir.toURI().relativize(file.toURI()).path
                            zipOut.putNextEntry(java.util.zip.ZipEntry(relativePath))
                            file.inputStream().use { input -> input.copyTo(zipOut) }
                            zipOut.closeEntry()
                        }
                    }
                }
                addDirToZip(notesDir, notesDir)
            }
            exportFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun sortNotesList(list: List<Note>, order: NoteSortOrder): List<Note> {
        val pinned = list.filter { it.isPinned }
        val unpinned = list.filter { !it.isPinned }

        val comparator = when (order) {
            NoteSortOrder.DATE_EDITED -> compareByDescending<Note> { it.lastModified }
            NoteSortOrder.DATE_CREATED -> compareBy<Note> { it.file?.nameWithoutExtension ?: "" }
            NoteSortOrder.TITLE -> compareBy { it.displayTitle.lowercase() }
        }

        return pinned.sortedWith(comparator) + unpinned.sortedWith(comparator)
    }

    private fun parseNoteFromFile(file: File, rawContent: String): Note {
        val (cleanTitle, cleanBody, isPinned) = MarkdownConverter.toUi(rawContent, file.nameWithoutExtension)
        val tags = TAG_REGEX.findAll(rawContent).map { it.groupValues[1] }.distinct().toList()
        val folder = if (file.parentFile != null && file.parentFile != notesDir && !file.parentFile!!.name.startsWith(".")) {
            file.parentFile!!.name
        } else {
            "Notes"
        }

        return Note(
            id = file.absolutePath,
            file = file,
            title = cleanTitle,
            content = cleanBody,
            tags = tags,
            lastModified = file.lastModified(),
            isPinned = isPinned,
            folder = folder
        )
    }

    companion object {
        private val TAG_REGEX = "(?:^|\\s)#([a-zA-Z0-9_\\-/]+)".toRegex()

        val WELCOME_MARKDOWN = """
            # Welcome to Monolith Notes

            To the thinkers, the dreamers, and the builders.

            We built Monolith because we believe your thoughts are sacred. In a world where every app demands your credit card, stores your private words in distant corporate clouds, tracks your every tap, and locks you behind proprietary databases, Monolith chooses a different path.

            Here, you are sovereign. Every note you write is saved as a clean, plain CommonMark `.md` file directly on your device. Zero telemetry. Zero accounts. Zero cloud lock-in. If this app disappears fifty years from now, your notes will still be yours—ready to open in Obsidian, VS Code, or whatever comes next.

            ---

            ### 📖 Complete Feature Guide

            Here is everything you need to know to master your new workspace:

            #### 1. 📁 Folders & Subdirectory Vaults
            • Tap `< Folders` in the top left to organize your thoughts into notebooks.
            • Folders in Monolith are real directories in `Documents/MonolithNotes/`.
            • 1:1 bidirectional sync with Obsidian desktop vaults.

            #### 2. 🔒 Hardware Biometric Lock
            • Protect private journals with hardware-backed fingerprint and face authentication.
            • Toggle **Biometric Lock** in Settings.
            • The vault instantly shields itself the second you switch apps or turn off your screen.

            #### 3. 🗑️ 30-Day Trash & Recovery
            • Never worry about accidental deletions.
            • Deleted notes rest safely in `.trash/` with a 30-day recovery countdown.
            • Access and restore notes anytime from **Settings → Recently Deleted**.

            #### 4. 🖼️ Inline Media & Attachments
            • Tap the camera icon on the formatting bar to insert photos.
            • Images are copied directly into your vault's `attachments/` folder.
            • Tap any photo to expand into a high-res full-screen preview.

            #### 5. ⚡ Gestures & Apple Spring Physics
            • **Swipe Right** on any note card to pin it to the top.
            • **Swipe Left** to move it smoothly to Trash.
            • **Long-Press** for quick actions: Duplicate, Move to Folder, Share.
            • Ultra-low 0ms typing response synchronized with 120Hz display refresh.

            ---

            ### ✨ Your First Steps

            - [x] Experience 0ms typing response
            - [ ] Write your first honest thought
            - [ ] Explore the Folders drawer
            - [ ] Turn on Biometric Lock in Settings
            - [ ] Star the project on GitHub: github.com/ayush-rdev/monolith-notes

            ---

            ### 🌐 Open Source & Legacy

            Monolith Notes is 100% free and open-source software under the MIT License.
            Crafted with passion, stubborn engineering, and care by **Ayush** ([@ayush-rdev](https://github.com/ayush-rdev)).

            GitHub: **https://github.com/ayush-rdev/monolith-notes**

            *Write fearlessly. Your mind belongs to you.*

            #welcome #manifesto #freedom #craft #opensource
        """.trimIndent()
    }
}
