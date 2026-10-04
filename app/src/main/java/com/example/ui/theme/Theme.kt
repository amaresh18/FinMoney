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
    primary = IndGreen,
    onPrimary = Color.White,
    primaryContainer = IndGreenLight,
    onPrimaryContainer = IndGreenDark,
    secondary = IndBlue,
    onSecondary = Color.White,
    secondaryContainer = IndBlueLight,
    onSecondaryContainer = IndBlueDark,
    tertiary = IndNavyHeader,
    onTertiary = Color.White,
    tertiaryContainer = IndCyanLight,
    onTertiaryContainer = IndCyan,
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
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = IndNavyHeader.toArgb()
                window.navigationBarColor = Color.White.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = IndMoneyColorScheme,
        typography = Typography,
        content = content
    )
}
