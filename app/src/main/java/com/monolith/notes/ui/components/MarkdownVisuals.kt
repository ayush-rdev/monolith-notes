package com.monolith.notes.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.monolith.notes.ui.theme.*
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.*
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.*
import org.commonmark.parser.Parser
import java.io.File

/**
 * CommonMark + GitHub Flavored Markdown (GFM) Specification Parser.
 * Single-pass AST engine with 0ms regex backtracking, rock-solid 120 FPS caching.
 */
private val commonMarkExtensions = listOf(
    TablesExtension.create(),
    StrikethroughExtension.create(),
    AutolinkExtension.create(),
    TaskListItemsExtension.create()
)

val CommonMarkParser: Parser = Parser.builder()
    .extensions(commonMarkExtensions)
    .build()

private val INLINE_EXTENSIONS_REGEX = "(\\[\\[[^\\]\\|\\n]+(?:\\|[^\\]\\n]+)?\\]\\]|==.*?==|#[a-zA-Z0-9_\\-/]+)".toRegex()
private val TASK_TOGGLE_REGEX = "(^[ \\t]*(?:[-*]\\s+\\[[ xX]\\]|[○✓]))([ \\t]+.*$)".toRegex(RegexOption.MULTILINE)

data class MarkdownThemeColors(
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val elevatedCard: Color,
    val accent: Color
)

/**
 * Converts CommonMark inline nodes (Text, Emphasis, StrongEmphasis, Strikethrough, Code, Link)
 * into a rich Compose AnnotatedString. Also resolves Obsidian [[WikiLinks]], ==highlights==, and #tags.
 */
@Composable
fun buildAnnotatedStringFromNodes(firstNode: Node?): AnnotatedString {
    val colors = MarkdownThemeColors(
        textPrimary = iOSTextPrimary,
        textSecondary = iOSTextSecondary,
        textTertiary = iOSTextTertiary,
        elevatedCard = iOSElevatedCard,
        accent = iOSYellow
    )
    return buildAnnotatedStringFromNodes(firstNode, colors)
}

fun buildAnnotatedStringFromNodes(firstNode: Node?, colors: MarkdownThemeColors): AnnotatedString {
    if (firstNode == null) return AnnotatedString("")
    return buildAnnotatedString {
        var current: Node? = firstNode
        while (current != null) {
            appendCommonMarkNode(current, colors)
            current = current.next
        }
    }
}

private fun AnnotatedString.Builder.appendCommonMarkNode(node: Node, colors: MarkdownThemeColors) {
    when (node) {
        is Text -> {
            appendPlainTextWithObsidianExtensions(node.literal, colors.accent)
        }
        is StrongEmphasis -> {
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colors.textPrimary))
            var child = node.firstChild
            while (child != null) {
                appendCommonMarkNode(child, colors)
                child = child.next
            }
            pop()
        }
        is Emphasis -> {
            pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = colors.textSecondary))
            var child = node.firstChild
            while (child != null) {
                appendCommonMarkNode(child, colors)
                child = child.next
            }
            pop()
        }
        is Strikethrough -> {
            pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = colors.textTertiary))
            var child = node.firstChild
            while (child != null) {
                appendCommonMarkNode(child, colors)
                child = child.next
            }
            pop()
        }
        is Code -> {
            pushStyle(
                SpanStyle(
                    fontFamily = SFMono,
                    background = colors.elevatedCard,
                    color = colors.accent,
                    fontSize = 14.sp
                )
            )
            append(" ${node.literal} ")
            pop()
        }
        is Link -> {
            val destination = node.destination
            pushStringAnnotation(tag = "URL", annotation = destination)
            pushStyle(
                SpanStyle(
                    color = Color(0xFF60A5FA),
                    fontWeight = FontWeight.Medium,
                    textDecoration = TextDecoration.Underline
                )
            )
            var child = node.firstChild
            while (child != null) {
                appendCommonMarkNode(child, colors)
                child = child.next
            }
            pop()
            pop()
        }
        is HardLineBreak -> {
            append("\n")
        }
        is SoftLineBreak -> {
            append(" ")
        }
        is TaskListItemMarker -> {
            // Handled at block list item level
        }
        else -> {
            var child = node.firstChild
            while (child != null) {
                appendCommonMarkNode(child, colors)
                child = child.next
            }
        }
    }
}

/**
 * Handles Obsidian-specific extensions: [[WikiLink|Alias]], ==highlight==, and #tags
 */
