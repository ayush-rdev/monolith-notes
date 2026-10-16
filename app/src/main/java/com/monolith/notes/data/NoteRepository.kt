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
            if (!raw.contains("Arthur Morgan") && (raw.contains("Welcome to") || raw.contains("Philosophy") || raw.contains("Monolith"))) {
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
            # 📓 Arthur's Journal (Welcome to Monolith)

            *“We’re more ghosts than people, but what we write down... that stays.”*

            Listen to me, friend.

            I ain’t much of a poet, and Lord knows I’ve spent most of my days with blood on my hands, cold wind in my face, and dust in my throat. But whenever the shooting stopped, and the camp fell quiet under the cold Dakota night... I’d reach into my satchel and pull out my journal.

            Just a bit of worn leather, some blank paper, and a scrap of lead pencil.

            It was the only place in this whole cruel world where a man could speak the truth without looking over his shoulder. When everything was falling apart around me, the words and sketches I put down were the only damn thing that were truly mine.

            Nowadays, folk tell me people hand their memories over to invisible wires in the sky, paying some fat cat in a fancy suit every month just to look at their own past. That ain't right. A man’s mind ought to belong to him, and him alone.

            That’s why my friend **Ayush** ([@ayush-rdev](https://github.com/ayush-rdev)) built this thing. No subscriptions. No snooping Pinkertons. No cloud lock-in. Just honest CommonMark `.md` files sitting right on your machine in `Documents/MonolithNotes/`. If every company in the world burns down tomorrow, your words will still be sitting right here, untouched.

            ---

            ### 🐎 A Trail Guide from an Old Outlaw

            Here’s how you keep your journal in order out here on the frontier:

            #### 1. 📁 The Camp & The Satchel (Folders & Notebooks)
            • Tap `< Folders` in the top corner to organize your notes into separate ledgers.
            • Keep your bounties, your maps, and your private thoughts in their own space.
            • Every folder is a real directory on your storage—syncs 1:1 with Obsidian if that’s your camp.

            #### 2. 🔒 Lock Up Your Secrets (Biometric Privacy)
            • If lawmen or curious eyes are sniffing around your camp, head into **Settings** and turn on **Biometric Lock**.
            • Your thumbprint or face is the only key.
            • The second you put this contraption down or switch screens, it shuts tight like a bank vault.

            #### 3. 🗑️ Pulling Notes from the Fire (30-Day Recovery)
            • We all make mistakes on the trail. If you tear a page out by accident, don’t curse just yet.
            • Deleted notes sit quietly in `.trash/` for 30 days before they turn to ashes.
            • Recover them anytime from **Settings → Recently Deleted**.

            #### 4. 🖼️ Sketches & Keepsakes (Inline Photos)
            • I always liked sketching what I saw—deer drinking at the creek, the mountains at dawn, faces I didn't want to forget.
            • Tap that camera button on your formatting bar to slip pictures right between your words.
            • Saved into your local `attachments/` folder. Tap any picture to open it up wide.

            #### 5. ⚡ Fast on the Draw (Apple Spring Physics & 0ms Lag)
            • **Swipe Right** on a note to pin it to the top of your ledger.
            • **Swipe Left** to toss it into the trash.
            • **Long-Press** for quick actions.
            • 0ms typing response. Fast as drawing a cattleman revolver, and smooth as a good horse running downhill.

            ---

            ### ✍️ Before You Ride Out

            - [x] Picked up this old leather journal
            - [ ] Write down one true thing about yourself today
            - [ ] Create a folder for the things that matter
            - [ ] Set your biometric lock so no one pries
            - [ ] Tip your hat to Ayush on GitHub: github.com/ayush-rdev/monolith-notes

            ---

            ### 🌅 One Last Thing...

            Take care of yourself, alright? There ain't no shame in looking back, but you've got to keep moving forward. Be a good man. Or at least try your best to be one.

            This app is 100% free and open-source forever under the MIT License.
            Crafted with real sweat, soul, and pride by **Ayush** ([@ayush-rdev](https://github.com/ayush-rdev)).

            Star the repository on GitHub:
            👉 **https://github.com/ayush-rdev/monolith-notes**

            *“I gave you all I had. I did.”*
            — **Arthur Morgan**

            #arthur #journal #redemption #manifesto #freedom #craft
        """.trimIndent()
    }
}
