@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.monolith.notes.ui.list

import android.content.Intent
import android.widget.Toast
import kotlinx.coroutines.launch
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.monolith.notes.data.AppSettings
import com.monolith.notes.data.EditorFontSize
import com.monolith.notes.data.NewNoteStartStyle
import com.monolith.notes.data.NoteSortOrder
import com.monolith.notes.data.sync.SyncResult
import com.monolith.notes.data.sync.SyncState
import com.monolith.notes.data.sync.WebDavConfig
import com.monolith.notes.model.Note
import com.monolith.notes.ui.components.SettingsSheet
import com.monolith.notes.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
    notes: List<Note>,
    vaultPath: String = "/sdcard/Documents/MonolithNotes",
    settings: AppSettings = AppSettings(),
    onUpdateHaptics: (Boolean) -> Unit = {},
    onUpdateStartStyle: (NewNoteStartStyle) -> Unit = {},
    onUpdateFontSize: (EditorFontSize) -> Unit = {},
    onUpdateShowWordCount: (Boolean) -> Unit = {},
    onUpdateAutoChecklists: (Boolean) -> Unit = {},
    onSortOrderChange: (NoteSortOrder) -> Unit = {},
    onNoteClick: (Note) -> Unit,
    onNewNoteClick: () -> Unit,
    onTogglePin: (Note) -> Unit = {},
    onDeleteNote: (Note) -> Unit,
    onDuplicateNote: (Note) -> Unit = {},
    onExportNotes: () -> Unit = {},
    trashNotesCount: Int = 0,
    onOpenTrash: () -> Unit = {},
    currentFolder: String? = null,
    folders: List<String> = emptyList(),
    onOpenFolders: () -> Unit = {},
    onOpenGraph: () -> Unit = {},
    onMoveNoteToFolder: (Note, String) -> Unit = { _, _ -> },
    onUpdateBiometricLock: (Boolean) -> Unit = {},
    onUpdateScreenSecurity: (Boolean) -> Unit = {},
    onUpdateTheme: (AppTheme) -> Unit = {},
    onUpdateCloudConfig: (com.monolith.notes.data.sync.MonolithCloudConfig) -> Unit = {},
    onPerformCloudSync: suspend () -> SyncResult = { SyncResult(false, message = "") },
    onConnectGoogleDrive: () -> Unit = {},
    onDisconnectGoogleDrive: () -> Unit = {},
    onUpdateGoogleDriveConfig: (com.monolith.notes.data.GoogleDriveConfig) -> Unit = {},
    onUpdateGoogleDriveUri: (String?) -> Unit = {},
    onPerformGoogleDriveSync: suspend () -> SyncResult = { SyncResult(false, message = "") },
    onUpdateWebDavConfig: (WebDavConfig) -> Unit = {},
    onPerformSync: suspend () -> SyncResult = { SyncResult(false, message = "") },
    syncState: SyncState = SyncState.IDLE,
    onTriggerSync: () -> Unit = {},
    isLoaded: Boolean = true,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    fun performHaptic(type: HapticFeedbackType) {
        if (settings.hapticsEnabled) {
            haptic.performHapticFeedback(type)
        }
    }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var noteToDelete by remember { mutableStateOf<Note?>(null) }
    var noteActionTarget by remember { mutableStateOf<Note?>(null) }
    var noteToMove by remember { mutableStateOf<Note?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    val baseNotes = remember(notes, currentFolder) {
        if (currentFolder == null) notes
        else notes.filter { it.folderName.equals(currentFolder, ignoreCase = true) }
    }

    val allTags = remember(baseNotes) {
        baseNotes.flatMap { it.tags }.distinct().sorted()
    }

    val filteredNotes = remember(baseNotes, searchQuery, selectedTag) {
        baseNotes.filter { note ->
            val matchesSearch = searchQuery.isBlank() ||
                    note.displayTitle.contains(searchQuery, ignoreCase = true) ||
                    note.content.contains(searchQuery, ignoreCase = true)
            val matchesTag = selectedTag == null || note.tags.contains(selectedTag)
            matchesSearch && matchesTag
        }
    }

    val pinnedNotes = remember(filteredNotes) { filteredNotes.filter { it.isPinned } }
    val unpinnedNotes = remember(filteredNotes) { filteredNotes.filter { !it.isPinned } }

    val groupedUnpinnedNotes = remember(unpinnedNotes, settings.sortOrder) {
        if (settings.sortOrder == com.monolith.notes.data.NoteSortOrder.TITLE) {
            val byLetter = unpinnedNotes.groupBy {
                val firstChar = it.displayTitle.trim().firstOrNull()?.uppercaseChar() ?: '#'
                if (firstChar in 'A'..'Z') "$firstChar" else "#"
            }
            byLetter.entries.sortedBy { it.key }.map { it.key to it.value }
        } else {
            val now = System.currentTimeMillis()
            val oneDayMs = 24L * 60 * 60 * 1000
            val sevenDaysMs = 7 * oneDayMs
            val thirtyDaysMs = 30 * oneDayMs

            val today = mutableListOf<Note>()
            val prev7 = mutableListOf<Note>()
            val prev30 = mutableListOf<Note>()
            val earlier = mutableListOf<Note>()

            unpinnedNotes.forEach { note ->
                val diff = now - note.lastModified
                when {
                    diff < oneDayMs -> today.add(note)
                    diff < sevenDaysMs -> prev7.add(note)
                    diff < thirtyDaysMs -> prev30.add(note)
                    else -> earlier.add(note)
                }
            }

            listOfNotNull(
                if (today.isNotEmpty()) "TODAY" to today else null,
                if (prev7.isNotEmpty()) "PREVIOUS 7 DAYS" to prev7 else null,
                if (prev30.isNotEmpty()) "PREVIOUS 30 DAYS" to prev30 else null,
                if (earlier.isNotEmpty()) "EARLIER" to earlier else null
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(iOSBackground)
            .statusBarsPadding()
            .displayCutoutPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            // iOS Top Navigation Bar (< Folders on left, Settings on right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back to Folders button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            performHaptic(HapticFeedbackType.TextHandleMove)
                            onOpenFolders()
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Folders",
                        tint = iOSYellow,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Folders",
                        color = iOSYellow,
                        style = MonolithTypography.titleMedium.copy(fontSize = 17.sp)
                    )
                }

                // Right action buttons: Cloud Sync Status, Knowledge Graph & Settings
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isCloudConfigured = settings.googleDriveConfig.isConnected ||
                            settings.cloudConfig.isLoggedIn ||
                            settings.webDavConfig.isConfigured

                    IconButton(
                        onClick = {
                            performHaptic(HapticFeedbackType.TextHandleMove)
                            if (isCloudConfigured) {
                                onTriggerSync()
                                val providerName = when {
                                    settings.googleDriveConfig.isConnected -> "Google Drive"
                                    settings.cloudConfig.isLoggedIn -> "Monolith Cloud"
                                    settings.webDavConfig.isConfigured -> "WebDAV"
                                    else -> "Cloud"
                                }
                                Toast.makeText(context, "Syncing vault with $providerName...", Toast.LENGTH_SHORT).show()
                            } else {
                                showSettings = true
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        when (syncState) {
                            SyncState.SYNCING -> {
                                SyncSpinningIcon(modifier = Modifier.size(22.dp))
                            }
                            SyncState.SUCCESS -> {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = "Vault Synced",
                                    tint = Color(0xFF4ADE80),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            SyncState.ERROR -> {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = "Sync Error",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            SyncState.IDLE -> {
                                if (isCloudConfigured) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDone,
                                        contentDescription = "Cloud Connected",
                                        tint = iOSYellow.copy(alpha = 0.85f),
                                        modifier = Modifier.size(22.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CloudQueue,
                                        contentDescription = "Connect Cloud Sync",
                                        tint = iOSTextSecondary.copy(alpha = 0.55f),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            performHaptic(HapticFeedbackType.TextHandleMove)
                            onOpenGraph()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = "Knowledge Graph",
                            tint = iOSYellow,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            performHaptic(HapticFeedbackType.TextHandleMove)
                            showSettings = true
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = iOSYellow,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Large Title below navigation bar:
            Text(
                text = currentFolder ?: "All Notes",
                style = MonolithTypography.displayLarge,
                color = iOSTextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
            )

            // iOS Search Capsule (Specular translucent hairline border, compact 35dp height)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 3.dp)
                    .height(35.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iOSSearchPill)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = iOSTextSecondary,
                        modifier = Modifier.size(17.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MonolithTypography.bodyMedium.copy(color = iOSTextPrimary, fontSize = 15.sp),
                        cursorBrush = SolidColor(iOSYellow),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search",
                                    style = MonolithTypography.bodyMedium.copy(fontSize = 15.sp),
                                    color = iOSTextSecondary
                                )
                            }
                            innerTextField()
                        }
                    )

                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = "Clear",
                                tint = iOSTextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            // Tag Filter Chips (iOS Concentric Capsule Chips with Specular Hairline Borders)
            if (allTags.isNotEmpty()) {
                val tagScrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .horizontalScroll(tagScrollState),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IOSTagChip(
                        label = "All Notes",
                        isSelected = selectedTag == null,
                        onClick = { selectedTag = null }
                    )
                    allTags.forEach { tag ->
                        IOSTagChip(
                            label = "#$tag",
                            isSelected = selectedTag == tag,
                            onClick = { selectedTag = if (selectedTag == tag) null else tag }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Notes Stream (Apple Notes Inset Grouped Table)
            if (filteredNotes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoaded) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "No Results for \"$searchQuery\"" else "No Notes",
                                style = MonolithTypography.titleLarge,
                                color = iOSTextSecondary
                            )
                            if (searchQuery.isBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Tap the pencil icon to write your first note",
                                    style = MonolithTypography.bodyMedium,
                                    color = iOSTextTertiary
                                )
                            }
                        }
                    }
                }
            } else {
                // INSET GROUPED LIST VIEW WITH SWIPE GESTURES
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 60.dp)
                ) {
                    // 1. PINNED SECTION (if any notes pinned)
                    if (pinnedNotes.isNotEmpty()) {
                        item(key = "header_pinned", contentType = "section_header") {
                            Text(
                                text = "PINNED",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = iOSTextSecondary,
                                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                            )
                        }
                        itemsIndexed(
                            items = pinnedNotes,
                            key = { _, note -> "pinned_${note.id}" },
                            contentType = { _, _ -> "note_card_row" }
                        ) { index, note ->
                            val rowShape = remember(index, pinnedNotes.size) {
                                getRowShape(index, pinnedNotes.size)
                            }
                            IOSSwipeableNoteRow(
                                note = note,
                                rowShape = rowShape,
                                searchQuery = searchQuery,
                                showFolderBadge = (currentFolder == null),
                                onClick = {
                                    performHaptic(HapticFeedbackType.TextHandleMove)
                                    onNoteClick(note)
                                },
                                onLongClick = {
                                    performHaptic(HapticFeedbackType.LongPress)
                                    noteActionTarget = note
                                },
                                onTogglePin = {
                                    performHaptic(HapticFeedbackType.LongPress)
                                    onTogglePin(note)
                                },
                                onDelete = {
                                    performHaptic(HapticFeedbackType.LongPress)
                                    noteToDelete = note
                                },
                                onMove = {
                                    performHaptic(HapticFeedbackType.TextHandleMove)
                                    noteToMove = note
                                },
                                showDivider = index < pinnedNotes.size - 1
                            )
                        }
                    }

                    // 2. TIMELINE GROUPED SECTIONS (Apple Notes Date Architecture)
                    if (unpinnedNotes.isNotEmpty()) {
                        groupedUnpinnedNotes.forEach { (sectionTitle, sectionNotes) ->
                            item(key = "header_$sectionTitle", contentType = "section_header") {
                                Text(
                                    text = sectionTitle,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = iOSTextSecondary,
                                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                                )
                            }
                            itemsIndexed(
                                items = sectionNotes,
                                key = { _, note -> note.id },
                                contentType = { _, _ -> "note_card_row" }
                            ) { index, note ->
                                val rowShape = remember(index, sectionNotes.size) {
                                    getRowShape(index, sectionNotes.size)
                                }
                                IOSSwipeableNoteRow(
                                    note = note,
                                    rowShape = rowShape,
                                    searchQuery = searchQuery,
                                    showFolderBadge = (currentFolder == null),
                                    onClick = {
                                        performHaptic(HapticFeedbackType.TextHandleMove)
                                        onNoteClick(note)
                                    },
                                    onLongClick = {
                                        performHaptic(HapticFeedbackType.LongPress)
                                        noteActionTarget = note
                                    },
                                    onTogglePin = {
                                        performHaptic(HapticFeedbackType.LongPress)
                                        onTogglePin(note)
                                    },
                                    onDelete = {
                                        performHaptic(HapticFeedbackType.LongPress)
                                        noteToDelete = note
                                    },
                                    onMove = {
                                        performHaptic(HapticFeedbackType.TextHandleMove)
                                        noteToMove = note
                                    },
                                    showDivider = index < sectionNotes.size - 1
                                )
                            }
                        }
                    }
                }
            }

            // Monolith Notes Bottom Bar with Universal Inset Adaptation (Android 15+ & all aspect ratios)
            val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val safeBottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
            val bottomInset = if (imeBottom > 0.dp) 0.dp else maxOf(navBottom, safeBottom, 16.dp)

            Surface(
                color = iOSCardBackground,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = bottomInset)
                ) {
                    HorizontalDivider(thickness = 0.5.dp, color = iOSDivider)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Balance spacer for centered count label (Folders navigation is already prominent at top-left)
                        Box(modifier = Modifier.size(36.dp))
                        val countLabel = if (searchQuery.isNotBlank()) {
                            "${filteredNotes.size} ${if (filteredNotes.size == 1) "Result" else "Results"}"
                        } else {
                            "${notes.size} ${if (notes.size == 1) "Note" else "Notes"}"
                        }
                        Text(
                            text = countLabel,
                            style = MonolithTypography.labelSmall.copy(fontSize = 12.sp),
                            color = iOSTextSecondary
                        )

                        // Yellow Compose Button (Pencil Icon)
                        IconButton(
                            onClick = {
                                performHaptic(HapticFeedbackType.LongPress)
                                onNewNoteClick()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EditNote,
                                contentDescription = "New Note",
                                tint = iOSYellow,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        }

        // Long-Press iOS Cupertino Action Sheet
        if (noteActionTarget != null) {
            val targetNote = noteActionTarget!!
            ModalBottomSheet(
                onDismissRequest = { noteActionTarget = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = Color.Transparent,
                scrimColor = Color.Black.copy(alpha = 0.6f),
                dragHandle = null,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // Group 1: Preview Header & Actions
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = iOSCardBackground,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            // Header preview
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = targetNote.displayTitle,
                                    style = MonolithTypography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    ),
                                    color = iOSTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = targetNote.previewSnippet,
                                    style = MonolithTypography.bodyMedium.copy(fontSize = 12.sp),
                                    color = iOSTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            HorizontalDivider(thickness = 0.5.dp, color = iOSDivider)

                            // Pin / Unpin Action
                            CupertinoActionRow(
                                title = if (targetNote.isPinned) "Unpin Note" else "Pin Note",
                                icon = Icons.Default.PushPin,
                                tint = iOSYellow,
                                onClick = {
                                    performHaptic(HapticFeedbackType.TextHandleMove)
                                    onTogglePin(targetNote)
                                    noteActionTarget = null
                                }
                            )

                            HorizontalDivider(thickness = 0.5.dp, color = iOSDivider)

                            // Duplicate Action
                            CupertinoActionRow(
                                title = "Duplicate Note",
                                icon = Icons.Default.ContentCopy,
                                tint = Color(0xFF0A84FF),
                                onClick = {
                                    performHaptic(HapticFeedbackType.TextHandleMove)
                                    onDuplicateNote(targetNote)
                                    noteActionTarget = null
                                }
                            )

                            HorizontalDivider(thickness = 0.5.dp, color = iOSDivider)

                            // Copy Markdown Action
                            CupertinoActionRow(
                                title = "Copy Markdown",
                                icon = Icons.Default.ContentPaste,
                                tint = iOSEmerald,
                                onClick = {
                                    performHaptic(HapticFeedbackType.LongPress)
                                    val fullMarkdown = if (targetNote.content.isBlank()) {
                                        "# ${targetNote.displayTitle}"
                                    } else {
                                        "# ${targetNote.displayTitle}\n\n${targetNote.content}"
                                    }
                                    clipboardManager.setText(AnnotatedString(fullMarkdown))
                                    Toast.makeText(context, "Note copied as Markdown", Toast.LENGTH_SHORT).show()
                                    noteActionTarget = null
                                }
                            )

                            HorizontalDivider(thickness = 0.5.dp, color = iOSDivider)

                            // Share Note Action
                            CupertinoActionRow(
                                title = "Share Note",
                                icon = Icons.Default.Share,
                                tint = iOSYellow,
                                onClick = {
                                    performHaptic(HapticFeedbackType.TextHandleMove)
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TITLE, targetNote.displayTitle)
                                        putExtra(Intent.EXTRA_SUBJECT, targetNote.displayTitle)
                                        val fullText = if (targetNote.content.isBlank()) targetNote.displayTitle else "${targetNote.displayTitle}\n\n${targetNote.content}"
                                        putExtra(Intent.EXTRA_TEXT, fullText)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share Note"))
                                    noteActionTarget = null
                                }
                            )

                            HorizontalDivider(thickness = 0.5.dp, color = iOSDivider)

                            // Move to Folder Action
                            CupertinoActionRow(
                                title = "Move to Folder...",
                                icon = Icons.AutoMirrored.Filled.DriveFileMove,
                                tint = Color(0xFFFF9F0A),
                                onClick = {
                                    performHaptic(HapticFeedbackType.TextHandleMove)
                                    val toMove = targetNote
                                    noteActionTarget = null
                                    noteToMove = toMove
                                }
                            )

                            HorizontalDivider(thickness = 0.5.dp, color = iOSDivider)

                            // Delete Action (Destructive Red)
                            CupertinoActionRow(
                                title = "Delete Note",
                                icon = Icons.Default.Delete,
                                tint = Color(0xFFFF453A),
                                isDestructive = true,
                                onClick = {
                                    performHaptic(HapticFeedbackType.LongPress)
                                    val toDelete = targetNote
                                    noteActionTarget = null
                                    noteToDelete = toDelete
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Group 2: Isolated Cupertino Cancel Pill
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = iOSCardBackground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clickable {
                                performHaptic(HapticFeedbackType.TextHandleMove)
                                noteActionTarget = null
                            }
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Cancel",
                                style = MonolithTypography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 17.sp
                                ),
                                color = iOSYellow
                            )
                        }
                    }
                }
            }
        }

        // Move to Folder Bottom Sheet
        noteToMove?.let { targetNote ->
            ModalBottomSheet(
                onDismissRequest = { noteToMove = null },
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
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Move to Folder",
                        style = MonolithTypography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        color = iOSTextPrimary,
                        modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                    )
                    Text(
                        text = targetNote.displayTitle,
                        style = MonolithTypography.bodyMedium.copy(fontSize = 13.sp),
                        color = iOSTextSecondary,
                        modifier = Modifier.padding(bottom = 12.dp, start = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(iOSCardBackground)
                    ) {
                        Column {
                            // "Notes" Root
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .clickable {
                                        performHaptic(HapticFeedbackType.TextHandleMove)
                                        onMoveNoteToFolder(targetNote, "Notes")
                                        noteToMove = null
                                    }
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = iOSYellow,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Notes (Root)",
                                    style = MonolithTypography.bodyLarge.copy(fontSize = 16.sp),
                                    color = iOSTextPrimary
                                )
                            }

                            // Custom folders
                            val otherFolders = folders.filter { it != "Notes" }
                            otherFolders.forEach { folderName ->
                                HorizontalDivider(modifier = Modifier.padding(start = 48.dp), thickness = 0.5.dp, color = iOSDivider)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .clickable {
                                            performHaptic(HapticFeedbackType.TextHandleMove)
                                            onMoveNoteToFolder(targetNote, folderName)
                                            noteToMove = null
                                        }
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = iOSYellow,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = folderName,
                                        style = MonolithTypography.bodyLarge.copy(fontSize = 16.sp),
                                        color = iOSTextPrimary
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // Cupertino Delete Confirmation Sheet
        if (noteToDelete != null) {
            val targetNote = noteToDelete!!
            ModalBottomSheet(
                onDismissRequest = { noteToDelete = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = Color.Transparent,
                scrimColor = Color.Black.copy(alpha = 0.65f),
                dragHandle = null,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // Group 1: Warning header + Delete Button
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = iOSCardBackground,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Delete Note?",
                                    style = MonolithTypography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    ),
                                    color = iOSTextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Are you sure you want to delete '${targetNote.displayTitle}'? It will be moved to Recycle Bin and kept for 30 days.",
                                    style = MonolithTypography.bodyMedium.copy(fontSize = 13.sp),
                                    color = iOSTextSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }

                            HorizontalDivider(thickness = 0.5.dp, color = iOSDivider)

                            // Red Delete Button
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clickable {
                                        performHaptic(HapticFeedbackType.LongPress)
                                        onDeleteNote(targetNote)
                                        noteToDelete = null
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Delete Note",
                                    style = MonolithTypography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 17.sp
                                    ),
                                    color = Color(0xFFFF453A)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Group 2: Cancel Pill
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = iOSCardBackground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clickable {
                                performHaptic(HapticFeedbackType.TextHandleMove)
                                noteToDelete = null
                            }
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Cancel",
                                style = MonolithTypography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 17.sp
                                ),
                                color = iOSYellow
                            )
                        }
                    }
                }
            }
        }

        // Settings Sheet
        if (showSettings) {
            val totalWords = remember(notes) { notes.sumOf { it.wordCount } }
            SettingsSheet(
                settings = settings,
                noteCount = notes.size,
                totalWords = totalWords,
                vaultPath = vaultPath,
                onUpdateHaptics = onUpdateHaptics,
                onUpdateStartStyle = onUpdateStartStyle,
                onUpdateFontSize = onUpdateFontSize,
                onUpdateShowWordCount = onUpdateShowWordCount,
                onUpdateAutoChecklists = onUpdateAutoChecklists,
                onSortOrderChange = onSortOrderChange,
                onDismiss = { showSettings = false },
                onExportNotes = {
                    showSettings = false
                    onExportNotes()
                },
                trashNotesCount = trashNotesCount,
                onOpenTrash = onOpenTrash,
                onUpdateBiometricLock = onUpdateBiometricLock,
                onUpdateScreenSecurity = onUpdateScreenSecurity,
                onUpdateTheme = onUpdateTheme,
                onUpdateCloudConfig = onUpdateCloudConfig,
                onPerformCloudSync = onPerformCloudSync,
                onConnectGoogleDrive = onConnectGoogleDrive,
                onDisconnectGoogleDrive = onDisconnectGoogleDrive,
                onUpdateGoogleDriveConfig = onUpdateGoogleDriveConfig,
                onUpdateGoogleDriveUri = onUpdateGoogleDriveUri,
                onPerformGoogleDriveSync = onPerformGoogleDriveSync,
                onUpdateWebDavConfig = onUpdateWebDavConfig,
                onPerformSync = onPerformSync
            )
        }
    }
}