private fun AnnotatedString.Builder.appendPlainTextWithObsidianExtensions(text: String, accentColor: Color) {
    if (text.isEmpty()) return
    var cursor = 0
    val matches = INLINE_EXTENSIONS_REGEX.findAll(text)

    for (match in matches) {
        val start = match.range.first
        val end = match.range.last + 1
        if (start > cursor) {
            append(text.substring(cursor, start))
        }
        val matchValue = match.value
        when {
            // Obsidian WikiLink: [[Target]] or [[Target|Alias]]
            matchValue.startsWith("[[") && matchValue.endsWith("]]") && matchValue.length >= 4 -> {
                val inner = matchValue.substring(2, matchValue.length - 2)
                val pipeIdx = inner.indexOf('|')
                val target = if (pipeIdx != -1) inner.substring(0, pipeIdx).trim() else inner.trim()
                val label = if (pipeIdx != -1) inner.substring(pipeIdx + 1).trim() else inner.trim()

                if (target.isNotBlank()) {
                    pushStringAnnotation(tag = "WIKILINK", annotation = target)
                    pushStyle(
                        SpanStyle(
                            color = accentColor,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                    append(label.ifBlank { target })
                    pop()
                    pop()
                } else {
                    append(matchValue)
                }
            }
            // Highlight: ==text==
            matchValue.startsWith("==") && matchValue.endsWith("==") && matchValue.length >= 4 -> {
                val inner = matchValue.substring(2, matchValue.length - 2)
                pushStyle(
                    SpanStyle(
                        background = accentColor.copy(alpha = 0.28f),
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                append(inner)
                pop()
            }
            // Hashtag: #tag
            matchValue.startsWith("#") -> {
                pushStyle(SpanStyle(color = accentColor, fontWeight = FontWeight.SemiBold))
                append(matchValue)
                pop()
            }
            else -> append(matchValue)
        }
        cursor = end
    }

    if (cursor < text.length) {
        append(text.substring(cursor))
    }
}

private val TABLE_DELIM_COL_REGEX = "^:?-{1,}:?$".toRegex()

fun isTableDelimiter(line: String): Boolean {
    val trimmed = line.trim()
    if (!trimmed.contains('|') || !trimmed.contains('-')) return false
    val inner = trimmed.trim('|').trim()
    if (inner.isEmpty()) return false
    val parts = inner.split('|')
    if (parts.isEmpty()) return false
    return parts.all { col ->
        val c = col.trim()
        c.matches(TABLE_DELIM_COL_REGEX)
    }
}

fun splitTableRow(line: String): List<String> {
    var trimmed = line.trim()
    if (trimmed.startsWith('|')) {
        trimmed = trimmed.substring(1)
    }
    if (trimmed.endsWith('|')) {
        trimmed = trimmed.substring(0, trimmed.length - 1)
    }
    return trimmed.split('|').map { it.trim() }
}

/**
 * High-performance inline parser using CommonMark AST.
 */
fun parseInlineMarkdown(text: String, colors: MarkdownThemeColors): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString("")
    val doc = CommonMarkParser.parse(text)
    val firstBlock = doc.firstChild
    return if (firstBlock is Paragraph) {
        buildAnnotatedStringFromNodes(firstBlock.firstChild, colors)
    } else {
        buildAnnotatedStringFromNodes(doc.firstChild, colors)
    }
}

@Composable
fun parseInlineMarkdown(text: String): AnnotatedString {
    val colors = MarkdownThemeColors(
        textPrimary = iOSTextPrimary,
        textSecondary = iOSTextSecondary,
        textTertiary = iOSTextTertiary,
        elevatedCard = iOSElevatedCard,
        accent = iOSYellow
    )
    return parseInlineMarkdown(text, colors)
}

/**
 * Safely toggles a markdown checklist task at [targetTaskIndex], preserving indentation.
 */
fun toggleMarkdownTask(content: String, targetTaskIndex: Int): String {
    var inCodeBlock = false
    var currentTaskIndex = 0
    val lines = content.lines()
    val result = ArrayList<String>(lines.size)

    val singleLineTaskRegex = "^([ \\t]*(?:[-*]\\s+\\[[ xX]\\]|\\d+\\.\\s+\\[[ xX]\\]|[○✓]))(?:([ \\t]+)(.*))?$".toRegex()

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            inCodeBlock = !inCodeBlock
            result.add(line)
            continue
        }
        if (inCodeBlock) {
            result.add(line)
            continue
        }

        val match = singleLineTaskRegex.find(line)
        if (match != null) {
            if (currentTaskIndex == targetTaskIndex) {
                val fullPrefix = match.groupValues[1]
                val leadingWhitespace = fullPrefix.takeWhile { it == ' ' || it == '\t' }
                val marker = fullPrefix.trim()
                val space = if (match.groupValues[2].isNotEmpty()) match.groupValues[2] else " "
                val rest = match.groupValues[3]

                val newMarker = when {
                    marker.startsWith("○") -> "✓"
                    marker.startsWith("✓") -> "○"
                    marker.contains("[ ]") -> marker.replace("[ ]", "[x]")
                    else -> marker.replace("[x]", "[ ]").replace("[X]", "[ ]")
                }
                currentTaskIndex++
                result.add("$leadingWhitespace$newMarker$space$rest")
            } else {
                currentTaskIndex++
                result.add(line)
            }
        } else {
            result.add(line)
        }
    }
    return result.joinToString("\n")
}

/**
 * Interactive text composable that opens URLs in browser and navigates to WikiLinks.
 */
@Composable
fun MarkdownClickableText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    lineHeight: TextUnit = TextUnit.Unspecified,
    onNavigateToNote: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val hasWikiLinks = remember(text) {
        text.getStringAnnotations("WIKILINK", 0, text.length).isNotEmpty()
    }
    val hasUrls = remember(text) {
        text.getStringAnnotations("URL", 0, text.length).isNotEmpty()
    }

    if (hasWikiLinks || hasUrls) {
        ClickableText(
            text = text,
            style = style.copy(lineHeight = lineHeight),
            modifier = modifier,
            onClick = { offset ->
                val urlAnnotations = text.getStringAnnotations("URL", offset, offset)
                if (urlAnnotations.isNotEmpty()) {
                    val rawUrl = urlAnnotations.first().item
                    val validUrl = if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
                        "https://$rawUrl"
                    } else {
                        rawUrl
                    }
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(validUrl)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(context, "Cannot open link: $validUrl", Toast.LENGTH_SHORT).show()
                    }
                    return@ClickableText
                }

                if (onNavigateToNote != null) {
                    val wikiAnnotations = text.getStringAnnotations("WIKILINK", offset, offset)
                    if (wikiAnnotations.isNotEmpty()) {
                        onNavigateToNote(wikiAnnotations.first().item)
                    }
                }
            }
        )
    } else {
        Text(
            text = text,
            style = style,
            lineHeight = lineHeight,
            modifier = modifier
        )
    }
}

