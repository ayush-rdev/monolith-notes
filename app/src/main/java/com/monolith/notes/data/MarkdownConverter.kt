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
     * Returns Quadruple/Tuple: cleanTitle, cleanBody, isPinned, rawFrontmatter
     */
    fun toUi(rawContent: String, fallbackTitle: String = "Untitled"): Triple<String, String, Boolean> {
        return toUiWithFrontmatter(rawContent, fallbackTitle).let {
            Triple(it.title, it.body, it.isPinned)
        }
    }

    data class ParsedUiNote(
        val title: String,
        val body: String,
        val isPinned: Boolean,
        val rawFrontmatter: String? = null
    )

    fun toUiWithFrontmatter(rawContent: String, fallbackTitle: String = "Untitled"): ParsedUiNote {
        var content = rawContent
        var isPinned = false
        var rawFrontmatter: String? = null

        // Preserve YAML frontmatter for Obsidian interoperability
        if (content.startsWith("---")) {
            val secondSeparator = content.indexOf("\n---", 3)
            if (secondSeparator != -1) {
                rawFrontmatter = content.substring(3, secondSeparator).trim()
                if (rawFrontmatter.contains(PINNED_REGEX)) {
                    isPinned = true
                }
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
        var inCodeBlock = false

        while (lineStart < len) {
            var lineEnd = content.indexOf('\n', lineStart)
            if (lineEnd == -1) lineEnd = len

            val line = content.substring(lineStart, lineEnd)
            val trimmed = line.trimStart()
            val indent = line.takeWhile { it == ' ' || it == '\t' }

            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                inCodeBlock = !inCodeBlock
            }

            if (!titleFound && !inCodeBlock) {
                if (trimmed.isNotBlank()) {
                    if (trimmed.startsWith("# ") && !trimmed.startsWith("##")) {
                        cleanTitle = trimmed.removePrefix("# ").trim().ifBlank { fallbackTitle }
                        titleFound = true
                    } else if (!trimmed.startsWith("#") && !trimmed.startsWith("-") && !trimmed.startsWith("*") && !trimmed.startsWith("○") && !trimmed.startsWith("✓") && !trimmed.startsWith(">") && !trimmed.startsWith("`")) {
                        cleanTitle = trimmed.trim().ifBlank { fallbackTitle }
                        titleFound = true
                    } else {
                        cleanTitle = fallbackTitle
                        titleFound = true
                        isBodyLeading = false
                        when {
                            trimmed.startsWith("- [ ] ") -> bodySb.append(indent).append("○ ").append(trimmed.substring(6))
                            trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ") -> bodySb.append(indent).append("✓ ").append(trimmed.substring(6))
                            trimmed.startsWith("* [ ] ") -> bodySb.append(indent).append("○ ").append(trimmed.substring(6))
                            trimmed.startsWith("* [x] ") || trimmed.startsWith("* [X] ") -> bodySb.append(indent).append("✓ ").append(trimmed.substring(6))
                            trimmed.startsWith("- ") -> bodySb.append(indent).append("• ").append(trimmed.substring(2))
                            trimmed.startsWith("* ") -> bodySb.append(indent).append("• ").append(trimmed.substring(2))
                            trimmed.matches("^(-{3,}|\\*{3,}|_{3,}|─{3,}|={3,}|(-\\s*){3,}|(\\*\\s*){3,}|(_\\s*){3,})$".toRegex()) -> bodySb.append(indent).append("───")
                            else -> bodySb.append(line)
                        }
                    }
                }
            } else {
                if (isBodyLeading && trimmed.isBlank()) {
                    // Skip leading blank lines in body
                } else {
                    isBodyLeading = false
                    if (bodySb.isNotEmpty()) {
                        bodySb.append('\n')
                    }
                    if (inCodeBlock) {
                        bodySb.append(line)
                    } else {
                        when {
                            trimmed.startsWith("- [ ] ") -> bodySb.append(indent).append("○ ").append(trimmed.substring(6))
                            trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ") -> bodySb.append(indent).append("✓ ").append(trimmed.substring(6))
                            trimmed.startsWith("* [ ] ") -> bodySb.append(indent).append("○ ").append(trimmed.substring(6))
                            trimmed.startsWith("* [x] ") || trimmed.startsWith("* [X] ") -> bodySb.append(indent).append("✓ ").append(trimmed.substring(6))
                            trimmed.startsWith("- ") -> bodySb.append(indent).append("• ").append(trimmed.substring(2))
                            trimmed.startsWith("* ") -> bodySb.append(indent).append("• ").append(trimmed.substring(2))
                            trimmed.matches("^(-{3,}|\\*{3,}|_{3,}|─{3,}|={3,}|(-\\s*){3,}|(\\*\\s*){3,}|(_\\s*){3,})$".toRegex()) -> bodySb.append(indent).append("───")
                            else -> bodySb.append(line)
                        }
                    }
                }
            }

            lineStart = lineEnd + 1
        }

        if (!titleFound) {
            return ParsedUiNote(fallbackTitle, "", isPinned, rawFrontmatter)
        }

        return ParsedUiNote(cleanTitle, bodySb.toString(), isPinned, rawFrontmatter)
    }

    /**
     * Converts clean Apple Notes UI text back to standard Obsidian Markdown on disk:
     * - Preserves full Obsidian frontmatter with updated `pinned` state
     * - Adds `# $title\n\n`
     * - Converts `○ ` -> `- [ ] ` with indentation preserved
     * - Converts `✓ ` -> `- [x] ` with indentation preserved
     * - Converts `• ` -> `- ` with indentation preserved
     */
    fun toMarkdown(title: String, body: String, isPinned: Boolean = false, existingFrontmatter: String? = null): String {
        val cleanTitle = title.trim()
        val sb = StringBuilder(body.length + cleanTitle.length + 128)

        if (existingFrontmatter != null && existingFrontmatter.isNotBlank()) {
            val updatedFrontmatter = if (isPinned) {
                if (existingFrontmatter.contains(PINNED_REGEX)) {
                    existingFrontmatter
                } else {
                    "$existingFrontmatter\npinned: true"
                }
            } else {
                existingFrontmatter.replace("(?m)^pinned:\\s*true\\s*\\n?".toRegex(RegexOption.IGNORE_CASE), "").trim()
            }
            if (updatedFrontmatter.isNotBlank()) {
                sb.append("---\n").append(updatedFrontmatter).append("\n---\n\n")
            }
        } else if (isPinned) {
            sb.append("---\npinned: true\n---\n\n")
        }

        if (cleanTitle.isNotBlank()) {
            val strippedTitle = cleanTitle.replaceFirst("^#{1,6}\\s*".toRegex(), "").trim()
            if (strippedTitle.isNotBlank()) {
                sb.append("# ").append(strippedTitle).append("\n\n")
            }
        }

        var lineStart = 0
        val len = body.length
        var isFirst = true
        var inCodeBlock = false

        while (lineStart < len) {
            var lineEnd = body.indexOf('\n', lineStart)
            if (lineEnd == -1) lineEnd = len

            if (!isFirst) {
                sb.append('\n')
            }
            isFirst = false

            val line = body.substring(lineStart, lineEnd)
            val trimmed = line.trimStart()
            val indent = line.takeWhile { it == ' ' || it == '\t' }

            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                inCodeBlock = !inCodeBlock
            }

            if (inCodeBlock) {
                sb.append(line)
            } else {
                when {
                    trimmed.startsWith("○ ") -> sb.append(indent).append("- [ ] ").append(trimmed.substring(2))
                    trimmed.startsWith("✓ ") -> sb.append(indent).append("- [x] ").append(trimmed.substring(2))
                    trimmed.startsWith("• ") -> sb.append(indent).append("- ").append(trimmed.substring(2))
                    trimmed == "───" || trimmed.startsWith("───") -> sb.append(indent).append("---")
                    else -> sb.append(line)
                }
            }

            lineStart = lineEnd + 1
        }

        return sb.toString()
    }
}
