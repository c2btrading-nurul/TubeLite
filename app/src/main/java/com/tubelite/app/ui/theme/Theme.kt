package com.tubelite.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TubeLiteDarkColors = darkColorScheme(
    primary = Color(0xFFFF4C4C),
    secondary = Color(0xFF8AB4F8),
    background = Color(0xFF0F0F0F),
    surface = Color(0xFF181818),
    onBackground = Color(0xFFF1F1F1),
    onSurface = Color(0xFFF1F1F1)
)

@Composable
fun TubeLiteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TubeLiteDarkColors,
        content = content
    )
}
