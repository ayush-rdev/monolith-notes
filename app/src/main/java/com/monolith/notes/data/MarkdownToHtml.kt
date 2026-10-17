package com.monolith.notes.data

import com.monolith.notes.model.Note
import java.text.SimpleDateFormat
import java.util.*

object MarkdownToHtml {

    private val DATE_FORMAT = SimpleDateFormat("MMMM d, yyyy • h:mm a", Locale.getDefault())

    /**
     * Converts a note and its markdown content to a self-contained, print-ready HTML document.
     */
    fun convertToHtml(note: Note, vaultPath: String? = null): String {
        val title = note.displayTitle.ifBlank { "Untitled Note" }
        val dateStr = DATE_FORMAT.format(Date(note.lastModified))
        val folder = note.folderName
        val wordCount = note.wordCount
        val readingTime = note.readingTimeMinutes

        val bodyHtml = parseMarkdownBody(note.content, vaultPath)

        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${escapeHtml(title)}</title>
    <style>
        @page {
            size: A4;
            margin: 20mm 18mm 20mm 18mm;
            @bottom-right {
                content: counter(page);
                font-size: 9pt;
                color: #9ca3af;
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            }
        }

        *, *:before, *:after {
            box-sizing: border-box;
        }

        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
            color: #1f2937;
            background-color: #ffffff;
            line-height: 1.65;
            font-size: 11pt;
            margin: 0;
            padding: 0;
            -webkit-print-color-adjust: exact;
            print-color-adjust: exact;
        }

        /* Note Header Section */
        .header {
            margin-bottom: 24px;
            padding-bottom: 16px;
            border-bottom: 1.5px solid #ca8a04;
        }

        .note-title {
            font-size: 24pt;
            font-weight: 700;
            color: #111827;
            line-height: 1.25;
            margin: 0 0 8px 0;
            letter-spacing: -0.02em;
        }

        .meta-row {
            display: flex;
            align-items: center;
            flex-wrap: wrap;
            gap: 12px;
            font-size: 9.5pt;
            color: #6b7280;
        }

        .folder-badge {
            background-color: #fef08a;
            color: #854d0e;
            padding: 2px 8px;
            border-radius: 4px;
            font-weight: 600;
            font-size: 8.5pt;
            text-transform: uppercase;
            letter-spacing: 0.04em;
        }

        .meta-item {
            display: inline-flex;
            align-items: center;
        }

        /* Content Styling */
        h1, h2, h3, h4, h5, h6 {
            color: #111827;
            font-weight: 600;
            margin-top: 24px;
            margin-bottom: 10px;
            line-height: 1.3;
            page-break-after: avoid;
        }

