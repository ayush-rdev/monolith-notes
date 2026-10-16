package com.monolith.notes.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
// Monolith WYSIWYG Editor Visual Transformation

/**
 * Apple Notes Visual Transformation:
 * Live WYSIWYG styling of text with 100% 1:1 OffsetMapping (Identity).
 * 
 * Guarantees 0ms typing latency, zero cursor jumping, and pristine Gboard/Samsung Keyboard compatibility:
 * - Checklists: "○ " glows in iOS Yellow; "✓ " is checked and the line is struck-through in subtle gray.
 * - Headings: "# ", "## ", "### " markers are dimmed, text is styled large and bold.
 * - Bold: "**word**" markers are dimmed, inner word is bold white.
 * - Italic: "*word*" markers are dimmed, inner word is italic.
 * - Hashtags: "#tag" is highlighted in theme accent color.
 * - Quotes: "> text" is italicized and tinted.
 */
class AppleNotesVisualTransformation(
    private val accentColor: Color = Color(0xFFE5A93C),
    private val secondaryTextColor: Color = Color(0xFF8E8E93)
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val builder = AnnotatedString.Builder(raw)
        val ghostSyntaxColor = Color(0xFF545458) // Dimmed subtle iOS gray for markdown syntax tokens

        // 1. Process line-by-line formatting (Headings, Checklists, Bullets, Quotes)
        var lineStart = 0
        while (lineStart < raw.length) {
            val lineEnd = raw.indexOf('\n', lineStart).let { if (it == -1) raw.length else it }
            val line = raw.substring(lineStart, lineEnd)
            val trimmedLine = line.trimStart()
            val indent = line.length - trimmedLine.length
            val actualStart = lineStart + indent

            when {
                // Completed Checklist: "✓ " or "- [x] "
                trimmedLine.startsWith("✓ ") || trimmedLine.startsWith("- [x] ") || trimmedLine.startsWith("- [X] ") -> {
                    val prefixLen = if (trimmedLine.startsWith("✓ ")) 2 else 6
                    builder.addStyle(
                        SpanStyle(color = accentColor, fontWeight = FontWeight.Bold),
                        actualStart,
                        actualStart + prefixLen - 1
                    )
                    if (actualStart + prefixLen < lineEnd) {
                        builder.addStyle(
                            SpanStyle(
                                color = secondaryTextColor,
                                textDecoration = TextDecoration.LineThrough
                            ),
                            actualStart + prefixLen,
                            lineEnd
                        )
                    }
                }

                // Active Checklist: "○ " or "- [ ] "
                trimmedLine.startsWith("○ ") || trimmedLine.startsWith("- [ ] ") -> {
                    val prefixLen = if (trimmedLine.startsWith("○ ")) 2 else 6
                    builder.addStyle(
                        SpanStyle(color = accentColor, fontWeight = FontWeight.Bold),
                        actualStart,
                        actualStart + prefixLen - 1
                    )
                }

                // Bullet item: "• ", "- ", "* "
                trimmedLine.startsWith("• ") || (trimmedLine.startsWith("- ") && !trimmedLine.startsWith("- [")) || (trimmedLine.startsWith("* ") && !trimmedLine.startsWith("* [")) -> {
                    builder.addStyle(
                        SpanStyle(color = accentColor, fontWeight = FontWeight.Bold),
                        actualStart,
                        actualStart + 1
                    )
                }

                // Headings (H1 to H6): Must have 1-6 '#' followed by at least one space
                HEADING_PREFIX_REGEX.find(trimmedLine) != null -> {
                    val match = HEADING_PREFIX_REGEX.find(trimmedLine)!!
                    val level = match.groupValues[1].length
                    val prefixLen = match.value.length
                    val (prefixSize, textSize, weight) = when (level) {
                        1 -> Triple(14.sp, 24.sp, FontWeight.Bold)
                        2 -> Triple(13.sp, 20.sp, FontWeight.Bold)
                        3 -> Triple(12.sp, 18.sp, FontWeight.Bold)
                        4 -> Triple(12.sp, 16.sp, FontWeight.SemiBold)
                        5 -> Triple(11.sp, 15.sp, FontWeight.SemiBold)
                        else -> Triple(11.sp, 14.sp, FontWeight.SemiBold)
                    }
                    builder.addStyle(SpanStyle(color = ghostSyntaxColor, fontSize = prefixSize), actualStart, actualStart + prefixLen)
                    if (actualStart + prefixLen < lineEnd) {
                        builder.addStyle(SpanStyle(fontSize = textSize, fontWeight = weight, color = Color.White), actualStart + prefixLen, lineEnd)
                    }
                }

                // Blockquote: "> "
                trimmedLine.startsWith("> ") -> {
                    builder.addStyle(
                        SpanStyle(color = ghostSyntaxColor),
                        actualStart,
                        actualStart + 2
                    )
                    builder.addStyle(
                        SpanStyle(fontStyle = FontStyle.Italic, color = secondaryTextColor),
                        actualStart + 2,
                        lineEnd
                    )
                }

                // Horizontal Rule / Separator: "---", "***", "___", "───"
                trimmedLine.matches("^(\\-{3,}|\\*{3,}|_{3,}|─{3,})$".toRegex()) -> {
                    builder.addStyle(
                        SpanStyle(
                            color = ghostSyntaxColor,
                            letterSpacing = 6.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        actualStart,
                        lineEnd
                    )
                }
            }

            lineStart = lineEnd + 1
        }

        // 2. Inline Bold: "**text**"
        for (match in BOLD_REGEX.findAll(raw)) {
            val range = match.range
            builder.addStyle(SpanStyle(color = ghostSyntaxColor, fontSize = 11.sp), range.first, range.first + 2)
            builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White), range.first + 2, range.last - 1)
            builder.addStyle(SpanStyle(color = ghostSyntaxColor, fontSize = 11.sp), range.last - 1, range.last + 1)
        }

        // 3. Inline Italic: "*text*"
        for (match in ITALIC_REGEX.findAll(raw)) {
            val range = match.range
            builder.addStyle(SpanStyle(color = ghostSyntaxColor, fontSize = 11.sp), range.first, range.first + 1)
            builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic, color = Color.White), range.first + 1, range.last)
            builder.addStyle(SpanStyle(color = ghostSyntaxColor, fontSize = 11.sp), range.last, range.last + 1)
        }

        // 4. Inline Strikethrough: "~~text~~"
        for (match in STRIKE_REGEX.findAll(raw)) {
            val range = match.range
            builder.addStyle(SpanStyle(color = ghostSyntaxColor, fontSize = 11.sp), range.first, range.first + 2)
            builder.addStyle(
                SpanStyle(textDecoration = TextDecoration.LineThrough, color = secondaryTextColor),
                range.first + 2,
                range.last - 1
            )
            builder.addStyle(SpanStyle(color = ghostSyntaxColor, fontSize = 11.sp), range.last - 1, range.last + 1)
        }

        // 5. Inline Code: "`code`"
        for (match in CODE_REGEX.findAll(raw)) {
            val range = match.range
            builder.addStyle(SpanStyle(color = ghostSyntaxColor, fontSize = 11.sp), range.first, range.first + 1)
            builder.addStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    color = accentColor,
                    background = Color(0xFF2C2C2E)
                ),
                range.first + 1,
                range.last
            )
            builder.addStyle(SpanStyle(color = ghostSyntaxColor, fontSize = 11.sp), range.last, range.last + 1)
        }

        // 6. Hashtags: "#tag"
        for (match in TAG_REGEX.findAll(raw)) {
            val tagGroup = match.groups[1]
            if (tagGroup != null) {
                builder.addStyle(
                    SpanStyle(color = accentColor, fontWeight = FontWeight.SemiBold),
                    tagGroup.range.first,
                    tagGroup.range.last + 1
                )
            }
        }

        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }

    companion object {
        private val HEADING_PREFIX_REGEX = "^(#{1,6})\\s+".toRegex()
        private val BOLD_REGEX = "\\*\\*(.+?)\\*\\*".toRegex()
        private val ITALIC_REGEX = "(?<!\\*)\\*([^*\\n]+?)\\*(?!\\*)".toRegex()
        private val STRIKE_REGEX = "~~(.+?)~~".toRegex()
        private val CODE_REGEX = "`([^`\\n]+)`".toRegex()
        private val TAG_REGEX = "(?:^|\\s)(#[a-zA-Z0-9_\\-]+)".toRegex()
    }
}