/**
 * Normalizes Monolith UI Apple Notes syntax (○ / ✓ checklists, • bullets) into standard
 * Markdown task items (- [ ] / - [x] / - ) so CommonMark AST treats each item as a distinct list element.
 */
fun normalizeContentForMarkdownParser(raw: String): String {
    if (!raw.contains("○") && !raw.contains("✓") && !raw.contains("•") && !raw.contains("- [")) {
        return raw
    }
    val lines = raw.lines()
    val sb = StringBuilder(raw.length + 64)
    var inCodeBlock = false
    var prevWasTaskOrBullet = false

    for (i in lines.indices) {
        val line = lines[i]
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            inCodeBlock = !inCodeBlock
            sb.append(line)
            prevWasTaskOrBullet = false
        } else if (inCodeBlock) {
            sb.append(line)
            prevWasTaskOrBullet = false
        } else {
            val indent = line.takeWhile { it == ' ' || it == '\t' }
            val isTask = trimmed.startsWith("○ ") || trimmed == "○" ||
                         trimmed.startsWith("✓ ") || trimmed == "✓" ||
                         trimmed.startsWith("- [ ] ") || trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ")
            val isBullet = trimmed.startsWith("• ") || trimmed == "•"

            // Ensure clean blank line separation before starting a list from a regular paragraph
            if ((isTask || isBullet) && !prevWasTaskOrBullet && i > 0 && lines[i - 1].isNotBlank()) {
                sb.append('\n')
            } else if (!isTask && !isBullet && prevWasTaskOrBullet && line.isNotBlank()) {
                sb.append('\n')
            }

            when {
                trimmed.startsWith("○ ") -> {
                    sb.append(indent).append("- [ ] ").append(trimmed.substring(2))
                    prevWasTaskOrBullet = true
                }
                trimmed == "○" -> {
                    sb.append(indent).append("- [ ] ")
                    prevWasTaskOrBullet = true
                }
                trimmed.startsWith("✓ ") -> {
                    sb.append(indent).append("- [x] ").append(trimmed.substring(2))
                    prevWasTaskOrBullet = true
                }
                trimmed == "✓" -> {
                    sb.append(indent).append("- [x] ")
                    prevWasTaskOrBullet = true
                }
                trimmed.startsWith("• ") -> {
                    sb.append(indent).append("- ").append(trimmed.substring(2))
                    prevWasTaskOrBullet = true
                }
                trimmed == "•" -> {
                    sb.append(indent).append("- ")
                    prevWasTaskOrBullet = true
                }
                else -> {
                    sb.append(line)
                    prevWasTaskOrBullet = isTask || isBullet
                }
            }
        }
        if (i < lines.size - 1) {
            sb.append('\n')
        }
    }
    return sb.toString()
}