private val TagChipShape = CircleShape

@Composable
fun IOSTagChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val chipBgState = animateColorAsState(
        targetValue = if (isSelected) iOSYellowContainer else iOSCardBackground,
        animationSpec = tween(durationMillis = 120),
        label = "chipBg"
    )
    val chipTextColor by animateColorAsState(
        targetValue = if (isSelected) iOSYellow else iOSTextSecondary,
        animationSpec = tween(durationMillis = 120),
        label = "chipTextColor"
    )

    Box(
        modifier = Modifier
            .clip(TagChipShape)
            .drawBehind { drawRect(chipBgState.value) }
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MonolithTypography.labelMedium.copy(fontSize = 12.5.sp),
            color = chipTextColor,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun IOSNoteRow(
    note: Note,
    searchQuery: String = "",
    showFolderBadge: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val formattedDate = note.formattedDate

    val highlightColor = iOSYellow
    val titleText = remember(note.displayTitle, searchQuery, highlightColor) {
        if (searchQuery.isBlank()) AnnotatedString(note.displayTitle)
        else highlightQuery(note.displayTitle, searchQuery, highlightColor)
    }
    val snippetText = remember(note.previewSnippet, searchQuery, highlightColor) {
        if (note.previewSnippet.isBlank()) null
        else if (searchQuery.isBlank()) AnnotatedString(note.previewSnippet)
        else highlightQuery(note.previewSnippet, searchQuery, highlightColor)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val s = if (isPressed) 0.985f else 1f
                scaleX = s
                scaleY = s
            }
            .drawBehind {
                if (isPressed) {
                    drawRect(Color(0xFF2C2C2E))
                }
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (note.isPinned) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        tint = iOSYellow,
                        modifier = Modifier
                            .size(14.dp)
                            .padding(end = 4.dp)
                    )
                }
                Text(
                    text = titleText,
                    style = MonolithTypography.titleLarge.copy(fontSize = 16.sp),
                    color = iOSTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                note.taskStats?.let { (completed, total) ->
                    val isAllDone = completed == total
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isAllDone) iOSEmerald.copy(alpha = 0.2f) else iOSYellowContainer)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isAllDone) "✓ Done" else "✓ $completed/$total",
                            style = MonolithTypography.labelSmall.copy(fontSize = 10.5.sp),
                            color = if (isAllDone) iOSEmerald else iOSYellow,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formattedDate,
                    style = MonolithTypography.bodyMedium.copy(fontSize = 13.5.sp),
                    color = iOSTextPrimary.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Medium
                )

                if (showFolderBadge && note.folderName != "Notes") {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(iOSYellow.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 1.5.dp)
                    ) {
                        Text(
                            text = "📁 ${note.folderName}",
                            style = MonolithTypography.bodySmall.copy(fontSize = 10.5.sp),
                            color = iOSYellow,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (snippetText != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = snippetText,
                        style = MonolithTypography.bodyMedium.copy(fontSize = 13.5.sp),
                        color = iOSTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * System-Level iOS ProMotion Swipeable Note Row (120Hz Zero-Lag Architecture):
 * - Direct RenderThread draw phase tracking (0 recompositions during swipe)
 * - Immediate action dispatch on threshold release (0ms delay)
 * - Apple rubber-banding resistance past action boundaries
 * - Critically damped snappy spring snap (stiffness 850f)
 * - Instant tap to close open rows
 */
@Composable
fun IOSSwipeableNoteRow(
    note: Note,
    rowShape: RoundedCornerShape,
    searchQuery: String = "",
    showFolderBadge: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    showDivider: Boolean = false,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val actionWidthPx = with(density) { 140.dp.toPx() }
    val fullDeleteThresholdPx = with(density) { 180.dp.toPx() }
    val pinThresholdPx = with(density) { 90.dp.toPx() }
    val maxSwipeLeftPx = with(density) { 260.dp.toPx() }
    val maxSwipeRightPx = with(density) { 120.dp.toPx() }

    val offsetXAnim = remember { Animatable(0f) }
    var hasHapticTriggered by remember { mutableStateOf(false) }

    // Snappy Apple Spring for crisp, instantaneous return
    val iosSnapSpring = remember { spring<Float>(dampingRatio = 0.9f, stiffness = 850f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(iOSCardBackground)
    ) {
        // Behind Actions: Rendered once, driven purely via RenderThread graphicsLayer (0 recompositions)
        // 1. Swipe Right: Pin Action (Warm Amber Gold)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(iOSYellow)
                .graphicsLayer {
                    alpha = if (offsetXAnim.value > 0f) (offsetXAnim.value / 40f).coerceIn(0f, 1f) else 0f
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier
                    .padding(start = 18.dp)
                    .graphicsLayer {
                        val progress = (offsetXAnim.value / pinThresholdPx).coerceIn(0.6f, 1.15f)
                        scaleX = progress
                        scaleY = progress
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = if (note.isPinned) "Unpin" else "Pin",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (note.isPinned) "Unpin" else "Pin",
                    style = MonolithTypography.titleMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.Black
                )
            }
        }

        // 2. Swipe Left: Move + Delete Actions
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    alpha = if (offsetXAnim.value < 0f) (-offsetXAnim.value / 40f).coerceIn(0f, 1f) else 0f
                }
        ) {
            // Full delete background fill when dragged far left
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFFFF453A))
                    .graphicsLayer {
                        alpha = if (offsetXAnim.value <= -fullDeleteThresholdPx) 1f else 0f
                    },
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    modifier = Modifier.padding(end = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Delete",
                        style = MonolithTypography.titleMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Normal Move & Delete split buttons
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = if (offsetXAnim.value > -fullDeleteThresholdPx) 1f else 0f
                    },
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Move Button (Indigo)
                Box(
                    modifier = Modifier
                        .width(70.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF5E5CE6))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            coroutineScope.launch {
                                offsetXAnim.animateTo(0f, iosSnapSpring)
                            }
                            onMove()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Move",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Move",
                            style = MonolithTypography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                            color = Color.White
                        )
                    }
                }

                // Delete Button (Red)
                Box(
                    modifier = Modifier
                        .width(70.dp)
                        .fillMaxHeight()
                        .background(Color(0xFFFF453A))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            coroutineScope.launch {
                                offsetXAnim.animateTo(0f, iosSnapSpring)
                            }
                            onDelete()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Delete",
                            style = MonolithTypography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Foreground Note Row
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = offsetXAnim.value
                }
                .background(iOSCardBackground)
                .draggable(
                    state = rememberDraggableState { delta ->
                        val current = offsetXAnim.value
                        // Apple Rubber-Banding Resistance past action limits
                        val effectiveDelta = when {
                            current < -actionWidthPx && delta < 0 -> delta * 0.45f
                            current > pinThresholdPx && delta > 0 -> delta * 0.45f
                            else -> delta
                        }
                        val newOffset = (current + effectiveDelta).coerceIn(-maxSwipeLeftPx, maxSwipeRightPx)
                        coroutineScope.launch {
                            offsetXAnim.snapTo(newOffset)
                        }
                        val isFullAction = newOffset <= -fullDeleteThresholdPx || newOffset >= pinThresholdPx
                        if (isFullAction && !hasHapticTriggered) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            hasHapticTriggered = true
                        } else if (!isFullAction && hasHapticTriggered) {
                            hasHapticTriggered = false
                        }
                    },
                    orientation = Orientation.Horizontal,
                    onDragStopped = { velocity ->
                        val current = offsetXAnim.value
                        when {
                            // Full swipe delete: trigger action IMMEDIATELY without waiting for animation!
                            current <= -fullDeleteThresholdPx || (current < -actionWidthPx / 2f && velocity < -1600f) -> {
                                onDelete()
                                coroutineScope.launch {
                                    offsetXAnim.animateTo(0f, iosSnapSpring, initialVelocity = velocity)
                                }
                            }
                            // Snap open to action buttons (or flick left)
                            current <= -actionWidthPx / 2f || (current < 0f && velocity < -500f) -> {
                                coroutineScope.launch {
                                    offsetXAnim.animateTo(-actionWidthPx, iosSnapSpring, initialVelocity = velocity)
                                }
                            }
                            // Full swipe pin: trigger action IMMEDIATELY without waiting for animation! (or flick right)
                            current >= pinThresholdPx || (current > 0f && velocity > 600f) -> {
                                onTogglePin()
                                coroutineScope.launch {
                                    offsetXAnim.animateTo(0f, iosSnapSpring, initialVelocity = velocity)
                                }
                            }
                            // Snap closed
                            else -> {
                                coroutineScope.launch {
                                    offsetXAnim.animateTo(0f, iosSnapSpring, initialVelocity = velocity)
                                }
                            }
                        }
                    }
                )
        ) {
            IOSNoteRow(
                note = note,
                searchQuery = searchQuery,
                showFolderBadge = showFolderBadge,
                onClick = {
                    if (kotlin.math.abs(offsetXAnim.value) > 4f) {
                        coroutineScope.launch {
                            offsetXAnim.animateTo(0f, iosSnapSpring)
                        }
                    } else {
                        onClick()
                    }
                },
                onLongClick = onLongClick
            )
            if (showDivider) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp),
                    thickness = 0.5.dp,
                    color = iOSDivider
                )
            }
        }
    }
}

