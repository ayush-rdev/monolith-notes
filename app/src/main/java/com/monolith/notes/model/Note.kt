package com.monolith.notes.model

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

data class Note(
    val id: String = UUID.randomUUID().toString(),
    val file: File? = null,
    val title: String = "",
    val content: String = "",
    val tags: List<String> = emptyList(),
    val lastModified: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val folder: String = "Notes",
    val rawFrontmatter: String? = null
) {
    val folderName: String
        get() {
            val parent = file?.parentFile ?: return folder
            return if (parent.name == "MonolithNotes" || parent.name == "Notes" || parent.name.startsWith(".")) {
                "Notes"
            } else {
                parent.name
            }
        }

    val displayTitle: String by lazy(LazyThreadSafetyMode.NONE) {
        when {
            title.isNotBlank() -> title
            content.isNotBlank() -> {
                var lineStart = 0
                val len = content.length
                var firstLine: String? = null
                while (lineStart < len) {
                    var lineEnd = content.indexOf('\n', lineStart)
                    if (lineEnd == -1) lineEnd = len
                    val line = content.substring(lineStart, lineEnd).trim()
                    if (line.isNotEmpty()) {
                        firstLine = line
                        break
                    }
                    lineStart = lineEnd + 1
                }
                if (firstLine != null) {
                    firstLine.removePrefix("#").removePrefix("○").removePrefix("✓").removePrefix("•").trim()
                } else {
                    "New Note"
                }
            }
            else -> "New Note"
        }
    }

    val previewSnippet: String by lazy(LazyThreadSafetyMode.NONE) {
        if (content.isEmpty()) return@lazy "No additional text"

        val sb = StringBuilder(128)
        var linesFound = 0
        var lineStart = 0
        val len = content.length

        while (lineStart < len && linesFound < 2) {
            var lineEnd = content.indexOf('\n', lineStart)
            if (lineEnd == -1) lineEnd = len

            var start = lineStart
            while (start < lineEnd && content[start].isWhitespace()) {
                start++
            }

            if (start < lineEnd && content[start] != '#') {
                if (content.startsWith("○ ", start) || content.startsWith("✓ ", start) || content.startsWith("• ", start)) {
                    start += 2
                } else if (content.startsWith("- [ ] ", start) || content.startsWith("* [ ] ", start)) {
                    start += 6
                } else if (content.startsWith("- [x] ", start) || content.startsWith("- [X] ", start) ||
                    content.startsWith("* [x] ", start) || content.startsWith("* [X] ", start)) {
                    start += 6
                } else if (content.startsWith("- ", start) || content.startsWith("* ", start)) {
                    start += 2
                }

                var appendedInLine = false
                for (i in start until lineEnd) {
                    val c = content[i]
                    if (c != '#' && c != '*' && c != '_' && c != '`' && c != '~') {
                        if (sb.length < 120) {
                            if (!appendedInLine && sb.isNotEmpty()) {
                                sb.append(' ')
                            }
                            sb.append(c)
                            appendedInLine = true
                        }
                    }
                }
                if (appendedInLine) {
                    linesFound++
                }
            }

            lineStart = lineEnd + 1
        }

        val res = sb.toString().trim()
        if (res.isBlank()) "No additional text" else res
    }

    val wordCount: Int by lazy(LazyThreadSafetyMode.NONE) {
        var count = 0
        var inWord = false

        for (i in 0 until title.length) {
            val c = title[i]
            if (c.isWhitespace()) {
                inWord = false
            } else if (!inWord) {
                inWord = true
                count++
            }
        }
        inWord = false
        for (i in 0 until content.length) {
            val c = content[i]
            if (c.isWhitespace()) {
                inWord = false
            } else if (!inWord) {
                inWord = true
                count++
            }
        }
        count
    }

    val characterCount: Int by lazy(LazyThreadSafetyMode.NONE) {
        val t = title.trim()
        val c = content.trim()
        when {
            t.isEmpty() -> c.length
            c.isEmpty() -> t.length
            else -> t.length + 1 + c.length
        }
    }

    val readingTimeMinutes: Int by lazy(LazyThreadSafetyMode.NONE) {
        maxOf(1, (wordCount / 200))
    }

    val taskStats: Pair<Int, Int>? by lazy(LazyThreadSafetyMode.NONE) {
        if (content.isEmpty()) return@lazy null
        var total = 0
        var completed = 0
        var lineStart = 0
        val len = content.length

        while (lineStart < len) {
            var lineEnd = content.indexOf('\n', lineStart)
            if (lineEnd == -1) lineEnd = len

            var start = lineStart
            while (start < lineEnd && content[start].isWhitespace()) {
                start++
            }

            if (start < lineEnd) {
                if (content.startsWith("○ ", start)) {
                    total++
                } else if (content.startsWith("✓ ", start)) {
                    total++
                    completed++
                } else if (content.startsWith("- [ ] ", start) || content.startsWith("* [ ] ", start)) {
                    total++
                } else if (content.startsWith("- [x] ", start) || content.startsWith("- [X] ", start) ||
                    content.startsWith("* [x] ", start) || content.startsWith("* [X] ", start)) {
                    total++
                    completed++
                }
            }

            lineStart = lineEnd + 1
        }

        if (total == 0) null else Pair(completed, total)
    }

    val outlinks: List<String> by lazy(LazyThreadSafetyMode.NONE) {
        if (content.isEmpty() && title.isEmpty()) return@lazy emptyList()
        val full = if (title.isEmpty()) content else "$title\n$content"
        WIKILINK_REGEX.findAll(full)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }

    val formattedDate: String by lazy(LazyThreadSafetyMode.NONE) {
        val instant = Instant.ofEpochMilli(lastModified)
        DATE_FORMATTER.format(instant)
    }

    companion object {
        val WIKILINK_REGEX = """(?<!\!)\[\[([^\]\|\n]+)(?:\|([^\]\n]+))?\]\]""".toRegex()
        private val DATE_FORMATTER = DateTimeFormatter
            .ofPattern("dd/MM/yy")
            .withZone(ZoneId.systemDefault())
    }
}

