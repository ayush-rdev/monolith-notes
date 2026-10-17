package com.monolith.notes

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.monolith.notes.data.NoteRepository
import com.monolith.notes.data.SettingsManager
import com.monolith.notes.data.sync.GoogleDriveSyncEngine
import com.monolith.notes.data.sync.MonolithCloudSyncEngine
import com.monolith.notes.data.sync.SyncResult
import com.monolith.notes.data.sync.SyncState
import com.monolith.notes.data.sync.WebDavSyncEngine
import com.monolith.notes.model.Note
import com.monolith.notes.ui.editor.NoteEditorScreen
import com.monolith.notes.ui.folders.FoldersScreen
import com.monolith.notes.ui.graph.KnowledgeGraphScreen
import com.monolith.notes.ui.list.NotesListScreen
import com.monolith.notes.ui.trash.TrashScreen
import com.monolith.notes.ui.theme.*
import com.monolith.notes.widget.QuickCaptureWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class Screen {
    data object Folders : Screen()
    data object List : Screen()
    data class Editor(val note: Note) : Screen()
    data object Trash : Screen()
    data object Graph : Screen()

    fun depth(): Int = when (this) {
        Folders -> 0
        List -> 1
        Trash -> 2
        Graph -> 2
        is Editor -> 3
    }
}

class MainActivity : FragmentActivity() {

    private lateinit var repository: NoteRepository
    private lateinit var settingsManager: SettingsManager
    private var isAppUnlocked by mutableStateOf(false)
    private var pendingWidgetIntent by mutableStateOf<Intent?>(null)
    var syncState by mutableStateOf(SyncState.IDLE)
        private set
    private var syncJob: kotlinx.coroutines.Job? = null

