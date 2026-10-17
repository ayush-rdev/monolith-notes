package com.monolith.notes.ui.trash

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monolith.notes.model.Note
import com.monolith.notes.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    trashNotes: List<Note>,
    onBack: () -> Unit,
    onRestoreNote: (Note) -> Unit,
    onPermanentlyDeleteNote: (Note) -> Unit,
    onEmptyTrash: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var selectedNote by remember { mutableStateOf<Note?>(null) }
    var showEmptyTrashConfirm by remember { mutableStateOf(false) }

    BackHandler {
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(iOSBackground)
            .statusBarsPadding()
            .displayCutoutPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            // Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button: "< Notes"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onBack()
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Back",
                        tint = iOSYellow,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Notes",
                        color = iOSYellow,
                        style = MonolithTypography.titleMedium.copy(fontSize = 17.sp)
                    )
                }

                // Empty Trash Button
                if (trashNotes.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showEmptyTrashConfirm = true
                        }
                    ) {
                        Text(
                            text = "Empty",
                            color = Color(0xFFFF453A),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            // Screen Title & Subtitle Banner
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Recently Deleted",
                    style = MonolithTypography.displayLarge,
                    color = iOSTextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Notes are permanently deleted after 30 days.",
                    style = MonolithTypography.bodyMedium,
                    color = iOSTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Trash List or Empty Placeholder
            if (trashNotes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = iOSTextTertiary.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Recently Deleted Notes",
                            style = MonolithTypography.titleLarge,
                            color = iOSTextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Notes you delete will appear here for 30 days before being permanently erased.",
                            style = MonolithTypography.bodyMedium,
                            color = iOSTextTertiary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 40.dp)
                ) {
                    itemsIndexed(
                        items = trashNotes,
                        key = { _, note -> note.id },
                        contentType = { _, _ -> "trash_row" }
                    ) { index, note ->
                        val shape = when {
                            trashNotes.size == 1 -> SingleTrashShape
                            index == 0 -> TopTrashShape
                            index == trashNotes.size - 1 -> BottomTrashShape
                            else -> MiddleTrashShape
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(iOSCardBackground)
                        ) {
                            Column {
                                TrashNoteRow(
                                    note = note,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedNote = note
                                    }
                                )
                                if (index < trashNotes.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 16.dp),
                                        thickness = 0.5.dp,
                                        color = iOSDivider
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Action Sheet for Selected Trash Note (Recover / Delete Immediately)
        if (selectedNote != null) {
            val note = selectedNote!!
            ModalBottomSheet(
                onDismissRequest = { selectedNote = null },
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
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Actions Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(iOSCardBackground)
                    ) {
                        Column {
                            // Note Title Header
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                            ) {
                                Text(
                                    text = note.displayTitle,
                                    style = MonolithTypography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = iOSTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (note.previewSnippet.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = note.previewSnippet,
                                        style = MonolithTypography.bodyMedium.copy(fontSize = 13.sp),
                                        color = iOSTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            HorizontalDivider(thickness = 0.5.dp, color = iOSDivider)

                            // 1. Recover Note Action
                            CupertinoSheetAction(
                                title = "Recover Note",
                                icon = Icons.AutoMirrored.Filled.Undo,
                                tint = iOSYellow,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedNote = null
                                    onRestoreNote(note)
                                    Toast.makeText(context, "Note restored to Notes", Toast.LENGTH_SHORT).show()
                                }
                            )

                            HorizontalDivider(thickness = 0.5.dp, color = iOSDivider)

                            // 2. Delete Immediately Action
                            CupertinoSheetAction(
                                title = "Delete Immediately",
                                icon = Icons.Default.DeleteForever,
                                tint = Color(0xFFFF453A),
                                isDestructive = true,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedNote = null
                                    onPermanentlyDeleteNote(note)
                                    Toast.makeText(context, "Note permanently deleted", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Cancel Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(iOSCardBackground)
                            .clickable { selectedNote = null }
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

        // Empty Trash Confirmation Dialog
        if (showEmptyTrashConfirm) {
            AlertDialog(
                onDismissRequest = { showEmptyTrashConfirm = false },
                containerColor = iOSCardBackground,
                title = {
                    Text(
                        text = "Empty Recently Deleted?",
                        style = MonolithTypography.titleLarge,
                        color = Color.White
                    )
                },
                text = {
                    Text(
                        text = "All ${trashNotes.size} ${if (trashNotes.size == 1) "note" else "notes"} in Recently Deleted will be permanently erased. This action cannot be undone.",
                        style = MonolithTypography.bodyMedium,
                        color = iOSTextSecondary
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showEmptyTrashConfirm = false
                            onEmptyTrash()
                            Toast.makeText(context, "Recently Deleted emptied", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text(
                            text = "Empty Trash",
                            color = Color(0xFFFF453A),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEmptyTrashConfirm = false }) {
                        Text(text = "Cancel", color = iOSYellow)
                    }
                }
            )
        }
    }
}

private val SingleTrashShape = RoundedCornerShape(12.dp)
private val TopTrashShape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
private val BottomTrashShape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
private val MiddleTrashShape = RoundedCornerShape(0.dp)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TrashNoteRow(
    note: Note,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val rowBgState = animateColorAsState(
        targetValue = if (isPressed) Color(0xFF2C2C2E) else Color.Transparent,
        animationSpec = tween(durationMillis = 80),
        label = "trashRowBg"
    )

    val pressScaleState = animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 520f),
        label = "trashPressScale"
    )

    val formattedDate = note.formattedDate

    // Calculate days remaining (30 - days passed)
    val daysRemaining = remember(note.lastModified) {
        val daysPassed = ((System.currentTimeMillis() - note.lastModified) / 86400000L).toInt()
        maxOf(1, 30 - daysPassed)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val s = pressScaleState.value
                scaleX = s
                scaleY = s
            }
            .drawBehind {
                val bg = rowBgState.value
                if (bg.alpha > 0f) {
                    drawRect(bg)
                }
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = note.displayTitle,
                style = MonolithTypography.titleLarge.copy(fontSize = 16.sp),
                color = iOSTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formattedDate,
                    style = MonolithTypography.bodyMedium.copy(fontSize = 13.5.sp),
                    color = iOSTextPrimary.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Medium
                )

                if (note.previewSnippet.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = note.previewSnippet,
                        style = MonolithTypography.bodyMedium.copy(fontSize = 13.5.sp),
                        color = iOSTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Days remaining capsule badge
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color(0xFF2C2C2E))
                .padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Text(
                text = "${daysRemaining}d left",
                style = MonolithTypography.labelSmall.copy(fontSize = 10.5.sp),
                color = if (daysRemaining <= 3) Color(0xFFFF453A) else iOSTextTertiary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun CupertinoSheetAction(
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
