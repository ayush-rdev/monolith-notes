package com.monolith.notes.ui.folders

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monolith.notes.model.Note
import com.monolith.notes.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(
    notes: List<Note>,
    folders: List<String>,
    trashCount: Int,
    selectedFolder: String?, // null means "All Monolith"
    onSelectFolder: (String?) -> Unit,
    onOpenTrash: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onNewNoteClick: () -> Unit,
    onBackToNotes: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var isEditing by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var folderToDelete by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    BackHandler {
        onBackToNotes()
    }

    val totalNotesCount = notes.size
    val rootNotesCount = notes.count { it.folderName == "Notes" }

    // Map each custom folder to its count
    val customFolders = remember(folders, notes) {
        folders.filter { it != "Notes" }.sorted()
    }

    val filteredCustomFolders = remember(customFolders, searchQuery) {
        if (searchQuery.isBlank()) customFolders
        else customFolders.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(iOSBackground)
            .statusBarsPadding()
            .displayCutoutPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Done button (leads back to notes list)
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onBackToNotes()
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Notes",
                        color = iOSYellow,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Edit button
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isEditing = !isEditing
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isEditing) "Done" else "Edit",
                        color = iOSYellow,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            // Scrollable Content
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // Large iOS Title: "Folders"
                item {
                    Text(
                        text = "Folders",
                        style = MonolithTypography.headlineLarge.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold),
                        color = iOSTextPrimary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                // Inset Group 1: MONOLITH VAULT
                item {
                    Text(
                        text = "MONOLITH VAULT",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = iOSTextSecondary,
                        modifier = Modifier.padding(start = 12.dp, bottom = 6.dp)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(iOSCardBackground)
                    ) {
                        Column {
                            // "All Monolith"
                            FolderRow(
                                title = "All Monolith",
                                icon = Icons.Default.FolderSpecial,
                                iconTint = iOSYellow,
                                count = totalNotesCount,
                                isSelected = selectedFolder == null,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectFolder(null)
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(start = 54.dp), thickness = 0.5.dp, color = iOSDivider)

                            // "Notes" (Root Folder)
                            FolderRow(
                                title = "Notes",
                                icon = Icons.Default.Folder,
                                iconTint = iOSYellow,
                                count = rootNotesCount,
                                isSelected = selectedFolder == "Notes",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectFolder("Notes")
                                }
                            )

                            // Custom Subfolders
                            filteredCustomFolders.forEach { folderName ->
                                HorizontalDivider(modifier = Modifier.padding(start = 54.dp), thickness = 0.5.dp, color = iOSDivider)
                                val count = notes.count { it.folderName == folderName }
                                FolderRow(
                                    title = folderName,
                                    icon = Icons.Default.Folder,
                                    iconTint = iOSYellow,
                                    count = count,
                                    isSelected = selectedFolder == folderName,
                                    isEditing = isEditing,
                                    onDeleteClick = {
                                        folderToDelete = folderName
                                    },
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSelectFolder(folderName)
                                    }
                                )
                            }
                        }
                    }
                }

                // Inset Group 2: RECENTLY DELETED
                item {
                    Text(
                        text = "SAFETY NET",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = iOSTextSecondary,
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 6.dp)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(iOSCardBackground)
                    ) {
                        FolderRow(
                            title = "Recently Deleted",
                            icon = Icons.Default.DeleteOutline,
                            iconTint = Color(0xFFFF453A),
                            count = trashCount,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onOpenTrash()
                            }
                        )
                    }
                }
            }

            // Monolith Bottom Bar: "New Folder" & "New Note" (Universal Android 15 Insets)
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
                    HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.07f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // New Folder button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    newFolderName = ""
                                    showCreateDialog = true
                                }
                                .padding(vertical = 4.dp, horizontal = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CreateNewFolder,
                                contentDescription = "New Folder",
                                tint = iOSYellow,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "New Folder",
                                color = iOSYellow,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // New Note button
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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

        // New Folder Cupertino Alert Dialog
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                containerColor = iOSCardBackground,
                shape = RoundedCornerShape(16.dp),
                title = {
                    Text(
                        text = "New Folder",
                        style = MonolithTypography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        color = iOSTextPrimary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Enter a name for this notebook folder.",
                            style = MonolithTypography.bodyMedium.copy(fontSize = 13.sp),
                            color = iOSTextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            placeholder = { Text("Folder Name", color = iOSTextTertiary) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = iOSTextPrimary,
                                unfocusedTextColor = iOSTextPrimary,
                                focusedBorderColor = iOSYellow,
                                unfocusedBorderColor = iOSDivider,
                                cursorColor = iOSYellow
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val name = newFolderName.trim()
                            if (name.isNotBlank()) {
                                onCreateFolder(name)
                                showCreateDialog = false
                            }
                        }
                    ) {
                        Text(
                            text = "Save",
                            color = iOSYellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text(
                            text = "Cancel",
                            color = iOSYellow,
                            fontSize = 16.sp
                        )
                    }
                }
            )
        }

        // Delete Folder Confirmation Dialog
        folderToDelete?.let { folderName ->
            AlertDialog(
                onDismissRequest = { folderToDelete = null },
                containerColor = iOSCardBackground,
                shape = RoundedCornerShape(16.dp),
                title = {
                    Text(
                        text = "Delete \"$folderName\"?",
                        style = MonolithTypography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        color = iOSTextPrimary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                },
                text = {
                    Text(
                        text = "Deleting this folder will move all notes inside it to Recently Deleted. You can restore them within 30 days.",
                        style = MonolithTypography.bodyMedium.copy(fontSize = 13.sp),
                        color = iOSTextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteFolder(folderName)
                            folderToDelete = null
                        }
                    ) {
                        Text(
                            text = "Delete",
                            color = Color(0xFFFF453A),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { folderToDelete = null }) {
                        Text(
                            text = "Cancel",
                            color = iOSYellow,
                            fontSize = 16.sp
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun FolderRow(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    count: Int,
    isSelected: Boolean = false,
    isEditing: Boolean = false,
    onDeleteClick: () -> Unit = {},
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .graphicsLayer {
                alpha = if (isPressed) 0.7f else 1.0f
            }
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Delete button in edit mode
            if (isEditing) {
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(end = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RemoveCircle,
                        contentDescription = "Delete Folder",
                        tint = Color(0xFFFF453A),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = title,
                style = MonolithTypography.bodyLarge.copy(
                    fontSize = 17.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                ),
                color = if (isSelected) iOSYellow else iOSTextPrimary
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$count",
                style = MonolithTypography.bodyMedium.copy(fontSize = 15.sp),
                color = iOSTextSecondary
            )

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = iOSTextTertiary.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
