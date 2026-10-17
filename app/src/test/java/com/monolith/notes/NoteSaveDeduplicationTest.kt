package com.monolith.notes

import com.monolith.notes.model.Note
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

class NoteSaveDeduplicationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testShortIdExtraction() {
        val uuid = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
        val shortId = uuid.replace("-", "").take(8)
        assertEquals("3fa85f64", shortId)
        assertEquals(8, shortId.length)
    }

    @Test
    fun testGhostDraftCleanupLogic() {
        val vaultDir = tempFolder.newFolder("MonolithNotes")
        val shortId = "a1b2c3d4"

        // Simulate 4 autosaves with changing titles leaving files on disk
        val file1 = File(vaultDir, "H_$shortId.md").apply {
            writeText("H")
            setLastModified(1000L)
        }
        val file2 = File(vaultDir, "Hel_$shortId.md").apply {
            writeText("Hel")
            setLastModified(2000L)
        }
        val file3 = File(vaultDir, "Hello_$shortId.md").apply {
            writeText("Hello")
            setLastModified(3000L)
        }
        val file4 = File(vaultDir, "Hello World_$shortId.md").apply {
            writeText("Hello World")
            setLastModified(4000L)
        }

        val allFiles = vaultDir.listFiles()?.filter { it.isFile && it.extension.equals("md", ignoreCase = true) } ?: emptyList()
        assertEquals(4, allFiles.size)

        val shortIdRegex = "_([a-fA-F0-9]{8})\\.md$".toRegex()
        val validFiles = mutableListOf<File>()
        val groupedByShortId = allFiles.groupBy { file ->
            shortIdRegex.find(file.name)?.value
        }

        for ((shortIdSuffix, files) in groupedByShortId) {
            if (shortIdSuffix != null && files.size > 1) {
                val newest = files.maxByOrNull { it.lastModified() } ?: files.first()
                validFiles.add(newest)
                for (older in files) {
                    if (older.absolutePath != newest.absolutePath) {
                        older.delete()
                    }
                }
            } else {
                validFiles.addAll(files)
            }
        }

        assertEquals(1, validFiles.size)
        assertEquals(file4.name, validFiles[0].name)
        assertFalse("file1 should be deleted", file1.exists())
        assertFalse("file2 should be deleted", file2.exists())
        assertFalse("file3 should be deleted", file3.exists())
        assertTrue("file4 (newest) must exist", file4.exists())
        assertEquals("Hello World", file4.readText())
    }

    @Test
    fun testListDeduplicationByShortIdAndPath() {
        val shortId = "550e8400"
        val oldFile = File("/mock/dir/Draft_$shortId.md")
        val newFile = File("/mock/dir/Final_$shortId.md")

        val noteId = "550e8400-e29b-41d4-a716-446655440000"

        val noteSnapshot1 = Note(
            id = noteId,
            file = oldFile,
            title = "Draft",
            content = "1 line",
            lastModified = 1000L
        )
        val noteSnapshot2 = Note(
            id = oldFile.absolutePath,
            file = oldFile,
            title = "Draft",
            content = "2 lines",
            lastModified = 2000L
        )

        val currentList = mutableListOf(noteSnapshot1, noteSnapshot2)

        val updatedNote = Note(
            id = noteId,
            file = newFile,
            title = "Final",
            content = "Full content with 4 lines",
            lastModified = 4000L
        )

        currentList.removeAll {
            it.id == updatedNote.id ||
            it.id == newFile.absolutePath ||
            it.file?.absolutePath == newFile.absolutePath ||
            (it.file?.name?.endsWith("_$shortId.md") == true)
        }
        currentList.add(0, updatedNote)

        assertEquals(1, currentList.size)
        assertEquals("Final", currentList[0].title)
        assertEquals(newFile.absolutePath, currentList[0].file?.absolutePath)
        assertEquals("Full content with 4 lines", currentList[0].content)
    }

    @Test
    fun testDynamicFileRenamingOnTitleChange() {
        val vaultDir = tempFolder.newFolder("RenameTest")
        val shortId = "cafebabe"
        val oldFile = File(vaultDir, "Note_$shortId.md").apply {
            writeText("# Draft")
        }
        assertTrue(oldFile.exists())

        val newTitle = "Engineering Spec"
        val expectedFileName = "${newTitle}_$shortId.md"
        val renamedFile = File(vaultDir, expectedFileName)

        if (oldFile.exists() && oldFile.name.endsWith("_$shortId.md") && oldFile.name != expectedFileName) {
            val success = oldFile.renameTo(renamedFile)
            assertTrue(success)
        }

        assertFalse(oldFile.exists())
        assertTrue(renamedFile.exists())
        assertEquals("Engineering Spec_cafebabe.md", renamedFile.name)
    }
}