private val SingleCardShape = RoundedCornerShape(12.dp)
private val TopCardShape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
private val BottomCardShape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
private val MiddleCardShape = RoundedCornerShape(0.dp)

private fun getRowShape(index: Int, total: Int): RoundedCornerShape {
    return when {
        total <= 1 -> SingleCardShape
        index == 0 -> TopCardShape
        index == total - 1 -> BottomCardShape
        else -> MiddleCardShape
    }
}

fun highlightQuery(text: String, query: String, highlightColor: Color = Color(0xFFE5A93C)): AnnotatedString {
    if (query.isBlank() || text.isEmpty()) return AnnotatedString(text)
    val trimmedQuery = query.trim()
    if (trimmedQuery.isEmpty()) return AnnotatedString(text)
    val firstMatch = text.indexOf(trimmedQuery, 0, ignoreCase = true)
    if (firstMatch == -1) return AnnotatedString(text)

    return try {
        buildAnnotatedString {
            var startIdx = 0
            val textLen = text.length
            val queryLen = trimmedQuery.length
            while (startIdx < textLen) {
                val matchIdx = text.indexOf(trimmedQuery, startIdx, ignoreCase = true)
                if (matchIdx == -1) {
                    append(text.substring(startIdx))
                    break
                }
                if (matchIdx > startIdx) {
                    append(text.substring(startIdx, matchIdx))
                }
                val matchEnd = (matchIdx + queryLen).coerceAtMost(textLen)
                pushStyle(SpanStyle(background = highlightColor.copy(alpha = 0.35f), color = Color.White, fontWeight = FontWeight.Bold))
                append(text.substring(matchIdx, matchEnd))
                pop()
                startIdx = maxOf(matchEnd, matchIdx + 1)
            }
        }
    } catch (_: Exception) {
        AnnotatedString(text)
    }
}

@Composable
private fun SyncSpinningIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "SyncSpin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SyncSpinAngle"
    )
    Icon(
        imageVector = Icons.Default.Sync,
        contentDescription = "Syncing with Cloud",
        tint = iOSYellow,
        modifier = modifier.graphicsLayer { rotationZ = spinAngle }
    )
}

@Composable
private fun CupertinoActionRow(
    title: String,
    icon: ImageVector,
    tint: Color,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MonolithTypography.bodyLarge.copy(
                fontSize = 16.sp,
                fontWeight = if (isDestructive) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (isDestructive) Color(0xFFFF453A) else iOSTextPrimary
        )

        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

