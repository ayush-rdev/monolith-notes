package com.monolith.notes

import com.monolith.notes.data.MarkdownConverter
import com.monolith.notes.ui.components.toggleMarkdownTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class MarkdownConverterTest {

    @Test
    fun testToUiExtraction() {
        val raw = """
            # Grocery List
            
            - [ ] Buy Milk
            - [x] Eggs
            - Apples
            • Bananas
        """.trimIndent()

        val (title, body, isPinned) = MarkdownConverter.toUi(raw)
        assertEquals("Grocery List", title)
        assertFalse(isPinned)
        assertTrue(body.contains("○ Buy Milk"))
        assertTrue(body.contains("✓ Eggs"))
        assertTrue(body.contains("• Apples"))
        assertTrue(body.contains("• Bananas"))
    }

    @Test
    fun testPinnedFrontmatterRoundTrip() {
        val title = "Priority Meeting"
        val body = "Discussion points"
        val md = MarkdownConverter.toMarkdown(title, body, isPinned = true)
        assertTrue(md.contains("pinned: true"))

        val (extractedTitle, extractedBody, isPinned) = MarkdownConverter.toUi(md)
        assertEquals("Priority Meeting", extractedTitle)
        assertTrue(isPinned)
        assertTrue(extractedBody.contains("Discussion points"))
    }

    @Test
    fun testToMarkdownSerialization() {
        val title = "Grocery List"
        val body = """
            ○ Buy Milk
            ✓ Eggs
            • Apples
        """.trimIndent()

        val md = MarkdownConverter.toMarkdown(title, body)
        assertTrue(md.startsWith("# Grocery List\n\n"))
        assertTrue(md.contains("- [ ] Buy Milk"))
        assertTrue(md.contains("- [x] Eggs"))
        assertTrue(md.contains("- Apples"))
    }

    @Test
    fun testToggleMarkdownTask() {
        val content = """
            ○ Task 1
            ✓ Task 2
            - [ ] Task 3
        """.trimIndent()

        // Toggle task 0 (○ -> ✓)
        val toggled0 = toggleMarkdownTask(content, 0)
        assertTrue(toggled0.startsWith("✓ Task 1"))

        // Toggle task 1 (✓ -> ○)
        val toggled1 = toggleMarkdownTask(content, 1)
        assertTrue(toggled1.contains("○ Task 2"))

        // Toggle task 2 (- [ ] -> - [x])
        val toggled2 = toggleMarkdownTask(content, 2)
        assertTrue(toggled2.contains("- [x] Task 3"))
    }
}
