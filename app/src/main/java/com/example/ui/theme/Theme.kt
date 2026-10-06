package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val IndMoneyColorScheme = lightColorScheme(
    primary = IndBlue,
    onPrimary = Color.White,
    primaryContainer = IndBlueLight,
    onPrimaryContainer = IndBlueDark,
    secondary = IndNavyHeader,
    onSecondary = Color.White,
    secondaryContainer = IndCardSecondary,
    onSecondaryContainer = IndNavyHeader,
    tertiary = IndBlueDark,
    onTertiary = Color.White,
    background = IndBackground,
    onBackground = IndTextPrimary,
    surface = IndSurface,
    onSurface = IndTextPrimary,
    surfaceVariant = IndCardSecondary,
    onSurfaceVariant = IndTextSecondary,
    outline = IndBorder,
    outlineVariant = IndBorderSubtle,
    error = IndRed,
    onError = Color.White,
    errorContainer = IndRedLight,
    onErrorContainer = IndRedDark
)

@Composable
fun FinMoneyTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = IndMoneyColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = IndSurface.toArgb()
                window.navigationBarColor = IndSurface.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = true
                insetsController.isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