/**
 * Master CommonMark + GFM Markdown Render View.
 * Parses arbitrary markdown documents into native Compose components at 120 FPS.
 */
@Composable
fun MarkdownRenderView(
    content: String,
    vaultPath: String = "/sdcard/Documents/MonolithNotes",
    modifier: Modifier = Modifier,
    onTaskToggled: ((taskIndex: Int) -> Unit)? = null,
    onNavigateToNote: ((String) -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val document = remember(content) {
        val normalized = normalizeContentForMarkdownParser(content)
        CommonMarkParser.parse(normalized)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        var taskCounter = 0
        var blockNode: Node? = document.firstChild

        while (blockNode != null) {
            when (val node = blockNode) {
                is Heading -> {
                    val headingText = buildAnnotatedStringFromNodes(node.firstChild)
                    val (style, topPadding) = when (node.level) {
                        1 -> MonolithTypography.headlineLarge.copy(letterSpacing = (-0.5).sp) to 14.dp
                        2 -> MonolithTypography.headlineMedium.copy(letterSpacing = (-0.3).sp) to 12.dp
                        3 -> MonolithTypography.titleLarge.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp) to 10.dp
                        4 -> MonolithTypography.titleLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold) to 8.dp
                        else -> MonolithTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold) to 6.dp
                    }
                    MarkdownClickableText(
                        text = headingText,
                        style = style.copy(color = iOSTextPrimary),
                        modifier = Modifier.padding(top = topPadding, bottom = 2.dp),
                        onNavigateToNote = onNavigateToNote
                    )
                }
                is Paragraph -> {
                    // Check if paragraph is solely an image
                    val firstChild = node.firstChild
                    if (firstChild is Image && firstChild.next == null) {
                        IOSInlineImage(
                            alt = firstChild.title ?: "",
                            path = firstChild.destination,
                            vaultPath = vaultPath
                        )
                    } else {
                        // Check for standalone Apple checklist circle ○ / ✓ in paragraph
                        val paragraphText = buildAnnotatedStringFromNodes(node.firstChild)
                        val rawString = paragraphText.text
                        if (rawString.contains("○ ") || rawString.contains("✓ ")) {
                            val lines = rawString.lines()
                            for (line in lines) {
                                val trimmed = line.trim()
                                if (trimmed.startsWith("○ ") || trimmed.startsWith("✓ ")) {
                                    val isChecked = trimmed.startsWith("✓ ")
                                    val taskText = trimmed.substring(2)
                                    val currentIndex = taskCounter++
                                    iOSChecklistRow(
                                        checked = isChecked,
                                        text = taskText,
                                        onToggle = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onTaskToggled?.invoke(currentIndex)
                                        },
                                        onNavigateToNote = onNavigateToNote
                                    )
                                } else if (trimmed.isNotBlank()) {
                                    MarkdownClickableText(
                                        text = parseInlineMarkdown(trimmed),
                                        style = MonolithTypography.bodyLarge,
                                        lineHeight = 26.sp,
                                        onNavigateToNote = onNavigateToNote
                                    )
                                }
                            }
                        } else {
                            MarkdownClickableText(
                                text = paragraphText,
                                style = MonolithTypography.bodyLarge,
                                lineHeight = 26.sp,
                                onNavigateToNote = onNavigateToNote
                            )
                        }
                    }
                }
                is BulletList -> {
                    RenderCommonMarkList(
                        listBlock = node,
                        isOrdered = false,
                        vaultPath = vaultPath,
                        getTaskIndex = { taskCounter++ },
                        onTaskToggled = onTaskToggled,
                        onNavigateToNote = onNavigateToNote
                    )
                }
                is OrderedList -> {
                    RenderCommonMarkList(
                        listBlock = node,
                        isOrdered = true,
                        vaultPath = vaultPath,
                        getTaskIndex = { taskCounter++ },
                        onTaskToggled = onTaskToggled,
                        onNavigateToNote = onNavigateToNote
                    )
                }
                is BlockQuote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(iOSCardBackground)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(22.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(iOSYellow)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            var quoteChild: Node? = node.firstChild
                            while (quoteChild != null) {
                                when (quoteChild) {
                                    is Paragraph -> {
                                        MarkdownClickableText(
                                            text = buildAnnotatedStringFromNodes(quoteChild.firstChild),
                                            style = MonolithTypography.bodyMedium.copy(
                                                fontStyle = FontStyle.Italic,
                                                color = iOSTextSecondary
                                            ),
                                            onNavigateToNote = onNavigateToNote
                                        )
                                    }
                                    else -> {}
                                }
                                quoteChild = quoteChild.next
                            }
                        }
                    }
                }
                is FencedCodeBlock -> {
                    iOSCodeBlockCard(
                        code = node.literal.trimEnd(),
                        language = node.info ?: ""
                    )
                }
                is IndentedCodeBlock -> {
                    iOSCodeBlockCard(
                        code = node.literal.trimEnd(),
                        language = ""
                    )
                }
                is TableBlock -> {
                    RenderCommonMarkTable(
                        tableBlock = node,
                        onNavigateToNote = onNavigateToNote
                    )
                }
                is ThematicBreak -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        thickness = 1.dp,
                        color = iOSDivider
                    )
                }
                else -> {
                    var child = node.firstChild
                    while (child != null) {
                        if (child is Paragraph) {
                            MarkdownClickableText(
                                text = buildAnnotatedStringFromNodes(child.firstChild),
                                style = MonolithTypography.bodyLarge,
                                lineHeight = 26.sp,
                                onNavigateToNote = onNavigateToNote
                            )
                        }
                        child = child.next
                    }
                }
            }
            blockNode = blockNode.next
        }
    }
}

