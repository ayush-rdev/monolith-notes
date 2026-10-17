@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.monolith.notes.ui.editor

import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.monolith.notes.data.PdfPublisher
import java.io.File
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monolith.notes.data.AppSettings
import com.monolith.notes.data.NewNoteStartStyle
import com.monolith.notes.model.Note
import com.monolith.notes.ui.components.AppleFormatSheet
import com.monolith.notes.ui.components.FormattingBar
import com.monolith.notes.ui.components.MarkdownRenderView
import com.monolith.notes.ui.components.toggleMarkdownTask
import com.monolith.notes.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    note: Note,
    allNotes: List<Note> = emptyList(),
    vaultPath: String = "/sdcard/Documents/MonolithNotes",
    settings: AppSettings = AppSettings(),
    onBack: () -> Unit,
    onSave: (Note) -> Unit,
    onNavigateToNote: ((String) -> Unit)? = null,
    onSaveAttachment: (suspend (Uri) -> String?)? = null,
    onDelete: ((Note) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    fun performHaptic(type: HapticFeedbackType) {
        if (settings.hapticsEnabled) {
            haptic.performHapticFeedback(type)
        }
    }
    val coroutineScope = rememberCoroutineScope()
    val titleFocusRequester = remember { FocusRequester() }
    val bodyFocusRequester = remember { FocusRequester() }

    // Start in editing mode only if it's a brand new empty note
    val isNewNote = remember(note.id) { note.title.isEmpty() && note.content.isEmpty() }
    var isEditing by remember(note.id) { mutableStateOf(isNewNote) }

    var titleState by remember(note.id) {
        mutableStateOf(
            TextFieldValue(
                text = note.title,
                selection = TextRange(note.title.length)
            )
        )
    }

    var bodyState by remember(note.id) {
        mutableStateOf(
            TextFieldValue(
                text = note.content,
                selection = TextRange(note.content.length)
            )
        )
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null && onSaveAttachment != null) {
            coroutineScope.launch {
                val relativePath = onSaveAttachment(uri)
                if (relativePath != null) {
                    val cursor = bodyState.selection.start.coerceIn(0, bodyState.text.length)
                    val insertion = "\n\n![Image]($relativePath)\n\n"
                    val newText = bodyState.text.substring(0, cursor) + insertion + bodyState.text.substring(cursor)
                    val newSelection = TextRange(cursor + insertion.length)
                    bodyState = TextFieldValue(text = newText, selection = newSelection)
                }
            }
        }
    }

    var showFormatSheet by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("d MMMM yyyy 'at' h:mm a", Locale.getDefault()) }
    val formattedDate = remember(note.lastModified) { dateFormat.format(Date(note.lastModified)) }

    var currentNote by remember(note.id) { mutableStateOf(note) }
    var lastSavedTitle by remember(note.id) { mutableStateOf(note.title) }
    var lastSavedBody by remember(note.id) { mutableStateOf(note.content) }

    // Save helper
    val flushSave = {
        val currentTitle = titleState.text
        val currentBody = bodyState.text
        if (currentTitle != lastSavedTitle || currentBody != lastSavedBody) {
            if (currentTitle.isNotBlank() || currentBody.isNotBlank() || currentNote.file != null) {
                lastSavedTitle = currentTitle
                lastSavedBody = currentBody
                val updated = currentNote.copy(
                    title = currentTitle,
                    content = currentBody,
                    lastModified = System.currentTimeMillis()
                )
                currentNote = updated
                onSave(updated)
            }
        }
    }

    // Debounced autosave in background
    LaunchedEffect(titleState.text, bodyState.text) {
        if (titleState.text != lastSavedTitle || bodyState.text != lastSavedBody) {
            delay(800)
            flushSave()
        }
    }

    // Flush any unsaved keystrokes immediately whenever the editor leaves composition
    DisposableEffect(Unit) {
        onDispose {
            flushSave()
        }
    }

    // Back handler: flush save, clear keyboard, and navigate
    BackHandler {
        if (showFormatSheet) {
            showFormatSheet = false
        } else {
            focusManager.clearFocus()
            flushSave()
            onBack()
        }
    }

    val undoStack = remember(note.id) { ArrayDeque<TextFieldValue>() }
    val redoStack = remember(note.id) { ArrayDeque<TextFieldValue>() }
    var canUndo by remember(note.id) { mutableStateOf(false) }
    var canRedo by remember(note.id) { mutableStateOf(false) }

    fun pushUndoState(oldValue: TextFieldValue) {
        if (undoStack.isEmpty() || undoStack.last().text != oldValue.text) {
            undoStack.addLast(oldValue)
            if (undoStack.size > 30) undoStack.removeFirst()
            redoStack.clear()
            canUndo = true
            canRedo = false
        }
    }

    val onUndo: () -> Unit = {
        if (undoStack.isNotEmpty()) {
            val prev = undoStack.removeLast()
            redoStack.addLast(bodyState)
            bodyState = prev
            canUndo = undoStack.isNotEmpty()
            canRedo = true
        }
    }

    val onRedo: () -> Unit = {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeLast()
            undoStack.addLast(bodyState)
            bodyState = next
            canUndo = true
            canRedo = redoStack.isNotEmpty()
        }
    }

    val wikiLinkMatch = remember(bodyState.text, bodyState.selection, isEditing) {
        if (!isEditing || !bodyState.selection.collapsed) null
        else detectWikiLinkQuery(bodyState.text, bodyState.selection.start)
    }

    val wikiLinkSuggestions = remember(wikiLinkMatch, allNotes) {
        if (wikiLinkMatch == null) emptyList()
        else {
            val query = wikiLinkMatch.second.trim()
            val currentId = currentNote.id
            allNotes
                .asSequence()
                .filter { it.id != currentId && it.file?.name != currentNote.file?.name }
                .filter { noteItem ->
                    if (query.isEmpty()) true
                    else noteItem.displayTitle.contains(query, ignoreCase = true) ||
                         (noteItem.file != null && noteItem.file.nameWithoutExtension.contains(query, ignoreCase = true))
                }
                .sortedWith(
                    compareByDescending<Note> {
                        if (query.isNotEmpty() && it.displayTitle.startsWith(query, ignoreCase = true)) 2
                        else if (query.isNotEmpty() && it.displayTitle.contains(query, ignoreCase = true)) 1
                        else 0
                    }.thenByDescending { it.lastModified }
                )
                .take(10)
                .toList()
        }
    }

    val onSelectWikiLink: (String) -> Unit = { targetTitle ->
        performHaptic(HapticFeedbackType.TextHandleMove)
        pushUndoState(bodyState)
        val match = wikiLinkMatch
        if (match != null) {
            val current = bodyState
            val text = current.text
            val cursor = current.selection.start
            val start = match.first
            // If cursor is followed immediately by "]]", consume them as well
            val end = if (cursor + 1 < text.length && text.substring(cursor, cursor + 2) == "]]") {
                cursor + 2
            } else {
                cursor
            }
            val replacement = "[[$targetTitle]] "
            val newText = text.replaceRange(start, end, replacement)
            val newCursor = start + replacement.length
            bodyState = TextFieldValue(text = newText, selection = TextRange(newCursor))
            bodyFocusRequester.requestFocus()
        }
    }

    // Auto-focus on new note AFTER slide transition finishes (respects user settings)
    LaunchedEffect(Unit) {
        if (isNewNote) {
            delay(350)
            try {
                if (settings.newNoteStartStyle == NewNoteStartStyle.BODY) {
                    bodyFocusRequester.requestFocus()
                } else {
                    titleFocusRequester.requestFocus()
                }
            } catch (_: Exception) {}
        }
    }

    val scrollState = rememberScrollState()

    // Dynamic Monolith selection colors (Custom cursor and highlights)
    val theme = LocalMonolithTheme.current
    val appleTextSelectionColors = remember(theme) {
        TextSelectionColors(
            handleColor = theme.accent,
            backgroundColor = theme.accentContainer
        )
    }

    // Line prefix toggle helper (Checklists, Bullets, Numbers, Quotes, Headings)
    fun toggleLinePrefix(targetPrefix: String, alternatePrefix: String? = null) {
        pushUndoState(bodyState)
        val current = bodyState
        val text = current.text
        val cursor = current.selection.start.coerceIn(0, text.length)

        val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let {
            if (it == -1) 0 else it + 1
        }
        val lineEnd = text.indexOf('\n', cursor).let {
            if (it == -1) text.length else it
        }
        val line = text.substring(lineStart, lineEnd)

        val cleanLinePrefix = { input: String ->
            input
                .replaceFirst("^#{1,6}\\s*".toRegex(), "")
                .removePrefix("○ ")
                .removePrefix("✓ ")
                .removePrefix("• ")
                .removePrefix("> ")
                .replaceFirst("^-\\s+\\[[ xX]\\]\\s*".toRegex(), "")
                .replaceFirst("^[-*]\\s+".toRegex(), "")
        }

        val (newLine, cursorOffset) = when {
            targetPrefix.isEmpty() -> {
                val cleanLine = cleanLinePrefix(line)
                Pair(cleanLine, cleanLine.length - line.length)
            }
            line.startsWith(targetPrefix) -> {
                if (alternatePrefix != null) {
                    val toggled = alternatePrefix + line.removePrefix(targetPrefix)
                    Pair(toggled, alternatePrefix.length - targetPrefix.length)
                } else {
                    val stripped = line.removePrefix(targetPrefix)
                    Pair(stripped, -targetPrefix.length)
                }
            }
            alternatePrefix != null && line.startsWith(alternatePrefix) -> {
                val toggled = line.removePrefix(alternatePrefix)
                Pair(toggled, -alternatePrefix.length)
            }
            else -> {
                val cleanLine = cleanLinePrefix(line)
                Pair(targetPrefix + cleanLine, (targetPrefix + cleanLine).length - line.length)
            }
        }

        val newFullText = text.replaceRange(lineStart, lineEnd, newLine)
        val newCursor = (cursor + cursorOffset).coerceIn(0, newFullText.length)
        bodyState = TextFieldValue(
            text = newFullText,
            selection = TextRange(newCursor)
        )
        bodyFocusRequester.requestFocus()
    }

    // Wrap selection helper
    fun wrapSelection(token: String) {
        pushUndoState(bodyState)
        val current = bodyState
        val text = current.text
        val start = current.selection.min.coerceIn(0, text.length)
        val end = current.selection.max.coerceIn(0, text.length)

        if (start != end) {
            val selected = text.substring(start, end)
            val wrapped = if (selected.startsWith(token) && selected.endsWith(token) && selected.length >= token.length * 2) {
                selected.removePrefix(token).removeSuffix(token)
            } else {
                "$token$selected$token"
            }
            val newText = text.replaceRange(start, end, wrapped)
            val newCursor = (start + wrapped.length).coerceIn(0, newText.length)
            bodyState = TextFieldValue(text = newText, selection = TextRange(newCursor))
        } else {
            var wordStart = (start - 1).coerceAtLeast(0)
            while (wordStart > 0 && !text[wordStart - 1].isWhitespace()) {
                wordStart--
            }
            var wordEnd = start
            while (wordEnd < text.length && !text[wordEnd].isWhitespace()) {
                wordEnd++
            }

            if (wordStart < wordEnd && !text[wordStart].isWhitespace()) {
                val word = text.substring(wordStart, wordEnd)
                val wrapped = if (word.startsWith(token) && word.endsWith(token) && word.length >= token.length * 2) {
                    word.removePrefix(token).removeSuffix(token)
                } else {
                    "$token$word$token"
                }
                val newText = text.replaceRange(wordStart, wordEnd, wrapped)
                val newCursor = (wordStart + wrapped.length).coerceIn(0, newText.length)
                bodyState = TextFieldValue(text = newText, selection = TextRange(newCursor))
            } else {
                val insert = "$token$token"
                val newText = text.replaceRange(start, start, insert)
                val newCursor = start + token.length
                bodyState = TextFieldValue(text = newText, selection = TextRange(newCursor))
            }
        }
        bodyFocusRequester.requestFocus()
    }

    val isKeyboardVisible = WindowInsets.isImeVisible

    CompositionLocalProvider(LocalTextSelectionColors provides appleTextSelectionColors) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(iOSBackground)
                .statusBarsPadding()
                .displayCutoutPadding()
                .imePadding()
        ) {
            // Apple Notes Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Yellow "< Notes" button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            performHaptic(HapticFeedbackType.TextHandleMove)
                            focusManager.clearFocus()
                            flushSave()
                            onBack()
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = iOSYellow,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Notes",
                        color = iOSYellow,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Right Button: "Done" (if editing) or "Edit" (if viewing)
                if (isEditing) {
                    TextButton(
                        onClick = {
                            performHaptic(HapticFeedbackType.TextHandleMove)
                            focusManager.clearFocus()
                            showFormatSheet = false
                            flushSave()
                            isEditing = false
                        }
                    ) {
                        Text(
                            text = "Done",
                            color = iOSYellow,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    var showInfoSheet by remember { mutableStateOf(false) }
                    var showShareMenu by remember { mutableStateOf(false) }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Share & Publish Actions
                        Box {
                            IconButton(
                                onClick = {
                                    performHaptic(HapticFeedbackType.TextHandleMove)
                                    showShareMenu = true
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share & Publish",
                                    tint = iOSYellow,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showShareMenu,
                                onDismissRequest = { showShareMenu = false },
                                modifier = Modifier
                                    .background(iOSCardBackground)
                                    .border(0.5.dp, iOSDivider, RoundedCornerShape(12.dp))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Share Text / Markdown", color = Color.White, fontSize = 14.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Share, contentDescription = null, tint = iOSYellow, modifier = Modifier.size(18.dp))
                                    },
                                    onClick = {
                                        showShareMenu = false
                                        performHaptic(HapticFeedbackType.TextHandleMove)
                                        val shareIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TITLE, currentNote.displayTitle)
                                            putExtra(Intent.EXTRA_SUBJECT, currentNote.displayTitle)
                                            val fullText = if (bodyState.text.isBlank()) currentNote.displayTitle else "${currentNote.displayTitle}\n\n${bodyState.text}"
                                            putExtra(Intent.EXTRA_TEXT, fullText)
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Note"))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export as Formatted PDF / Print", color = Color.White, fontSize = 14.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = iOSYellow, modifier = Modifier.size(18.dp))
                                    },
                                    onClick = {
                                        showShareMenu = false
                                        performHaptic(HapticFeedbackType.TextHandleMove)
                                        val activity = context as? Activity ?: (context as? ContextWrapper)?.baseContext as? Activity
                                        if (activity != null) {
                                            PdfPublisher.printOrSavePdf(activity, currentNote, File(vaultPath))
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export Standalone HTML", color = Color.White, fontSize = 14.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Code, contentDescription = null, tint = iOSYellow, modifier = Modifier.size(18.dp))
                                    },
                                    onClick = {
                                        showShareMenu = false
                                        performHaptic(HapticFeedbackType.TextHandleMove)
                                        coroutineScope.launch {
                                            val htmlFile = PdfPublisher.exportFormattedHtml(currentNote, File(vaultPath))
                                            if (htmlFile != null) {
                                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.fileprovider",
                                                    htmlFile
                                                )
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/html"
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Share Formatted HTML"))
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                performHaptic(HapticFeedbackType.TextHandleMove)
                                showInfoSheet = true
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Note Info",
                                tint = iOSYellow,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        if (onDelete != null && !isNewNote) {
                            var showDeleteConfirm by remember { mutableStateOf(false) }
                            IconButton(
                                onClick = {
                                    performHaptic(HapticFeedbackType.LongPress)
                                    showDeleteConfirm = true
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Note",
                                    tint = Color(0xFFFF453A),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            if (showDeleteConfirm) {
                                AlertDialog(
                                    onDismissRequest = { showDeleteConfirm = false },
                                    containerColor = iOSCardBackground,
                                    title = { Text("Delete Note?", color = Color.White) },
                                    text = { Text("Are you sure you want to delete this note?", color = iOSTextSecondary) },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                showDeleteConfirm = false
                                                onDelete(currentNote)
                                            }
                                        ) {
                                            Text("Delete", color = Color(0xFFFF453A), fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showDeleteConfirm = false }) {
                                            Text("Cancel", color = iOSYellow)
                                        }
                                    }
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                performHaptic(HapticFeedbackType.TextHandleMove)
                                isEditing = true
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Note",
                                tint = iOSYellow,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    if (showInfoSheet) {
                        val clipboardManager = LocalClipboardManager.current
                        var copied by remember { mutableStateOf(false) }
                        ModalBottomSheet(
                            onDismissRequest = { showInfoSheet = false },
                            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                            containerColor = iOSBackground,
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .padding(bottom = 32.dp)
                            ) {
                                Text(
                                    text = "NOTE INTEL",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = iOSTextSecondary,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(iOSCardBackground)
                                ) {
                                    Column {
                                        InfoRow(title = "Words", value = "${note.wordCount}")
                                        HorizontalDivider(modifier = Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = iOSDivider)
                                        InfoRow(title = "Characters", value = "${note.characterCount}")
                                        HorizontalDivider(modifier = Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = iOSDivider)
                                        InfoRow(title = "Reading Time", value = "${note.readingTimeMinutes} min")
                                        note.taskStats?.let { (done, total) ->
                                            HorizontalDivider(modifier = Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = iOSDivider)
                                            InfoRow(title = "Checklist Tasks", value = "$done of $total done")
                                        }
                                        HorizontalDivider(modifier = Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = iOSDivider)
                                        InfoRow(title = "Format", value = "Standard Markdown (.md)")
                                        HorizontalDivider(modifier = Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = iOSDivider)
                                        InfoRow(title = "Location", value = "Documents/MonolithNotes")
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        performHaptic(HapticFeedbackType.TextHandleMove)
                                        clipboardManager.setText(AnnotatedString("${titleState.text}\n\n${bodyState.text}".trim()))
                                        copied = true
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = iOSCardBackground)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = iOSYellow,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (copied) "Copied to Clipboard!" else "Copy Note as Markdown",
                                        color = if (copied) iOSEmerald else iOSYellow,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = {
                                        performHaptic(HapticFeedbackType.TextHandleMove)
                                        showInfoSheet = false
                                        val activity = context as? Activity ?: (context as? ContextWrapper)?.baseContext as? Activity
                                        if (activity != null) {
                                            PdfPublisher.printOrSavePdf(activity, currentNote, File(vaultPath))
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = iOSYellow)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = "PDF",
                                        tint = Color.Black,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Export as Formatted PDF / Print",
                                        color = Color.Black,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Main Content Area: Formatted View vs Fast Editor
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    // Apple Centered Timestamp & Live Stats
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = formattedDate,
                            style = MonolithTypography.labelSmall,
                            color = iOSTextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        if (settings.showWordCount) {
                            val liveWords = remember(bodyState.text) {
                                var count = 0
                                var inWord = false
                                val s = bodyState.text
                                for (i in 0 until s.length) {
                                    if (s[i].isWhitespace()) {
                                        inWord = false
                                    } else if (!inWord) {
                                        inWord = true
                                        count++
                                    }
                                }
                                count
                            }
                            val liveChars = remember(bodyState.text) { bodyState.text.length }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "$liveWords words • $liveChars characters",
                                style = MonolithTypography.labelSmall.copy(fontSize = 11.sp),
                                color = iOSTextTertiary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }

                    if (!isEditing) {
                        // 1. GORGEOUS FORMATTED VIEW (WYSIWYG: Real Checkboxes, Headings, Bold, Italic)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    isEditing = true
                                }
                        ) {
                            if (titleState.text.isNotBlank()) {
                                Text(
                                    text = titleState.text,
                                    style = MonolithTypography.displayMedium.copy(
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    ),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            SelectionContainer {
                                MarkdownRenderView(
                                    content = bodyState.text,
                                    vaultPath = vaultPath,
                                    onTaskToggled = { taskIndex ->
                                        val updated = toggleMarkdownTask(bodyState.text, taskIndex)
                                        bodyState = TextFieldValue(
                                            text = updated,
                                            selection = TextRange(updated.length)
                                        )
                                        flushSave()
                                    },
                                    onNavigateToNote = { targetTitle ->
                                        focusManager.clearFocus()
                                        flushSave()
                                        onNavigateToNote?.invoke(targetTitle)
                                    }
                                )
                            }
                        }
                    } else {
                        // 2. 0ms INSTANT LATENCY EDITING CANVAS
                        // Title
                        BasicTextField(
                            value = titleState,
                            onValueChange = { titleState = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(titleFocusRequester),
                            textStyle = TextStyle(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = SFProDisplay
                            ),
                            cursorBrush = SolidColor(theme.accent),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = {
                                    bodyFocusRequester.requestFocus()
                                    coroutineScope.launch {
                                        scrollState.scrollTo(0)
                                    }
                                }
                            ),
                            decorationBox = { innerTextField ->
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    if (titleState.text.isEmpty()) {
                                        Text(
                                            text = "Title",
                                            style = TextStyle(
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = iOSTextTertiary,
                                                fontFamily = SFProDisplay
                                            )
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Body (Zero regex lag on keystrokes, pure native 60fps typing)
                        BasicTextField(
                            value = bodyState,
                            onValueChange = { newValue ->
                                // FAST PATH: If text has not changed (selection dragging or cursor jump),
                                // apply immediately without running regexes or smart input logic!
                                if (newValue.text == bodyState.text) {
                                    bodyState = newValue
                                    return@BasicTextField
                                }

                                if (settings.autoContinueChecklists) {
                                    val handled = handleSmartInput(bodyState, newValue)
                                    if (kotlin.math.abs(handled.text.length - bodyState.text.length) > 1 || handled.text.endsWith('\n')) {
                                        pushUndoState(bodyState)
                                    }
                                    bodyState = handled
                                } else {
                                    if (kotlin.math.abs(newValue.text.length - bodyState.text.length) > 1 || newValue.text.endsWith('\n')) {
                                        pushUndoState(bodyState)
                                    }
                                    bodyState = newValue
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(bodyFocusRequester),
                            textStyle = TextStyle(
                                fontSize = settings.editorFontSize.sizeSp.sp,
                                fontWeight = FontWeight.Normal,
                                lineHeight = settings.editorFontSize.lineHeightSp.sp,
                                color = Color.White,
                                fontFamily = SFProText
                            ),
                            cursorBrush = SolidColor(theme.accent),
                            visualTransformation = remember(theme) {
                                AppleNotesVisualTransformation(
                                    accentColor = theme.accent,
                                    secondaryTextColor = theme.textSecondary
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Default
                            ),
                            decorationBox = { innerTextField ->
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    if (bodyState.text.isEmpty()) {
                                        Text(
                                            text = "Note",
                                            style = TextStyle(
                                                fontSize = 17.sp,
                                                lineHeight = 26.sp,
                                                color = iOSTextTertiary,
                                                fontFamily = SFProText
                                            )
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )

                        // Tappable empty canvas area that focuses the body and places cursor at the end
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 260.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    bodyFocusRequester.requestFocus()
                                    bodyState = bodyState.copy(selection = TextRange(bodyState.text.length))
                                }
                        )
                    }

                    // 3. Linked References (Backlinks) Section
                    val targetTitle = currentNote.displayTitle.trim()
                    val targetFileName = currentNote.file?.nameWithoutExtension?.trim() ?: ""
                    val backlinks = remember(targetTitle, targetFileName, allNotes) {
                        allNotes.filter { otherNote ->
                            otherNote.id != currentNote.id && otherNote.outlinks.any { link ->
                                link.equals(targetTitle, ignoreCase = true) ||
                                (targetFileName.isNotEmpty() && link.equals(targetFileName, ignoreCase = true))
                            }
                        }
                    }

                    if (backlinks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        LinkedReferencesSection(
                            backlinks = backlinks,
                            targetTitle = targetTitle,
                            onNavigateToNote = { clickedNote ->
                                focusManager.clearFocus()
                                flushSave()
                                onNavigateToNote?.invoke(clickedNote.displayTitle)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            // Intelligent Bottom Alignment:
            // - When Editing: Show format accessories wrapped in a solid theme surface extending 100% to the screen bottom edge.
            //   When keyboard is up, root imePadding elevates the bar above the soft keyboard (bottom inset = 0dp).
            //   When keyboard is closed, bottom inset provides comfortable clearance above system navigation buttons/pill.
            // - When Viewing: Add a spacer equal to navigationBars inset so the last lines of the note are never obscured behind 3-button nav.
            if (isEditing) {
                val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
                val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val safeBottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
                val isKeyboardUp = imeBottom > 0.dp || isKeyboardVisible
                val bottomInset = if (isKeyboardUp) 0.dp else maxOf(navBottom, safeBottom)

                Surface(
                    color = iOSCardBackground,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = bottomInset)
                    ) {
                        AnimatedVisibility(
                            visible = showFormatSheet,
                            enter = expandVertically(
                                animationSpec = spring(dampingRatio = 0.82f, stiffness = 580f),
                                expandFrom = Alignment.Bottom
                            ) + fadeIn(animationSpec = tween(120)),
                            exit = shrinkVertically(
                                animationSpec = spring(dampingRatio = 0.9f, stiffness = 650f),
                                shrinkTowards = Alignment.Bottom
                            ) + fadeOut(animationSpec = tween(95))
                        ) {
                            AppleFormatSheet(
                                onSetStyle = { prefix ->
                                    toggleLinePrefix(prefix)
                                },
                                onToggleInline = { token ->
                                    wrapSelection(token)
                                },
                                onToggleChecklist = {
                                    toggleLinePrefix("○ ", alternatePrefix = "✓ ")
                                },
                                onToggleBullet = {
                                    toggleLinePrefix("• ")
                                },
                                onToggleNumbered = {
                                    toggleLinePrefix("1. ")
                                },
                                onToggleQuote = {
                                    toggleLinePrefix("> ")
                                },
                                onInsertDivider = {
                                    pushUndoState(bodyState)
                                    val current = bodyState
                                    val cursor = current.selection.start
                                    val text = current.text
                                    val needsLeadingNewline = cursor > 0 && text[cursor - 1] != '\n'
                                    val separatorInsert = (if (needsLeadingNewline) "\n" else "") + "───\n"
                                    val newText = text.substring(0, cursor) + separatorInsert + text.substring(cursor)
                                    val newCursor = cursor + separatorInsert.length
                                    bodyState = TextFieldValue(text = newText, selection = TextRange(newCursor))
                                    bodyFocusRequester.requestFocus()
                                    showFormatSheet = false
                                },
                                onInsertTable = {
                                    pushUndoState(bodyState)
                                    val current = bodyState
                                    val cursor = current.selection.start
                                    val text = current.text
                                    val needsLeadingNewline = cursor > 0 && text[cursor - 1] != '\n'
                                    val tableTemplate = (if (needsLeadingNewline) "\n" else "") +
                                        "| Item | Category | Status |\n| :--- | :--- | :--- |\n| First | General | In progress |\n| Second | Details | Done |\n\n"
                                    val newText = text.substring(0, cursor) + tableTemplate + text.substring(cursor)
                                    val firstCellOffset = tableTemplate.indexOf("First")
                                    val newCursor = if (firstCellOffset != -1) cursor + firstCellOffset else cursor + tableTemplate.length
                                    bodyState = TextFieldValue(text = newText, selection = TextRange(newCursor, newCursor + 5))
                                    bodyFocusRequester.requestFocus()
                                    showFormatSheet = false
                                },
                                onInsertLink = {
                                    pushUndoState(bodyState)
                                    val current = bodyState
                                    val text = current.text
                                    val start = current.selection.min.coerceIn(0, text.length)
                                    val end = current.selection.max.coerceIn(0, text.length)
                                    if (start != end) {
                                        val selected = text.substring(start, end)
                                        val wrapped = "[$selected](https://)"
                                        val newText = text.replaceRange(start, end, wrapped)
                                        val urlCursor = start + selected.length + 3 + 8
                                        bodyState = TextFieldValue(text = newText, selection = TextRange(urlCursor))
                                    } else {
                                        val insert = "[link](https://)"
                                        val newText = text.replaceRange(start, start, insert)
                                        bodyState = TextFieldValue(text = newText, selection = TextRange(start + 1, start + 5))
                                    }
                                    bodyFocusRequester.requestFocus()
                                    showFormatSheet = false
                                },
                                onDismiss = {
                                    showFormatSheet = false
                                }
                            )
                        }

                        AnimatedVisibility(
                            visible = isEditing && wikiLinkMatch != null,
                            enter = expandVertically(
                                animationSpec = spring(dampingRatio = 0.82f, stiffness = 580f),
                                expandFrom = Alignment.Bottom
                            ) + fadeIn(animationSpec = tween(120)),
                            exit = shrinkVertically(
                                animationSpec = spring(dampingRatio = 0.9f, stiffness = 650f),
                                shrinkTowards = Alignment.Bottom
                            ) + fadeOut(animationSpec = tween(95))
                        ) {
                            if (wikiLinkMatch != null) {
                                WikiLinkAutocompleteBar(
                                    query = wikiLinkMatch.second,
                                    suggestions = wikiLinkSuggestions,
                                    onSelect = onSelectWikiLink
                                )
                            }
                        }

                        FormattingBar(
                            isFormatSheetOpen = showFormatSheet,
                            canUndo = canUndo,
                            canRedo = canRedo,
                            onUndo = onUndo,
                            onRedo = onRedo,
                            onToggleFormatSheet = {
                                showFormatSheet = !showFormatSheet
                            },
                            onToggleChecklist = {
                                toggleLinePrefix("○ ", alternatePrefix = "✓ ")
                            },
                            onToggleBullet = {
                                toggleLinePrefix("• ")
                            },
                            onToggleBold = {
                                wrapSelection("**")
                            },
                            onInsertWikiLink = {
                                pushUndoState(bodyState)
                                val current = bodyState
                                val text = current.text
                                val start = current.selection.min.coerceIn(0, text.length)
                                val end = current.selection.max.coerceIn(0, text.length)
                                if (start != end) {
                                    val selected = text.substring(start, end)
                                    val wrapped = "[[$selected]]"
                                    val newText = text.replaceRange(start, end, wrapped)
                                    val newCursor = (start + wrapped.length).coerceIn(0, newText.length)
                                    bodyState = TextFieldValue(text = newText, selection = TextRange(newCursor))
                                } else {
                                    val insert = "[[]]"
                                    val newText = text.replaceRange(start, start, insert)
                                    val newCursor = (start + 2).coerceIn(0, newText.length)
                                    bodyState = TextFieldValue(text = newText, selection = TextRange(newCursor))
                                }
                                bodyFocusRequester.requestFocus()
                            },
                            onInsertTag = {
                                pushUndoState(bodyState)
                                val current = bodyState
                                val text = current.text
                                val start = current.selection.min.coerceIn(0, text.length)
                                val end = current.selection.max.coerceIn(0, text.length)

                                if (start != end) {
                                    val selected = text.substring(start, end)
                                    val toggled = if (selected.startsWith("#")) selected.removePrefix("#") else "#$selected"
                                    val newText = text.replaceRange(start, end, toggled)
                                    val newCursor = (start + toggled.length).coerceIn(0, newText.length)
                                    bodyState = TextFieldValue(text = newText, selection = TextRange(newCursor))
                                } else {
                                    var wordStart = (start - 1).coerceAtLeast(0)
                                    while (wordStart > 0 && !text[wordStart - 1].isWhitespace()) {
                                        wordStart--
                                    }
                                    var wordEnd = start
                                    while (wordEnd < text.length && !text[wordEnd].isWhitespace()) {
                                        wordEnd++
                                    }

                                    if (wordStart < wordEnd && !text[wordStart].isWhitespace()) {
                                        val word = text.substring(wordStart, wordEnd)
                                        val toggled = if (word.startsWith("#")) word.removePrefix("#") else "#$word"
                                        val newText = text.replaceRange(wordStart, wordEnd, toggled)
                                        val newCursor = (wordStart + toggled.length).coerceIn(0, newText.length)
                                        bodyState = TextFieldValue(text = newText, selection = TextRange(newCursor))
                                    } else {
                                        val newText = text.replaceRange(start, start, "#")
                                        val newCursor = (start + 1).coerceIn(0, newText.length)
                                        bodyState = TextFieldValue(text = newText, selection = TextRange(newCursor))
                                    }
                                }
                                bodyFocusRequester.requestFocus()
                            },
                            onAttachImage = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onDismissKeyboard = {
                                focusManager.clearFocus()
                                showFormatSheet = false
                                isEditing = false
                            }
                        )
                    }
                }
            } else {
                val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val safeBottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
                Spacer(modifier = Modifier.height(maxOf(navBottom, safeBottom, 24.dp)))
            }
        }
    }
}

/**
 * Official Apple Notes list continuation on Enter:
 * - If line starts with "○ " and has content -> adds "○ " to new line
 * - If line is just "○ " (empty) -> deletes "○ " and reverts to plain text
 * - Same for "• " and numbered lists ("1. " -> "2. ")
 */
private fun handleSmartListContinuation(
    oldValue: TextFieldValue,
    newValue: TextFieldValue
): TextFieldValue {
    val oldText = oldValue.text
    val newText = newValue.text

    if (newText.length == oldText.length + 1 &&
        newValue.selection.start == oldValue.selection.start + 1 &&
        newValue.selection.collapsed
    ) {
        val insertPos = oldValue.selection.start
        if (insertPos < newText.length && newText[insertPos] == '\n') {
            val prevLineEnd = insertPos
            val prevLineStart = newText.lastIndexOf('\n', (prevLineEnd - 1).coerceAtLeast(0)).let {
                if (it == -1) 0 else it + 1
            }
            val prevLine = newText.substring(prevLineStart, prevLineEnd)

            // 0. Horizontal Rule on Enter: "---", "***", "___", "===", or long lines
            val separatorRegex = "^(-{3,}|\\*{3,}|_{3,}|─{3,}|={3,}|(-\\s*){3,}|(\\*\\s*){3,}|(_\\s*){3,})$".toRegex()
            if (prevLine.matches(separatorRegex)) {
                val resultText = newText.substring(0, prevLineStart) + "───\n" + newText.substring(insertPos + 1)
                val newCursor = prevLineStart + 4
                return TextFieldValue(text = resultText, selection = TextRange(newCursor))
            }

            // 0b. Markdown Table Row Continuation on Enter:
            if (prevLine.trim().startsWith('|') && prevLine.trim().endsWith('|')) {
                val cells = com.monolith.notes.ui.components.splitTableRow(prevLine)
                val isAllBlank = cells.all { it.isBlank() }
                if (isAllBlank) {
                    val resultText = newText.removeRange(prevLineStart, prevLineEnd)
                    val newCursor = prevLineStart
                    return TextFieldValue(
                        text = resultText,
                        selection = TextRange(newCursor.coerceIn(0, resultText.length))
                    )
                } else if (com.monolith.notes.ui.components.isTableDelimiter(prevLine)) {
                    val colCount = cells.size.coerceAtLeast(2)
                    val newRow = List(colCount) { " " }.joinToString(" | ", prefix = "| ", postfix = " |")
                    val resultText = newText.substring(0, insertPos + 1) + newRow + newText.substring(insertPos + 1)
                    val newCursor = insertPos + 1 + 2
                    return TextFieldValue(
                        text = resultText,
                        selection = TextRange(newCursor.coerceIn(0, resultText.length))
                    )
                } else {
                    val colCount = cells.size.coerceAtLeast(1)
                    val newRow = List(colCount) { " " }.joinToString(" | ", prefix = "| ", postfix = " |")
                    val resultText = newText.substring(0, insertPos + 1) + newRow + newText.substring(insertPos + 1)
                    val newCursor = insertPos + 1 + 2
                    return TextFieldValue(
                        text = resultText,
                        selection = TextRange(newCursor.coerceIn(0, resultText.length))
                    )
                }
            }

            // 1. Checklist item: "○ " or "✓ "
            if (prevLine.startsWith("○ ") || prevLine.startsWith("✓ ")) {
                val itemContent = prevLine.substring(2)
                if (itemContent.isBlank()) {
                    val resultText = newText.removeRange(prevLineStart, prevLineEnd)
                    val newCursor = prevLineStart
                    return TextFieldValue(
                        text = resultText,
                        selection = TextRange(newCursor.coerceIn(0, resultText.length))
                    )
                } else {
                    val prefix = "○ "
                    val resultText = newText.substring(0, insertPos + 1) + prefix + newText.substring(insertPos + 1)
                    val newCursor = insertPos + 1 + prefix.length
                    return TextFieldValue(
                        text = resultText,
                        selection = TextRange(newCursor)
                    )
                }
            }

            // 2. Bullet list item: "• "
            if (prevLine.startsWith("• ")) {
                val itemContent = prevLine.substring(2)
                if (itemContent.isBlank()) {
                    val resultText = newText.removeRange(prevLineStart, prevLineEnd)
                    val newCursor = prevLineStart
                    return TextFieldValue(
                        text = resultText,
                        selection = TextRange(newCursor.coerceIn(0, resultText.length))
                    )
                } else {
                    val prefix = "• "
                    val resultText = newText.substring(0, insertPos + 1) + prefix + newText.substring(insertPos + 1)
                    val newCursor = insertPos + 1 + prefix.length
                    return TextFieldValue(
                        text = resultText,
                        selection = TextRange(newCursor)
                    )
                }
            }

            // 3. Numbered list item: "1. ", "2. ", etc.
            val numMatch = "^(\\d+)\\.\\s*(.*)$".toRegex().find(prevLine)
            if (numMatch != null) {
                val num = numMatch.groupValues[1].toIntOrNull() ?: 1
                val itemContent = numMatch.groupValues[2]
                if (itemContent.isBlank()) {
                    val resultText = newText.removeRange(prevLineStart, prevLineEnd)
                    val newCursor = prevLineStart
                    return TextFieldValue(
                        text = resultText,
                        selection = TextRange(newCursor.coerceIn(0, resultText.length))
                    )
                } else {
                    val prefix = "${num + 1}. "
                    val resultText = newText.substring(0, insertPos + 1) + prefix + newText.substring(insertPos + 1)
                    val newCursor = insertPos + 1 + prefix.length
                    return TextFieldValue(
                        text = resultText,
                        selection = TextRange(newCursor)
                    )
                }
            }
        }
    }

    return newValue
}

/**
 * Intelligent Markdown Input Dispatcher:
 * - Enter: runs handleSmartListContinuation
 * - Space: converts [] or () or - [ ] -> ○, * or - -> •, --- -> horizontal rule
 */
private fun handleSmartInput(
    oldValue: TextFieldValue,
    newValue: TextFieldValue
): TextFieldValue {
    val oldText = oldValue.text
    val newText = newValue.text

    if (newText.length == oldText.length + 1 &&
        newValue.selection.start == oldValue.selection.start + 1 &&
        newValue.selection.collapsed
    ) {
        val insertPos = oldValue.selection.start
        val charInserted = newText[insertPos]

        if (charInserted == '\n') {
            return handleSmartListContinuation(oldValue, newValue)
        }

        if (charInserted == '-' || charInserted == '*' || charInserted == '_' || charInserted == '=') {
            val lineStart = newText.lastIndexOf('\n', (insertPos - 1).coerceAtLeast(0)).let {
                if (it == -1) 0 else it + 1
            }
            val textBeforeCursor = newText.substring(lineStart, insertPos + 1)
            if (textBeforeCursor == "---" || textBeforeCursor == "***" || textBeforeCursor == "___" || textBeforeCursor == "===") {
                val lineRest = newText.substring(insertPos + 1)
                val replacement = "───\n"
                val resultText = newText.substring(0, lineStart) + replacement + lineRest
                val newCursor = lineStart + replacement.length
                return TextFieldValue(text = resultText, selection = TextRange(newCursor))
            }
        }

        if (charInserted == ' ') {
            val lineStart = newText.lastIndexOf('\n', (insertPos - 1).coerceAtLeast(0)).let {
                if (it == -1) 0 else it + 1
            }
            val textBeforeCursor = newText.substring(lineStart, insertPos + 1)

            val replacement = when (textBeforeCursor) {
                "[] ", "() ", "- [ ] " -> "○ "
                "* " -> "• "
                "- " -> "• "
                "--- ", "*** ", "___ ", "=== " -> "───\n"
                else -> null
            }

            if (replacement != null) {
                val lineRest = newText.substring(insertPos + 1)
                val resultText = newText.substring(0, lineStart) + replacement + lineRest
                val newCursor = lineStart + replacement.length
                return TextFieldValue(text = resultText, selection = TextRange(newCursor))
            }
        }
    }

    return newValue
}

@Composable
private fun InfoRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = iOSTextPrimary, fontSize = 16.sp)
        Text(text = value, color = iOSTextSecondary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

fun detectWikiLinkQuery(text: String, cursor: Int): Pair<Int, String>? {
    if (cursor < 2) return null
    val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
    val lineUpToCursor = text.substring(lineStart, cursor)
    val openIndexInLine = lineUpToCursor.lastIndexOf("[[")
    if (openIndexInLine == -1) return null
    val afterOpen = lineUpToCursor.substring(openIndexInLine + 2)
    if (afterOpen.contains("]]") || afterOpen.contains("|")) return null
    val absoluteOpenIndex = lineStart + openIndexInLine
    return Pair(absoluteOpenIndex, afterOpen)
}

@Composable
private fun WikiLinkAutocompleteBar(
    query: String,
    suggestions: List<Note>,
    onSelect: (String) -> Unit
) {
    Surface(
        color = iOSBackground,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = iOSYellow,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (query.isBlank()) "LINK TO NOTE" else "LINK TO: \"$query\"",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = iOSTextSecondary
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            ) {
                val trimmedQuery = query.trim()
                val exactMatch = suggestions.any { it.displayTitle.equals(trimmedQuery, ignoreCase = true) }

                if (trimmedQuery.isNotEmpty() && !exactMatch) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = iOSYellow.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, iOSYellow.copy(alpha = 0.6f)),
                            modifier = Modifier.clickable {
                                onSelect(trimmedQuery)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = iOSYellow,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Create \"$trimmedQuery\"",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = iOSYellow
                                )
                            }
                        }
                    }
                }

                items(suggestions, key = { it.id }) { targetNote ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = iOSCardBackground,
                        border = BorderStroke(1.dp, iOSDivider),
                        modifier = Modifier.clickable {
                            onSelect(targetNote.displayTitle)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = targetNote.displayTitle,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            if (targetNote.folderName != "Notes") {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = targetNote.folderName,
                                    fontSize = 10.sp,
                                    color = iOSTextTertiary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkedReferencesSection(
    backlinks: List<Note>,
    targetTitle: String,
    onNavigateToNote: (Note) -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(iOSCardBackground)
            .border(1.dp, iOSDivider, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = iOSYellow,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Linked References",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(iOSYellow.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${backlinks.size}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = iOSYellow
                    )
                }
            }

            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = iOSTextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                backlinks.forEach { backlinkNote ->
                    BacklinkItemCard(
                        note = backlinkNote,
                        targetTitle = targetTitle,
                        onClick = { onNavigateToNote(backlinkNote) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BacklinkItemCard(
    note: Note,
    targetTitle: String,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = iOSBackground,
        border = BorderStroke(0.5.dp, iOSDivider),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = note.displayTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                if (note.folderName != "Notes") {
                    Text(
                        text = note.folderName,
                        fontSize = 11.sp,
                        color = iOSYellow,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(iOSYellow.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }

            val snippet = remember(note.content, targetTitle) {
                var foundSnippet = ""
                for (line in note.content.lineSequence()) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) continue
                    val lower = trimmed.lowercase()
                    if (lower.contains("[[${targetTitle.lowercase()}")) {
                        foundSnippet = trimmed.removePrefix("#").removePrefix("○").removePrefix("✓").removePrefix("•").trim()
                        break
                    }
                }
                if (foundSnippet.isEmpty()) note.previewSnippet else foundSnippet
            }

            if (snippet.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = snippet,
                    fontSize = 12.sp,
                    color = iOSTextSecondary,
                    maxLines = 2,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
