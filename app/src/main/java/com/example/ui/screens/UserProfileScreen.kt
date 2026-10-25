package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.BankSyncManager
import com.example.ui.FinMoneyViewModel
import com.example.ui.components.IndCard
import com.example.ui.theme.*
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

/**
 * User Profile & Notification Settings Screen
 * Displays authenticated user account details, notification interceptor toggles,
 * budget preferences, and sign-out controls matching INDmoney fintech design standards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    viewModel: FinMoneyViewModel,
    onBack: () -> Unit = {}
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val firebaseUser = remember {
        try {
            com.google.firebase.Firebase.auth.currentUser
        } catch (_: Throwable) {
            null
        }
    }
    val currencySymbol by viewModel.currencySymbol.collectAsStateWithLifecycle()
    val isFinancialSafetyVisible by viewModel.isFinancialSafetyVisible.collectAsStateWithLifecycle()

    val effectiveName = remember(userProfile, firebaseUser) {
        userProfile?.name?.takeIf { it != "Signed Out" && it.isNotBlank() }
            ?: firebaseUser?.displayName?.takeIf { it.isNotBlank() }
            ?: "FinMoney Member"
    }
    val effectiveEmail = remember(userProfile, firebaseUser) {
        userProfile?.email?.takeIf { it.isNotBlank() }
            ?: firebaseUser?.email?.takeIf { it.isNotBlank() }
            ?: ""
    }
    val effectivePhone = remember(userProfile) {
        userProfile?.phoneNumber?.takeIf { it.isNotBlank() } ?: ""
    }
    val phoneVerified = userProfile?.isOtpVerified == true
    val emailVerified = userProfile?.isEmailVerified == true

    LaunchedEffect(Unit) {
        viewModel.syncVerificationStatusFromFirebaseAsync()
    }

    var nameInput by remember(effectiveName) { mutableStateOf(effectiveName) }
    var emailInput by remember(effectiveEmail) { mutableStateOf(effectiveEmail) }
    var phoneInput by remember(effectivePhone) { mutableStateOf(effectivePhone) }

    var isEditProfileMode by remember { mutableStateOf(false) }

    // Notification Settings States
    var enableSmsInterceptor by remember { mutableStateOf(true) }
    var enableInstantAlerts by remember { mutableStateOf(true) }
    var enableDueReminders by remember { mutableStateOf(true) }

    var showSignOutDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showDataSafetyDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = IndBackground,
        topBar = {
            Surface(
                color = IndSurface,
                shadowElevation = 2.dp,
                border = BorderStroke(1.dp, IndBorderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("profile_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = IndNavyHeader
                            )
                        }
                        com.example.ui.components.BrandLogo(
                            name = "FinMoney",
                            badgeSize = 28.dp
                        )
                        Text(
                            text = "Profile & Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = IndNavyHeader
                        )
                    }

                    TextButton(
                        onClick = { showSignOutDialog = true },
                        modifier = Modifier.testTag("top_sign_out_button")
                    ) {
                        Text(
                            text = "Sign Out",
                            fontWeight = FontWeight.Bold,
                            color = IndRed,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // 1. Authenticated User Profile Summary Card
            IndCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(IndGreenLight)
                                    .border(2.dp, IndGreen, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (effectiveName.trim().take(1).takeIf { it.isNotBlank() } ?: "A").uppercase(),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = IndGreenDark
                                )
                            }

                            Column {
                                Text(
                                    text = effectiveName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = IndNavyHeader
                                )
                                Text(
                                    text = effectiveEmail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = IndTextSecondary
                                )
                                if (effectivePhone.isNotBlank()) {
                                    Text(
                                        text = "+91 $effectivePhone",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = IndTextMuted
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { isEditProfileMode = !isEditProfileMode },
                            modifier = Modifier.testTag("edit_profile_toggle")
                        ) {
                            Icon(
                                imageVector = if (isEditProfileMode) Icons.Filled.Check else Icons.Outlined.Edit,
                                contentDescription = "Edit Profile",
                                tint = IndBlue
                            )
                        }
                    }

                    // Security & Account Verification Badges
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (phoneVerified) IndGreenLight else IndSurface,
                            border = BorderStroke(1.dp, if (phoneVerified) IndGreen.copy(alpha = 0.3f) else IndBorderSubtle)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    if (phoneVerified) Icons.Filled.Verified else Icons.Outlined.PhoneAndroid,
                                    contentDescription = null,
                                    tint = if (phoneVerified) IndGreenDark else IndTextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    if (phoneVerified) "Mobile verified" else "Mobile pending",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (phoneVerified) IndGreenDark else IndTextSecondary
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (emailVerified) IndGreenLight else IndSurface,
                            border = BorderStroke(1.dp, if (emailVerified) IndGreen.copy(alpha = 0.3f) else IndBorderSubtle)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    if (emailVerified) Icons.Filled.MarkEmailRead else Icons.Outlined.Email,
                                    contentDescription = null,
                                    tint = if (emailVerified) IndGreenDark else IndTextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    if (emailVerified) "Email verified" else "Email pending",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (emailVerified) IndGreenDark else IndTextSecondary
                                )
                            }
                        }
                    }

                    // Inline Edit Profile Fields
                    if (isEditProfileMode) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                label = { Text("Full Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (effectivePhone.isNotBlank()) {
                                OutlinedTextField(
                                    value = "+91 ${effectivePhone.takeLast(10)}",
                                    onValueChange = {},
                                    readOnly = true,
                                    enabled = false,
                                    label = { Text("Mobile (OTP verified)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (effectiveEmail.isNotBlank()) {
                                OutlinedTextField(
                                    value = effectiveEmail,
                                    onValueChange = {},
                                    readOnly = true,
                                    enabled = false,
                                    label = { Text("Email") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Text(
                                "Phone and email can only be changed after re-verification. Sign out and complete onboarding to update.",
                                fontSize = 11.sp,
                                color = IndTextSecondary
                            )

                            Button(
                                onClick = {
                                    viewModel.saveUserProfile(
                                        name = nameInput,
                                        email = effectiveEmail,
                                        phoneNumber = effectivePhone,
                                        isOtpVerified = phoneVerified,
                                        isEmailVerified = emailVerified
                                    )
                                    isEditProfileMode = false
                                    android.widget.Toast.makeText(context, "Profile updated successfully!", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = IndBlue),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Save Profile Changes", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 2. Notification & Transaction Interceptor Settings Card
            IndCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = IndBlue, modifier = Modifier.size(20.dp))
                        Text(
                            text = "NOTIFICATIONS & AUTO-SYNC",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = IndBlueDark
                        )
                    }

                    // Toggle 1: Notification Interceptor
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Bank SMS & UPI Interceptor", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = IndNavyHeader)
                            Text("Auto-detect transactions from HDFC, SBI, PhonePe & Google Pay", fontSize = 11.sp, color = IndTextSecondary)
                        }
                        Switch(
                            checked = enableSmsInterceptor,
                            onCheckedChange = { enableSmsInterceptor = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = IndBlue)
                        )
                    }

                    Divider(color = IndBorderSubtle)

                    // Toggle 2: Instant Approval Alerts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Instant Approval Alerts", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = IndNavyHeader)
                            Text("Show 1-tap Approve/Dismiss buttons in notification shade", fontSize = 11.sp, color = IndTextSecondary)
                        }
                        Switch(
                            checked = enableInstantAlerts,
                            onCheckedChange = { enableInstantAlerts = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = IndBlue)
                        )
                    }

                    Divider(color = IndBorderSubtle)

                    // Toggle 3: EMI & Bill Dues Reminders
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Monthly Due Reminders", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = IndNavyHeader)
                            Text("Receive alerts 2 days before EMI, Rent & Bill due dates", fontSize = 11.sp, color = IndTextSecondary)
                        }
                        Switch(
                            checked = enableDueReminders,
                            onCheckedChange = { enableDueReminders = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = IndBlue)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = BankSyncManager.createNotificationSettingsIntent()
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Open Settings -> Notification Access", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        border = BorderStroke(1.dp, IndBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("grant_notification_access_button")
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = IndBlue, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Grant Notification Listener Permission", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = IndBlue)
                    }
                }
            }

            // 3. Application Security & Preferences Card
            IndCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Tune, contentDescription = null, tint = IndBlue, modifier = Modifier.size(20.dp))
                        Text(
                            text = "PREFERENCES & PRIVACY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = IndBlueDark
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Financial Safety Privacy Mode", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = IndNavyHeader)
                            Text("Mask sensitive balances and total income on dashboard", fontSize = 11.sp, color = IndTextSecondary)
                        }
                        Switch(
                            checked = !isFinancialSafetyVisible,
                            onCheckedChange = { viewModel.toggleFinancialSafetyVisible() },
                            colors = SwitchDefaults.colors(checkedThumbColor = IndBlue)
                        )
                    }

                    Divider(color = IndBorderSubtle)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showResetDialog = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Reset Local Budget Data", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = IndRed)
                            Text("Re-seed starter template dataset (October 2026)", fontSize = 11.sp, color = IndTextSecondary)
                        }
                        Icon(Icons.Filled.RestartAlt, contentDescription = null, tint = IndRed, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // 4. Account Deletion & Legal Policies Card
            fun openUrlInBrowser(url: String) {
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Could not open link", android.widget.Toast.LENGTH_SHORT).show()
                }
            }

            IndCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.PrivacyTip, contentDescription = null, tint = IndBlue, modifier = Modifier.size(20.dp))
                        Text(
                            text = "ACCOUNT DELETION & LEGAL POLICIES",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = IndBlueDark
                        )
                    }

                    // Account & Data Deletion Link
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                openUrlInBrowser("https://docs.google.com/forms/d/e/1FAIpQLScYFK0kYaruDdJjmtrwwh33q4wXq3M0RL2xH_Hq18ZRTuvZOg/viewform")
                            }
                            .testTag("request_account_deletion_link"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Request Account & Data Deletion", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = IndRed)
                            Text("Permanently delete your account, phone number & financial records", fontSize = 11.sp, color = IndTextSecondary)
                        }
                        Icon(Icons.Filled.OpenInNew, contentDescription = "Open Form", tint = IndRed, modifier = Modifier.size(18.dp))
                    }

                    Divider(color = IndBorderSubtle)

                    // Privacy Policy Link
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                openUrlInBrowser("https://gist.githubusercontent.com/amareshgajawada/raw/finmoney_privacy_policy.txt")
                            }
                            .testTag("privacy_policy_link"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Privacy Policy", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = IndNavyHeader)
                            Text("Read our Google Play compliant Privacy Policy", fontSize = 11.sp, color = IndTextSecondary)
                        }
                        Icon(Icons.Filled.OpenInNew, contentDescription = "Open Privacy Policy", tint = IndBlue, modifier = Modifier.size(18.dp))
                    }

                    Divider(color = IndBorderSubtle)

                    // Data Safety Disclosures Info
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDataSafetyDialog = true }
                            .testTag("data_safety_disclosures_link"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Data Safety & Disclosures", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = IndNavyHeader)
                            Text("View how personal info & phone numbers are protected", fontSize = 11.sp, color = IndTextSecondary)
                        }
                        Icon(Icons.Filled.Info, contentDescription = "View Disclosures", tint = IndBlue, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // 5. Account Switch & Sign Out Buttons
            OutlinedButton(
                onClick = {
                    viewModel.signOut()
                    onBack()
                },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, IndBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("switch_account_button")
            ) {
                Icon(Icons.Filled.SwitchAccount, contentDescription = null, tint = IndBlue, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Switch Google / Mobile Account", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = IndBlue)
            }

            Button(
                onClick = { showSignOutDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = IndRed),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("sign_out_profile_button")
            ) {
                Icon(Icons.Filled.Logout, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Out of FinMoney", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showDataSafetyDialog) {
            AlertDialog(
                onDismissRequest = { showDataSafetyDialog = false },
                title = { Text("Data Safety & Privacy Information", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("FinMoney collects user data strictly for core app functionality and account management:\n", fontSize = 12.sp)
                        Text("• Phone Number: Collected for account identification and SMS bank sync verification.", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("• Name & Email Address: Collected for account creation, Google Sign-In, and personal ledger sync.", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("• Contacts: Optional permission used solely to select borrower/lender contacts when recording Lent & Borrowed Khaata entries. Contacts are never shared or uploaded to third parties.", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("• Encryption: All transmitted data is encrypted in transit using SSL/TLS protocols.", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("• Third-Party Sharing: We DO NOT share or sell personal data to any third parties.", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("• Right to Deletion: You can request complete deletion of your account and data at any time via the Account Deletion Form link.", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showDataSafetyDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = IndBlue)
                    ) {
                        Text("Got It", color = Color.White)
                    }
                }
            )
        }
    }

    // Sign Out Confirmation Dialog
    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign Out Confirmation", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to sign out? Your cloud synced data remains safe in Firebase.") },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutDialog = false
                        viewModel.signOut()
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndRed)
                ) {
                    Text("Sign Out", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Reset Data Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Starter Ledger?", fontWeight = FontWeight.Bold) },
            text = { Text("This will re-seed the October 2026 starter budget dataset (₹100,000 Income & 42 debits).") },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDialog = false
                        viewModel.restoreSampleLedger()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndBlue)
                ) {
                    Text("Reset Data", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
