package com.monolith.notes

import com.monolith.notes.ui.components.CommonMarkParser
import com.monolith.notes.ui.components.isTableDelimiter
import com.monolith.notes.ui.components.splitTableRow
import com.monolith.notes.ui.components.toggleMarkdownTask
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.node.*
import org.junit.Assert.*
import org.junit.Test

class MarkdownEngineTest {

    @Test
    fun testCommonMarkHeadingsAndFormatting() {
        val md = """
            # Heading 1
            ## Heading 2
            
            This is **bold**, *italic*, ***bold italic***, and ~~strikethrough~~.
            
            Here is `inline code` and a [link](https://google.com).
        """.trimIndent()

        val doc = CommonMarkParser.parse(md)
        assertNotNull(doc)

        val firstBlock = doc.firstChild
        assertTrue(firstBlock is Heading)
        assertEquals(1, (firstBlock as Heading).level)

        val secondBlock = firstBlock.next
        assertTrue(secondBlock is Heading)
        assertEquals(2, (secondBlock as Heading).level)

        val thirdBlock = secondBlock.next
        assertTrue(thirdBlock is Paragraph)
    }

    @Test
    fun testCommonMarkOrderedAndUnorderedLists() {
        val md = """
            - Bullet 1
            - Bullet 2
              - Sub Bullet 2.1
            
            1. First item
            2. Second item
               1. Sub item 2.1
        """.trimIndent()

        val doc = CommonMarkParser.parse(md)
        val bulletList = doc.firstChild
        assertTrue(bulletList is BulletList)

        val orderedList = bulletList.next
        assertTrue(orderedList is OrderedList)
        assertEquals(1, (orderedList as OrderedList).startNumber)
    }

    @Test
    fun testGfmTables() {
        val md = """
            | Item | Price | Qty |
            | :--- | :---: | --: |
            | Apples | $2.50 | 10 |
            | Oranges | $3.00 | 5 |
        """.trimIndent()

        val doc = CommonMarkParser.parse(md)
        val table = doc.firstChild
        assertTrue(table is TableBlock)

        assertTrue(isTableDelimiter("| :--- | :---: | --: |"))
        val cells = splitTableRow("| Apples | $2.50 | 10 |")
        assertEquals(listOf("Apples", "$2.50", "10"), cells)
    }

    @Test
    fun testTaskTogglingPreservesIndentation() {
        val content = """
            - [ ] Root task
              - [ ] Indented task
            ○ Circular task
        """.trimIndent()

        // Toggle first task
        val toggled1 = toggleMarkdownTask(content, 0)
        assertTrue(toggled1.contains("- [x] Root task"))
        assertTrue(toggled1.contains("  - [ ] Indented task"))

        // Toggle second task
        val toggled2 = toggleMarkdownTask(content, 1)
        assertTrue(toggled2.contains("- [ ] Root task"))
        assertTrue(toggled2.contains("  - [x] Indented task"))

        // Toggle circular task
        val toggled3 = toggleMarkdownTask(content, 2)
        assertTrue(toggled3.contains("✓ Circular task"))
    }
}
