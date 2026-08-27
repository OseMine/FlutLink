package com.flutcloud.flutlink.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// FlutLink shape scale mirrors the desktop CSS:
// 8 px (inputs/buttons) → 12 px (cards/inner) → 16 px (modals) → 20 px (large)
val FlutShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)