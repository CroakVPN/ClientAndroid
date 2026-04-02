package com.croakvpn.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary          = Color(0xFF4CAF50),
    onPrimary        = Color.White,
    secondary        = Color(0xFF4CAF50),
    background       = Color(0xFF0A0A0A),
    surface          = Color(0xFF111827),
    onBackground     = Color.White,
    onSurface        = Color.White,
)

@Composable
fun CroakVpnTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color(0xFF0A0A0A).toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(
        colorScheme = DarkColors,
        content     = content
    )
}
