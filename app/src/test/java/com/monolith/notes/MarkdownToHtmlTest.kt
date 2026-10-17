package com.monolith.notes

import com.monolith.notes.data.MarkdownToHtml
import com.monolith.notes.model.Note
import org.junit.Assert.*
import org.junit.Test

class MarkdownToHtmlTest {

    @Test
    fun `test complete HTML document generation with Apple Notes styling`() {
        val note = Note(
            id = "test-1",
            title = "Q4 Strategy & Roadmap",
            content = """
                # Executive Overview
                This is a critical update for the team regarding [[Project Titan]].

                ## Key Objectives
                ○ Launch Android 14 release
                ✓ Finalize security audit

                > Note: All deadlines are strictly enforced.

                ```kotlin
                fun main() {
                    println("Ship fast")
                }
                ```

                | Feature | Status |
                |---|---|
                | WikiLinks | Done |
                | PDF Export | Done |
            """.trimIndent(),
            folder = "Work",
            lastModified = 1700000000000L
        )

        val html = MarkdownToHtml.convertToHtml(note)

        // Document Structure
        assertTrue(html.contains("<!DOCTYPE html>"))
        assertTrue(html.contains("<title>Q4 Strategy &amp; Roadmap</title>"))
        assertTrue(html.contains("Q4 Strategy &amp; Roadmap</h1>"))
        assertTrue(html.contains("<span class=\"folder-badge\">Work</span>"))

        // Headings
        assertTrue(html.contains("<h1>Executive Overview</h1>"))
        assertTrue(html.contains("<h2>Key Objectives</h2>"))

        // WikiLinks
        assertTrue(html.contains("<span class=\"wikilink\">Project Titan</span>"))

        // Checklists
        assertTrue(html.contains("class=\"task\""))
        assertTrue(html.contains("Launch Android 14 release"))
        assertTrue(html.contains("class=\"task checked\""))
        assertTrue(html.contains("Finalize security audit"))

        // Blockquotes
        assertTrue(html.contains("<blockquote><p>Note: All deadlines are strictly enforced.</p></blockquote>"))

        // Code block
        assertTrue(html.contains("<pre><code class=\"language-kotlin\">"))
        assertTrue(html.contains("println(&quot;Ship fast&quot;)"))

        // Table
        assertTrue(html.contains("<table><thead><tr>"))
        assertTrue(html.contains("<th>Feature</th>"))
        assertTrue(html.contains("<td>WikiLinks</td>"))

        // Print CSS & Styling
        assertTrue(html.contains("@page {"))
        assertTrue(html.contains("size: A4;"))
        assertTrue(html.contains("font-family:"))
    }

    @Test
    fun `test HTML escaping prevents injection`() {
        val maliciousText = "<script>alert('pwned')</script> & <b>test</b>"
        val formatted = MarkdownToHtml.formatInline(maliciousText)
        assertFalse(formatted.contains("<script>"))
        assertTrue(formatted.contains("&lt;script&gt;"))
        assertTrue(formatted.contains("&amp;"))
    }

    @Test
    fun `test inline formatting styles`() {
        val text = "**bold text** and *italic text* and `inline code` and ==highlighted== and ~~strikethrough~~"
        val formatted = MarkdownToHtml.formatInline(text)
        assertTrue(formatted.contains("<strong>bold text</strong>"))
        assertTrue(formatted.contains("<em>italic text</em>"))
        assertTrue(formatted.contains("<code>inline code</code>"))
        assertTrue(formatted.contains("<mark>highlighted</mark>"))
        assertTrue(formatted.contains("<del>strikethrough</del>"))
    }

    @Test
    fun `test wikilinks with alias formatting`() {
        val text = "Refer to [[Target Note|Custom Display Name]] here"
        val formatted = MarkdownToHtml.formatInline(text)
        assertTrue(formatted.contains("<span class=\"wikilink\">Custom Display Name</span>"))
    }
}
