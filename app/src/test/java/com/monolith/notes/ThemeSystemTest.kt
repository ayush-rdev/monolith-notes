package com.monolith.notes

import com.monolith.notes.ui.theme.AppTheme
import com.monolith.notes.ui.theme.ThemeCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSystemTest {

    @Test
    fun testThemeCountAndCategories() {
        val allThemes = AppTheme.entries
        assertEquals("Expected 23 curated themes", 23, allThemes.size)

        val oledThemes = allThemes.filter { it.category == ThemeCategory.OLED }
        val classicsThemes = allThemes.filter { it.category == ThemeCategory.CLASSICS }
        val brutalistThemes = allThemes.filter { it.category == ThemeCategory.BRUTALIST }
        val paperThemes = allThemes.filter { it.category == ThemeCategory.PAPER }

        assertEquals(6, oledThemes.size)
        assertEquals(8, classicsThemes.size)
        assertEquals(5, brutalistThemes.size)
        assertEquals(4, paperThemes.size)
    }

    @Test
    fun testOledThemesHavePureBlackBackground() {
        val oledThemes = AppTheme.entries.filter { it.category == ThemeCategory.OLED }
        for (theme in oledThemes) {
            assertEquals("OLED theme ${theme.displayName} must have pure black (#000000) background", androidx.compose.ui.graphics.Color(0xFF000000), theme.background)
            assertTrue("OLED theme ${theme.displayName} must be marked dark", theme.isDark)
        }
    }

    @Test
    fun testPaperThemesAreMarkedLight() {
        val paperThemes = AppTheme.entries.filter { it.category == ThemeCategory.PAPER }
        for (theme in paperThemes) {
            assertFalse("Paper theme ${theme.displayName} must have isDark = false", theme.isDark)
        }
    }

    @Test
    fun testFromIdResolutionAndFallback() {
        // Standard themes
        assertEquals(AppTheme.OBSIDIAN, AppTheme.fromId("obsidian"))
        assertEquals(AppTheme.DRACULA, AppTheme.fromId("dracula"))
        assertEquals(AppTheme.NORD, AppTheme.fromId("nord"))
        assertEquals(AppTheme.TOKYO_NIGHT, AppTheme.fromId("tokyo_night"))
        assertEquals(AppTheme.CATPPUCCIN, AppTheme.fromId("catppuccin"))
        assertEquals(AppTheme.GRUVBOX, AppTheme.fromId("gruvbox"))
        assertEquals(AppTheme.TEENAGE_ENGINEERING, AppTheme.fromId("field_op"))
        assertEquals(AppTheme.APPLE_PARCHMENT, AppTheme.fromId("parchment"))
        assertEquals(AppTheme.E_INK, AppTheme.fromId("e_ink"))

        // Case insensitivity
        assertEquals(AppTheme.DRACULA, AppTheme.fromId("DrAcUlA"))
        assertEquals(AppTheme.NORD, AppTheme.fromId("NORD"))

        // Graceful fallback on unknown/null/corrupt ID
        assertEquals(AppTheme.OBSIDIAN, AppTheme.fromId("unknown_custom_theme"))
        assertEquals(AppTheme.OBSIDIAN, AppTheme.fromId(""))
    }

    @Test
    fun testThemeContrastIntegrity() {
        for (theme in AppTheme.entries) {
            assertNotNull(theme.displayName)
            assertNotNull(theme.subtitle)
            assertNotEquals("Background and text primary must not match in ${theme.name}", theme.background, theme.textPrimary)
            assertNotEquals("Background and accent must not match in ${theme.name}", theme.background, theme.accent)
        }
    }

    @Test
    fun testScreenSecurityDefaultAndToggle() {
        val defaultSettings = com.monolith.notes.data.AppSettings()
        // Screen security must be disabled by default so screenshots and screen recordings can be performed
        assertFalse("Screen security should be false by default to allow screenshots and screen recording", defaultSettings.screenSecurityEnabled)

        // When toggled on, screen security enables window protection
        val securedSettings = defaultSettings.copy(screenSecurityEnabled = true)
        assertTrue("Screen security should be true when enabled", securedSettings.screenSecurityEnabled)

        // When toggled off, screen security turns off window protection
        val unsecuredSettings = securedSettings.copy(screenSecurityEnabled = false)
        assertFalse("Screen security should be false when turned off", unsecuredSettings.screenSecurityEnabled)
    }

    @Test
    fun testAll23ThemesHaveUniqueIdsAndValidPalettes() {
        val ids = mutableSetOf<String>()
        for (theme in AppTheme.entries) {
            assertTrue("Theme ID must not be blank", theme.id.isNotBlank())
            assertTrue("Theme ID '${theme.id}' must be unique", ids.add(theme.id))
            assertNotNull("Theme category must not be null", theme.category)
            assertTrue("Theme display name must not be blank", theme.displayName.isNotBlank())
            assertTrue("Theme subtitle must not be blank", theme.subtitle.isNotBlank())
        }
        assertEquals(23, ids.size)
    }
}
