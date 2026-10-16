package com.monolith.notes.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val iOSColorScheme = darkColorScheme(
    primary = iOSYellow,
    onPrimary = Color.Black,
    primaryContainer = iOSYellowContainer,
    onPrimaryContainer = iOSYellow,
    secondary = iOSEmerald,
    onSecondary = Color.Black,
    background = iOSBackground,
    onBackground = iOSTextPrimary,
    surface = iOSBackground,
    onSurface = iOSTextPrimary,
    surfaceVariant = iOSCardBackground,
    onSurfaceVariant = iOSTextSecondary,
    surfaceContainerLowest = iOSBackground,
    surfaceContainerLow = iOSCardBackground,
    surfaceContainer = iOSCardBackground,
    surfaceContainerHigh = iOSElevatedCard,
    surfaceContainerHighest = iOSElevatedCard,
    outline = iOSDivider
)

@Composable
fun MonolithTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = iOSColorScheme,
        typography = MonolithTypography,
        content = content
    )
}
