package com.cinex.player.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val CineXColorScheme = darkColorScheme(
    primary = DeepRed,
    secondary = CineGold,
    tertiary = SelectedText,
    background = DarkBackground,
    surface = SurfaceColor,
    onPrimary = TextWhite,
    onSecondary = DarkBackground,
    onTertiary = DarkBackground,
    onBackground = TextWhite,
    onSurface = TextWhite
)

@Composable
fun CineXPlayerTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = CineXColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = CineX_DeepRed.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
