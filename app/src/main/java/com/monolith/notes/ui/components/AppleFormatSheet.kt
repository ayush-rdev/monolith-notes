package com.monolith.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monolith.notes.ui.theme.*

@Composable
fun AppleFormatSheet(
    modifier: Modifier = Modifier,
    onSetStyle: (prefix: String) -> Unit,
    onToggleInline: (wrap: String) -> Unit,
    onToggleChecklist: () -> Unit,
    onToggleBullet: () -> Unit,
    onToggleNumbered: () -> Unit,
    onToggleQuote: () -> Unit,
    onInsertDivider: () -> Unit = {},
    onInsertTable: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(Color(0xFF1C1C1E))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: "Format" + "Done"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Format",
                    style = MonolithTypography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Format Sheet",
                        tint = iOSTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 1. Paragraph Styles Segmented Row (Title, Heading, Subheading, Body, Monospaced)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF2C2C2E))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FormatStyleChip(label = "Title", fontSize = 16.sp, fontWeight = FontWeight.Bold) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSetStyle("# ")
                }
                FormatStyleChip(label = "Heading", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSetStyle("## ")
                }
                FormatStyleChip(label = "Sub", fontSize = 14.sp, fontWeight = FontWeight.Medium) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSetStyle("### ")
                }
                FormatStyleChip(label = "Body", fontSize = 14.sp, fontWeight = FontWeight.Normal) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSetStyle("")
                }
                FormatStyleChip(label = "Mono", fontSize = 13.sp, fontFamily = FontFamily.Monospace) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSetStyle("```\n")
                }
            }

            // 2. Inline Styles: Bold, Italic, Underline, Strikethrough
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF2C2C2E))
                    .padding(vertical = 4.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                InlineFormatButton(label = "B", fontWeight = FontWeight.Bold) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleInline("**")
                }
                InlineFormatButton(label = "I", fontStyle = FontStyle.Italic) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleInline("*")
                }
                InlineFormatButton(label = "S", textDecoration = TextDecoration.LineThrough) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleInline("~~")
                }
                InlineFormatButton(label = "<>", fontFamily = FontFamily.Monospace) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleInline("`")
                }
            }

            // 3. Lists Row: Checklist, Bullets, Numbers, Quote
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF2C2C2E))
                    .padding(vertical = 4.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ListFormatButton(icon = Icons.Default.RadioButtonUnchecked, label = "Checklist", tint = iOSYellow) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleChecklist()
                }
                ListFormatButton(icon = Icons.AutoMirrored.Filled.FormatListBulleted, label = "Bullet") {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleBullet()
                }
                ListFormatButton(icon = Icons.Default.FormatListNumbered, label = "Numbered") {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleNumbered()
                }
                ListFormatButton(icon = Icons.Default.FormatQuote, label = "Quote") {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleQuote()
                }
                ListFormatButton(icon = Icons.Default.HorizontalRule, label = "Divider") {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onInsertDivider()
                }
                ListFormatButton(icon = Icons.Default.TableChart, label = "Table") {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onInsertTable()
                }
            }
        }
    }
}

@Composable
private fun FormatStyleChip(
    label: String,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    fontFamily: FontFamily? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            color = Color.White
        )
    }
}

@Composable
private fun InlineFormatButton(
    label: String,
    fontWeight: FontWeight = FontWeight.Normal,
    fontStyle: FontStyle? = null,
    textDecoration: TextDecoration? = null,
    fontFamily: FontFamily? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 18.sp,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            textDecoration = textDecoration,
            fontFamily = fontFamily,
            color = Color.White
        )
    }
}

@Composable
private fun ListFormatButton(
    icon: ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}
