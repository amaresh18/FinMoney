package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class Dimensions(
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 12.dp,
    val large: Dp = 16.dp,
    val extraLarge: Dp = 24.dp,
    val huge: Dp = 32.dp,
    
    // Semantic paddings
    val paddingExtraSmall: Dp = 4.dp,
    val paddingSmall: Dp = 8.dp,
    val paddingMedium: Dp = 12.dp,
    val paddingLarge: Dp = 16.dp,
    val paddingExtraLarge: Dp = 24.dp,
    val screenHorizontal: Dp = 16.dp,
    val screenVertical: Dp = 16.dp,
    val cardPadding: Dp = 16.dp,
    val cardCornerRadius: Dp = 12.dp,
    val cardRadius: Dp = 16.dp,
    val buttonRadius: Dp = 28.dp,
    val chipCornerRadius: Dp = 8.dp,
    val chipRadius: Dp = 8.dp,
    
    // Icon sizes
    val iconSmall: Dp = 16.dp,
    val iconMedium: Dp = 24.dp,
    val iconLarge: Dp = 32.dp,
    val iconExtraLarge: Dp = 48.dp,

    // Touch targets & Layout constraints
    val minTouchTarget: Dp = 48.dp,
    val containerMaxWidth: Dp = 600.dp,

    // Strokes & Elevation
    val strokeThin: Dp = 1.dp,
    val strokeThick: Dp = 2.dp,
    val elevationLow: Dp = 2.dp,
    val elevationMedium: Dp = 4.dp,
    val elevationHigh: Dp = 8.dp,
    val dialogRadius: Dp = 20.dp
)

val LocalDimensions = staticCompositionLocalOf { Dimensions() }