        h1 { font-size: 18pt; border-bottom: 1px solid #e5e7eb; padding-bottom: 6px; }
        h2 { font-size: 15pt; }
        h3 { font-size: 13pt; }
        h4 { font-size: 11.5pt; }

        p {
            margin: 0 0 12px 0;
        }

        /* Checklists */
        ul.checklist {
            list-style: none;
            padding-left: 0;
            margin: 10px 0;
        }

        li.task {
            display: flex;
            align-items: flex-start;
            margin-bottom: 6px;
            page-break-inside: avoid;
        }

        .checkbox-icon {
            display: inline-block;
            width: 16px;
            height: 16px;
            margin-right: 8px;
            margin-top: 3px;
            flex-shrink: 0;
        }

        li.task.checked .task-text {
            color: #9ca3af;
            text-decoration: line-through;
        }

        /* Lists */
        ul:not(.checklist), ol {
            margin: 8px 0 14px 0;
            padding-left: 24px;
        }

        li {
            margin-bottom: 4px;
        }

        /* Quotes */
        blockquote {
            margin: 14px 0;
            padding: 10px 16px;
            background-color: #fffbeb;
            border-left: 3.5px solid #ca8a04;
            color: #4b5563;
            font-style: italic;
            border-radius: 0 6px 6px 0;
            page-break-inside: avoid;
        }

        /* Code Blocks */
        pre {
            background-color: #f8fafc;
            border: 1px solid #e2e8f0;
            border-radius: 6px;
            padding: 12px 14px;
            overflow-x: auto;
            font-family: ui-monospace, SFMono-Regular, "JetBrains Mono", Menlo, Consolas, monospace;
            font-size: 9.5pt;
            line-height: 1.5;
            margin: 14px 0;
            page-break-inside: avoid;
        }

        code {
            font-family: ui-monospace, SFMono-Regular, "JetBrains Mono", Menlo, Consolas, monospace;
            background-color: #f1f5f9;
            padding: 2px 5px;
            border-radius: 4px;
            font-size: 9.5pt;
            color: #0f172a;
        }

        pre code {
            background-color: transparent;
            padding: 0;
            border-radius: 0;
        }

        /* Tables */
        table {
            width: 100%;
            border-collapse: collapse;
            margin: 16px 0;
            page-break-inside: avoid;
            font-size: 10pt;
        }

        th, td {
            border: 1px solid #d1d5db;
            padding: 8px 12px;
            text-align: left;
        }

        th {
            background-color: #f9fafb;
            font-weight: 600;
            color: #374151;
        }

        tr:nth-child(even) td {
            background-color: #fafafa;
        }

        /* Dividers */
        hr {
            border: 0;
            border-top: 1px solid #e5e7eb;
            margin: 20px 0;
        }

        /* Images */
        .image-container {
            margin: 16px 0;
            text-align: center;
            page-break-inside: avoid;
        }

        img {
            max-width: 100%;
            height: auto;
            border-radius: 6px;
            box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
        }

        .image-caption {
            font-size: 8.5pt;
            color: #6b7280;
            margin-top: 4px;
        }

        /* WikiLinks & Highlights */
        .wikilink {
            color: #b45309;
            font-weight: 500;
            text-decoration: underline;
            text-decoration-color: #fde047;
            text-underline-offset: 2px;
        }

        mark {
            background-color: #fef08a;
            color: inherit;
            padding: 1px 3px;
            border-radius: 2px;
        }

        /* Footer */
        .footer {
            margin-top: 36px;
            padding-top: 12px;
            border-top: 1px solid #f3f4f6;
            display: flex;
            justify-content: space-between;
            align-items: center;
            font-size: 8.5pt;
            color: #9ca3af;
        }
    </style>
</head>
<body>

    <div class="header">
        <h1 class="note-title">${escapeHtml(title)}</h1>
        <div class="meta-row">
            <span class="folder-badge">${escapeHtml(folder)}</span>
            <span class="meta-item">${escapeHtml(dateStr)}</span>
            <span class="meta-item">•</span>
            <span class="meta-item">$wordCount words</span>
            <span class="meta-item">•</span>
            <span class="meta-item">$readingTime min read</span>
        </div>
    </div>

    <div class="content">
        $bodyHtml
    </div>

    <div class="footer">
        <span>Monolith Notes • Vault Document</span>
        <span>${escapeHtml(dateStr)}</span>
    </div>

</body>
</html>
        """.trimIndent()
    }

    /**
     * Parses markdown text into HTML elements.
     */
    fun parseMarkdownBody(text: String, vaultPath: String? = null): String {
        if (text.isBlank()) return "<p><em>No additional text</em></p>"

        val sb = StringBuilder()
        val lines = text.lines()
        var inCodeBlock = false
        var codeLang = ""
        val codeBuffer = StringBuilder()

        var inChecklist = false
        var inBulletList = false
        var inNumberedList = false
        var inTable = false
        var tableHeaderProcessed = false

        fun inline(str: String): String = formatInline(str, vaultPath)

        fun closeOpenLists() {
            if (inChecklist) {
                sb.append("</ul>\n")
                inChecklist = false
            }
            if (inBulletList) {
                sb.append("</ul>\n")
                inBulletList = false
            }
            if (inNumberedList) {
                sb.append("</ol>\n")
                inNumberedList = false
            }
            if (inTable) {
                sb.append("</tbody></table>\n")
                inTable = false
                tableHeaderProcessed = false
            }
        }

        for (line in lines) {
            val trimmed = line.trim()

            // 1. Code block handling
            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    sb.append("<pre><code class=\"language-$codeLang\">")
                        .append(escapeHtml(codeBuffer.toString()))
                        .append("</code></pre>\n")
                    codeBuffer.clear()
                    inCodeBlock = false
                    codeLang = ""
                } else {
                    closeOpenLists()
                    inCodeBlock = true
                    codeLang = trimmed.removePrefix("```").trim()
                }
                continue
            }

            if (inCodeBlock) {
                codeBuffer.append(line).append('\n')
                continue
            }

            // 2. Empty line resets paragraphs and lists
            if (trimmed.isBlank()) {
                closeOpenLists()
                continue
            }

            // 3. Headings
            if (trimmed.startsWith("#")) {
                closeOpenLists()
                val headingLevel = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 6)
                val headingText = trimmed.drop(headingLevel).trim()
                sb.append("<h$headingLevel>").append(inline(headingText)).append("</h$headingLevel>\n")
                continue
            }

            // 4. Horizontal Rule
            if (trimmed.matches("^(-{3,}|\\*{3,}|_{3,}|─{3,})$".toRegex())) {
                closeOpenLists()
                sb.append("<hr />\n")
                continue
            }

            // 5. Blockquote
            if (trimmed.startsWith(">")) {
                closeOpenLists()
                val quoteText = trimmed.removePrefix(">").trim()
                sb.append("<blockquote><p>").append(inline(quoteText)).append("</p></blockquote>\n")
                continue
            }

            // 6. Checklists: ○ or ✓ or - [ ] or - [x]
            val isUnchecked = trimmed.startsWith("○ ") || trimmed.startsWith("- [ ] ") || trimmed.startsWith("* [ ] ")
            val isChecked = trimmed.startsWith("✓ ") || trimmed.startsWith("- [x] ", ignoreCase = true) || trimmed.startsWith("* [x] ", ignoreCase = true)

            if (isUnchecked || isChecked) {
                if (inBulletList || inNumberedList || inTable) closeOpenLists()
                if (!inChecklist) {
                    sb.append("<ul class=\"checklist\">\n")
                    inChecklist = true
                }

                val taskContent = when {
                    trimmed.startsWith("○ ") -> trimmed.removePrefix("○ ")
                    trimmed.startsWith("✓ ") -> trimmed.removePrefix("✓ ")
                    trimmed.startsWith("- [ ] ") -> trimmed.removePrefix("- [ ] ")
                    trimmed.startsWith("* [ ] ") -> trimmed.removePrefix("* [ ] ")
                    trimmed.startsWith("- [x] ", ignoreCase = true) -> trimmed.substring(6)
                    trimmed.startsWith("* [x] ", ignoreCase = true) -> trimmed.substring(6)
                    else -> trimmed
                }

                val svgIcon = if (isChecked) {
                    """<svg class="checkbox-icon" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9" fill="#ca8a04"/><path d="M7 12l3.5 3.5 6.5-6.5" stroke="#ffffff" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" fill="none"/></svg>"""
                } else {
                    """<svg class="checkbox-icon" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9" fill="none" stroke="#9ca3af" stroke-width="2"/></svg>"""
                }

