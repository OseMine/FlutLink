package com.flutcloud.flutlink.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// FlutLink typography mirrors the desktop CSS hierarchy:
// Headings: weight 650/600, tight tracking; body: neutral; labels: 11px uppercase.
private val Base = Typography()

val FlutTypography = Typography(
    displayLarge = Base.displayLarge.copy(
        fontWeight = FontWeight(650),
        letterSpacing = (-0.5).sp
    ),
    displayMedium = Base.displayMedium.copy(
        fontWeight = FontWeight(650),
        letterSpacing = (-0.5).sp
    ),
    displaySmall = Base.displaySmall.copy(
        fontWeight = FontWeight(650),
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = Base.headlineLarge.copy(
        fontWeight = FontWeight(650),
        letterSpacing = (-0.4).sp
    ),
    headlineMedium = Base.headlineMedium.copy(
        fontWeight = FontWeight(600),
        letterSpacing = (-0.3).sp
    ),
    headlineSmall = Base.headlineSmall.copy(
        fontWeight = FontWeight(600),
        letterSpacing = (-0.2).sp
    ),
    titleLarge = Base.titleLarge.copy(
        fontWeight = FontWeight(600),
        letterSpacing = (-0.2).sp
    ),
    titleMedium = Base.titleMedium.copy(
        fontWeight = FontWeight(600)
    ),
    titleSmall = Base.titleSmall.copy(
        fontWeight = FontWeight(600)
    ),
    bodyLarge = Base.bodyLarge.copy(fontFamily = FontFamily.Default),
    bodyMedium = Base.bodyMedium,
    bodySmall = Base.bodySmall,
    labelLarge = Base.labelLarge.copy(
        fontWeight = FontWeight(500),
        letterSpacing = 0.1.sp
    ),
    labelMedium = Base.labelMedium.copy(
        fontWeight = FontWeight(500)
    ),
    labelSmall = Base.labelSmall.copy(
        fontWeight = FontWeight(600),
        letterSpacing = 0.5.sp
    )
)