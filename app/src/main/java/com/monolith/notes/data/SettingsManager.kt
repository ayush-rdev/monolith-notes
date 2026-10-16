package com.monolith.notes.data

import android.content.Context
import android.content.SharedPreferences
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

data class AppSettings(
    val hapticsEnabled: Boolean = true,
    val newNoteStartStyle: NewNoteStartStyle = NewNoteStartStyle.TITLE,
    val editorFontSize: EditorFontSize = EditorFontSize.DEFAULT,
    val showWordCount: Boolean = true,
    val autoContinueChecklists: Boolean = true,
    val sortOrder: NoteSortOrder = NoteSortOrder.DATE_EDITED,
    val biometricLockEnabled: Boolean = false,
    val theme: AppTheme = AppTheme.OBSIDIAN
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
        val themeId = prefs.getString(KEY_APP_THEME, AppTheme.OBSIDIAN.id) ?: AppTheme.OBSIDIAN.id
        val theme = AppTheme.fromId(themeId)

        return AppSettings(
            hapticsEnabled = haptics,
            newNoteStartStyle = startStyle,
            editorFontSize = fontSize,
            showWordCount = showWords,
            autoContinueChecklists = autoChecklist,
            sortOrder = sortOrder,
            biometricLockEnabled = biometricLock,
            theme = theme
        )
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

    fun setTheme(theme: AppTheme) {
        prefs.edit().putString(KEY_APP_THEME, theme.id).apply()
        _settings.value = _settings.value.copy(theme = theme)
    }

    companion object {
        private const val KEY_HAPTICS = "key_haptics"
        private const val KEY_START_STYLE = "key_start_style"
        private const val KEY_FONT_SIZE = "key_font_size"
        private const val KEY_SHOW_WORD_COUNT = "key_show_word_count"
        private const val KEY_AUTO_CHECKLIST = "key_auto_checklist"
        private const val KEY_SORT_ORDER = "key_sort_order"
        private const val KEY_BIOMETRIC_LOCK = "key_biometric_lock"
        private const val KEY_APP_THEME = "key_app_theme"
    }
}