                val itemClass = if (isChecked) "task checked" else "task"
                sb.append("<li class=\"$itemClass\">$svgIcon<span class=\"task-text\">")
                    .append(inline(taskContent))
                    .append("</span></li>\n")
                continue
            }

            // 7. Bullet Lists: • or - or *
            if (trimmed.startsWith("• ") || trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                if (inChecklist || inNumberedList || inTable) closeOpenLists()
                if (!inBulletList) {
                    sb.append("<ul>\n")
                    inBulletList = true
                }
                val itemText = when {
                    trimmed.startsWith("• ") -> trimmed.removePrefix("• ")
                    trimmed.startsWith("- ") -> trimmed.removePrefix("- ")
                    else -> trimmed.removePrefix("* ")
                }
                sb.append("<li>").append(inline(itemText)).append("</li>\n")
                continue
            }

            // 8. Numbered Lists: 1. 2. etc.
            if (trimmed.matches("^\\d+\\.\\s+.*$".toRegex())) {
                if (inChecklist || inBulletList || inTable) closeOpenLists()
                if (!inNumberedList) {
                    sb.append("<ol>\n")
                    inNumberedList = true
                }
                val itemText = trimmed.replaceFirst("^\\d+\\.\\s+".toRegex(), "")
                sb.append("<li>").append(inline(itemText)).append("</li>\n")
                continue
            }

            // 9. Markdown Tables: | cell | cell |
            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                val cells = trimmed.split("|").filter { it.isNotBlank() }.map { it.trim() }
                if (!inTable) {
                    closeOpenLists()
                    sb.append("<table><thead><tr>\n")
                    for (cell in cells) {
                        sb.append("<th>").append(inline(cell)).append("</th>")
                    }
                    sb.append("</tr></thead><tbody>\n")
                    inTable = true
                    tableHeaderProcessed = false
                    continue
                } else if (!tableHeaderProcessed && cells.all { it.matches("^-+:?$".toRegex()) || it.matches("^:-+:?$".toRegex()) }) {
                    // Header separator row (e.g. |---|---|)
                    tableHeaderProcessed = true
                    continue
                } else {
                    sb.append("<tr>\n")
                    for (cell in cells) {
                        sb.append("<td>").append(inline(cell)).append("</td>")
                    }
                    sb.append("</tr>\n")
                    continue
                }
            }

            // 10. Default Paragraph
            closeOpenLists()
            sb.append("<p>").append(inline(trimmed)).append("</p>\n")
        }

        closeOpenLists()

        if (inCodeBlock && codeBuffer.isNotEmpty()) {
            sb.append("<pre><code>").append(escapeHtml(codeBuffer.toString())).append("</code></pre>\n")
        }

        return sb.toString()
    }

    /**
     * Formats inline Markdown styles: bold, italic, strikethrough, highlights, code, wikilinks, links, images.
     */
    fun formatInline(text: String, vaultPath: String? = null): String {
        var result = escapeHtml(text)

        // Images: ![alt](url)
        result = result.replace("!\\[([^\\]]*)\\]\\(([^\\)]+)\\)".toRegex()) { match ->
            val alt = match.groupValues[1]
            var src = match.groupValues[2]
            if (vaultPath != null && !src.startsWith("http://") && !src.startsWith("https://") && !src.startsWith("file://") && !src.startsWith("data:")) {
                src = "file://$vaultPath/$src"
            }
            """<div class="image-container"><img src="$src" alt="$alt" />${if (alt.isNotBlank()) "<div class=\"image-caption\">$alt</div>" else ""}</div>"""
        }

        // Links: [text](url)
        result = result.replace("\\[([^\\]]+)\\]\\(([^\\)]+)\\)".toRegex()) { match ->
            val label = match.groupValues[1]
            val url = match.groupValues[2]
            """<a href="$url">$label</a>"""
        }

        // WikiLinks: [[Target|Alias]] or [[Target]]
        result = result.replace("\\[\\[([^\\]\\|]+)(?:\\|([^\\]]+))?\\]\\]".toRegex()) { match ->
            val target = match.groupValues[1]
            val alias = match.groupValues[2].ifBlank { target }
            """<span class="wikilink">$alias</span>"""
        }

        // Inline code: `code`
        result = result.replace("`([^`]+)`".toRegex()) { match ->
            "<code>${match.groupValues[1]}</code>"
        }

        // Highlights: ==text==
        result = result.replace("==([^=]+)==".toRegex()) { match ->
            "<mark>${match.groupValues[1]}</mark>"
        }

        // Strikethrough: ~~text~~
        result = result.replace("~~([^~]+)~~".toRegex()) { match ->
            "<del>${match.groupValues[1]}</del>"
        }

        // Bold: **text** or __text__
        result = result.replace("\\*\\*([^\\*]+)\\*\\*".toRegex()) { match ->
            "<strong>${match.groupValues[1]}</strong>"
        }
        result = result.replace("__(.+?)__".toRegex()) { match ->
            "<strong>${match.groupValues[1]}</strong>"
        }

        // Italic: *text* or _text_
        result = result.replace("(?<!\\*)\\*([^\\*\\n]+)\\*(?!\\*)".toRegex()) { match ->
            "<em>${match.groupValues[1]}</em>"
        }
        result = result.replace("(?<!_)_([^_\\n]+)_(?!_)".toRegex()) { match ->
            "<em>${match.groupValues[1]}</em>"
        }

        return result
    }

    private fun escapeHtml(s: String): String {
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
