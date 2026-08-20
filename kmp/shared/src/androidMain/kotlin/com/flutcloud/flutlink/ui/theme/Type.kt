package com.flutcloud.flutlink.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Material 3 "Expressive" typography: heavier weights and tighter tracking on
// the display/headline roles, keeping body copy neutral.
private val Expressive = Typography()

val FlutTypography = Typography(
    displayLarge = Expressive.displayLarge.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp
    ),
    displayMedium = Expressive.displayMedium.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp
    ),
    displaySmall = Expressive.displaySmall.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = Expressive.headlineLarge.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.25).sp
    ),
    headlineMedium = Expressive.headlineMedium.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.25).sp
    ),
    headlineSmall = Expressive.headlineSmall.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.25).sp
    ),
    titleLarge = Expressive.titleLarge.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.25).sp
    ),
    titleMedium = Expressive.titleMedium.copy(
        fontWeight = FontWeight.SemiBold
    ),
    titleSmall = Expressive.titleSmall.copy(
        fontWeight = FontWeight.SemiBold
    ),
    bodyLarge = Expressive.bodyLarge.copy(fontFamily = FontFamily.Default),
    labelLarge = Expressive.labelLarge.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp
    )
)