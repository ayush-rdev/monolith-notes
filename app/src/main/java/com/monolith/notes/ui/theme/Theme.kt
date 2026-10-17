package com.monolith.notes.ui.theme

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

fun createMonolithColorScheme(theme: AppTheme): ColorScheme = if (theme.isDark) {
    darkColorScheme(
        primary = theme.accent,
        onPrimary = Color.Black,
        primaryContainer = theme.accentContainer,
        onPrimaryContainer = theme.accent,
        secondary = theme.accent,
        onSecondary = Color.Black,
        background = theme.background,
        onBackground = theme.textPrimary,
        surface = theme.background,
        onSurface = theme.textPrimary,
        surfaceVariant = theme.cardBackground,
        onSurfaceVariant = theme.textSecondary,
        surfaceContainerLowest = theme.background,
        surfaceContainerLow = theme.cardBackground,
        surfaceContainer = theme.cardBackground,
        surfaceContainerHigh = theme.elevatedCard,
        surfaceContainerHighest = theme.elevatedCard,
        outline = theme.divider
    )
} else {
    lightColorScheme(
        primary = theme.accent,
        onPrimary = Color.White,
        primaryContainer = theme.accentContainer,
        onPrimaryContainer = theme.accent,
        secondary = theme.accent,
        onSecondary = Color.White,
        background = theme.background,
        onBackground = theme.textPrimary,
        surface = theme.background,
        onSurface = theme.textPrimary,
        surfaceVariant = theme.cardBackground,
        onSurfaceVariant = theme.textSecondary,
        surfaceContainerLowest = theme.background,
        surfaceContainerLow = theme.cardBackground,
        surfaceContainer = theme.cardBackground,
        surfaceContainerHigh = theme.elevatedCard,
        surfaceContainerHighest = theme.elevatedCard,
        outline = theme.divider
    )
}

@Composable
fun MonolithTheme(
    theme: AppTheme = AppTheme.OBSIDIAN,
    content: @Composable () -> Unit
) {
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !theme.isDark
            controller.isAppearanceLightNavigationBars = !theme.isDark
        }
    }

    CompositionLocalProvider(LocalMonolithTheme provides theme) {
        MaterialTheme(
            colorScheme = createMonolithColorScheme(theme),
            typography = MonolithTypography,
            content = content
        )
    }
}
