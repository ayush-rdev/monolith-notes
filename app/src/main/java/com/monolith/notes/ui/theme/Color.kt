package com.monolith.notes.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Monolith Minimal Premium Design Themes:
 * Highly curated, atmospheric palettes engineered for OLED battery saving,
 * distraction-free writing, and tactile contrast.
 */
enum class AppTheme(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val background: Color,
    val cardBackground: Color,
    val elevatedCard: Color,
    val accent: Color,
    val accentContainer: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color
) {
    OBSIDIAN(
        id = "obsidian",
        displayName = "Obsidian",
        subtitle = "OLED Pure Black & Gold",
        background = Color(0xFF000000),
        cardBackground = Color(0xFF18181B),
        elevatedCard = Color(0xFF27272A),
        accent = Color(0xFFE5A93C),
        accentContainer = Color(0x33E5A93C),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFA1A1AA),
        textTertiary = Color(0xFF71717A),
        divider = Color(0xFF27272A)
    ),
    TITANIUM(
        id = "titanium",
        displayName = "Titanium",
        subtitle = "Nordic Silver & Graphite",
        background = Color(0xFF0C0D10),
        cardBackground = Color(0xFF16181D),
        elevatedCard = Color(0xFF22252C),
        accent = Color(0xFFE2E8F0),
        accentContainer = Color(0x33E2E8F0),
        textPrimary = Color(0xFFF8FAFC),
        textSecondary = Color(0xFF94A3B8),
        textTertiary = Color(0xFF64748B),
        divider = Color(0xFF22252C)
    ),
    NOCTURNE(
        id = "nocturne",
        displayName = "Nocturne",
        subtitle = "British Racing & Sage",
        background = Color(0xFF070B09),
        cardBackground = Color(0xFF101814),
        elevatedCard = Color(0xFF1A2620),
        accent = Color(0xFF34D399),
        accentContainer = Color(0x3334D399),
        textPrimary = Color(0xFFF0FDF4),
        textSecondary = Color(0xFF86EFAC),
        textTertiary = Color(0xFF4ADE80),
        divider = Color(0xFF1A2620)
    ),
    KANSO(
        id = "kanso",
        displayName = "Kanso",
        subtitle = "Tokyo Espresso & Sand",
        background = Color(0xFF0F0D0C),
        cardBackground = Color(0xFF1C1916),
        elevatedCard = Color(0xFF2A2522),
        accent = Color(0xFFE6BA82),
        accentContainer = Color(0x33E6BA82),
        textPrimary = Color(0xFFFAF7F5),
        textSecondary = Color(0xFFB5A99F),
        textTertiary = Color(0xFF786E65),
        divider = Color(0xFF2A2522)
    ),
    MIDNIGHT(
        id = "midnight",
        displayName = "Midnight",
        subtitle = "Deep Space & Cyan",
        background = Color(0xFF060911),
        cardBackground = Color(0xFF0E1424),
        elevatedCard = Color(0xFF18223B),
        accent = Color(0xFF38BDF8),
        accentContainer = Color(0x3338BDF8),
        textPrimary = Color(0xFFF0F9FF),
        textSecondary = Color(0xFF93C5FD),
        textTertiary = Color(0xFF60A5FA),
        divider = Color(0xFF18223B)
    );

    companion object {
        fun fromId(id: String): AppTheme = entries.find { it.id.equals(id, ignoreCase = true) } ?: OBSIDIAN
    }
}

val LocalMonolithTheme = staticCompositionLocalOf { AppTheme.OBSIDIAN }

// Seamless dynamic composable accessors for existing UI components
val iOSBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalMonolithTheme.current.background

val iOSCardBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalMonolithTheme.current.cardBackground

val iOSElevatedCard: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalMonolithTheme.current.elevatedCard

val iOSSearchPill: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalMonolithTheme.current.cardBackground

val iOSYellow: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalMonolithTheme.current.accent

val iOSYellowContainer: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalMonolithTheme.current.accentContainer

val iOSEmerald: Color
    @Composable
    @ReadOnlyComposable
    get() = Color(0xFF34C759)

val iOSTextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalMonolithTheme.current.textPrimary

val iOSTextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalMonolithTheme.current.textSecondary

val iOSTextTertiary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalMonolithTheme.current.textTertiary

val iOSDivider: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalMonolithTheme.current.divider
