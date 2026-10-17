package com.monolith.notes.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Categorization of Monolith's themes for curated discovery and visual harmony.
 */
enum class ThemeCategory(val displayName: String, val subtitle: String) {
    OLED("OLED Studio", "Pure black pixels for 0% power & infinite contrast"),
    CLASSICS("Developer Classics", "Iconic editor palettes tuned for focus"),
    BRUTALIST("Industrial & Hardware", "Tactile Dieter Rams, Teenage Engineering & retro CRT"),
    PAPER("Light & Warm Paper", "Distraction-free daytime writing & E-ink aesthetic")
}

/**
 * Monolith Minimal Premium Design Themes:
 * Highly curated, atmospheric palettes engineered for OLED battery saving,
 * distraction-free writing, and tactile contrast.
 */
enum class AppTheme(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val category: ThemeCategory,
    val background: Color,
    val cardBackground: Color,
    val elevatedCard: Color,
    val accent: Color,
    val accentContainer: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    val isDark: Boolean = true
) {
    // ==========================================
    // 1. OLED STUDIO (Pure #000000 Blacks)
    // ==========================================
    OBSIDIAN(
        id = "obsidian",
        displayName = "Obsidian Minimal",
        subtitle = "OLED Pure Black & Obsidian Violet",
        category = ThemeCategory.OLED,
        background = Color(0xFF000000),
        cardBackground = Color(0xFF121214),
        elevatedCard = Color(0xFF1E1E22),
        accent = Color(0xFF8B5CF6),
        accentContainer = Color(0x338B5CF6),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFA1A1AA),
        textTertiary = Color(0xFF71717A),
        divider = Color(0xFF222226),
        isDark = true
    ),
    CRIMSON_NOIR(
        id = "crimson_noir",
        displayName = "Crimson Noir",
        subtitle = "OLED Pitch Black & Vermilion",
        category = ThemeCategory.OLED,
        background = Color(0xFF000000),
        cardBackground = Color(0xFF161214),
        elevatedCard = Color(0xFF261D22),
        accent = Color(0xFFFF453A),
        accentContainer = Color(0x33FF453A),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFE5B8BC),
        textTertiary = Color(0xFF8A6C71),
        divider = Color(0xFF261D22),
        isDark = true
    ),
    CYBER_AMETHYST(
        id = "cyber_amethyst",
        displayName = "Cyber Amethyst",
        subtitle = "OLED Pitch Black & Electric Violet",
        category = ThemeCategory.OLED,
        background = Color(0xFF000000),
        cardBackground = Color(0xFF15121B),
        elevatedCard = Color(0xFF251E30),
        accent = Color(0xFFBF5AF2),
        accentContainer = Color(0x33BF5AF2),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFCFBFE3),
        textTertiary = Color(0xFF7A6891),
        divider = Color(0xFF251E30),
        isDark = true
    ),
    MATRIX_OBSIDIAN(
        id = "matrix_obsidian",
        displayName = "Matrix Obsidian",
        subtitle = "OLED Pitch Black & Emerald",
        category = ThemeCategory.OLED,
        background = Color(0xFF000000),
        cardBackground = Color(0xFF101712),
        elevatedCard = Color(0xFF1B281F),
        accent = Color(0xFF30D158),
        accentContainer = Color(0x3330D158),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFB4DFC0),
        textTertiary = Color(0xFF658A70),
        divider = Color(0xFF1B281F),
        isDark = true
    ),
    COBALT_NOIR(
        id = "cobalt_noir",
        displayName = "Cobalt Noir",
        subtitle = "OLED Pitch Black & Royal Cobalt",
        category = ThemeCategory.OLED,
        background = Color(0xFF000000),
        cardBackground = Color(0xFF10141D),
        elevatedCard = Color(0xFF1A2233),
        accent = Color(0xFF0A84FF),
        accentContainer = Color(0x330A84FF),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFB6CBEC),
        textTertiary = Color(0xFF637C9E),
        divider = Color(0xFF1A2233),
        isDark = true
    ),
    ROSE_GOLD(
        id = "rose_gold",
        displayName = "Rose Gold",
        subtitle = "OLED Pitch Black & Champagne Pink",
        category = ThemeCategory.OLED,
        background = Color(0xFF000000),
        cardBackground = Color(0xFF161214),
        elevatedCard = Color(0xFF261B21),
        accent = Color(0xFFFB7185),
        accentContainer = Color(0x33FB7185),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFE5BFC6),
        textTertiary = Color(0xFF8F6F77),
        divider = Color(0xFF261B21),
        isDark = true
    ),

    // ==========================================
    // 2. DEVELOPER & STUDIO CLASSICS
    // ==========================================
    DRACULA(
        id = "dracula",
        displayName = "Dracula",
        subtitle = "Gothic Slate & Vampire Purple",
        category = ThemeCategory.CLASSICS,
        background = Color(0xFF282A36),
        cardBackground = Color(0xFF343746),
        elevatedCard = Color(0xFF44475A),
        accent = Color(0xFFBD93F9),
        accentContainer = Color(0x33BD93F9),
        textPrimary = Color(0xFFF8F8F2),
        textSecondary = Color(0xFFB2B6CA),
        textTertiary = Color(0xFF6272A4),
        divider = Color(0xFF44475A),
        isDark = true
    ),
    NORD(
        id = "nord",
        displayName = "Nord",
        subtitle = "Arctic Polar Night & Frost Teal",
        category = ThemeCategory.CLASSICS,
        background = Color(0xFF2E3440),
        cardBackground = Color(0xFF3B4252),
        elevatedCard = Color(0xFF434C5E),
        accent = Color(0xFF88C0D0),
        accentContainer = Color(0x3388C0D0),
        textPrimary = Color(0xFFECEFF4),
        textSecondary = Color(0xFFD8DEE9),
        textTertiary = Color(0xFF4C566A),
        divider = Color(0xFF434C5E),
        isDark = true
    ),
    TOKYO_NIGHT(
        id = "tokyo_night",
        displayName = "Tokyo Night",
        subtitle = "Neo-Tokyo Indigo & Electric Sky",
        category = ThemeCategory.CLASSICS,
        background = Color(0xFF1A1B26),
        cardBackground = Color(0xFF24283B),
        elevatedCard = Color(0xFF2F354F),
        accent = Color(0xFF7AA2F7),
        accentContainer = Color(0x337AA2F7),
        textPrimary = Color(0xFFC0CAF5),
        textSecondary = Color(0xFF9AA5CE),
        textTertiary = Color(0xFF565F89),
        divider = Color(0xFF2F354F),
        isDark = true
    ),
    CATPPUCCIN(
        id = "catppuccin",
        displayName = "Catppuccin Mocha",
        subtitle = "Cozy Charcoal & Warm Peach",
        category = ThemeCategory.CLASSICS,
        background = Color(0xFF1E1E2E),
        cardBackground = Color(0xFF252538),
        elevatedCard = Color(0xFF313244),
        accent = Color(0xFFFAB387),
        accentContainer = Color(0x33FAB387),
        textPrimary = Color(0xFFCDD6F4),
        textSecondary = Color(0xFFA6ADC8),
        textTertiary = Color(0xFF6C7086),
        divider = Color(0xFF313244),
        isDark = true
    ),
    GRUVBOX(
        id = "gruvbox",
        displayName = "Gruvbox Dark",
        subtitle = "Retro Groove & Amber Sun",
        category = ThemeCategory.CLASSICS,
        background = Color(0xFF1D2021),
        cardBackground = Color(0xFF282828),
        elevatedCard = Color(0xFF3C3836),
        accent = Color(0xFFFABD2F),
        accentContainer = Color(0x33FABD2F),
        textPrimary = Color(0xFFEBDBB2),
        textSecondary = Color(0xFFD5C4A1),
        textTertiary = Color(0xFF7C6F64),
        divider = Color(0xFF3C3836),
        isDark = true
    ),
    SOLARIZED_DARK(
        id = "solarized_dark",
        displayName = "Solarized Dark",
        subtitle = "Deep Ocean Cyan & Yellow Ochre",
        category = ThemeCategory.CLASSICS,
        background = Color(0xFF002B36),
        cardBackground = Color(0xFF073642),
        elevatedCard = Color(0xFF0E4756),
        accent = Color(0xFFB58900),
        accentContainer = Color(0x33B58900),
        textPrimary = Color(0xFF839496),
        textSecondary = Color(0xFF93A1A1),
        textTertiary = Color(0xFF586E75),
        divider = Color(0xFF0E4756),
        isDark = true
    ),
    MIDNIGHT(
        id = "midnight",
        displayName = "Midnight",
        subtitle = "Deep Space Navy & Cyan",
        category = ThemeCategory.CLASSICS,
        background = Color(0xFF060911),
        cardBackground = Color(0xFF0E1424),
        elevatedCard = Color(0xFF18223B),
        accent = Color(0xFF38BDF8),
        accentContainer = Color(0x3338BDF8),
        textPrimary = Color(0xFFF0F9FF),
        textSecondary = Color(0xFF93C5FD),
        textTertiary = Color(0xFF60A5FA),
        divider = Color(0xFF18223B),
        isDark = true
    ),
    NOCTURNE(
        id = "nocturne",
        displayName = "Nocturne",
        subtitle = "British Racing & Sage",
        category = ThemeCategory.CLASSICS,
        background = Color(0xFF070B09),
        cardBackground = Color(0xFF101814),
        elevatedCard = Color(0xFF1A2620),
        accent = Color(0xFF34D399),
        accentContainer = Color(0x3334D399),
        textPrimary = Color(0xFFF0FDF4),
        textSecondary = Color(0xFF86EFAC),
        textTertiary = Color(0xFF4ADE80),
        divider = Color(0xFF1A2620),
        isDark = true
    ),

    // ==========================================
    // 3. INDUSTRIAL & HARDWARE
    // ==========================================
    TEENAGE_ENGINEERING(
        id = "field_op",
        displayName = "Field OP-1",
        subtitle = "Matte Slate & Industrial Orange",
        category = ThemeCategory.BRUTALIST,
        background = Color(0xFF131417),
        cardBackground = Color(0xFF1D1F24),
        elevatedCard = Color(0xFF2A2C34),
        accent = Color(0xFFFF5500),
        accentContainer = Color(0x33FF5500),
        textPrimary = Color(0xFFF0F1F4),
        textSecondary = Color(0xFFA5A8B2),
        textTertiary = Color(0xFF6D707A),
        divider = Color(0xFF2A2C34),
        isDark = true
    ),
    BRAUN_BAUHAUS(
        id = "braun_bauhaus",
        displayName = "Braun Bauhaus",
        subtitle = "Dieter Rams Charcoal & Signal Red",
        category = ThemeCategory.BRUTALIST,
        background = Color(0xFF18181B),
        cardBackground = Color(0xFF232328),
        elevatedCard = Color(0xFF2F2F36),
        accent = Color(0xFFE02424),
        accentContainer = Color(0x33E02424),
        textPrimary = Color(0xFFF4F4F5),
        textSecondary = Color(0xFFA1A1AA),
        textTertiary = Color(0xFF71717A),
        divider = Color(0xFF2F2F36),
        isDark = true
    ),
    TERMINAL_CRT(
        id = "terminal_crt",
        displayName = "VT100 Terminal",
        subtitle = "Phosphor Matrix Green & Scanline Deep",
        category = ThemeCategory.BRUTALIST,
        background = Color(0xFF0A0E0A),
        cardBackground = Color(0xFF121B13),
        elevatedCard = Color(0xFF1A291C),
        accent = Color(0xFF22C55E),
        accentContainer = Color(0x3322C55E),
        textPrimary = Color(0xFF4ADE80),
        textSecondary = Color(0xFF22C55E),
        textTertiary = Color(0xFF15803D),
        divider = Color(0xFF1A291C),
        isDark = true
    ),
    TITANIUM(
        id = "titanium",
        displayName = "Titanium",
        subtitle = "Nordic Silver & Graphite",
        category = ThemeCategory.BRUTALIST,
        background = Color(0xFF0C0D10),
        cardBackground = Color(0xFF16181D),
        elevatedCard = Color(0xFF22252C),
        accent = Color(0xFFE2E8F0),
        accentContainer = Color(0x33E2E8F0),
        textPrimary = Color(0xFFF8FAFC),
        textSecondary = Color(0xFF94A3B8),
        textTertiary = Color(0xFF64748B),
        divider = Color(0xFF22252C),
        isDark = true
    ),
    KANSO(
        id = "kanso",
        displayName = "Kanso",
        subtitle = "Tokyo Espresso & Warm Sand",
        category = ThemeCategory.BRUTALIST,
        background = Color(0xFF0F0D0C),
        cardBackground = Color(0xFF1C1916),
        elevatedCard = Color(0xFF2A2522),
        accent = Color(0xFFE6BA82),
        accentContainer = Color(0x33E6BA82),
        textPrimary = Color(0xFFFAF7F5),
        textSecondary = Color(0xFFB5A99F),
        textTertiary = Color(0xFF786E65),
        divider = Color(0xFF2A2522),
        isDark = true
    ),

    // ==========================================
    // 4. LIGHT & WARM PAPER
    // ==========================================
    APPLE_PARCHMENT(
        id = "parchment",
        displayName = "Apple Parchment",
        subtitle = "Textured Ivory & Golden Ochre",
        category = ThemeCategory.PAPER,
        background = Color(0xFFFBF8F2),
        cardBackground = Color(0xFFFFFFFF),
        elevatedCard = Color(0xFFF3EDE2),
        accent = Color(0xFFD97706),
        accentContainer = Color(0x22D97706),
        textPrimary = Color(0xFF1C1917),
        textSecondary = Color(0xFF57534E),
        textTertiary = Color(0xFF78716C),
        divider = Color(0xFFE7E5E4),
        isDark = false
    ),
    E_INK(
        id = "e_ink",
        displayName = "E-Ink Minimal",
        subtitle = "Kindle Monochrome & Paper White",
        category = ThemeCategory.PAPER,
        background = Color(0xFFF5F5F3),
        cardBackground = Color(0xFFFFFFFF),
        elevatedCard = Color(0xFFEAEAE6),
        accent = Color(0xFF18181B),
        accentContainer = Color(0x1A18181B),
        textPrimary = Color(0xFF09090B),
        textSecondary = Color(0xFF3F3F46),
        textTertiary = Color(0xFF71717A),
        divider = Color(0xFFE4E4E7),
        isDark = false
    ),
    STUDIO_LIGHT(
        id = "studio_light",
        displayName = "Studio Light",
        subtitle = "Architect Pure White & Electric Blue",
        category = ThemeCategory.PAPER,
        background = Color(0xFFF8FAFC),
        cardBackground = Color(0xFFFFFFFF),
        elevatedCard = Color(0xFFEDF2F7),
        accent = Color(0xFF0284C7),
        accentContainer = Color(0x220284C7),
        textPrimary = Color(0xFF0F172A),
        textSecondary = Color(0xFF475569),
        textTertiary = Color(0xFF64748B),
        divider = Color(0xFFE2E8F0),
        isDark = false
    ),
    WARM_LINEN(
        id = "warm_linen",
        displayName = "Warm Linen",
        subtitle = "Editorial Muted Sage & Terracotta",
        category = ThemeCategory.PAPER,
        background = Color(0xFFFAF7F2),
        cardBackground = Color(0xFFFFFFFF),
        elevatedCard = Color(0xFFF0EAE0),
        accent = Color(0xFFC2410C),
        accentContainer = Color(0x22C2410C),
        textPrimary = Color(0xFF292524),
        textSecondary = Color(0xFF57534E),
        textTertiary = Color(0xFF78716C),
        divider = Color(0xFFE7E5E4),
        isDark = false
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

val isThemeDark: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LocalMonolithTheme.current.isDark
