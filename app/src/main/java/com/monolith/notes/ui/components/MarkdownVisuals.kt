package com.monolith.notes.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.monolith.notes.ui.theme.*
import java.io.File

private val INLINE_MARKDOWN_REGEX = "(\\*\\*.*?\\*\\*|__.*?__|\\*.*?\\*|_.*?_|`.*?`|~~.*?~~|#[a-zA-Z0-9_\\-/]+)".toRegex()
private val TASK_TOGGLE_REGEX = "(^[ \\t]*(?:[-*]\\s+\\[[ xX]\\]|[○✓]))([ \\t]+.*$)".toRegex(RegexOption.MULTILINE)
private val IMAGE_REGEX = """!\[(.*?)\]\((.*?)\)""".toRegex()
private val HEADING_REGEX = "^(#{1,6})\\s+(.+)$".toRegex()

@Composable
fun parseInlineMarkdown(text: String): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString("")
    return buildAnnotatedString {
        var cursor = 0
        val matches = INLINE_MARKDOWN_REGEX.findAll(text)

        for (match in matches) {
            val start = match.range.first
            val end = match.range.last + 1
            if (start > cursor) {
                append(text.substring(cursor, start))
            }
            val matchValue = match.value
            when {
                // Bold: **text**
                (matchValue.startsWith("**") && matchValue.endsWith("**") && matchValue.length >= 4) ||
                (matchValue.startsWith("__") && matchValue.endsWith("__") && matchValue.length >= 4) -> {
                    val inner = matchValue.substring(2, matchValue.length - 2)
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = iOSTextPrimary))
                    append(inner)
                    pop()
                }
                // Italic: *text*
                (matchValue.startsWith("*") && matchValue.endsWith("*") && matchValue.length >= 2) ||
                (matchValue.startsWith("_") && matchValue.endsWith("_") && matchValue.length >= 2) -> {
                    val inner = matchValue.substring(1, matchValue.length - 1)
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = iOSTextSecondary))
                    append(inner)
                    pop()
                }
                // Inline Code: `code`
                matchValue.startsWith("`") && matchValue.endsWith("`") && matchValue.length >= 2 -> {
                    val inner = matchValue.substring(1, matchValue.length - 1)
                    pushStyle(
                        SpanStyle(
                            fontFamily = SFMono,
                            background = iOSElevatedCard,
                            color = iOSYellow,
                            fontSize = 14.sp
                        )
                    )
                    append(" $inner ")
                    pop()
                }
                // Strikethrough: ~~text~~
                matchValue.startsWith("~~") && matchValue.endsWith("~~") && matchValue.length >= 4 -> {
                    val inner = matchValue.substring(2, matchValue.length - 2)
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = iOSTextTertiary))
                    append(inner)
                    pop()
                }
                // Hashtag: #tag
                matchValue.startsWith("#") -> {
                    pushStyle(SpanStyle(color = iOSYellow, fontWeight = FontWeight.SemiBold))
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
}

fun toggleMarkdownTask(content: String, targetTaskIndex: Int): String {
    var currentTaskIndex = 0
    return TASK_TOGGLE_REGEX.replace(content) { matchResult ->
        if (currentTaskIndex == targetTaskIndex) {
            val prefix = matchResult.groupValues[1].trim()
            val rest = matchResult.groupValues[2]
            val newPrefix = when {
                prefix.startsWith("○") -> "✓"
                prefix.startsWith("✓") -> "○"
                prefix.contains("[ ]") -> prefix.replace("[ ]", "[x]")
                else -> prefix.replace("[x]", "[ ]").replace("[X]", "[ ]")
            }
            currentTaskIndex++
            "$newPrefix$rest"
        } else {
            currentTaskIndex++
            matchResult.value
        }
    }
}

