package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.service.BankSyncManager
import com.example.ui.theme.*

/**
 * High-fidelity, user-friendly 'Notification Access' permission request dialog.
 * Clearly explains why reading SMS/banking notifications is essential to auto-track
 * banking, credit card, and merchant transactions without manual entry.
 *
 * If the user has already denied or needs system guidance, an intuitive
 * step-by-step walkthrough guides them directly into Android System Settings.
 */
@Composable
fun NotificationAccessRequestDialog(
    onDismiss: () -> Unit,
    onGrantClicked: () -> Unit = {},
    onSimulateTest: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var showStepByStepGuide by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight()
                .padding(vertical = 16.dp)
                .testTag("notification_access_request_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            tonalElevation = 6.dp,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header badge with bell & shield badge
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFFE0E7FF), Color(0xFFEFF6FF))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = "Notification Access",
                        tint = IndBlue,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Title
                Text(
                    text = "Automate Expense Tracking",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = IndNavyHeader,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Zero manual typing • Instant bank balance & debit detection",
                    fontSize = 12.sp,
                    color = IndTextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                )

                // Value proposition card explaining WHY we need notification access
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = IndCardSecondary,
                    border = androidx.compose.foundation.BorderStroke(1.dp, IndBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FeatureReasonRow(
                            icon = Icons.Filled.AccountBalance,
                            iconTint = IndBlue,
                            title = "Instant Bank & Credit Card Sync",
                            subtitle = "Reads official SMS alerts from HDFC, SBI, ICICI, Axis & others when you spend or receive money."
                        )

                        HorizontalDivider(color = IndBorderSubtle, thickness = 1.dp)

                        FeatureReasonRow(
                            icon = Icons.Filled.Storefront,
                            iconTint = Color(0xFFC2410C),
                            title = "Auto-Classifies Merchants",
                            subtitle = "Categorizes Swiggy, Zomato, Uber, Zepto, Blinkit & grocery bills automatically into your monthly budget."
                        )

                        HorizontalDivider(color = IndBorderSubtle, thickness = 1.dp)

                        FeatureReasonRow(
                            icon = Icons.Filled.Sync,
                            iconTint = IndGreenDark,
                            title = "Accurate Balance Reconciliations",
                            subtitle = "Updates your actual bank account balance seamlessly so your salary sheet is always up to date."
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Privacy & Security Guarantee Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF0FDF4),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = "Safe & Secure",
                            tint = Color(0xFF15803D),
                            modifier = Modifier
                                .size(20.dp)
                                .padding(top = 2.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "100% On-Device & OTP Shielded",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF14532D)
                            )
                            Text(
                                text = "Personal chats (WhatsApp, contacts) are completely ignored. Sensitive OTPs and passwords are automatically discarded and never stored.",
                                fontSize = 11.sp,
                                color = Color(0xFF166534),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                // Expandable step-by-step system settings guide (when denied or troubleshooting)
                AnimatedVisibility(
                    visible = showStepByStepGuide,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFFEF3C7))
                            .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = null,
                                tint = Color(0xFFB45309),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "How to enable in Android Settings:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF92400E)
                            )
                        }

                        SettingsGuideStep(step = "1", text = "Tap 'Open Android Settings' below.")
                        SettingsGuideStep(step = "2", text = "Locate and tap 'FinMoney' in the Device & App Notifications list.")
                        SettingsGuideStep(step = "3", text = "Toggle 'Allow notification access' ON.")
                        SettingsGuideStep(step = "4", text = "Return to FinMoney — live transaction auto-detection will be instantly active!")
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Primary Action Button: Open Notification Access Settings
                Button(
                    onClick = {
                        onGrantClicked()
                        val success = openNotificationAccessSettings(context)
                        if (!success) {
                            showStepByStepGuide = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("enable_notification_access_btn"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IndBlue)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SettingsSuggest,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Allow Notification Access",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Secondary toggle: show settings steps / open app details if denied
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { showStepByStepGuide = !showStepByStepGuide },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (showStepByStepGuide) Icons.Filled.ExpandLess else Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = IndTextSecondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showStepByStepGuide) "Hide Guide" else "Need Help / Denied?",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = IndTextSecondary
                        )
                    }

                    if (onSimulateTest != null) {
                        TextButton(
                            onClick = onSimulateTest,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("dialog_test_simulate_alert_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = IndBlue
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Test Sample Alert",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = IndBlue
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Dismiss Button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Not Now, I'll Enter Manually",
                        color = IndTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/**
 * Banner shown on home/salary screens when Notification Access is disabled or denied.
 * Offers a friendly, informative callout explaining why enabling it saves time.
 */
@Composable
fun NotificationAccessPromptCard(
    onEnableClicked: () -> Unit,
    onDismissBanner: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("notification_access_prompt_card"),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFEFF6FF),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFDBEAFE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = null,
                            tint = IndBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Auto-Detect Bank & UPI Alerts",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = IndNavyHeader
                        )
                        Text(
                            text = "Enable notification access to log debits from HDFC, SBI, Swiggy & more automatically.",
                            fontSize = 11.sp,
                            color = IndTextSecondary,
                            lineHeight = 15.sp
                        )
                    }
                }

                if (onDismissBanner != null) {
                    IconButton(
                        onClick = onDismissBanner,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Dismiss",
                            tint = IndTextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onEnableClicked,
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IndBlue),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Enable Auto-Sync",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = { openSystemAppSettings(context) },
                    modifier = Modifier.height(34.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF94A3B8))
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = IndTextSecondary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Settings",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = IndTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureReasonRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp)
            )
        }
        Column {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = IndNavyHeader
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = IndTextSecondary,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun SettingsGuideStep(
    step: String,
    text: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(Color(0xFFD97706)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = text,
            fontSize = 11.sp,
            color = Color(0xFF78350F),
            lineHeight = 15.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Safely opens Android Notification Listener settings screen.
 * Falls back to general app settings if direct action fails.
 */
fun openNotificationAccessSettings(context: Context): Boolean {
    return try {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        try {
            openSystemAppSettings(context)
            false
        } catch (e2: Exception) {
            false
        }
    }
}

/**
 * Opens this application's Android system settings screen (e.g. if permissions were permanently denied).
 */
fun openSystemAppSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallback)
        } catch (e2: Exception) {
            // ignore
        }
    }
}
