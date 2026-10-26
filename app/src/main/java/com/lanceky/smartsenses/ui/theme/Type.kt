package com.lanceky.smartsenses.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily

private val base = Typography()
private val mono = FontFamily.Monospace

/** Material 3 type scale with the phone's own monospace font everywhere. */
val MonoTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = mono),
    displayMedium = base.displayMedium.copy(fontFamily = mono),
    displaySmall = base.displaySmall.copy(fontFamily = mono),
    headlineLarge = base.headlineLarge.copy(fontFamily = mono),
    headlineMedium = base.headlineMedium.copy(fontFamily = mono),
    headlineSmall = base.headlineSmall.copy(fontFamily = mono),
    titleLarge = base.titleLarge.copy(fontFamily = mono),
    titleMedium = base.titleMedium.copy(fontFamily = mono),
    titleSmall = base.titleSmall.copy(fontFamily = mono),
    bodyLarge = base.bodyLarge.copy(fontFamily = mono),
    bodyMedium = base.bodyMedium.copy(fontFamily = mono),
    bodySmall = base.bodySmall.copy(fontFamily = mono),
    labelLarge = base.labelLarge.copy(fontFamily = mono),
    labelMedium = base.labelMedium.copy(fontFamily = mono),
    labelSmall = base.labelSmall.copy(fontFamily = mono),
)
