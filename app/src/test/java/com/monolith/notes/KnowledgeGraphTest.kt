package com.monolith.notes

import com.monolith.notes.model.Note
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class KnowledgeGraphTest {

    @Test
    fun `test graph nodes and edges construction from wikilinks`() {
        val noteA = Note(
            id = "a",
            title = "Alpha Note",
            content = "Linking to [[Beta Note]] and [[Gamma Note]]"
        )
        val noteB = Note(
            id = "b",
            title = "Beta Note",
            content = "Linking back to [[Alpha Note]]"
        )
        val noteC = Note(
            id = "c",
            title = "Gamma Note",
            content = "Linking to [[Delta Note]] which doesn't exist"
        )
        val noteD = Note(
            id = "d",
            title = "Isolated Note",
            content = "No links here"
        )

        val notes = listOf(noteA, noteB, noteC, noteD)

        // Build Title Map
        val noteTitleMap = mutableMapOf<String, Int>()
        notes.forEachIndexed { index, note ->
            noteTitleMap[note.displayTitle.lowercase().trim()] = index
            note.file?.nameWithoutExtension?.let {
                noteTitleMap[it.lowercase().trim()] = index
            }
        }

        // Build Edges and Degrees
        val edgeSet = mutableSetOf<Pair<Int, Int>>()
        val degrees = IntArray(notes.size)

        notes.forEachIndexed { srcIndex, note ->
            for (outlink in note.outlinks) {
                val targetIndex = noteTitleMap[outlink.lowercase().trim()]
                if (targetIndex != null && targetIndex != srcIndex) {
                    val edgeKey = if (srcIndex < targetIndex) Pair(srcIndex, targetIndex) else Pair(targetIndex, srcIndex)
                    if (edgeSet.add(edgeKey)) {
                        degrees[srcIndex]++
                        degrees[targetIndex]++
                    }
                }
            }
        }

        // Assertions:
        // noteA (0) linked with noteB (1) and noteC (2) -> 2 unique edges
        // noteB (1) linked with noteA (0) -> already in edgeSet!
        // Total unique undirected edges = 2 (0-1 and 0-2)
        assertEquals(2, edgeSet.size)
        assertTrue(edgeSet.contains(Pair(0, 1)))
        assertTrue(edgeSet.contains(Pair(0, 2)))

        // Degrees:
        // Alpha Note connected to Beta and Gamma -> degree 2
        assertEquals(2, degrees[0])
        // Beta Note connected to Alpha -> degree 1
        assertEquals(1, degrees[1])
        // Gamma Note connected to Alpha -> degree 1
        assertEquals(1, degrees[2])
        // Isolated Note -> degree 0
        assertEquals(0, degrees[3])
    }

    @Test
    fun `test file name fallback matching for wikilinks`() {
        val tempFile = File.createTempFile("project_architecture", ".md")
        try {
            val noteWithFile = Note(
                id = "arch",
                file = tempFile,
                title = "", // Display title will fallback or empty
                content = "# Architecture Overview"
            )

            val referencingNote = Note(
                id = "ref",
                title = "Index",
                content = "See [[${tempFile.nameWithoutExtension}]]"
            )

            val notes = listOf(noteWithFile, referencingNote)
            val noteTitleMap = mutableMapOf<String, Int>()
            notes.forEachIndexed { index, note ->
                noteTitleMap[note.displayTitle.lowercase().trim()] = index
                note.file?.nameWithoutExtension?.let {
                    noteTitleMap[it.lowercase().trim()] = index
                }
            }

            val targetIndex = noteTitleMap[referencingNote.outlinks.first().lowercase().trim()]
            assertEquals(0, targetIndex)
        } finally {
            tempFile.delete()
        }
    }
}
