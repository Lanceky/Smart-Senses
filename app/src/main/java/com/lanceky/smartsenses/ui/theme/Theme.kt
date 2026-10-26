package com.lanceky.smartsenses.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ConsoleColors = darkColorScheme(
    primary = Neon,
    onPrimary = Black,
    secondary = TextDim,
    onSecondary = Black,
    background = Black,
    onBackground = TextMain,
    surface = Panel,
    onSurface = TextMain,
    surfaceVariant = PanelRaised,
    onSurfaceVariant = TextDim,
    outline = Border,
    error = Red,
    onError = Black,
)

@Composable
fun SmartSensesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ConsoleColors, typography = MonoTypography, content = content)
}
