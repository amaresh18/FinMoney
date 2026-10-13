package com.example.ui.theme

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Centralized theme constants and single-source design system properties
 * following INDmoney premium visual design specifications.
 */
object ThemeConstants {

    // 🎨 Standardized Color Palette
    object Colors {
        // Brand Primary & Accent
        val IndBlue = Color(0xFF1A56DB)
        val IndBlueDark = Color(0xFF1E40AF)
        val IndBlueLight = Color(0xFFEBF5FF)
        val IndNavyHeader = Color(0xFF1E293B)
        
        // Semantic Accents
        val IndGreen = Color(0xFF10B981)
        val IndGreenDark = Color(0xFF047857)
        val IndGreenLight = Color(0xFFD1FAE5)
        
        val IndRed = Color(0xFFEF4444)
        val IndRedDark = Color(0xFFB91C1C)
        val IndRedLight = Color(0xFFFEE2E2)
        
        val IndAmber = Color(0xFFF59E0B)
        val IndAmberLight = Color(0xFFFEF3C7)
        
        val IndCyan = Color(0xFF06B6D4)
        val IndCyanDark = Color(0xFF0891B2)
        val IndCyanLight = Color(0xFFCFFAFE)
        
        val IndPurple = Color(0xFF8B5CF6)
        val IndPurpleLight = Color(0xFFEDE9FE)

        // Surfaces & Backgrounds
        val Background = Color(0xFFF8FAFC)
        val Surface = Color(0xFFFFFFFF)
        val CardSecondary = Color(0xFFF1F5F9)
        val CardHighlight = Color(0xFFE2E8F0)
        
        // Borders
        val Border = Color(0xFFE2E8F0)
        val BorderSubtle = Color(0xFFF1F5F9)
        
        // Typography Colors
        val TextPrimary = Color(0xFF0F172A)
        val TextSecondary = Color(0xFF64748B)
        val TextMuted = Color(0xFF94A3B8)
        
        // Dark Mode / Specialized
        val DarkBg = Color(0xFF0F172A)
        val DarkCard = Color(0xFF1E293B)
        val DarkBorder = Color(0xFF334155)
    }

    // 📐 Standardized Dimensions & Spacing Values
    object Dimensions {
        val SpaceExtraSmall: Dp = 4.dp
        val SpaceSmall: Dp = 8.dp
        val SpaceMedium: Dp = 12.dp
        val SpaceLarge: Dp = 16.dp
        val SpaceExtraLarge: Dp = 24.dp
        val SpaceHuge: Dp = 32.dp

        val ScreenPaddingHorizontal: Dp = 16.dp
        val ScreenPaddingVertical: Dp = 16.dp
        val CardPadding: Dp = 16.dp
        val DialogPadding: Dp = 20.dp

        // Corner Radii
        val RadiusSmall: Dp = 8.dp
        val RadiusMedium: Dp = 12.dp
        val RadiusLarge: Dp = 16.dp
        val RadiusDialog: Dp = 20.dp
        val RadiusPill: Dp = 28.dp

        // Icon Sizes
        val IconSmall: Dp = 16.dp
        val IconMedium: Dp = 24.dp
        val IconLarge: Dp = 32.dp
        val IconHuge: Dp = 48.dp

        // Minimum Touch Target
        val MinTouchTarget: Dp = 48.dp
    }

    // 🔤 Standardized Typography Styles
    object Typography {
        val DisplayLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            color = Colors.TextPrimary
        )
        val DisplayMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            color = Colors.TextPrimary
        )
        val TitleLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            color = Colors.TextPrimary
        )
        val TitleMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            color = Colors.TextPrimary
        )
        val TitleSmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = Colors.TextPrimary
        )
        val BodyLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            color = Colors.TextPrimary
        )
        val BodyMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = Colors.TextSecondary
        )
        val BodySmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = Colors.TextMuted
        )
        val LabelBold = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            lineHeight = 18.sp
        )
        val LabelMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
        val LabelSmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            lineHeight = 14.sp
        )
    }

    // 🧱 Standardized Vertical & Horizontal Spacers
    object Spacers {
        @Composable fun ExtraSmall() = Spacer(modifier = Modifier.height(Dimensions.SpaceExtraSmall))
        @Composable fun Small() = Spacer(modifier = Modifier.height(Dimensions.SpaceSmall))
        @Composable fun Medium() = Spacer(modifier = Modifier.height(Dimensions.SpaceMedium))
        @Composable fun Large() = Spacer(modifier = Modifier.height(Dimensions.SpaceLarge))
        @Composable fun ExtraLarge() = Spacer(modifier = Modifier.height(Dimensions.SpaceExtraLarge))
        @Composable fun Huge() = Spacer(modifier = Modifier.height(Dimensions.SpaceHuge))

        @Composable fun HorizontalExtraSmall() = Spacer(modifier = Modifier.width(Dimensions.SpaceExtraSmall))
        @Composable fun HorizontalSmall() = Spacer(modifier = Modifier.width(Dimensions.SpaceSmall))
        @Composable fun HorizontalMedium() = Spacer(modifier = Modifier.width(Dimensions.SpaceMedium))
        @Composable fun HorizontalLarge() = Spacer(modifier = Modifier.width(Dimensions.SpaceLarge))
        @Composable fun HorizontalExtraLarge() = Spacer(modifier = Modifier.width(Dimensions.SpaceExtraLarge))
    }
}
