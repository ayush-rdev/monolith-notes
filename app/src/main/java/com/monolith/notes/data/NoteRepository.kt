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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private var currentSortOrder = NoteSortOrder.DATE_EDITED
    private val noteFileMap = ConcurrentHashMap<String, File>()
    private val saveMutex = Mutex()

    private val fastCacheFile: File by lazy {
        File(context.cacheDir, "monolith_notes_cache.json")
    }

    init {
        loadFastCache()
    }

    private fun loadFastCache() {
        try {
            if (!fastCacheFile.exists()) {
                val welcomeFile = File(notesDir, "Welcome_to_Notes.md")
                if (welcomeFile.exists()) {
                    val raw = try { welcomeFile.readText() } catch (_: Exception) { "" }
                    if (raw.isNotBlank()) {
                        val welcomeNote = parseNoteFromFile(welcomeFile, raw)
                        noteFileMap[welcomeNote.id] = welcomeFile
                        _notes.value = listOf(welcomeNote)
                        _isLoaded.value = true
                    }
                }
                return
            }

            val json = fastCacheFile.readText()
            if (json.isBlank()) return
            val array = org.json.JSONArray(json)
            val list = ArrayList<Note>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val filePath = obj.optString("file")
                val targetFile = if (filePath.isNotEmpty()) File(filePath) else null
                val id = obj.optString("id")
                val title = obj.optString("title")
                val content = obj.optString("content")
                val lastModified = obj.optLong("lastModified", System.currentTimeMillis())
                val isPinned = obj.optBoolean("isPinned", false)
                val folder = obj.optString("folder", "Notes")

                val note = Note(
                    id = id,
                    file = targetFile,
                    title = title,
                    content = content,
                    tags = TAG_REGEX.findAll(content).map { it.groupValues[1] }.distinct().toList(),
                    lastModified = lastModified,
                    isPinned = isPinned,
                    folder = folder
                )
                if (targetFile != null) {
                    noteFileMap[note.id] = targetFile
                }
                list.add(note)
            }
            if (list.isNotEmpty()) {
                _notes.value = sortNotesList(list, currentSortOrder)
                _isLoaded.value = true
            }
        } catch (_: Exception) {
            // Silently fall back to loadNotes
        }
    }

    private fun saveFastCache(notesList: List<Note>) {
        try {
            val array = org.json.JSONArray()
            for (note in notesList) {
                val obj = org.json.JSONObject().apply {
                    put("id", note.id)
                    put("file", note.file?.absolutePath ?: "")
                    put("title", note.title)
                    put("content", note.content)
                    put("lastModified", note.lastModified)
                    put("isPinned", note.isPinned)
                    put("folder", note.folder)
                }
                array.put(obj)
            }
            fastCacheFile.writeText(array.toString())
        } catch (_: Exception) {}
    }

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

        val loaded = mutableListOf<Note>()
        for ((file, parsed) in parsedEntries) {
            noteFileMap[parsed.id] = file
            loaded.add(parsed)
        }

        val welcomeFile = File(notesDir, "Welcome_to_Notes.md")
        if (welcomeFile.exists()) {
            val raw = try { welcomeFile.readText() } catch (_: Exception) { "" }
            if (raw.contains("Arthur Morgan") || raw.contains("Apple Notes") || raw.contains("Philosophy\n• File Over App") || !raw.contains("The Foundation")) {
                try {
                    welcomeFile.writeText(WELCOME_MARKDOWN)
                    val updatedWelcome = parseNoteFromFile(welcomeFile, WELCOME_MARKDOWN)
                    val idx = loaded.indexOfFirst { it.file?.name == "Welcome_to_Notes.md" }
                    if (idx >= 0) {
                        loaded[idx] = updatedWelcome
                    }
                } catch (_: Exception) {}
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
        _isLoaded.value = true
        saveFastCache(_notes.value)
        loadTrashNotes()
    }

    suspend fun saveNote(note: Note): Note = withContext(Dispatchers.IO) {
        if (note.title.isBlank() && note.content.isBlank() && note.file == null) {
            return@withContext note
        }

        saveMutex.withLock {
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
            saveFastCache(_notes.value)
            refreshFolders()
            updatedNote
        }
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
        saveFastCache(_notes.value)
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
        saveFastCache(_notes.value)
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
        saveFastCache(_notes.value)
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

    suspend fun getOrCreateNote(title: String, folder: String = "Notes"): Note = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim()
        val existing = _notes.value.firstOrNull { note ->
            note.displayTitle.equals(cleanTitle, ignoreCase = true) ||
            (note.file != null && note.file.nameWithoutExtension.equals(cleanTitle, ignoreCase = true))
        }
        if (existing != null) {
            return@withContext existing
        }
        val newNote = Note(
            title = cleanTitle,
            content = "",
            lastModified = System.currentTimeMillis(),
            folder = folder
        )
        saveNote(newNote)
    }

    fun getBacklinks(targetNote: Note): List<Note> {
        val targetTitle = targetNote.displayTitle.trim()
        val targetFileName = targetNote.file?.nameWithoutExtension?.trim() ?: ""
        val allNotes = _notes.value
        return allNotes.filter { note ->
            note.id != targetNote.id && note.outlinks.any { link ->
                link.equals(targetTitle, ignoreCase = true) ||
                (targetFileName.isNotEmpty() && link.equals(targetFileName, ignoreCase = true))
            }
        }
    }

    fun findLinkContext(content: String, targetTitle: String, targetFileName: String? = null): String {
        for (line in content.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val lower = trimmed.lowercase()
            val titleMatch = lower.contains("[[${targetTitle.lowercase()}]") || lower.contains("[[${targetTitle.lowercase()}|")
            val fileMatch = targetFileName?.let { lower.contains("[[${it.lowercase()}]") || lower.contains("[[${it.lowercase()}|") } ?: false
            if (titleMatch || fileMatch) {
                return trimmed.removePrefix("#").removePrefix("○").removePrefix("✓").removePrefix("•").trim()
            }
        }
        return content.take(120).trim()
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
            # Welcome to Monolith

            A local-first, distraction-free markdown canvas.

            ### The Foundation
            • **File Over App**: Every note is a plain CommonMark `.md` file stored locally in `Documents/MonolithNotes/`.
            • **Zero Lock-in**: 100% interoperable with Obsidian, Logseq, and text editors.
            • **Privacy by Design**: 0 telemetry, 0 cloud dependencies, 100% offline.

            ### Key Capabilities
            • **Bi-directional WikiLinks**: Type `[[` to instantly link thoughts, autocomplete vault notes, and explore backlinks.
            • **Folders**: Tap `< Folders` to organize notes into local subdirectories.
            • **Biometric Lock**: Protect private thoughts with hardware biometric authentication.
            • **Recently Deleted**: 30-day soft-delete safety net in `.trash/`.
            • **Media**: Attach inline photos stored in `attachments/`.
            • **Speed**: 0ms typing response with fluid gesture navigation.

            ### Quick Start
            - [x] Initialized local vault
            - [ ] Write your first note
            - [ ] Try linking a thought with [[Ideas]]
            - [ ] Create a custom folder in the Folders drawer
            - [ ] Configure Biometric Lock in Settings

            ---

            Open source under the MIT License by [Ayush](https://github.com/ayush-rdev).
            Repository: https://github.com/ayush-rdev/monolith-notes

            #welcome #manifesto #notes
        """.trimIndent()
    }
}
