package com.monolith.notes.data

/**
 * Bi-directional converter between Apple Notes WYSIWYG presentation and
 * Obsidian-compatible standard Markdown (.md) storage.
 *
 * File-Over-App Principle:
 * - On disk (/sdcard/Documents/MonolithNotes): Standard Markdown (# Title, - [ ] Task, - Bullet).
 * - In UI (Monolith App Canvas): Pure Apple Notes aesthetic (Title field, ○ / ✓ Checklist bubbles, • Bullets).
 * - Full Obsidian frontmatter support (persists `pinned: true` seamlessly).
 */
object MarkdownConverter {

    private val PINNED_REGEX = "pinned:\\s*true".toRegex(RegexOption.IGNORE_CASE)

    /**
     * Converts a raw .md file content from disk to clean Apple Notes presentation:
     * Returns Triple(cleanTitle, cleanBody, isPinned)
     */
    fun toUi(rawContent: String, fallbackTitle: String = "Untitled"): Triple<String, String, Boolean> {
        var content = rawContent
        var isPinned = false

        // Check for YAML frontmatter:
        // ---
        // pinned: true
        // ---
        if (content.startsWith("---")) {
            val secondSeparator = content.indexOf("\n---", 3)
            if (secondSeparator != -1) {
                val frontmatter = content.substring(3, secondSeparator)
                if (frontmatter.contains(PINNED_REGEX)) {
                    isPinned = true
                }
                // Strip frontmatter
                val postFrontmatterIndex = content.indexOf('\n', secondSeparator + 4)
                content = if (postFrontmatterIndex != -1) content.substring(postFrontmatterIndex + 1) else ""
            }
        }

        var lineStart = 0
        val len = content.length
        var titleFound = false
        var cleanTitle = fallbackTitle
        val bodySb = StringBuilder(content.length)
        var isBodyLeading = true

        while (lineStart < len) {
            var lineEnd = content.indexOf('\n', lineStart)
            if (lineEnd == -1) lineEnd = len

            val line = content.substring(lineStart, lineEnd)
            val trimmed = line.trimStart()

            if (!titleFound) {
                if (trimmed.isNotBlank()) {
                    cleanTitle = trimmed.removePrefix("#").trim().ifBlank { fallbackTitle }
                    titleFound = true
                }
            } else {
                if (isBodyLeading && trimmed.isBlank()) {
                    // Skip leading blank lines in body
                } else {
                    isBodyLeading = false
                    if (bodySb.isNotEmpty()) {
                        bodySb.append('\n')
                    }
                    when {
                        trimmed.startsWith("- [ ] ") -> bodySb.append("○ ").append(trimmed.substring(6))
                        trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ") -> bodySb.append("✓ ").append(trimmed.substring(6))
                        trimmed.startsWith("* [ ] ") -> bodySb.append("○ ").append(trimmed.substring(6))
                        trimmed.startsWith("* [x] ") || trimmed.startsWith("* [X] ") -> bodySb.append("✓ ").append(trimmed.substring(6))
                        trimmed.startsWith("- ") -> bodySb.append("• ").append(trimmed.substring(2))
                        trimmed.startsWith("* ") -> bodySb.append("• ").append(trimmed.substring(2))
                        else -> bodySb.append(line)
                    }
                }
            }

            lineStart = lineEnd + 1
        }

        if (!titleFound) {
            return Triple(fallbackTitle, "", isPinned)
        }

        return Triple(cleanTitle, bodySb.toString(), isPinned)
    }

    /**
     * Converts clean Apple Notes UI text back to standard Obsidian Markdown on disk:
     * - Optional YAML frontmatter for pinned notes
     * - Adds `# $title\n\n`
     * - Converts `○ ` -> `- [ ] `
     * - Converts `✓ ` -> `- [x] `
     * - Converts `• ` -> `- `
     */
    fun toMarkdown(title: String, body: String, isPinned: Boolean = false): String {
        val cleanTitle = title.trim()
        val sb = StringBuilder(body.length + cleanTitle.length + 64)
        if (isPinned) {
            sb.append("---\npinned: true\n---\n\n")
        }
        if (cleanTitle.isNotBlank()) {
            sb.append("# ").append(cleanTitle).append("\n\n")
        }

        var lineStart = 0
        val len = body.length
        var isFirst = true

        while (lineStart < len) {
            var lineEnd = body.indexOf('\n', lineStart)
            if (lineEnd == -1) lineEnd = len

            if (!isFirst) {
                sb.append('\n')
            }
            isFirst = false

            val line = body.substring(lineStart, lineEnd)
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("○ ") -> sb.append("- [ ] ").append(trimmed.substring(2))
                trimmed.startsWith("✓ ") -> sb.append("- [x] ").append(trimmed.substring(2))
                trimmed.startsWith("• ") -> sb.append("- ").append(trimmed.substring(2))
                else -> sb.append(line)
            }

            lineStart = lineEnd + 1
        }

        return sb.toString()
    }
}