fun isTableDelimiter(line: String): Boolean {
    val trimmed = line.trim()
    if (!trimmed.contains('|') || !trimmed.contains('-')) return false
    val inner = trimmed.trim('|').trim()
    if (inner.isEmpty()) return false
    val parts = inner.split('|')
    if (parts.isEmpty()) return false
    return parts.all { col ->
        val c = col.trim()
        c.matches("^:?-{1,}:?$".toRegex())
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

fun parseTableAlignments(delimiterLine: String): List<TextAlign> {
    return splitTableRow(delimiterLine).map { col ->
        val c = col.trim()
        when {
            c.startsWith(':') && c.endsWith(':') -> TextAlign.Center
            c.endsWith(':') -> TextAlign.End
            else -> TextAlign.Start
        }
    }
}

@Composable
fun iOSTableCard(
    headerLine: String,
    delimiterLine: String,
    dataRows: List<String>,
    modifier: Modifier = Modifier
) {
    val headers = remember(headerLine) { splitTableRow(headerLine) }
    val alignments = remember(delimiterLine) { parseTableAlignments(delimiterLine) }
    val numCols = headers.size.coerceAtLeast(1)
    val parsedRows = remember(dataRows) {
        dataRows.map { row ->
            val cells = splitTableRow(row)
            List(numCols) { idx -> if (idx < cells.size) cells[idx] else "" }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(iOSCardBackground)
            .border(1.dp, iOSDivider, RoundedCornerShape(12.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier.width(IntrinsicSize.Max)
            ) {
                // Table Header Row
                Row(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.05f))
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
                            Text(
                                text = parseInlineMarkdown(headerText),
                                style = MonolithTypography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                ),
                                textAlign = align
                            )
                        }
                    }
                }

                HorizontalDivider(
                    thickness = 1.dp,
                    color = iOSDivider
                )

                // Table Data Rows
                parsedRows.forEachIndexed { rowIndex, rowCells ->
                    Row(
                        modifier = Modifier
                            .padding(vertical = 9.dp, horizontal = 4.dp),
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
                                Text(
                                    text = parseInlineMarkdown(cellText),
                                    style = MonolithTypography.bodyMedium.copy(
                                        color = iOSTextPrimary
                                    ),
                                    textAlign = align
                                )
                            }
                        }
                    }

                    if (rowIndex < parsedRows.size - 1) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = iOSDivider.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MarkdownRenderView(
    content: String,
    vaultPath: String = "/sdcard/Documents/MonolithNotes",
    modifier: Modifier = Modifier,
    onTaskToggled: ((taskIndex: Int) -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val lines = content.lines()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        var taskCounter = 0
        var inCodeBlock = false
        val codeBlockLines = mutableListOf<String>()
        var codeBlockLang = ""

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trimStart()

            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    iOSCodeBlockCard(
                        code = codeBlockLines.joinToString("\n"),
                        language = codeBlockLang
                    )
                    codeBlockLines.clear()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                    codeBlockLang = trimmed.removePrefix("```").trim()
                }
                i++
                continue
            }

            if (inCodeBlock) {
                codeBlockLines.add(line)
                i++
                continue
            }

            // GFM Markdown Table Detection: line has '|' and next line is a valid delimiter
            if (trimmed.contains('|') && i + 1 < lines.size && isTableDelimiter(lines[i + 1])) {
                val headerLine = line
                val delimiterLine = lines[i + 1]
                val dataRows = mutableListOf<String>()
                i += 2
                while (i < lines.size) {
                    val candidate = lines[i].trim()
                    if (candidate.contains('|') && candidate.isNotBlank()) {
                        dataRows.add(lines[i])
                        i++
                    } else {
                        break
                    }
                }
                iOSTableCard(
                    headerLine = headerLine,
                    delimiterLine = delimiterLine,
                    dataRows = dataRows
                )
                continue
            }

            val headingMatch = HEADING_REGEX.find(trimmed)
            when {
                headingMatch != null -> {
                    val level = headingMatch.groupValues[1].length
                    val headingText = headingMatch.groupValues[2]
                    val (style, topPadding) = when (level) {
                        1 -> MonolithTypography.headlineLarge to 12.dp
                        2 -> MonolithTypography.headlineMedium to 10.dp
                        3 -> MonolithTypography.titleLarge.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold) to 8.dp
                        4 -> MonolithTypography.titleLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold) to 6.dp
                        else -> MonolithTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold) to 4.dp
                    }
                    Text(
                        text = parseInlineMarkdown(headingText.trim()),
                        style = style.copy(color = Color.White),
                        modifier = Modifier.padding(top = topPadding, bottom = 2.dp)
                    )
                }
                // Circular Apple Notes Task Checkbox (supports both ○/✓ and - [ ]/- [x])
                trimmed.startsWith("○ ") || trimmed.startsWith("✓ ") || trimmed.matches("^[\\-*]\\s+\\[[ xX]\\].*".toRegex()) -> {
                    val isChecked = trimmed.startsWith("✓ ") || trimmed.contains("[x]", ignoreCase = true)
                    val taskText = when {
                        trimmed.startsWith("○ ") -> trimmed.removePrefix("○ ")
                        trimmed.startsWith("✓ ") -> trimmed.removePrefix("✓ ")
                        else -> trimmed.replace("^[\\-*]\\s+\\[[ xX]\\]\\s*".toRegex(), "")
                    }
                    val currentIndex = taskCounter++

                    iOSChecklistRow(
                        checked = isChecked,
                        text = taskText,
                        onToggle = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTaskToggled?.invoke(currentIndex)
                        }
                    )
                }
                // Bullet List
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    val bulletText = trimmed.substring(2).trim()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 9.dp, end = 10.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(iOSYellow)
                        )
                        Text(
                            text = parseInlineMarkdown(bulletText),
                            style = MonolithTypography.bodyLarge,
                            lineHeight = 24.sp
                        )
                    }
                }
                // Blockquote
                trimmed.startsWith("> ") -> {
                    val quoteText = trimmed.removePrefix("> ").trim()
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
                        Text(
                            text = parseInlineMarkdown(quoteText),
                            style = MonolithTypography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = iOSTextSecondary
                        )
                    }
                }
                // Inline Image / Attachment: ![alt](path)
                trimmed.startsWith("![") && IMAGE_REGEX.containsMatchIn(trimmed) -> {
                    val match = IMAGE_REGEX.find(trimmed)
                    if (match != null) {
                        val altText = match.groupValues[1]
                        val rawPath = match.groupValues[2]
                        IOSInlineImage(
                            alt = altText,
                            path = rawPath,
                            vaultPath = vaultPath
                        )
                    }
                }
                // Horizontal Rule / Separator: ---, ***, ___, ───, ===, or long separators (------------------)
                trimmed.matches("^(-{3,}|\\*{3,}|_{3,}|─{3,}|={3,}|(-\\s*){3,}|(\\*\\s*){3,}|(_\\s*){3,})$".toRegex()) -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(),
                            thickness = 1.dp,
                            color = iOSDivider
                        )
                    }
                }
                trimmed.isBlank() -> {
                    Spacer(modifier = Modifier.height(4.dp))
                }
                else -> {
                    Text(
                        text = parseInlineMarkdown(trimmed),
                        style = MonolithTypography.bodyLarge,
                        lineHeight = 24.sp
                    )
                }
            }
            i++
        }

        if (inCodeBlock && codeBlockLines.isNotEmpty()) {
            iOSCodeBlockCard(
                code = codeBlockLines.joinToString("\n"),
                language = codeBlockLang
            )
        }
    }
}

@Composable
fun iOSChecklistRow(
    checked: Boolean,
    text: String,
    onToggle: () -> Unit
) {
    val checkboxBg by animateColorAsState(
        targetValue = if (checked) iOSYellow else Color.Transparent,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f),
        label = "iosCheckColor"
    )

    val checkScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (checked) 1f else 0.2f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "iosCheckScale"
    )

    val textColor by animateColorAsState(
        targetValue = if (checked) iOSTextTertiary else iOSTextPrimary,
        animationSpec = androidx.compose.animation.core.tween(200),
        label = "iosTextColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Apple Notes Circular Checklist Bubble
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(checkboxBg)
                .border(
                    width = 1.5.dp,
                    color = if (checked) iOSYellow else iOSTextSecondary,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
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

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = parseInlineMarkdown(text),
            style = MonolithTypography.bodyLarge.copy(
                textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None
            ),
            color = textColor,
            lineHeight = 22.sp
        )
    }
}

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
        Dialog(
            onDismissRequest = { isExpanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.94f))
                    .clickable { isExpanded = false },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageModel)
                        .crossfade(true)
                        .build(),
                    contentDescription = alt,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }
    }
}