    fun triggerAutoSync(debounceMs: Long = 0L) {
        val settings = settingsManager.settings.value
        val isGoogleConnected = settings.googleDriveConfig.isConnected
        val isCloudConnected = settings.cloudConfig.isLoggedIn
        val isWebDavConfigured = settings.webDavConfig.isConfigured

        if (!isGoogleConnected && !isCloudConnected && !isWebDavConfigured) {
            return
        }

        syncJob?.cancel()
        syncJob = lifecycleScope.launch(Dispatchers.IO) {
            if (debounceMs > 0) {
                kotlinx.coroutines.delay(debounceMs)
            }
            withContext(Dispatchers.Main) {
                syncState = SyncState.SYNCING
            }

            var anySuccess = false
            var anyChanges = false

            // 1. Google Drive Sync
            if (isGoogleConnected) {
                try {
                    val res = GoogleDriveSyncEngine.sync(this@MainActivity, repository.notesDir, settings.googleDriveConfig)
                    if (res.isSuccess) {
                        anySuccess = true
                        settingsManager.updateGoogleDriveLastSync(System.currentTimeMillis())
                        if (res.downloadedCount > 0 || res.uploadedCount > 0) {
                            anyChanges = true
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 2. Monolith Cloud Sync
            if (isCloudConnected) {
                try {
                    val res = MonolithCloudSyncEngine.sync(repository.notesDir, settings.cloudConfig)
                    if (res.isSuccess) {
                        anySuccess = true
                        settingsManager.updateCloudLastSync(System.currentTimeMillis())
                        if (res.downloadedCount > 0 || res.uploadedCount > 0) {
                            anyChanges = true
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 3. WebDAV Sync
            if (isWebDavConfigured) {
                try {
                    val res = WebDavSyncEngine.sync(repository.notesDir, settings.webDavConfig)
                    if (res.isSuccess) {
                        anySuccess = true
                        settingsManager.setLastSyncTimestamp(System.currentTimeMillis())
                        if (res.downloadedCount > 0 || res.uploadedCount > 0) {
                            anyChanges = true
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (anyChanges) {
                repository.loadNotes()
                repository.loadTrashNotes()
            }

            withContext(Dispatchers.Main) {
                syncState = if (anySuccess) SyncState.SUCCESS else SyncState.ERROR
            }
            kotlinx.coroutines.delay(2500)
            withContext(Dispatchers.Main) {
                if (syncState != SyncState.SYNCING) {
                    syncState = SyncState.IDLE
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingWidgetIntent = intent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        repository = NoteRepository(applicationContext)
        settingsManager = SettingsManager(applicationContext)
        pendingWidgetIntent = intent

        // If biometric lock is disabled, unlock immediately
        isAppUnlocked = !settingsManager.settings.value.biometricLockEnabled

        // Request MANAGE_EXTERNAL_STORAGE for seamless Obsidian vault access if needed
        checkStoragePermissions()

        setContent {
            val settings by settingsManager.settings.collectAsState()
            MonolithTheme(theme = settings.theme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = settings.theme.background
                ) {
                    val scope = rememberCoroutineScope()
                    var currentScreen by remember { mutableStateOf<Screen>(Screen.List) }
                    var selectedFolder by remember { mutableStateOf<String?>(null) }
                    val editorBackStack = remember { mutableStateListOf<Note>() }
                    var showGoogleCloudSetupDialog by remember { mutableStateOf(false) }

                    val googleSignInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
                    ) { result ->
                        val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
                        try {
                            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                            val email = account.email ?: ""
                            val displayName = account.displayName ?: ""
                            val updatedConfig = settings.googleDriveConfig.copy(
                                email = email,
                                displayName = displayName
                            )
                            settingsManager.setGoogleDriveConfig(updatedConfig)
                            android.widget.Toast.makeText(this@MainActivity, "Connected to Google Drive: $email", android.widget.Toast.LENGTH_SHORT).show()
                            triggerAutoSync(debounceMs = 0L)
                        } catch (e: com.google.android.gms.common.api.ApiException) {
                            val code = e.statusCode
                            if (code == 10) {
                                showGoogleCloudSetupDialog = true
                            } else if (code != 12501) {
                                android.widget.Toast.makeText(this@MainActivity, "Google Sign-In ($code): ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }

                    BackHandler(enabled = currentScreen != Screen.List) {
                        when (currentScreen) {
                            is Screen.Editor -> {
                                triggerAutoSync(debounceMs = 0L)
                                if (editorBackStack.isNotEmpty()) {
                                    val prevNote = editorBackStack.removeLast()
                                    val freshNote = repository.notes.value.firstOrNull { it.id == prevNote.id } ?: prevNote
                                    currentScreen = Screen.Editor(freshNote)
                                } else {
                                    currentScreen = Screen.List
                                }
                            }
                            is Screen.Graph, is Screen.Folders, is Screen.Trash -> {
                                currentScreen = Screen.List
                            }
                            is Screen.List -> { /* no-op */ }
                        }
                    }

                    // Sync sort order, load notes, and execute background auto-sync on launch
                    LaunchedEffect(Unit) {
                        repository.setSortOrder(settings.sortOrder)
                        repository.loadNotes()
                        repository.loadTrashNotes()
                        triggerAutoSync(debounceMs = 400L)
                    }

                    // Process incoming Intent capture triggers (Widget, Quick Note Tile, Share Target)
                    LaunchedEffect(pendingWidgetIntent) {
                        val currentIntent = pendingWidgetIntent
                        if (currentIntent != null) {
                            val widgetAction = currentIntent.getStringExtra("WIDGET_ACTION")
                            val intentAction = currentIntent.action

                            when {
                                widgetAction == "NEW_NOTE" || intentAction == "com.monolith.notes.QUICK_NOTE" -> {
                                    editorBackStack.clear()
                                    val newNote = Note(
                                        title = "",
                                        content = "",
                                        lastModified = System.currentTimeMillis(),
                                        folder = selectedFolder ?: "Notes"
                                    )
                                    currentScreen = Screen.Editor(newNote)
                                }
                                widgetAction == "NEW_CHECKLIST" -> {
                                    editorBackStack.clear()
                                    val newNote = Note(
                                        title = "",
                                        content = "○ ",
                                        lastModified = System.currentTimeMillis(),
                                        folder = selectedFolder ?: "Notes"
                                    )
                                    currentScreen = Screen.Editor(newNote)
                                }
                                widgetAction == "OPEN_NOTE" -> {
                                    editorBackStack.clear()
                                    val notePath = currentIntent.getStringExtra(QuickCaptureWidgetProvider.EXTRA_NOTE_PATH)
                                    if (notePath != null) {
                                        val targetFile = File(notePath)
                                        if (targetFile.exists()) {
                                            val raw = try { targetFile.readText() } catch (_: Exception) { "" }
                                            val (title, body, isPinned) = com.monolith.notes.data.MarkdownConverter.toUi(raw, targetFile.nameWithoutExtension)
                                            val note = Note(
                                                id = targetFile.absolutePath,
                                                file = targetFile,
                                                title = title,
                                                content = body,
                                                lastModified = targetFile.lastModified(),
                                                isPinned = isPinned
                                            )
                                            currentScreen = Screen.Editor(note)
                                        }
                                    }
                                }
                                intentAction == Intent.ACTION_SEND -> {
                                    editorBackStack.clear()
                                    val sharedSubject = currentIntent.getStringExtra(Intent.EXTRA_SUBJECT)
                                        ?: currentIntent.getStringExtra(Intent.EXTRA_TITLE)
                                    val sharedText = currentIntent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                                    val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        currentIntent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                                    } else {
                                        @Suppress("DEPRECATION")
                                        currentIntent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                                    }

                                    if (streamUri != null) {
                                        scope.launch {
                                            val relativePath = repository.saveAttachment(streamUri)
                                            val bodyContent = if (relativePath != null) {
                                                if (sharedText.isNotBlank()) "$sharedText\n\n![Shared Image]($relativePath)"
                                                else "![Shared Image]($relativePath)"
                                            } else {
                                                sharedText
                                            }
                                            val title = sharedSubject?.trim() ?: "Shared Image"
                                            val newNote = Note(
                                                title = title,
                                                content = bodyContent,
                                                lastModified = System.currentTimeMillis(),
                                                folder = selectedFolder ?: "Notes"
                                            )
                                            val saved = repository.saveNote(newNote)
                                            currentScreen = Screen.Editor(saved)
                                        }
                                    } else if (sharedText.isNotBlank() || !sharedSubject.isNullOrBlank()) {
                                        val cleanSubject = sharedSubject?.trim()
                                        val cleanText = sharedText.trim()
                                        val derivedTitle = when {
                                            !cleanSubject.isNullOrBlank() -> cleanSubject
                                            cleanText.startsWith("http://") || cleanText.startsWith("https://") -> "Shared Link"
                                            cleanText.contains('\n') -> cleanText.substringBefore('\n').take(30).trim()
                                            else -> cleanText.take(30)
                                        }
                                        val newNote = Note(
                                            title = derivedTitle,
                                            content = cleanText,
                                            lastModified = System.currentTimeMillis(),
                                            folder = selectedFolder ?: "Notes"
                                        )
                                        scope.launch {
                                            val saved = repository.saveNote(newNote)
                                            currentScreen = Screen.Editor(saved)
                                        }
                                    }
                                }
                            }
                            pendingWidgetIntent = null
                        }
                    }

                    // Biometric Lock Screen Overlay
                    if (settings.biometricLockEnabled && !isAppUnlocked) {
                        LaunchedEffect(Unit) {
                            promptBiometricAuth(
                                title = "Unlock Monolith Notes",
                                subtitle = "Authenticate to access your private vault",
                                onSuccess = { isAppUnlocked = true }
                            )
                        }

                        BiometricLockScreen(
                            onUnlockClick = {
                                promptBiometricAuth(
                                    title = "Unlock Monolith Notes",
                                    subtitle = "Authenticate to access your private vault",
                                    onSuccess = { isAppUnlocked = true }
                                )
                            }
                        )
                    } else {
                        // iOS UIKit fluid spring curve for page transitions (Apple 30% parallax slide, ProMotion 120Hz)
                        val iosSpringCurve = remember { CubicBezierEasing(0.20f, 0.0f, 0.0f, 1.0f) }

                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                val forward = targetState.depth() >= initialState.depth()
                                if (forward) {
                                    (slideInHorizontally(
                                        initialOffsetX = { fullWidth -> fullWidth },
                                        animationSpec = tween(durationMillis = 280, easing = iosSpringCurve)
                                    ) + fadeIn(animationSpec = tween(durationMillis = 160))).togetherWith(
                                        slideOutHorizontally(
                                            targetOffsetX = { fullWidth -> -(fullWidth * 0.30f).toInt() },
                                            animationSpec = tween(durationMillis = 280, easing = iosSpringCurve)
                                        ) + fadeOut(animationSpec = tween(durationMillis = 160))
                                    ).using(SizeTransform(clip = false))
                                } else {
                                    (slideInHorizontally(
                                        initialOffsetX = { fullWidth -> -(fullWidth * 0.30f).toInt() },
                                        animationSpec = tween(durationMillis = 280, easing = iosSpringCurve)
                                    ) + fadeIn(animationSpec = tween(durationMillis = 160))).togetherWith(
                                        slideOutHorizontally(
                                            targetOffsetX = { fullWidth -> fullWidth },
                                            animationSpec = tween(durationMillis = 280, easing = iosSpringCurve)
                                        ) + fadeOut(animationSpec = tween(durationMillis = 160))
                                    ).using(SizeTransform(clip = false))
                                }
                            },
                            label = "ScreenTransition"
                        ) { screen ->
                            when (screen) {
                                is Screen.Folders -> {
                                    val notes by repository.notes.collectAsState()
                                    val folders by repository.folders.collectAsState()
                                    val trashNotes by repository.trashNotes.collectAsState()

                                    FoldersScreen(
                                        notes = notes,
                                        folders = folders,
                                        trashCount = trashNotes.size,
                                        selectedFolder = selectedFolder,
                                        onSelectFolder = { folder ->
                                            selectedFolder = folder
                                            currentScreen = Screen.List
                                        },
                                        onOpenTrash = {
                                            currentScreen = Screen.Trash
                                        },
                                        onCreateFolder = { folderName ->
                                            scope.launch {
                                                repository.createFolder(folderName)
                                            }
                                        },
                                        onDeleteFolder = { folderName ->
                                            scope.launch {
                                                repository.deleteFolder(folderName)
                                                if (selectedFolder == folderName) {
                                                    selectedFolder = null
                                                }
                                            }
                                        },
                                        onNewNoteClick = {
                                            editorBackStack.clear()
                                            val newNote = Note(
                                                title = "",
                                                content = "",
                                                lastModified = System.currentTimeMillis(),
                                                folder = selectedFolder ?: "Notes"
                                            )
                                            currentScreen = Screen.Editor(newNote)
                                        },
                                        onBackToNotes = {
                                            currentScreen = Screen.List
                                        }
                                    )
                                }
                                is Screen.List -> {
                                    val notes by repository.notes.collectAsState()
                                    val folders by repository.folders.collectAsState()
                                    val trashNotes by repository.trashNotes.collectAsState()
                                    val isLoaded by repository.isLoaded.collectAsState()

                                    NotesListScreen(
                                        notes = notes,
                                        isLoaded = isLoaded,
                                        vaultPath = repository.notesDir.absolutePath,
                                        settings = settings,
                                        currentFolder = selectedFolder,
                                        folders = folders,
                                        trashNotesCount = trashNotes.size,
                                        onOpenFolders = {
                                            currentScreen = Screen.Folders
                                        },
                                        onOpenGraph = {
                                            currentScreen = Screen.Graph
                                        },
                                        onOpenTrash = {
                                            currentScreen = Screen.Trash
                                        },
                                        onMoveNoteToFolder = { note, targetFolder ->
                                            scope.launch {
                                                repository.moveNoteToFolder(note, targetFolder)
                                                triggerAutoSync(debounceMs = 0L)
                                            }
                                        },
                                        onUpdateBiometricLock = { enabled ->
                                            if (enabled) {
                                                promptBiometricAuth(
                                                    title = "Enable Biometric Lock",
                                                    subtitle = "Confirm identity to enable lock",
                                                    onSuccess = {
                                                        settingsManager.setBiometricLockEnabled(true)
                                                        isAppUnlocked = true
                                                    }
                                                )
                                            } else {
                                                promptBiometricAuth(
                                                    title = "Disable Biometric Lock",
                                                    subtitle = "Confirm identity to disable lock",
                                                    onSuccess = {
                                                        settingsManager.setBiometricLockEnabled(false)
                                                        isAppUnlocked = true
                                                    }
                                                )
                                            }
                                        },
                                        onUpdateHaptics = { settingsManager.setHapticsEnabled(it) },
                                        onUpdateStartStyle = { settingsManager.setNewNoteStartStyle(it) },
                                        onUpdateFontSize = { settingsManager.setEditorFontSize(it) },
                                        onUpdateShowWordCount = { settingsManager.setShowWordCount(it) },
                                        onUpdateAutoChecklists = { settingsManager.setAutoContinueChecklists(it) },
                                        onSortOrderChange = { order ->
                                            settingsManager.setSortOrder(order)
                                            scope.launch {
                                                repository.setSortOrder(order)
                                            }
                                        },
                                        onUpdateTheme = { settingsManager.setTheme(it) },
                                        onNoteClick = { note ->
                                            editorBackStack.clear()
                                            currentScreen = Screen.Editor(note)
                                        },
                                        onNewNoteClick = {
                                            editorBackStack.clear()
                                            val newNote = Note(
                                                title = "",
                                                content = "",
                                                lastModified = System.currentTimeMillis(),
                                                folder = selectedFolder ?: "Notes"
                                            )
                                            currentScreen = Screen.Editor(newNote)
                                        },
                                        onTogglePin = { note ->
                                            scope.launch {
                                                repository.togglePin(note)
                                                triggerAutoSync(debounceMs = 0L)
                                            }
                                        },
                                        onDeleteNote = { note ->
                                            scope.launch {
                                                repository.deleteNote(note)
                                                triggerAutoSync(debounceMs = 0L)
                                            }
                                        },
                                        onDuplicateNote = { note ->
                                            scope.launch {
                                                val duplicated = repository.duplicateNote(note)
                                                triggerAutoSync(debounceMs = 0L)
                                                currentScreen = Screen.Editor(duplicated)
                                            }
                                        },
                                        onExportNotes = {
                                            scope.launch {
                                                val zip = repository.exportVaultToZip()
                                                withContext(Dispatchers.Main) {
                                                    if (zip != null) {
                                                        android.widget.Toast.makeText(
                                                            this@MainActivity,
                                                            "Vault exported: ${zip.name}",
                                                            android.widget.Toast.LENGTH_LONG
                                                        ).show()
                                                    } else {
                                                        android.widget.Toast.makeText(
                                                            this@MainActivity,
                                                            "Export failed",
                                                            android.widget.Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                            }
                                        },
                                        onUpdateCloudConfig = { config ->
                                            settingsManager.setCloudConfig(config)
                                        },
                                        onPerformCloudSync = {
                                            val result = MonolithCloudSyncEngine.sync(repository.notesDir, settings.cloudConfig)
                                            if (result.isSuccess) {
                                                settingsManager.updateCloudLastSync(System.currentTimeMillis())
                                                repository.loadNotes()
                                                repository.loadTrashNotes()
                                            }
                                            result
                                        },
                                        onConnectGoogleDrive = {
                                            val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                                                .requestEmail()
                                                .requestScopes(com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.appdata"))
                                                .build()
                                            val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(this@MainActivity, gso)
                                            googleSignInLauncher.launch(client.signInIntent)
                                        },
                                        onDisconnectGoogleDrive = {
                                            val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                                            com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(this@MainActivity, gso).signOut().addOnCompleteListener {
                                                settingsManager.setGoogleDriveConfig(com.monolith.notes.data.GoogleDriveConfig())
                                                android.widget.Toast.makeText(this@MainActivity, "Disconnected from Google Drive", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onUpdateGoogleDriveConfig = { config ->
                                            settingsManager.setGoogleDriveConfig(config)
                                        },
                                        onPerformGoogleDriveSync = {
                                            val result = GoogleDriveSyncEngine.sync(this@MainActivity, repository.notesDir, settings.googleDriveConfig)
                                            if (result.isSuccess) {
                                                settingsManager.updateGoogleDriveLastSync(System.currentTimeMillis())
                                                repository.loadNotes()
                                                repository.loadTrashNotes()
                                            }
                                            result
                                        },
                                        onUpdateWebDavConfig = { config ->
                                            settingsManager.setWebDavConfig(config)
                                        },
                                        onPerformSync = {
                                            val result = WebDavSyncEngine.sync(repository.notesDir, settings.webDavConfig)
                                            if (result.isSuccess) {
                                                settingsManager.setLastSyncTimestamp(System.currentTimeMillis())
                                                repository.loadNotes()
                                                repository.loadTrashNotes()
                                            }
                                            result
                                        },
                                        syncState = syncState,
                                        onTriggerSync = { triggerAutoSync(debounceMs = 0L) }
                                    )
                                }
                                is Screen.Editor -> {
                                    val notes by repository.notes.collectAsState()
                                    NoteEditorScreen(
                                        note = screen.note,
                                        allNotes = notes,
                                        vaultPath = repository.notesDir.absolutePath,
                                        settings = settings,
                                        onBack = {
                                            triggerAutoSync(debounceMs = 0L)
                                            if (editorBackStack.isNotEmpty()) {
                                                val prevNote = editorBackStack.removeLast()
                                                val freshNote = repository.notes.value.firstOrNull { it.id == prevNote.id } ?: prevNote
                                                currentScreen = Screen.Editor(freshNote)
                                            } else {
                                                currentScreen = Screen.List
                                            }
                                        },
                                        onNavigateToNote = { targetTitle ->
                                            val cleanTarget = targetTitle.trim()
                                            val existing = notes.firstOrNull { noteItem ->
                                                noteItem.displayTitle.equals(cleanTarget, ignoreCase = true) ||
                                                (noteItem.file != null && noteItem.file.nameWithoutExtension.equals(cleanTarget, ignoreCase = true))
                                            }
                                            if (existing != null) {
                                                editorBackStack.add(screen.note)
                                                currentScreen = Screen.Editor(existing)
                                            } else {
                                                scope.launch {
                                                    val created = repository.getOrCreateNote(cleanTarget, selectedFolder ?: "Notes")
                                                    editorBackStack.add(screen.note)
                                                    currentScreen = Screen.Editor(created)
                                                }
                                            }
                                        },
                                        onSave = { updatedNote ->
                                            scope.launch {
                                                repository.saveNote(updatedNote)
                                                QuickCaptureWidgetProvider.updateAllWidgets(applicationContext)
                                                triggerAutoSync(debounceMs = 2500L)
                                            }
                                        },
                                        onSaveAttachment = { uri ->
                                            repository.saveAttachment(uri)
                                        },
                                        onDelete = { noteToDelete ->
                                            scope.launch {
                                                repository.deleteNote(noteToDelete)
                                                QuickCaptureWidgetProvider.updateAllWidgets(applicationContext)
                                                triggerAutoSync(debounceMs = 0L)
                                                if (editorBackStack.isNotEmpty()) {
                                                    val prevNote = editorBackStack.removeLast()
                                                    val freshNote = repository.notes.value.firstOrNull { it.id == prevNote.id } ?: prevNote
                                                    currentScreen = Screen.Editor(freshNote)
                                                } else {
                                                    currentScreen = Screen.List
                                                }
                                            }
                                        }
                                    )
                                }
                                is Screen.Trash -> {
                                    val trashNotes by repository.trashNotes.collectAsState()
                                    TrashScreen(
                                        trashNotes = trashNotes,
                                        onBack = {
                                            currentScreen = Screen.List
                                        },
                                        onRestoreNote = { noteToRestore ->
                                            scope.launch {
                                                repository.restoreNote(noteToRestore)
                                                currentScreen = Screen.List
                                                triggerAutoSync(debounceMs = 1500L)
                                            }
                                        },
                                        onPermanentlyDeleteNote = { noteToDelete ->
                                            scope.launch {
                                                repository.permanentlyDelete(noteToDelete)
                                                triggerAutoSync(debounceMs = 0L)
                                            }
                                        },
                                        onEmptyTrash = {
                                            scope.launch {
                                                repository.emptyTrash()
                                                triggerAutoSync(debounceMs = 0L)
                                            }
                                        }
                                    )
                                }
                                is Screen.Graph -> {
                                    val notes by repository.notes.collectAsState()
                                    KnowledgeGraphScreen(
                                        notes = notes,
                                        onBack = {
                                            currentScreen = Screen.List
                                        },
                                        onNavigateToNote = { note ->
                                            editorBackStack.clear()
                                            currentScreen = Screen.Editor(note)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (showGoogleCloudSetupDialog) {
                        com.monolith.notes.ui.components.GoogleDriveDialog(
                            initialConfig = settings.googleDriveConfig,
                            onConnectGoogle = {
                                showGoogleCloudSetupDialog = false
                                val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                                    .requestEmail()
                                    .requestScopes(com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.appdata"))
                                    .build()
                                val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(this@MainActivity, gso)
                                googleSignInLauncher.launch(client.signInIntent)
                            },
                            onDisconnectGoogle = {
                                val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                                com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(this@MainActivity, gso).signOut().addOnCompleteListener {
                                    settingsManager.setGoogleDriveConfig(com.monolith.notes.data.GoogleDriveConfig())
                                }
                            },
                            onSaveConfig = { config ->
                                settingsManager.setGoogleDriveConfig(config)
                            },
                            onDismiss = { showGoogleCloudSetupDialog = false },
                            showDevHelpInitially = true
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        triggerAutoSync(debounceMs = 600L)
    }

    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) return
        if (settingsManager.settings.value.biometricLockEnabled) {
            isAppUnlocked = false
        }
        triggerAutoSync(debounceMs = 0L)
    }

    private fun promptBiometricAuth(
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    onError(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun checkStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val prefs = getSharedPreferences("monolith_settings", MODE_PRIVATE)
                val hasPrompted = prefs.getBoolean("has_prompted_storage_manager", false)
                if (!hasPrompted) {
                    prefs.edit().putBoolean("has_prompted_storage_manager", true).apply()
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    } catch (_: Exception) {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            startActivity(intent)
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }
}

@Composable
fun BiometricLockScreen(
    onUnlockClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(iOSBackground)
            .statusBarsPadding()
            .displayCutoutPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(iOSCardBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Vault Locked",
                    tint = iOSYellow,
                    modifier = Modifier.size(46.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Notes are Locked",
                style = MonolithTypography.titleLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                color = iOSTextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Use Biometrics or device screen lock to view your notes vault.",
                style = MonolithTypography.bodyMedium.copy(fontSize = 15.sp),
                color = iOSTextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onUnlockClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = iOSYellow,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Unlock Notes",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
