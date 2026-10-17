package com.monolith.notes

import com.monolith.notes.model.Note
import com.monolith.notes.ui.editor.detectWikiLinkQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WikiLinkTest {

    @Test
    fun testOutlinksExtraction() {
        val note = Note(
            title = "Project Planning",
            content = """
                Check out [[Architecture Design]] and [[Backend API|Server Specification]] for details.
                Also review [[Architecture Design]] again (should deduplicate).
                Do not match images like ![[diagram.png]].
                And dangling [[   ]] empty links should be handled.
            """.trimIndent()
        )

        val outlinks = note.outlinks
        assertEquals(2, outlinks.size)
        assertTrue(outlinks.contains("Architecture Design"))
        assertTrue(outlinks.contains("Backend API"))
    }

    @Test
    fun testDetectWikiLinkQuery() {
        // 1. Just typed "[["
        val text1 = "Let's link to [["
        val match1 = detectWikiLinkQuery(text1, text1.length)
        assertEquals(14, match1?.first)
        assertEquals("", match1?.second)

        // 2. Typing query: "[[Arch"
        val text2 = "Let's link to [[Arch"
        val match2 = detectWikiLinkQuery(text2, text2.length)
        assertEquals(14, match2?.first)
        assertEquals("Arch", match2?.second)

        // 3. Completed link: "[[Architecture]] " (cursor at end)
        val text3 = "Let's link to [[Architecture]] and more"
        val match3 = detectWikiLinkQuery(text3, text3.indexOf("and"))
        assertNull(match3)

        // 4. Cursor right between "[[" and "]]" in "[[]]"
        val text4 = "Here is [[]] an empty link"
        val match4 = detectWikiLinkQuery(text4, text4.indexOf("]]"))
        assertEquals(8, match4?.first)
        assertEquals("", match4?.second)

        // 5. Multi-line note: cursor on line 2 after "[["
        val text5 = "Line 1\nLine 2 has [[Target"
        val match5 = detectWikiLinkQuery(text5, text5.length)
        val expectedOpenIdx = text5.indexOf("[[")
        assertEquals(expectedOpenIdx, match5?.first)
        assertEquals("Target", match5?.second)

        // 6. Inside alias "[[Target|"
        val text6 = "Link to [[Target|"
        val match6 = detectWikiLinkQuery(text6, text6.length)
        assertNull(match6)
    }

    @Test
    fun testBacklinkResolution() {
        val noteA = Note(
            id = "note-a",
            title = "Frontend Architecture",
            content = "This frontend module connects with [[Core Engine]]."
        )

        val noteB = Note(
            id = "note-b",
            title = "Database Migration",
            content = "We store data for [[core engine|Core]] and [[Analytics Service]]."
        )

        val targetNote = Note(
            id = "note-core",
            file = File("/sdcard/Documents/MonolithNotes/Core Engine.md"),
            title = "Core Engine",
            content = "The heart of Monolith."
        )

        val allNotes = listOf(noteA, noteB, targetNote)

        val targetFileName = targetNote.file?.nameWithoutExtension ?: ""

        // Helper simulation of NoteRepository.getBacklinks
        val backlinks = allNotes.filter { note ->
            note.id != targetNote.id && note.outlinks.any { link ->
                link.equals(targetNote.displayTitle, ignoreCase = true) ||
                (targetFileName.isNotEmpty() && link.equals(targetFileName, ignoreCase = true))
            }
        }

        assertEquals(2, backlinks.size)
        assertTrue(backlinks.any { it.id == "note-a" })
        assertTrue(backlinks.any { it.id == "note-b" })
    }

    @Test
    fun testLinkContextExtraction() {
        val noteContent = """
            # Meeting Notes
            
            ○ Discussed hiring plan
            • Important: Review [[Architecture Guidelines]] before pushing PR
            ✓ Completed initial draft
        """.trimIndent()

        var foundSnippet = ""
        for (line in noteContent.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.lowercase().contains("[[architecture guidelines")) {
                foundSnippet = trimmed.removePrefix("#").removePrefix("○").removePrefix("✓").removePrefix("•").trim()
                break
            }
        }

        assertEquals("Important: Review [[Architecture Guidelines]] before pushing PR", foundSnippet)
    }
}
