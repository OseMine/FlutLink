package com.flutcloud.flutlink.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Material 3 "Expressive": softer, larger radii and pill/capsule containers
// for interactive elements. The FlutLink desktop UI uses the same scale
// (4/8/12/16/28 px → full).
val FlutShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)