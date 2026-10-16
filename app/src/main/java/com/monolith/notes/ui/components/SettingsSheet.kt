package com.monolith.notes.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monolith.notes.data.AppSettings
import com.monolith.notes.data.EditorFontSize
import com.monolith.notes.data.NewNoteStartStyle
import com.monolith.notes.data.NoteSortOrder
import com.monolith.notes.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: AppSettings,
    noteCount: Int,
    totalWords: Int = 0,
    vaultPath: String,
    onUpdateHaptics: (Boolean) -> Unit,
    onUpdateStartStyle: (NewNoteStartStyle) -> Unit,
    onUpdateFontSize: (EditorFontSize) -> Unit,
    onUpdateShowWordCount: (Boolean) -> Unit,
    onUpdateAutoChecklists: (Boolean) -> Unit,
    onSortOrderChange: (NoteSortOrder) -> Unit,
    onDismiss: () -> Unit,
    onExportNotes: () -> Unit,
    trashNotesCount: Int = 0,
    onOpenTrash: () -> Unit = {},
    onUpdateBiometricLock: (Boolean) -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    fun performHaptic(type: HapticFeedbackType) {
        if (settings.hapticsEnabled) {
            haptic.performHapticFeedback(type)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = iOSBackground,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(iOSTextTertiary)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings",
                    style = MonolithTypography.headlineLarge,
                    color = iOSTextPrimary
                )
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Done",
                        color = iOSYellow,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Section 1: Notes & Editing (Actually useful controls!)
            SettingsSectionHeader(title = "NOTES & EDITING")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(iOSCardBackground)
            ) {
                Column {
                    // Editor Font Size
                    SettingsRow(
                        icon = Icons.Default.FormatSize,
                        iconTint = Color(0xFF0A84FF),
                        title = "Editor Text Size",
                        value = settings.editorFontSize.label,
                        onClick = {
                            performHaptic(HapticFeedbackType.TextHandleMove)
                            onUpdateFontSize(settings.editorFontSize.next())
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 58.dp), thickness = 0.5.dp, color = iOSDivider)

                    // New Notes Start With
                    SettingsRow(
                        icon = Icons.Default.TextFields,
                        iconTint = Color(0xFFFF9F0A),
                        title = "New Notes Start With",
                        value = settings.newNoteStartStyle.label,
                        onClick = {
                            performHaptic(HapticFeedbackType.TextHandleMove)
                            onUpdateStartStyle(settings.newNoteStartStyle.toggle())
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 58.dp), thickness = 0.5.dp, color = iOSDivider)

                    // Live Word Count in Editor
                    SettingsToggleRow(
                        icon = Icons.Default.Numbers,
                        iconTint = Color(0xFFBF5AF2),
                        title = "Live Word Count",
                        checked = settings.showWordCount,
                        onCheckedChange = {
                            performHaptic(HapticFeedbackType.LongPress)
                            onUpdateShowWordCount(it)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 58.dp), thickness = 0.5.dp, color = iOSDivider)

                    // Auto-Continue Lists
                    SettingsToggleRow(
                        icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                        iconTint = iOSEmerald,
                        title = "Auto-Continue Lists",
                        checked = settings.autoContinueChecklists,
                        onCheckedChange = {
                            performHaptic(HapticFeedbackType.LongPress)
                            onUpdateAutoChecklists(it)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 2: Behavior & Preferences
            SettingsSectionHeader(title = "BEHAVIOR & FEEDBACK")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(iOSCardBackground)
            ) {
                Column {
                    val sortLabel = when (settings.sortOrder) {
                        NoteSortOrder.DATE_EDITED -> "Date Edited"
                        NoteSortOrder.DATE_CREATED -> "Date Created"
                        NoteSortOrder.TITLE -> "Title (A-Z)"
                    }
                    SettingsRow(
                        icon = Icons.AutoMirrored.Filled.Sort,
                        iconTint = iOSYellow,
                        title = "Sort Notes By",
                        value = sortLabel,
                        onClick = {
                            performHaptic(HapticFeedbackType.TextHandleMove)
                            val nextOrder = when (settings.sortOrder) {
                                NoteSortOrder.DATE_EDITED -> NoteSortOrder.DATE_CREATED
                                NoteSortOrder.DATE_CREATED -> NoteSortOrder.TITLE
                                NoteSortOrder.TITLE -> NoteSortOrder.DATE_EDITED
                            }
                            onSortOrderChange(nextOrder)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 58.dp), thickness = 0.5.dp, color = iOSDivider)

                    SettingsToggleRow(
                        icon = Icons.Default.Vibration,
                        iconTint = Color(0xFFFF375F),
                        title = "Tactile Haptics",
                        checked = settings.hapticsEnabled,
                        onCheckedChange = {
                            if (it) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            onUpdateHaptics(it)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 58.dp), thickness = 0.5.dp, color = iOSDivider)

                    SettingsToggleRow(
                        icon = Icons.Default.Fingerprint,
                        iconTint = Color(0xFF34C759),
                        title = "Biometric Lock",
                        checked = settings.biometricLockEnabled,
                        onCheckedChange = {
                            performHaptic(HapticFeedbackType.TextHandleMove)
                            onUpdateBiometricLock(it)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 3: Storage & Vault
            SettingsSectionHeader(title = "VAULT & DATA STORAGE")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(iOSCardBackground)
            ) {
                Column {
                    SettingsDetailRow(
                        icon = Icons.Default.Folder,
                        iconTint = iOSYellow,
                        title = "Vault Location",
                        subtitle = vaultPath,
                        actionLabel = "COPY",
                        onClick = {
                            performHaptic(HapticFeedbackType.LongPress)
                            clipboardManager.setText(AnnotatedString(vaultPath))
                            Toast.makeText(context, "Copied vault path to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 58.dp), thickness = 0.5.dp, color = iOSDivider)

                    SettingsDetailRow(
                        icon = Icons.Default.DeleteOutline,
                        iconTint = Color(0xFFFF453A),
                        title = "Recently Deleted",
                        subtitle = "Review and recover notes deleted within 30 days",
                        actionLabel = if (trashNotesCount > 0) "$trashNotesCount" else null,
                        trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        onClick = {
                            performHaptic(HapticFeedbackType.TextHandleMove)
                            onDismiss()
                            onOpenTrash()
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 58.dp), thickness = 0.5.dp, color = iOSDivider)

                    SettingsDetailRow(
                        icon = Icons.Default.FileDownload,
                        iconTint = Color(0xFF0A84FF),
                        title = "Export Vault (.zip)",
                        subtitle = "Full backup archive of all your markdown notes",
                        actionLabel = "EXPORT",
                        onClick = {
                            performHaptic(HapticFeedbackType.LongPress)
                            onExportNotes()
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 58.dp), thickness = 0.5.dp, color = iOSDivider)

                    SettingsRow(
                        icon = Icons.Default.Description,
                        iconTint = iOSEmerald,
                        title = "Total Notes",
                        value = "$noteCount notes"
                    )
                    if (totalWords > 0) {
                        HorizontalDivider(modifier = Modifier.padding(start = 58.dp), thickness = 0.5.dp, color = iOSDivider)
                        SettingsRow(
                            icon = Icons.Default.Description,
                            iconTint = Color(0xFFFF9F0A),
                            title = "Vault Words",
                            value = "$totalWords words"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section 4: About (Clean Apple Notes Card - No Useless Tech Specs)
            SettingsSectionHeader(title = "ABOUT")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(iOSCardBackground)
                    .padding(vertical = 22.dp, horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(17.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFFF9D46A),
                                        Color(0xFFE5A93C)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = "Monolith Notes",
                            tint = Color(0xFF1C1C1E),
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Monolith Notes",
                        style = MonolithTypography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            letterSpacing = (-0.5).sp
                        ),
                        color = iOSTextPrimary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(iOSElevatedCard)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Version 1.4 • Pure Offline Markdown",
                            style = MonolithTypography.labelSmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = iOSYellow
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Every note is saved directly as a standard CommonMark .md file in your storage. 100% private, zero tracking, and fully compatible with Obsidian.",
                        style = MonolithTypography.bodyMedium.copy(
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        ),
                        color = iOSTextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section 5: Creator & Open Source (Ayush / @ayush-rdev)
            SettingsSectionHeader(title = "OPEN SOURCE & CREATOR")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(iOSCardBackground)
            ) {
                Column {
                    SettingsDetailRow(
                        icon = Icons.Default.Person,
                        iconTint = iOSYellow,
                        title = "Ayush (@ayush-rdev)",
                        subtitle = "Lead Architect & Creator • github.com/ayush-rdev",
                        actionLabel = "VISIT",
                        trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        onClick = {
                            performHaptic(HapticFeedbackType.LongPress)
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ayush-rdev"))
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 58.dp), thickness = 0.5.dp, color = iOSDivider)

                    SettingsDetailRow(
                        icon = Icons.Default.Code,
                        iconTint = Color(0xFF0A84FF),
                        title = "GitHub Repository",
                        subtitle = "ayush-rdev/monolith-notes • Star & Contribute",
                        actionLabel = "STAR",
                        trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        onClick = {
                            performHaptic(HapticFeedbackType.LongPress)
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ayush-rdev/monolith-notes"))
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 58.dp), thickness = 0.5.dp, color = iOSDivider)

                    SettingsRow(
                        icon = Icons.Default.Favorite,
                        iconTint = Color(0xFFFF375F),
                        title = "License & Freedom",
                        value = "MIT Open Source"
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MonolithTypography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
        color = iOSTextSecondary,
        modifier = Modifier.padding(start = 14.dp, bottom = 6.dp)
    )
}

/**
 * iOS Inset Grouped Key-Value Row with single-line guarantee
 */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    value: String? = null,
    trailingIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(iconTint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(17.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = title,
            style = MonolithTypography.bodyLarge.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal
            ),
            color = iOSTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (value != null || onClick != null || trailingIcon != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                if (value != null) {
                    Text(
                        text = value,
                        style = MonolithTypography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = iOSTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val iconToShow = trailingIcon ?: if (onClick != null) Icons.AutoMirrored.Filled.KeyboardArrowRight else null
                if (iconToShow != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = iconToShow,
                        contentDescription = null,
                        tint = iOSTextTertiary.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * iOS Inset Grouped Detail Row (for multi-line subtitles like vault path and export)
 */
@Composable
private fun SettingsDetailRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    trailingIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(iconTint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(17.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MonolithTypography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Normal),
                color = iOSTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MonolithTypography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = iOSTextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (actionLabel != null || trailingIcon != null) {
            Spacer(modifier = Modifier.width(10.dp))
            if (actionLabel != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(iOSYellowContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = actionLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = iOSYellow
                    )
                }
            }
            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = iOSTextTertiary.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * iOS Inset Grouped Toggle Switch Row
 */
@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(iconTint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(17.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = title,
                style = MonolithTypography.bodyLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = iOSTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = iOSEmerald,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFF3A3A3C),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}
