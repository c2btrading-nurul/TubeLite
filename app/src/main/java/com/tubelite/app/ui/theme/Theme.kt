package com.tubelite.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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

private val TubeLiteLightColors = lightColorScheme(
    primary = Color(0xFFD93025),
    secondary = Color(0xFF1A73E8),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF5F5F5),
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF1A1A1A)
)

@Composable
fun TubeLiteTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) TubeLiteDarkColors else TubeLiteLightColors,
        content = content
    )
}
