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

    @Test
    fun testSeparatorRoundTrip() {
        val raw = """
            # Architecture Notes
            
            Section 1 content
            
            ---
            
            Section 2 content
        """.trimIndent()

        val (title, body, _) = MarkdownConverter.toUi(raw)
        assertEquals("Architecture Notes", title)
        assertTrue(body.contains("───"))

        val exported = MarkdownConverter.toMarkdown(title, body)
        assertTrue(exported.contains("---"))
    }

    @Test
    fun testLongSeparatorSupport() {
        val raw = """
            # Long Separators
            First section
            ----------------------------------------
            Second section
            ========================================
            Third section
            ****************************************
            Fourth section
        """.trimIndent()

        val (_, body, _) = MarkdownConverter.toUi(raw)
        val separatorCount = body.lines().count { it.trim() == "───" }
        assertEquals(3, separatorCount)

        val exported = MarkdownConverter.toMarkdown("Long Separators", body)
        val exportedCount = exported.lines().count { it.trim() == "---" }
        assertEquals(3, exportedCount)
    }

    @Test
    fun testTableDelimiterRecognition() {
        assertTrue(com.monolith.notes.ui.components.isTableDelimiter("| --- | --- |"))
        assertTrue(com.monolith.notes.ui.components.isTableDelimiter("| :--- | :---: | ---: |"))
        assertTrue(com.monolith.notes.ui.components.isTableDelimiter("--- | ---"))
        assertFalse(com.monolith.notes.ui.components.isTableDelimiter("| Header 1 | Header 2 |"))
        assertFalse(com.monolith.notes.ui.components.isTableDelimiter("Just some text"))
        assertFalse(com.monolith.notes.ui.components.isTableDelimiter("---"))
    }

    @Test
    fun testSplitTableRow() {
        val cells = com.monolith.notes.ui.components.splitTableRow("| Item | Price | Status |")
        assertEquals(listOf("Item", "Price", "Status"), cells)

        val emptyCells = com.monolith.notes.ui.components.splitTableRow("|  |  |  |")
        assertEquals(listOf("", "", ""), emptyCells)
    }

    @Test
    fun testTableRoundTrip() {
        val raw = """
            # Roadmap

            | Milestone | Status | Owner |
            | :--- | :---: | ---: |
            | Alpha | Done | Ayush |
            | Beta | In Progress | Team |
        """.trimIndent()

        val (title, body, _) = MarkdownConverter.toUi(raw)
        assertEquals("Roadmap", title)
        assertTrue(body.contains("| Milestone | Status | Owner |"))
        assertTrue(body.contains("| :--- | :---: | ---: |"))
        assertTrue(body.contains("| Alpha | Done | Ayush |"))

        val exported = MarkdownConverter.toMarkdown(title, body)
        assertTrue(exported.contains("| Milestone | Status | Owner |"))
        assertTrue(exported.contains("| Alpha | Done | Ayush |"))
    }

    @Test
    fun testEmptyNoteMarkdownSerialization() {
        val exported = MarkdownConverter.toMarkdown("", "")
        assertEquals("", exported.trim())
    }

    @Test
    fun testCleanTableRowSpacing() {
        val colCount = 3
        val newRow = List(colCount) { " " }.joinToString(" | ", prefix = "| ", postfix = " |")
        assertEquals("|   |   |   |", newRow)
        val cells = com.monolith.notes.ui.components.splitTableRow(newRow)
        assertEquals(3, cells.size)
        assertTrue(cells.all { it.isBlank() })
    }

    @Test
    fun testFullObsidianFrontmatterPreserved() {
        val raw = """
            ---
            aliases: [SuperNote, Secret]
            tags: [research, ai, android]
            pinned: true
            author: Ayush
            ---
            # Deep Learning Notes
            Content here
        """.trimIndent()

        val parsed = MarkdownConverter.toUiWithFrontmatter(raw)
        assertEquals("Deep Learning Notes", parsed.title)
        assertTrue(parsed.isPinned)
        assertTrue(parsed.rawFrontmatter != null)
        assertTrue(parsed.rawFrontmatter!!.contains("aliases: [SuperNote, Secret]"))
        assertTrue(parsed.rawFrontmatter!!.contains("tags: [research, ai, android]"))
        assertTrue(parsed.rawFrontmatter!!.contains("author: Ayush"))

        // Now convert back to markdown and verify all keys are kept!
        val backToMd = MarkdownConverter.toMarkdown(parsed.title, parsed.body, parsed.isPinned, parsed.rawFrontmatter)
        assertTrue(backToMd.startsWith("---\n"))
        assertTrue(backToMd.contains("aliases: [SuperNote, Secret]"))
        assertTrue(backToMd.contains("tags: [research, ai, android]"))
        assertTrue(backToMd.contains("author: Ayush"))
        assertTrue(backToMd.contains("pinned: true"))
        assertTrue(backToMd.contains("# Deep Learning Notes\n\nContent here"))
    }

    @Test
    fun testChecklistInsideCodeBlockNotConverted() {
        val raw = """
            # Code Sample
            
            ```markdown
            - [ ] This should stay as raw markdown
            - [x] This too
            ```
            
            - [ ] Outer Task
        """.trimIndent()

        val (title, body, _) = MarkdownConverter.toUi(raw)
        assertEquals("Code Sample", title)
        // Inside code block stays unchanged
        assertTrue(body.contains("- [ ] This should stay as raw markdown"))
        assertTrue(body.contains("- [x] This too"))
        // Outer task converted to ○
        assertTrue(body.contains("○ Outer Task"))

        // Test toggling: toggling task 0 should toggle Outer Task, NOT the one in the code block
        val toggled = toggleMarkdownTask(body, 0)
        assertTrue(toggled.contains("✓ Outer Task"))
        assertTrue(toggled.contains("- [ ] This should stay as raw markdown"))
    }

    @Test
    fun testNestedIndentedChecklistToggling() {
        val content = """
            ○ Top level
              ○ Nested 2 spaces
                ○ Nested 4 spaces
        """.trimIndent()

        // Toggle task 1 (the 2-space nested item)
        val toggled = toggleMarkdownTask(content, 1)
        assertTrue(toggled.contains("  ✓ Nested 2 spaces"))
        assertTrue(toggled.contains("○ Top level"))
        assertTrue(toggled.contains("    ○ Nested 4 spaces"))
    }

    @Test
    fun testNormalizeContentForMarkdownParser() {
        val input = """
            Notes intro
            ○ Buy milk
            ○ Eggs
            ✓ Bread
            • Bananas
        """.trimIndent()

        val normalized = com.monolith.notes.ui.components.normalizeContentForMarkdownParser(input)
        assertTrue(normalized.contains("- [ ] Buy milk"))
        assertTrue(normalized.contains("- [ ] Eggs"))
        assertTrue(normalized.contains("- [x] Bread"))
        assertTrue(normalized.contains("- Bananas"))

        // Verify CommonMark parses normalized text into BulletList with TaskListItemMarkers
        val doc = com.monolith.notes.ui.components.CommonMarkParser.parse(normalized)
        var bulletListFound = false
        var taskCount = 0

        var node = doc.firstChild
        while (node != null) {
            if (node is org.commonmark.node.BulletList) {
                bulletListFound = true
                var item = node.firstChild
                while (item != null) {
                    if (item is org.commonmark.node.ListItem) {
                        var isTask = false
                        var ch = item.firstChild
                        while (ch != null) {
                            if (ch is org.commonmark.ext.task.list.items.TaskListItemMarker) {
                                isTask = true
                                break
                            }
                            if (ch is org.commonmark.node.Paragraph) {
                                var inl = ch.firstChild
                                while (inl != null) {
                                    if (inl is org.commonmark.ext.task.list.items.TaskListItemMarker) {
                                        isTask = true
                                        break
                                    }
                                    inl = inl.next
                                }
                            }
                            ch = ch.next
                        }
                        if (isTask) taskCount++
                    }
                    item = item.next
                }
            }
            node = node.next
        }

        assertTrue("Must parse as CommonMark BulletList", bulletListFound)
        assertEquals("Must parse exactly 3 task items", 3, taskCount)
    }
}

