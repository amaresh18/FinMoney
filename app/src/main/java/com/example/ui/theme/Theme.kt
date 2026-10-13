package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = IndBlue,
    secondary = IndGreen,
    background = IndBackground,
    surface = IndSurface,
    onBackground = IndTextPrimary,
    onSurface = IndTextPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = IndBlue,
    secondary = IndGreen,
    background = IndBackground,
    surface = IndSurface,
    onBackground = IndTextPrimary,
    onSurface = IndTextPrimary
)

@Composable
fun FinMoneyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    AppTheme {
        MaterialTheme(
            colorScheme = LightColorScheme,
            typography = Typography,
            content = content
        )
    }
}

@Composable
fun FinAuthTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
