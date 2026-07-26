package com.vzlpr.controller.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    secondary = Color(0xFF00897B),
    background = Color(0xFFF5F6F8),
    surface = Color.White,
    error = Color(0xFFD32F2F)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF64B5F6),
    secondary = Color(0xFF4DB6AC),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E)
)

@Composable
fun VzTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
