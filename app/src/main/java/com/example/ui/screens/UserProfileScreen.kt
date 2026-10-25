package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.BankSyncManager
import com.example.ui.FinMoneyViewModel
import com.example.data.model.CustomCategory
import com.example.ui.components.AddCustomCategoryDialog
import com.example.ui.components.IndCard
import java.text.DateFormat
import java.util.Date
import com.example.ui.components.ManageCustomCategoriesDialog
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
    onBack: () -> Unit = {},
    /** When true, shows back + "Profile & Settings" + top Sign Out (standalone). Main tab uses false. */
    showShellHeader: Boolean = true
) {
    if (showShellHeader) {
        BackHandler { onBack() }
    }

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

    val cloudSyncUi by viewModel.cloudSyncUi.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.syncVerificationStatusFromFirebaseAsync()
        viewModel.refreshCloudSyncMeta()
    }

    val lastSyncLabel = remember(cloudSyncUi.lastSuccessAtMillis) {
        val at = cloudSyncUi.lastSuccessAtMillis
        if (at <= 0L) "Not synced yet"
        else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(at))
    }

    var nameInput by remember(effectiveName) { mutableStateOf(effectiveName) }

    var isEditProfileMode by remember { mutableStateOf(false) }

    val storedProfilePicUri = remember(userProfile, firebaseUser) {
        userProfile?.profilePicUri?.takeIf { it.isNotBlank() }
            ?: userProfile?.profileImageUri?.takeIf { it.isNotBlank() }
            ?: firebaseUser?.photoUrl?.toString()
    }
    var localProfilePicUri by remember { mutableStateOf<String?>(null) }
    val displayProfilePicUri = localProfilePicUri ?: storedProfilePicUri

    val profilePhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val uriString = uri.toString()
            localProfilePicUri = uriString
            viewModel.saveUserProfile(
                name = nameInput.ifBlank { effectiveName },
                email = effectiveEmail,
                phoneNumber = effectivePhone,
                profilePicUri = uriString,
                isOtpVerified = phoneVerified,
                isEmailVerified = emailVerified
            )
            android.widget.Toast.makeText(context, "Profile photo updated", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Notification Settings States
    var enableSmsInterceptor by remember { mutableStateOf(true) }
    var enableInstantAlerts by remember { mutableStateOf(true) }
    var enableDueReminders by remember { mutableStateOf(true) }

    var showSignOutDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showDataSafetyDialog by remember { mutableStateOf(false) }
    var showPhoneVerifyDialog by remember { mutableStateOf(false) }
    var showManageCustomCategoriesDialog by remember { mutableStateOf(false) }
    var editingCustomCategory by remember { mutableStateOf<CustomCategory?>(null) }
    val customCategories by viewModel.customCategories.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = IndBackground,
        topBar = {
            if (showShellHeader) {
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
            if (!showShellHeader) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Profile",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = IndNavyHeader
                    )
                    Text(
                        text = "Account, privacy & alerts",
                        style = MaterialTheme.typography.bodySmall,
                        color = IndTextSecondary
                    )
                }
            }

            // —— Profile hero ——
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color.Transparent,
                shadowElevation = 4.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(IndNavyHeader, IndBlue.copy(alpha = 0.92f))
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 22.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ProfileAvatarPicker(
                            displayName = effectiveName,
                            imageUri = displayProfilePicUri,
                            onChangePhoto = {
                                profilePhotoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.testTag("profile_avatar_picker")
                        )

                        Text(
                            text = effectiveName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (effectiveEmail.isNotBlank()) {
                            Text(
                                text = effectiveEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                        if (effectivePhone.isNotBlank()) {
                            Text(
                                text = "+91 ${effectivePhone.takeLast(10)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            ProfileVerificationChip(
                                verified = phoneVerified,
                                verifiedLabel = "Mobile verified",
                                pendingLabel = "Mobile pending",
                                iconVerified = Icons.Filled.Verified,
                                iconPending = Icons.Outlined.PhoneAndroid
                            )
                            ProfileVerificationChip(
                                verified = emailVerified,
                                verifiedLabel = "Email verified",
                                pendingLabel = "Email pending",
                                iconVerified = Icons.Filled.MarkEmailRead,
                                iconPending = Icons.Outlined.Email
                            )
                        }

                        FilledTonalButton(
                            onClick = { isEditProfileMode = !isEditProfileMode },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color.White.copy(alpha = 0.2f),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.testTag("edit_profile_toggle")
                        ) {
                            Icon(
                                imageVector = if (isEditProfileMode) Icons.Filled.Check else Icons.Outlined.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEditProfileMode) "Done editing" else "Edit display name",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Mobile Verification Card (Required for Lent & Borrow / Khaata access)
            IndCard(modifier = Modifier.fillMaxWidth().testTag("profile_mobile_verification_card")) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (phoneVerified) Icons.Filled.VerifiedUser else Icons.Filled.Security,
                                contentDescription = null,
                                tint = if (phoneVerified) IndGreen else IndRed,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "Mobile & Khaata Access",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = IndNavyHeader
                                )
                                Text(
                                    text = if (phoneVerified) "Verified • Lent & Borrow Unlocked" else "Verification required for Lent & Borrow",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (phoneVerified) IndGreen else IndRed,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (phoneVerified) IndGreenLight else Color(0xFFFEF3C7),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (phoneVerified) IndGreen else Color(0xFFF59E0B))
                        ) {
                            Text(
                                text = if (phoneVerified) "VERIFIED" else "PENDING",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (phoneVerified) IndGreenDark else Color(0xFFB45309),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (effectivePhone.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = IndNavyHeader, modifier = Modifier.size(16.dp))
                            Text(
                                text = "+91 ${effectivePhone.takeLast(10)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = IndNavyHeader
                            )
                        }
                    }

                    Text(
                        text = if (phoneVerified)
                            "Your mobile number is verified with OTP. You have full access to Lent & Borrow (Khaata peer ledger)."
                        else
                            "FinMoney uses your verified mobile number to secure peer loan agreements and debt settlement receipts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = IndTextSecondary,
                        lineHeight = 17.sp
                    )

                    Button(
                        onClick = { showPhoneVerifyDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (phoneVerified) IndSurface else IndBlue,
                            contentColor = if (phoneVerified) IndBlue else Color.White
                        ),
                        border = if (phoneVerified) androidx.compose.foundation.BorderStroke(1.dp, IndBlue) else null,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("profile_verify_phone_action")
                    ) {
                        Icon(
                            imageVector = if (phoneVerified) Icons.Outlined.Edit else Icons.Filled.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (phoneVerified) "Update Phone Number" else "Verify Mobile Number with OTP",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            if (isEditProfileMode) {
                IndCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProfileSectionTitle(
                            title = "Personal details",
                            subtitle = "Name shown on your dashboard & agreements",
                            icon = Icons.Outlined.Person
                        )
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Full name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = if (effectivePhone.isNotBlank()) "+91 ${effectivePhone.takeLast(10)}" else "—",
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            label = { Text("Mobile") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = effectiveEmail.ifBlank { "—" },
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            label = { Text("Email") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Text(
                            text = "To update and verify your mobile number, use the Mobile & Khaata Access card above.",
                            fontSize = 11.sp,
                            color = IndTextSecondary,
                            lineHeight = 15.sp
                        )
                        Button(
                            onClick = {
                                viewModel.saveUserProfile(
                                    name = nameInput,
                                    email = effectiveEmail,
                                    phoneNumber = effectivePhone,
                                    profilePicUri = displayProfilePicUri,
                                    isOtpVerified = phoneVerified,
                                    isEmailVerified = emailVerified
                                )
                                isEditProfileMode = false
                                android.widget.Toast.makeText(
                                    context,
                                    "Profile updated",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = IndBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save changes", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            IndCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileSectionTitle(
                        title = "Cloud backup & sync",
                        subtitle = "Linked to your Google account on Firebase",
                        icon = Icons.Filled.CloudSync
                    )
                    Text(
                        text = "Last successful sync: $lastSyncLabel",
                        fontSize = 12.sp,
                        color = IndTextSecondary
                    )
                    if (cloudSyncUi.pendingOutbox > 0) {
                        Text(
                            text = "${cloudSyncUi.pendingOutbox} change(s) waiting to upload",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = IndBlue
                        )
                    }
                    if (cloudSyncUi.isRunning && !cloudSyncUi.showFullscreenOverlay) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = IndBlue,
                            trackColor = IndBlue.copy(alpha = 0.15f)
                        )
                        Text(
                            text = cloudSyncUi.statusLine,
                            fontSize = 12.sp,
                            color = IndTextSecondary
                        )
                    }
                    Button(
                        onClick = { viewModel.runCloudBackupSyncAsync() },
                        enabled = !cloudSyncUi.isRunning && (firebaseUser != null),
                        colors = ButtonDefaults.buttonColors(containerColor = IndBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cloud_sync_button")
                    ) {
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (cloudSyncUi.isRunning) "Syncing…" else "Sync to cloud now",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Downloads your backup after reinstall, then uploads any pending changes.",
                        fontSize = 11.sp,
                        color = IndTextSecondary,
                        lineHeight = 15.sp
                    )
                }
            }

            IndCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ProfileSectionTitle(
                        title = "Notifications & auto-sync",
                        subtitle = "Bank alerts, approvals & due-date reminders",
                        icon = Icons.Filled.NotificationsActive
                    )

                    ProfileSettingsToggleRow(
                        title = "Bank SMS & UPI interceptor",
                        subtitle = "HDFC, SBI, PhonePe, GPay & more",
                        checked = enableSmsInterceptor,
                        onCheckedChange = { enableSmsInterceptor = it }
                    )

                    HorizontalDivider(color = IndBorderSubtle, modifier = Modifier.padding(vertical = 4.dp))

                    ProfileSettingsToggleRow(
                        title = "Instant approval alerts",
                        subtitle = "1-tap approve or dismiss in notification shade",
                        checked = enableInstantAlerts,
                        onCheckedChange = { enableInstantAlerts = it }
                    )

                    HorizontalDivider(color = IndBorderSubtle, modifier = Modifier.padding(vertical = 4.dp))

                    ProfileSettingsToggleRow(
                        title = "Monthly due reminders",
                        subtitle = "EMI, rent & bills — 2 days before due",
                        checked = enableDueReminders,
                        onCheckedChange = { enableDueReminders = it }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

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

            IndCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ProfileSectionTitle(
                        title = "Privacy & account",
                        subtitle = "Control visibility and session",
                        icon = Icons.Filled.Shield
                    )

                    ProfileSettingsToggleRow(
                        title = "Financial safety mode",
                        subtitle = "Mask balances & income on dashboard",
                        checked = !isFinancialSafetyVisible,
                        onCheckedChange = { viewModel.toggleFinancialSafetyVisible() }
                    )

                    HorizontalDivider(color = IndBorderSubtle, modifier = Modifier.padding(vertical = 8.dp))

                    OutlinedButton(
                        onClick = { showSignOutDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, IndRed.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("sign_out_profile_button")
                    ) {
                        Icon(Icons.Filled.Logout, contentDescription = null, tint = IndRed, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign out of FinMoney", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = IndRed)
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.signOut()
                            onBack()
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, IndBlue.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("switch_account_button")
                    ) {
                        Icon(Icons.Filled.SwitchAccount, contentDescription = null, tint = IndBlue, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Switch Google / mobile account", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = IndBlue)
                    }

                    ProfileSettingsNavRow(
                        title = "Custom expense categories",
                        subtitle = if (customCategories.isEmpty()) {
                            "Create icons & colors for your spending"
                        } else {
                            "${customCategories.size} categories — edit or remove"
                        },
                        titleColor = IndNavyHeader,
                        icon = Icons.Filled.Category,
                        iconTint = IndBlue,
                        onClick = { showManageCustomCategoriesDialog = true }
                    )

                    HorizontalDivider(color = IndBorderSubtle, modifier = Modifier.padding(vertical = 8.dp))

                    ProfileSettingsNavRow(
                        title = "Reset local budget data",
                        subtitle = "Re-seed October 2026 starter template",
                        titleColor = IndRed,
                        icon = Icons.Filled.RestartAlt,
                        iconTint = IndRed,
                        onClick = { showResetDialog = true }
                    )
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ProfileSectionTitle(
                        title = "Legal & support",
                        subtitle = "Policies and data rights",
                        icon = Icons.Filled.PrivacyTip
                    )

                    ProfileSettingsNavRow(
                        title = "Request account deletion",
                        subtitle = "Permanently remove account & financial records",
                        titleColor = IndRed,
                        icon = Icons.Filled.DeleteForever,
                        iconTint = IndRed,
                        onClick = {
                            openUrlInBrowser(
                                "https://docs.google.com/forms/d/e/1FAIpQLScYFK0kYaruDdJjmtrwwh33q4wXq3M0RL2xH_Hq18ZRTuvZOg/viewform"
                            )
                        },
                        testTag = "request_account_deletion_link"
                    )

                    HorizontalDivider(color = IndBorderSubtle, modifier = Modifier.padding(vertical = 4.dp))

                    ProfileSettingsNavRow(
                        title = "Privacy policy",
                        subtitle = "Google Play compliant policy",
                        titleColor = IndNavyHeader,
                        icon = Icons.Outlined.Description,
                        iconTint = IndBlue,
                        onClick = {
                            openUrlInBrowser(
                                "https://gist.githubusercontent.com/amareshgajawada/raw/finmoney_privacy_policy.txt"
                            )
                        },
                        testTag = "privacy_policy_link"
                    )

                    HorizontalDivider(color = IndBorderSubtle, modifier = Modifier.padding(vertical = 4.dp))

                    ProfileSettingsNavRow(
                        title = "Data safety & disclosures",
                        subtitle = "How we protect phone & personal data",
                        titleColor = IndNavyHeader,
                        icon = Icons.Filled.Info,
                        iconTint = IndBlue,
                        onClick = { showDataSafetyDialog = true },
                        testTag = "data_safety_disclosures_link"
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
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

    if (showManageCustomCategoriesDialog) {
        ManageCustomCategoriesDialog(
            categories = customCategories,
            onDismiss = { showManageCustomCategoriesDialog = false },
            onEdit = { cat ->
                showManageCustomCategoriesDialog = false
                editingCustomCategory = cat
            },
            onDelete = { cat -> viewModel.deleteCustomCategory(cat) },
            expenseCountForCategory = { name -> viewModel.countExpensesUsingCustomCategory(name) }
        )
    }

    if (editingCustomCategory != null) {
        AddCustomCategoryDialog(
            onDismiss = { editingCustomCategory = null },
            editing = editingCustomCategory,
            onSave = { name, icon, color ->
                viewModel.updateCustomCategory(editingCustomCategory!!.id, name, icon, color)
                editingCustomCategory = null
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

    // Mobile OTP Verification Dialog
    if (showPhoneVerifyDialog) {
        com.example.ui.components.PhoneVerificationDialog(
            initialPhone = effectivePhone,
            viewModel = viewModel,
            onDismiss = { showPhoneVerifyDialog = false },
            onVerified = {
                showPhoneVerifyDialog = false
                viewModel.syncVerificationStatusFromFirebaseAsync()
            }
        )
    }
}

@Composable
private fun ProfileAvatarPicker(
    displayName: String,
    imageUri: String?,
    onChangePhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    val initial = (displayName.trim().take(1).takeIf { it.isNotBlank() } ?: "A").uppercase()

    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomEnd
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f))
                .border(3.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                .clickable(onClick = onChangePhoto),
            contentAlignment = Alignment.Center
        ) {
            if (!imageUri.isNullOrBlank()) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = "Profile photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
        }
        Surface(
            onClick = onChangePhoto,
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 4.dp,
            modifier = Modifier
                .size(32.dp)
                .offset(x = 4.dp, y = 4.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = "Change photo",
                    tint = IndNavyHeader,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
private fun ProfileVerificationChip(
    verified: Boolean,
    verifiedLabel: String,
    pendingLabel: String,
    iconVerified: ImageVector,
    iconPending: ImageVector
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (verified) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (verified) 0.5f else 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (verified) iconVerified else iconPending,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = if (verified) verifiedLabel else pendingLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun ProfileSectionTitle(
    title: String,
    subtitle: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(IndBlueLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = IndBlue, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = IndNavyHeader
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = IndTextSecondary
            )
        }
    }
}

@Composable
private fun ProfileSettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = IndNavyHeader)
            Text(subtitle, fontSize = 11.sp, color = IndTextSecondary, lineHeight = 14.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = IndBlue, checkedTrackColor = IndBlueLight)
        )
    }
}

@Composable
private fun ProfileSettingsNavRow(
    title: String,
    subtitle: String,
    titleColor: Color,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    testTag: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = titleColor)
                Text(subtitle, fontSize = 11.sp, color = IndTextSecondary)
            }
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = IndTextMuted, modifier = Modifier.size(22.dp))
    }
}
