package com.monolith.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monolith.notes.ui.theme.*

@Composable
fun FormattingBar(
    modifier: Modifier = Modifier,
    isFormatSheetOpen: Boolean = false,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onToggleFormatSheet: () -> Unit,
    onToggleChecklist: () -> Unit,
    onToggleBullet: () -> Unit,
    onToggleBold: () -> Unit,
    onInsertWikiLink: () -> Unit = {},
    onInsertTag: () -> Unit,
    onAttachImage: () -> Unit = {},
    onDismissKeyboard: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        HorizontalDivider(thickness = 0.5.dp, color = iOSDivider)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Checklist Button (Apple Yellow)
            IOSToolbarButton(
                icon = Icons.Default.RadioButtonUnchecked,
                contentDescription = "Checklist",
                tint = iOSYellow
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggleChecklist()
            }

            // 2. Apple Notes Aa Format Button (Highlighted when active)
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isFormatSheetOpen) iOSYellowContainer else Color.Transparent)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleFormatSheet()
                    }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aa",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isFormatSheetOpen) iOSYellow else iOSTextPrimary
                )
            }

            // 3. Bullet List Button
            IOSToolbarButton(
                icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                contentDescription = "Bullet List"
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggleBullet()
            }

            // 4. Bold Button
            IOSToolbarButton(
                icon = Icons.Default.FormatBold,
                contentDescription = "Bold"
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggleBold()
            }

            // 5. WikiLink [[ ]] Button
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onInsertWikiLink()
                    }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "[[ ]]",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = iOSYellow
                )
            }

            // 6. Hashtag Button
            IOSToolbarButton(
                icon = Icons.Default.Tag,
                contentDescription = "Hashtag",
                tint = iOSYellow
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onInsertTag()
            }

            // 7. Photo / Attachment Button
            IOSToolbarButton(
                icon = Icons.Default.AddPhotoAlternate,
                contentDescription = "Attach Photo",
                tint = iOSYellow
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onAttachImage()
            }

            // 7. Undo Button
            IOSToolbarButton(
                icon = Icons.AutoMirrored.Filled.Undo,
                contentDescription = "Undo",
                tint = if (canUndo) iOSTextPrimary else iOSTextTertiary.copy(alpha = 0.35f),
                enabled = canUndo
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onUndo()
            }

            // 8. Redo Button
            IOSToolbarButton(
                icon = Icons.AutoMirrored.Filled.Redo,
                contentDescription = "Redo",
                tint = if (canRedo) iOSTextPrimary else iOSTextTertiary.copy(alpha = 0.35f),
                enabled = canRedo
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onRedo()
            }

            // 9. Dismiss Keyboard Button
            IOSToolbarButton(
                icon = Icons.Default.KeyboardHide,
                contentDescription = "Hide Keyboard",
                tint = iOSTextSecondary
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onDismissKeyboard()
            }
        }
    }
}

@Composable
private fun IOSToolbarButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color = iOSTextPrimary,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(19.dp)
        )
    }
}
