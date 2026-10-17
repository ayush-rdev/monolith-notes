package com.monolith.notes.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.res.painterResource
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
import com.monolith.notes.data.sync.SyncResult
import com.monolith.notes.data.sync.WebDavConfig
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
    onUpdateBiometricLock: (Boolean) -> Unit = {},
    onUpdateTheme: (AppTheme) -> Unit = {},
    onUpdateWebDavConfig: (WebDavConfig) -> Unit = {},
    onPerformSync: suspend () -> SyncResult = { SyncResult(false, message = "") }
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
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp)
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

            // Section 0: Minimal Premium Theme Selector
            SettingsSectionHeader(title = "MINIMAL PREMIUM THEMES")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppTheme.entries.forEach { themeOption ->
                    val isSelected = settings.theme == themeOption
                    ThemeCard(
                        theme = themeOption,
                        isSelected = isSelected,
                        onClick = {
                            performHaptic(HapticFeedbackType.TextHandleMove)
                            onUpdateTheme(themeOption)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

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

            // Section 2B: Cloud & WebDAV Vault Sync
            var showWebDavDialog by remember { mutableStateOf(false) }
            var isSyncing by remember { mutableStateOf(false) }
            var syncStatusMessage by remember { mutableStateOf<String?>(null) }
            val coroutineScope = rememberCoroutineScope()

            SettingsSectionHeader(title = "CLOUD & WEBDAV VAULT SYNC")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(iOSCardBackground)
            ) {
                Column {
                    val isConfigured = settings.webDavConfig.isConfigured
                    val syncSubtitle = when {
                        !isConfigured -> "Connect Nextcloud, ownCloud, or custom server"
                        syncStatusMessage != null -> syncStatusMessage!!
                        settings.lastSyncTimestamp > 0L -> {
                            val timeAgo = formatTimeAgo(settings.lastSyncTimestamp)
                            val mode = if (settings.webDavConfig.e2eeEnabled) "E2EE AES-256" else "Markdown"
                            "Synced $timeAgo • $mode"
                        }
                        else -> "Configured (${settings.webDavConfig.username}) • Ready to sync"
                    }

                    SettingsDetailRow(
                        icon = Icons.Default.CloudSync,
                        iconTint = if (isConfigured) iOSYellow else iOSTextSecondary,
                        title = "WebDAV Cloud Vault",
                        subtitle = syncSubtitle,
                        actionLabel = if (isSyncing) "SYNCING..." else if (isConfigured) "SYNC NOW" else "SETUP",
                        trailingIcon = Icons.Default.Tune,
                        onClick = {
                            if (!isConfigured) {
                                performHaptic(HapticFeedbackType.TextHandleMove)
                                showWebDavDialog = true
                            } else if (!isSyncing) {
                                performHaptic(HapticFeedbackType.TextHandleMove)
                                isSyncing = true
                                syncStatusMessage = "Syncing in progress..."
                                coroutineScope.launch {
                                    val result = onPerformSync()
                                    isSyncing = false
                                    syncStatusMessage = result.message
                                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )

                    if (isConfigured) {
                        HorizontalDivider(modifier = Modifier.padding(start = 58.dp), thickness = 0.5.dp, color = iOSDivider)
                        SettingsDetailRow(
                            icon = Icons.Default.Settings,
                            iconTint = iOSTextSecondary,
                            title = "Server Configuration",
                            subtitle = settings.webDavConfig.serverUrl.take(35) + if (settings.webDavConfig.serverUrl.length > 35) "..." else "",
                            actionLabel = "EDIT",
                            onClick = {
                                performHaptic(HapticFeedbackType.TextHandleMove)
                                showWebDavDialog = true
                            }
                        )
                    }
                }
            }

            if (showWebDavDialog) {
                WebDavConfigDialog(
                    initialConfig = settings.webDavConfig,
                    onSave = { newConfig ->
                        showWebDavDialog = false
                        onUpdateWebDavConfig(newConfig)
                    },
                    onDismiss = { showWebDavDialog = false }
                )
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
                    Image(
                        painter = painterResource(id = com.monolith.notes.R.drawable.monolith_logo),
                        contentDescription = "Monolith Notes Logo",
                        modifier = Modifier.size(76.dp)
                    )

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
                            text = "Version 1.6.0 • Pure Offline Markdown",
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

@Composable
private fun ThemeCard(
    theme: AppTheme,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(136.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(theme.cardBackground)
            .border(
                width = if (isSelected) 2.dp else 0.5.dp,
                color = if (isSelected) theme.accent else theme.divider,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Swatch Preview Bar (Shows Theme Background and Accent Dot)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(theme.background)
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(theme.accent)
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = theme.accent,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = theme.displayName,
                    style = MonolithTypography.titleMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    color = theme.textPrimary
                )
                Text(
                    text = theme.subtitle,
                    style = MonolithTypography.labelSmall.copy(fontSize = 10.sp),
                    color = theme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatTimeAgo(timeMillis: Long): String {
    if (timeMillis <= 0L) return "Never"
    val diff = System.currentTimeMillis() - timeMillis
    val minutes = diff / (60 * 1000)
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        else -> "${days}d ago"
    }
}