/**
 * Recursively renders Bullet and Ordered lists, including GFM Task Lists and nested sublists.
 */
@Composable
private fun RenderCommonMarkList(
    listBlock: Block,
    isOrdered: Boolean,
    vaultPath: String,
    getTaskIndex: () -> Int,
    indentLevel: Int = 0,
    onTaskToggled: ((taskIndex: Int) -> Unit)? = null,
    onNavigateToNote: ((String) -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    var itemCounter = if (listBlock is OrderedList) listBlock.startNumber else 1
    var itemNode: Node? = listBlock.firstChild

    while (itemNode != null) {
        val currentListItem = itemNode as? ListItem
        if (currentListItem != null) {
            val firstBlock = currentListItem.firstChild
            var isTask = false
            var isChecked = false
            var textParagraph: Paragraph? = null

            var child = currentListItem.firstChild
            while (child != null) {
                if (child is TaskListItemMarker) {
                    isTask = true
                    isChecked = child.isChecked
                } else if (child is Paragraph) {
                    if (textParagraph == null) {
                        textParagraph = child
                    }
                    var inlineNode = child.firstChild
                    while (inlineNode != null) {
                        if (inlineNode is TaskListItemMarker) {
                            isTask = true
                            isChecked = inlineNode.isChecked
                            break
                        }
                        inlineNode = inlineNode.next
                    }
                }
                child = child.next
            }

            val indentPadding = (indentLevel * 18).dp

            if (isTask) {
                val currentIndex = getTaskIndex()
                val taskAnnotated = buildAnnotatedStringFromNodes(textParagraph?.firstChild)
                iOSChecklistRow(
                    checked = isChecked,
                    text = taskAnnotated.text.trim(),
                    modifier = Modifier.padding(start = indentPadding),
                    onToggle = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTaskToggled?.invoke(currentIndex)
                    },
                    onNavigateToNote = onNavigateToNote
                )
                var nextBlock = firstBlock?.next
                while (nextBlock != null) {
                    when (nextBlock) {
                        is BulletList -> {
                            RenderCommonMarkList(
                                listBlock = nextBlock,
                                isOrdered = false,
                                vaultPath = vaultPath,
                                getTaskIndex = getTaskIndex,
                                indentLevel = indentLevel + 1,
                                onTaskToggled = onTaskToggled,
                                onNavigateToNote = onNavigateToNote
                            )
                        }
                        is OrderedList -> {
                            RenderCommonMarkList(
                                listBlock = nextBlock,
                                isOrdered = true,
                                vaultPath = vaultPath,
                                getTaskIndex = getTaskIndex,
                                indentLevel = indentLevel + 1,
                                onTaskToggled = onTaskToggled,
                                onNavigateToNote = onNavigateToNote
                            )
                        }
                    }
                    nextBlock = nextBlock.next
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp + indentPadding, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    if (isOrdered) {
                        Text(
                            text = "$itemCounter.",
                            style = MonolithTypography.bodyLarge.copy(
                                color = iOSYellow,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        itemCounter++
                    } else {
                        Box(
                            modifier = Modifier
                                .padding(top = 9.dp, end = 10.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(iOSYellow)
                        )
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        var childBlock = currentListItem.firstChild
                        while (childBlock != null) {
                            when (childBlock) {
                                is Paragraph -> {
                                    MarkdownClickableText(
                                        text = buildAnnotatedStringFromNodes(childBlock.firstChild),
                                        style = MonolithTypography.bodyLarge,
                                        lineHeight = 24.sp,
                                        onNavigateToNote = onNavigateToNote
                                    )
                                }
                                is BulletList -> {
                                    RenderCommonMarkList(
                                        listBlock = childBlock,
                                        isOrdered = false,
                                        vaultPath = vaultPath,
                                        getTaskIndex = getTaskIndex,
                                        indentLevel = indentLevel + 1,
                                        onTaskToggled = onTaskToggled,
                                        onNavigateToNote = onNavigateToNote
                                    )
                                }
                                is OrderedList -> {
                                    RenderCommonMarkList(
                                        listBlock = childBlock,
                                        isOrdered = true,
                                        vaultPath = vaultPath,
                                        getTaskIndex = getTaskIndex,
                                        indentLevel = indentLevel + 1,
                                        onTaskToggled = onTaskToggled,
                                        onNavigateToNote = onNavigateToNote
                                    )
                                }
                                else -> {}
                            }
                            childBlock = childBlock.next
                        }
                    }
                }
            }
        }
        itemNode = itemNode.next
    }
}

/**
 * GFM Table Renderer with horizontal scroll and iOS card theme.
 */
@Composable
private fun RenderCommonMarkTable(
    tableBlock: TableBlock,
    onNavigateToNote: ((String) -> Unit)? = null
) {
    val headers = mutableListOf<AnnotatedString>()
    val alignments = mutableListOf<TextAlign>()
    val rows = mutableListOf<List<AnnotatedString>>()

    var section = tableBlock.firstChild
    while (section != null) {
        when (section) {
            is TableHead -> {
                var rowNode = section.firstChild
                while (rowNode != null) {
                    if (rowNode is TableRow) {
                        var cellNode = rowNode.firstChild
                        while (cellNode != null) {
                            if (cellNode is TableCell) {
                                headers.add(buildAnnotatedStringFromNodes(cellNode.firstChild))
                                alignments.add(
                                    when (cellNode.alignment) {
                                        TableCell.Alignment.CENTER -> TextAlign.Center
                                        TableCell.Alignment.RIGHT -> TextAlign.End
                                        else -> TextAlign.Start
                                    }
                                )
                            }
                            cellNode = cellNode.next
                        }
                    }
                    rowNode = rowNode.next
                }
            }
            is TableBody -> {
                var rowNode = section.firstChild
                while (rowNode != null) {
                    if (rowNode is TableRow) {
                        val rowCells = mutableListOf<AnnotatedString>()
                        var cellNode = rowNode.firstChild
                        while (cellNode != null) {
                            if (cellNode is TableCell) {
                                rowCells.add(buildAnnotatedStringFromNodes(cellNode.firstChild))
                            }
                            cellNode = cellNode.next
                        }
                        rows.add(rowCells)
                    }
                    rowNode = rowNode.next
                }
            }
        }
        section = section.next
    }

    if (headers.isEmpty() && rows.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(iOSCardBackground)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                // Table Header
                if (headers.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .background(iOSTextPrimary.copy(alpha = 0.05f))
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        headers.forEachIndexed { index, headerText ->
                            val align = alignments.getOrElse(index) { TextAlign.Start }
                            Box(
                                modifier = Modifier
                                    .widthIn(min = 100.dp, max = 240.dp)
                                    .padding(horizontal = 12.dp),
                                contentAlignment = when (align) {
                                    TextAlign.Center -> Alignment.Center
                                    TextAlign.End -> Alignment.CenterEnd
                                    else -> Alignment.CenterStart
                                }
                            ) {
                                MarkdownClickableText(
                                    text = headerText,
                                    style = MonolithTypography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = iOSTextPrimary
                                    ),
                                    onNavigateToNote = onNavigateToNote
                                )
                            }
                        }
                    }

                    HorizontalDivider(thickness = 1.dp, color = iOSDivider)
                }

                // Table Rows
                rows.forEachIndexed { rowIndex, rowCells ->
                    Row(
                        modifier = Modifier.padding(vertical = 9.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowCells.forEachIndexed { colIndex, cellText ->
                            val align = alignments.getOrElse(colIndex) { TextAlign.Start }
                            Box(
                                modifier = Modifier
                                    .widthIn(min = 100.dp, max = 240.dp)
                                    .padding(horizontal = 12.dp),
                                contentAlignment = when (align) {
                                    TextAlign.Center -> Alignment.Center
                                    TextAlign.End -> Alignment.CenterEnd
                                    else -> Alignment.CenterStart
                                }
                            ) {
                                MarkdownClickableText(
                                    text = cellText,
                                    style = MonolithTypography.bodyMedium.copy(color = iOSTextPrimary),
                                    onNavigateToNote = onNavigateToNote
                                )
                            }
                        }
                    }

                    if (rowIndex < rows.size - 1) {
                        HorizontalDivider(thickness = 0.5.dp, color = iOSDivider.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}

/**
 * Apple Notes Circular Checklist Item.
 */
@Composable
fun iOSChecklistRow(
    checked: Boolean,
    text: String,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onNavigateToNote: ((String) -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }

    val checkboxBg by animateColorAsState(
        targetValue = if (checked) iOSYellow else Color.Transparent,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 850f),
        label = "iosCheckColor"
    )

    val circleBounce by animateFloatAsState(
        targetValue = if (checked) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 850f),
        label = "iosCircleBounce"
    )

    val checkScale by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 950f),
        label = "iosCheckScale"
    )

    val textColor by animateColorAsState(
        targetValue = if (checked) iOSTextTertiary else iOSTextPrimary,
        animationSpec = androidx.compose.animation.core.tween(120),
        label = "iosTextColor"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 24.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onToggle
            )
            .padding(vertical = 5.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(22.dp)
                .graphicsLayer {
                    scaleX = circleBounce
                    scaleY = circleBounce
                }
                .clip(CircleShape)
                .background(checkboxBg)
                .border(
                    width = 1.5.dp,
                    color = if (checked) iOSYellow else iOSTextSecondary,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked || checkScale > 0.05f) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Completed",
                    tint = Color.Black,
                    modifier = Modifier
                        .size(14.dp)
                        .scale(checkScale)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        val colors = MarkdownThemeColors(
            textPrimary = iOSTextPrimary,
            textSecondary = iOSTextSecondary,
            textTertiary = iOSTextTertiary,
            elevatedCard = iOSElevatedCard,
            accent = iOSYellow
        )
        val parsedText = remember(text, colors) { parseInlineMarkdown(text, colors) }

        MarkdownClickableText(
            text = parsedText,
            style = MonolithTypography.bodyLarge.copy(
                textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
                color = textColor
            ),
            lineHeight = 22.sp,
            modifier = Modifier.weight(1f, fill = false),
            onNavigateToNote = onNavigateToNote
        )
    }
}

/**
 * Monospace Code Block Card.
 */
@Composable
fun iOSCodeBlockCard(
    code: String,
    language: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(iOSCardBackground)
            .padding(14.dp)
    ) {
        if (language.isNotBlank()) {
            Text(
                text = language.uppercase(),
                style = MonolithTypography.labelSmall,
                color = iOSYellow,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Text(
            text = code,
            fontFamily = SFMono,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = iOSTextPrimary
        )
    }
}

/**
 * Image attachment viewer with full-screen zoom dialog.
 */
@Composable
fun IOSInlineImage(
    alt: String,
    path: String,
    vaultPath: String
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }

    val imageModel: Any = remember(path, vaultPath) {
        when {
            path.startsWith("http://") || path.startsWith("https://") -> path
            path.startsWith("content://") || path.startsWith("file://") -> Uri.parse(path)
            path.startsWith("/") -> File(path)
            else -> File(vaultPath, path)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(iOSCardBackground)
                    .clickable { isExpanded = true }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageModel)
                        .crossfade(true)
                        .build(),
                    contentDescription = alt.ifBlank { "Image Attachment" },
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 360.dp)
                )
            }

            if (alt.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = alt,
                    style = MonolithTypography.bodySmall.copy(fontSize = 12.sp),
                    color = iOSTextSecondary,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }

    if (isExpanded) {
        ImageLightboxDialog(
            imageModel = imageModel,
            alt = alt,
            onDismiss = { isExpanded = false }
        )
    }
}

