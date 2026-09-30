package com.eastonseidel.joyncon.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JoynConGreen = Color(0xFF00E5A0)
private val JoynConBlue = Color(0xFF00C2FF)

private val DarkColors = darkColorScheme(
    primary = JoynConGreen,
    secondary = JoynConBlue,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00875A),
    secondary = Color(0xFF0077A8),
)

@Composable
fun JoynConTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
