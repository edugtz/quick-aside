package com.edu.quickaside.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1558D6),
    secondary = Color(0xFF007D74),
    tertiary = Color(0xFF6B4FD3),
    surface = Color(0xFFFBF9FF),
    surfaceVariant = Color(0xFFF0F1F7),
)

@Composable
fun QuickAsideTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}
