package com.monolith.notes.data

import android.content.Context
import android.content.SharedPreferences
import com.monolith.notes.data.sync.MonolithCloudConfig
import com.monolith.notes.data.sync.WebDavConfig
import com.monolith.notes.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class EditorFontSize(val label: String, val sizeSp: Int, val lineHeightSp: Int) {
    COMPACT("Compact (15sp)", 15, 23),
    DEFAULT("Default (17sp)", 17, 26),
    LARGE("Large (19sp)", 19, 29),
    EXTRA_LARGE("Extra Large (21sp)", 21, 32);

    fun next(): EditorFontSize {
        val entries = entries
        return entries[(ordinal + 1) % entries.size]
    }
}

enum class NewNoteStartStyle(val label: String) {
    TITLE("Title"),
    BODY("Body");

    fun toggle(): NewNoteStartStyle = if (this == TITLE) BODY else TITLE
}

data class GoogleDriveConfig(
    val email: String = "",
    val displayName: String = "",
    val encryptionKey: String = "",
    val autoSyncOnLaunch: Boolean = true,
    val lastSyncTime: Long = 0L
) {
    val isConnected: Boolean get() = email.isNotBlank()
}

data class AppSettings(
    val hapticsEnabled: Boolean = true,
    val newNoteStartStyle: NewNoteStartStyle = NewNoteStartStyle.TITLE,
    val editorFontSize: EditorFontSize = EditorFontSize.DEFAULT,
    val showWordCount: Boolean = true,
    val autoContinueChecklists: Boolean = true,
    val sortOrder: NoteSortOrder = NoteSortOrder.DATE_EDITED,
    val biometricLockEnabled: Boolean = false,
    val screenSecurityEnabled: Boolean = false,
    val theme: AppTheme = AppTheme.OBSIDIAN,
    val cloudConfig: MonolithCloudConfig = MonolithCloudConfig(),
    val googleDriveConfig: GoogleDriveConfig = GoogleDriveConfig(),
    val webDavConfig: WebDavConfig = WebDavConfig(),
    val googleDriveUri: String? = null,
    val lastSyncTimestamp: Long = 0L,
    val isGalleryView: Boolean = false
)

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("monolith_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        val haptics = prefs.getBoolean(KEY_HAPTICS, true)
        val startStyleStr = prefs.getString(KEY_START_STYLE, NewNoteStartStyle.TITLE.name) ?: NewNoteStartStyle.TITLE.name
        val startStyle = try { NewNoteStartStyle.valueOf(startStyleStr) } catch (_: Exception) { NewNoteStartStyle.TITLE }
        val fontStr = prefs.getString(KEY_FONT_SIZE, EditorFontSize.DEFAULT.name) ?: EditorFontSize.DEFAULT.name
        val fontSize = try { EditorFontSize.valueOf(fontStr) } catch (_: Exception) { EditorFontSize.DEFAULT }
        val showWords = prefs.getBoolean(KEY_SHOW_WORD_COUNT, true)
        val autoChecklist = prefs.getBoolean(KEY_AUTO_CHECKLIST, true)
        val sortOrderStr = prefs.getString(KEY_SORT_ORDER, NoteSortOrder.DATE_EDITED.name) ?: NoteSortOrder.DATE_EDITED.name
        val sortOrder = try { NoteSortOrder.valueOf(sortOrderStr) } catch (_: Exception) { NoteSortOrder.DATE_EDITED }
        val biometricLock = prefs.getBoolean(KEY_BIOMETRIC_LOCK, false)
        val screenSecurity = prefs.getBoolean(KEY_SCREEN_SECURITY, false)
        val themeId = prefs.getString(KEY_APP_THEME, AppTheme.OBSIDIAN.id) ?: AppTheme.OBSIDIAN.id
        val theme = AppTheme.fromId(themeId)

        val webDavUrl = prefs.getString(KEY_WEBDAV_URL, "") ?: ""
        val webDavUser = prefs.getString(KEY_WEBDAV_USER, "") ?: ""
        val webDavPass = prefs.getString(KEY_WEBDAV_PASS, "") ?: ""
        val webDavE2ee = prefs.getBoolean(KEY_WEBDAV_E2EE, false)
        val webDavPassphrase = prefs.getString(KEY_WEBDAV_PASSPHRASE, "") ?: ""
        val webDavAutoSync = prefs.getBoolean(KEY_WEBDAV_AUTOSYNC, false)
        val lastSync = prefs.getLong(KEY_LAST_SYNC_TIME, 0L)

        val webDavConfig = WebDavConfig(
            serverUrl = webDavUrl,
            username = webDavUser,
            password = webDavPass,
            e2eeEnabled = webDavE2ee,
            encryptionPassphrase = webDavPassphrase,
            autoSyncOnLaunch = webDavAutoSync
        )

        val cloudServer = prefs.getString(KEY_CLOUD_SERVER, MonolithCloudConfig.DEFAULT_SERVER_URL) ?: MonolithCloudConfig.DEFAULT_SERVER_URL
        val cloudEmail = prefs.getString(KEY_CLOUD_EMAIL, "") ?: ""
        val cloudToken = prefs.getString(KEY_CLOUD_TOKEN, "") ?: ""
        val cloudKey = prefs.getString(KEY_CLOUD_KEY, "") ?: ""
        val cloudAutoSync = prefs.getBoolean(KEY_CLOUD_AUTOSYNC, true)
        val cloudLastSync = prefs.getLong(KEY_CLOUD_LAST_SYNC, 0L)

        val cloudConfig = MonolithCloudConfig(
            serverUrl = cloudServer,
            email = cloudEmail,
            token = cloudToken,
            encryptionKey = cloudKey,
            autoSyncOnLaunch = cloudAutoSync,
            lastServerSyncTime = cloudLastSync
        )

        val gDriveEmail = prefs.getString(KEY_GDRIVE_EMAIL, "") ?: ""
        val gDriveName = prefs.getString(KEY_GDRIVE_NAME, "") ?: ""
        val gDriveKey = prefs.getString(KEY_GDRIVE_KEY, "") ?: ""
        val gDriveAutoSync = prefs.getBoolean(KEY_GDRIVE_AUTOSYNC, true)
        val gDriveLastSync = prefs.getLong(KEY_GDRIVE_LAST_SYNC, 0L)
        val googleDriveConfig = GoogleDriveConfig(
            email = gDriveEmail,
            displayName = gDriveName,
            encryptionKey = gDriveKey,
            autoSyncOnLaunch = gDriveAutoSync,
            lastSyncTime = gDriveLastSync
        )

        val gDriveUri = prefs.getString(KEY_GDRIVE_URI, null)
        val galleryView = prefs.getBoolean(KEY_GALLERY_VIEW, false)

        return AppSettings(
            hapticsEnabled = haptics,
            newNoteStartStyle = startStyle,
            editorFontSize = fontSize,
            showWordCount = showWords,
            autoContinueChecklists = autoChecklist,
            sortOrder = sortOrder,
            biometricLockEnabled = biometricLock,
            screenSecurityEnabled = screenSecurity,
            theme = theme,
            cloudConfig = cloudConfig,
            googleDriveConfig = googleDriveConfig,
            webDavConfig = webDavConfig,
            googleDriveUri = gDriveUri,
            lastSyncTimestamp = lastSync,
            isGalleryView = galleryView
        )
    }

    fun setGalleryView(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GALLERY_VIEW, enabled).apply()
        _settings.value = _settings.value.copy(isGalleryView = enabled)
    }

    fun setHapticsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTICS, enabled).apply()
        _settings.value = _settings.value.copy(hapticsEnabled = enabled)
    }

    fun setNewNoteStartStyle(style: NewNoteStartStyle) {
        prefs.edit().putString(KEY_START_STYLE, style.name).apply()
        _settings.value = _settings.value.copy(newNoteStartStyle = style)
    }

    fun setEditorFontSize(size: EditorFontSize) {
        prefs.edit().putString(KEY_FONT_SIZE, size.name).apply()
        _settings.value = _settings.value.copy(editorFontSize = size)
    }

    fun setShowWordCount(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_WORD_COUNT, enabled).apply()
        _settings.value = _settings.value.copy(showWordCount = enabled)
    }

    fun setAutoContinueChecklists(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CHECKLIST, enabled).apply()
        _settings.value = _settings.value.copy(autoContinueChecklists = enabled)
    }

    fun setSortOrder(order: NoteSortOrder) {
        prefs.edit().putString(KEY_SORT_ORDER, order.name).apply()
        _settings.value = _settings.value.copy(sortOrder = order)
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_LOCK, enabled).apply()
        _settings.value = _settings.value.copy(biometricLockEnabled = enabled)
    }

    fun setScreenSecurityEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SCREEN_SECURITY, enabled).apply()
        _settings.value = _settings.value.copy(screenSecurityEnabled = enabled)
    }

    fun setTheme(theme: AppTheme) {
        prefs.edit().putString(KEY_APP_THEME, theme.id).apply()
        _settings.value = _settings.value.copy(theme = theme)
    }

    fun setWebDavConfig(config: WebDavConfig) {
        prefs.edit()
            .putString(KEY_WEBDAV_URL, config.serverUrl)
            .putString(KEY_WEBDAV_USER, config.username)
            .putString(KEY_WEBDAV_PASS, config.password)
            .putBoolean(KEY_WEBDAV_E2EE, config.e2eeEnabled)
            .putString(KEY_WEBDAV_PASSPHRASE, config.encryptionPassphrase)
            .putBoolean(KEY_WEBDAV_AUTOSYNC, config.autoSyncOnLaunch)
            .apply()
        _settings.value = _settings.value.copy(webDavConfig = config)
    }

    fun setCloudConfig(config: MonolithCloudConfig) {
        prefs.edit()
            .putString(KEY_CLOUD_SERVER, config.serverUrl)
            .putString(KEY_CLOUD_EMAIL, config.email)
            .putString(KEY_CLOUD_TOKEN, config.token)
            .putString(KEY_CLOUD_KEY, config.encryptionKey)
            .putBoolean(KEY_CLOUD_AUTOSYNC, config.autoSyncOnLaunch)
            .putLong(KEY_CLOUD_LAST_SYNC, config.lastServerSyncTime)
            .apply()
        _settings.value = _settings.value.copy(cloudConfig = config)
    }

    fun updateCloudLastSync(timestamp: Long) {
        prefs.edit().putLong(KEY_CLOUD_LAST_SYNC, timestamp).apply()
        val current = _settings.value.cloudConfig
        _settings.value = _settings.value.copy(
            cloudConfig = current.copy(lastServerSyncTime = timestamp),
            lastSyncTimestamp = timestamp
        )
    }

    fun setGoogleDriveConfig(config: GoogleDriveConfig) {
        prefs.edit()
            .putString(KEY_GDRIVE_EMAIL, config.email)
            .putString(KEY_GDRIVE_NAME, config.displayName)
            .putString(KEY_GDRIVE_KEY, config.encryptionKey)
            .putBoolean(KEY_GDRIVE_AUTOSYNC, config.autoSyncOnLaunch)
            .putLong(KEY_GDRIVE_LAST_SYNC, config.lastSyncTime)
            .apply()
        _settings.value = _settings.value.copy(googleDriveConfig = config)
    }

    fun updateGoogleDriveLastSync(timestamp: Long) {
        val updated = _settings.value.googleDriveConfig.copy(lastSyncTime = timestamp)
        prefs.edit()
            .putLong(KEY_GDRIVE_LAST_SYNC, timestamp)
            .putLong(KEY_LAST_SYNC_TIME, timestamp)
            .apply()
        _settings.value = _settings.value.copy(
            googleDriveConfig = updated,
            lastSyncTimestamp = timestamp
        )
    }

    fun setGoogleDriveUri(uri: String?) {
        if (uri != null) {
            prefs.edit().putString(KEY_GDRIVE_URI, uri).apply()
        } else {
            prefs.edit().remove(KEY_GDRIVE_URI).apply()
        }
        _settings.value = _settings.value.copy(googleDriveUri = uri)
    }

    fun setLastSyncTimestamp(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC_TIME, timestamp).apply()
        _settings.value = _settings.value.copy(lastSyncTimestamp = timestamp)
    }

    companion object {
        private const val KEY_HAPTICS = "key_haptics"
        private const val KEY_START_STYLE = "key_start_style"
        private const val KEY_FONT_SIZE = "key_font_size"
        private const val KEY_SHOW_WORD_COUNT = "key_show_word_count"
        private const val KEY_AUTO_CHECKLIST = "key_auto_checklist"
        private const val KEY_SORT_ORDER = "key_sort_order"
        private const val KEY_BIOMETRIC_LOCK = "key_biometric_lock"
        private const val KEY_SCREEN_SECURITY = "key_screen_security"
        private const val KEY_APP_THEME = "key_app_theme"
        private const val KEY_CLOUD_SERVER = "key_cloud_server"
        private const val KEY_CLOUD_EMAIL = "key_cloud_email"
        private const val KEY_CLOUD_TOKEN = "key_cloud_token"
        private const val KEY_CLOUD_KEY = "key_cloud_key"
        private const val KEY_CLOUD_AUTOSYNC = "key_cloud_autosync"
        private const val KEY_CLOUD_LAST_SYNC = "key_cloud_last_sync"
        private const val KEY_GDRIVE_EMAIL = "key_gdrive_email"
        private const val KEY_GDRIVE_NAME = "key_gdrive_name"
        private const val KEY_GDRIVE_KEY = "key_gdrive_key"
        private const val KEY_GDRIVE_AUTOSYNC = "key_gdrive_autosync"
        private const val KEY_GDRIVE_LAST_SYNC = "key_gdrive_last_sync"
        private const val KEY_GDRIVE_URI = "key_gdrive_uri"
        private const val KEY_WEBDAV_URL = "key_webdav_url"
        private const val KEY_WEBDAV_USER = "key_webdav_user"
        private const val KEY_WEBDAV_PASS = "key_webdav_pass"
        private const val KEY_WEBDAV_E2EE = "key_webdav_e2ee"
        private const val KEY_WEBDAV_PASSPHRASE = "key_webdav_passphrase"
        private const val KEY_WEBDAV_AUTOSYNC = "key_webdav_autosync"
        private const val KEY_LAST_SYNC_TIME = "key_last_sync_time"
        private const val KEY_GALLERY_VIEW = "key_gallery_view"
    }
}
