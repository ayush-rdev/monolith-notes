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
}
