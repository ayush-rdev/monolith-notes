package com.monolith.notes.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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

        for (line in lines) {
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
                continue
            }

            if (inCodeBlock) {
                codeBlockLines.add(line)
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