/**
 * iOS-grade Full-Screen Image Lightbox Viewer:
 * - Dual-gesture pinch-to-zoom (1.0x - 5.0x) with pan constraints
 * - Double-tap zoom toggle (1.0x <-> 2.5x)
 * - Single-tap background dismiss when scale == 1.0x
 * - Glassmorphic top bar: frosted close button, zoom % badge, and system share intent
 * - Bottom frosted pill displaying caption/alt text
 * - OLED pitch-black background
 */
@Composable
fun ImageLightboxDialog(
    imageModel: Any,
    alt: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val animScale = remember { Animatable(1f) }
    val animOffsetX = remember { Animatable(0f) }
    val animOffsetY = remember { Animatable(0f) }
    val iosSnapSpring = remember { spring<Float>(dampingRatio = 0.85f, stiffness = 850f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            coroutineScope.launch {
                                if (animScale.value > 1.05f) {
                                    launch { animScale.animateTo(1f, iosSnapSpring) }
                                    launch { animOffsetX.animateTo(0f, iosSnapSpring) }
                                    launch { animOffsetY.animateTo(0f, iosSnapSpring) }
                                } else {
                                    launch { animScale.animateTo(2.5f, iosSnapSpring) }
                                }
                            }
                        },
                        onTap = {
                            if (animScale.value <= 1.05f) {
                                onDismiss()
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val currentScale = animScale.value
                        val newScale = (currentScale * zoom).coerceIn(1f, 5f)
                        coroutineScope.launch {
                            animScale.snapTo(newScale)
                            if (newScale > 1f) {
                                val maxOffsetX = (size.width * (newScale - 1f)) / 2f
                                val maxOffsetY = (size.height * (newScale - 1f)) / 2f
                                val newX = (animOffsetX.value + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                val newY = (animOffsetY.value + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                animOffsetX.snapTo(newX)
                                animOffsetY.snapTo(newY)
                            } else {
                                animOffsetX.snapTo(0f)
                                animOffsetY.snapTo(0f)
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Main Image
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageModel)
                    .crossfade(true)
                    .build(),
                contentDescription = alt.ifBlank { "Full-screen Image" },
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .graphicsLayer {
                        scaleX = animScale.value
                        scaleY = animScale.value
                        translationX = animOffsetX.value
                        translationY = animOffsetY.value
                    }
            )

            // Top Glassmorphic Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Frosted Glass Close Button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2C2C2E).copy(alpha = 0.85f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Zoom % Pill Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF2C2C2E).copy(alpha = 0.85f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            coroutineScope.launch {
                                launch { animScale.animateTo(1f, iosSnapSpring) }
                                launch { animOffsetX.animateTo(0f, iosSnapSpring) }
                                launch { animOffsetY.animateTo(0f, iosSnapSpring) }
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    val currentScale = animScale.value
                    Text(
                        text = "${(currentScale * 100).toInt()}%",
                        style = MonolithTypography.labelSmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (currentScale > 1.05f) iOSYellow else Color.White
                    )
                }

                // Frosted Glass Share Button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2C2C2E).copy(alpha = 0.85f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            try {
                                val shareIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    when (imageModel) {
                                        is Uri -> {
                                            putExtra(Intent.EXTRA_STREAM, imageModel)
                                            type = "image/*"
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        is File -> {
                                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                imageModel
                                            )
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            type = "image/*"
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        is String -> {
                                            if (imageModel.startsWith("http")) {
                                                putExtra(Intent.EXTRA_TEXT, imageModel)
                                                type = "text/plain"
                                            }
                                        }
                                    }
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Image"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not share image", Toast.LENGTH_SHORT).show()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Bottom Caption Pill (if alt text exists)
            if (alt.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp, start = 20.dp, end = 20.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1C1C1E).copy(alpha = 0.85f))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = alt,
                        style = MonolithTypography.bodySmall.copy(fontSize = 13.sp),
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}
