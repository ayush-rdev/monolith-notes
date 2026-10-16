package com.monolith.notes

import com.monolith.notes.model.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteTest {

    @Test
    fun testDisplayTitle() {
        val noteWithTitle = Note(title = "My Explicit Title", content = "Line 1\nLine 2")
        assertEquals("My Explicit Title", noteWithTitle.displayTitle)

        val noteWithoutTitle = Note(title = "", content = "\n\n# Heading from Content\nSome body")
        assertEquals("Heading from Content", noteWithoutTitle.displayTitle)

        val emptyNote = Note(title = "", content = "   \n\n  ")
        assertEquals("New Note", emptyNote.displayTitle)
    }

    @Test
    fun testWordCountAndCharacterCount() {
        val note = Note(
            title = "Swift Performance",
            content = "This is a zero-allocation word scanner test with multiple    spaces and newlines.\nAnother line."
        )
        // Title: 2 words ("Swift", "Performance")
        // Content: 14 words
        // Total: 16 words
        assertEquals(16, note.wordCount)

        val empty = Note(title = "", content = "")
        assertEquals(0, empty.wordCount)
        assertEquals(0, empty.characterCount)

        val spaced = Note(title = "   ", content = "   \n  \t ")
        assertEquals(0, spaced.wordCount)
    }

    @Test
    fun testPreviewSnippetZeroAllocation() {
        val note = Note(
            title = "Sprint Planning",
            content = """
                # Header Line Should Be Skipped
                
                ○ First checklist item to preview
                ✓ Second completed item to preview
                • Bullet item
                Some regular body text
            """.trimIndent()
        )
        val snippet = note.previewSnippet
        assertEquals("First checklist item to preview Second completed item to preview", snippet)
    }

    @Test
    fun testTaskStatsZeroAllocation() {
        val note = Note(
            title = "Task List",
            content = """
                # Tasks
                ○ Item 1
                ✓ Item 2
                - [ ] Item 3
                - [x] Item 4
                * [X] Item 5
                * [ ] Item 6
                Normal line
            """.trimIndent()
        )
        val stats = note.taskStats
        // Total tasks: 6
        // Completed: Item 2 (✓), Item 4 (- [x]), Item 5 (* [X]) -> 3
        assertEquals(Pair(3, 6), stats)

        val noteNoTasks = Note(title = "No Tasks", content = "Just plain text\nAnother line")
        assertNull(noteNoTasks.taskStats)
    }
}
