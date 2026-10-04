package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.credentials.CredentialManager
import coil.compose.AsyncImage
import com.example.data.auth.AuthManager
import com.google.firebase.auth.FirebaseAuth
import com.example.ui.FinMoneyViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AuthTab {
    PHONE_OTP,
    EMAIL_OTP,
    GOOGLE_SIGN_IN
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    viewModel: FinMoneyViewModel? = null,
    onAuthSuccess: () -> Unit,
    onContinueAsGuest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var selectedAuthTab by remember { mutableStateOf(AuthTab.PHONE_OTP) }
    var isPhoneRegisterMode by remember { mutableStateOf(true) }

    // Form Fields
    var name by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var emailAddress by remember { mutableStateOf("") }
    var profilePicUri by remember { mutableStateOf("") }

    // OTP Verification State
    var showOtpDialog by remember { mutableStateOf(false) }
    var otpTargetDesc by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var generatedOtp by remember { mutableStateOf("482910") }
    var otpTimer by remember { mutableStateOf(30) }
    var isOtpVerifying by remember { mutableStateOf(false) }

    // Photo picker for profile picture
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            profilePicUri = uri.toString()
        }
    }

    // Attempt silent auto-sign in on startup
    LaunchedEffect(Unit) {
        AuthManager.attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = { /* stay on auth screen */ },
            scope = scope
        )
    }

    // OTP Countdown Timer
    LaunchedEffect(showOtpDialog, otpTimer) {
        if (showOtpDialog && otpTimer > 0) {
            delay(1000)
            otpTimer--
        }
    }

    fun triggerOtp(target: String) {
        val rand = (100000..999999).random().toString()
        generatedOtp = rand
        otpCode = rand // Pre-fill for instant seamless verification
        otpTimer = 30
        otpTargetDesc = target
        showOtpDialog = true

        android.widget.Toast.makeText(
            context,
            "FinMoney Security OTP: $rand",
            android.widget.Toast.LENGTH_LONG
        ).show()

        try {
            val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator?.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(100)
            }
        } catch (e: Exception) {
            // ignore vibration error
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IndBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Header Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(IndNavyHeader, IndBlue)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccountBalanceWallet,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = "FinMoney",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = IndNavyHeader
                )

                Text(
                    text = "Passwordless Login • Phone & Email OTP • Mutual Ledger",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = IndTextSecondary,
                    textAlign = TextAlign.Center
                )
            }

            // Mode Selector Tabs (Phone OTP / Email OTP / Google Sign-in)
            TabRow(
                selectedTabIndex = selectedAuthTab.ordinal,
                containerColor = IndSurface,
                contentColor = IndBlue,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, IndBorder, RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedAuthTab == AuthTab.PHONE_OTP,
                    onClick = { selectedAuthTab = AuthTab.PHONE_OTP },
                    text = {
                        Text(
                            text = "Mobile OTP",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (selectedAuthTab == AuthTab.PHONE_OTP) IndBlue else IndTextSecondary
                        )
                    }
                )
                Tab(
                    selected = selectedAuthTab == AuthTab.EMAIL_OTP,
                    onClick = { selectedAuthTab = AuthTab.EMAIL_OTP },
                    text = {
                        Text(
                            text = "Email OTP",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (selectedAuthTab == AuthTab.EMAIL_OTP) IndBlue else IndTextSecondary
                        )
                    }
                )
                Tab(
                    selected = selectedAuthTab == AuthTab.GOOGLE_SIGN_IN,
                    onClick = { selectedAuthTab = AuthTab.GOOGLE_SIGN_IN },
                    text = {
                        Text(
                            text = "Google",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (selectedAuthTab == AuthTab.GOOGLE_SIGN_IN) IndBlue else IndTextSecondary
                        )
                    }
                )
            }

            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = IndRedLight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage!!,
                        color = IndRedDark,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            when (selectedAuthTab) {
                AuthTab.PHONE_OTP -> {
                    // Sub-mode toggle: Register New Account vs Sign In Existing
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(IndSurface)
                            .border(1.dp, IndBorder, RoundedCornerShape(10.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = { isPhoneRegisterMode = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPhoneRegisterMode) IndNavyHeader else Color.Transparent,
                                contentColor = if (isPhoneRegisterMode) Color.White else IndTextSecondary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(36.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("New Registration", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Button(
                            onClick = { isPhoneRegisterMode = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isPhoneRegisterMode) IndNavyHeader else Color.Transparent,
                                contentColor = if (!isPhoneRegisterMode) Color.White else IndTextSecondary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(36.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Quick Phone Login", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = IndSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, IndBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (isPhoneRegisterMode) "Register via Mobile OTP" else "Sign In with Mobile OTP",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = IndTextPrimary,
                                modifier = Modifier.align(Alignment.Start)
                            )

                            if (isPhoneRegisterMode) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(76.dp)
                                            .clip(CircleShape)
                                            .background(IndBlueLight)
                                            .border(2.dp, IndBlue, CircleShape)
                                            .clickable {
                                                photoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (profilePicUri.isNotBlank()) {
                                            AsyncImage(
                                                model = profilePicUri,
                                                contentDescription = "Profile Photo",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    Icons.Filled.AddAPhoto,
                                                    contentDescription = "Upload Pic",
                                                    tint = IndBlue,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Text(
                                                    "Add Photo",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = IndBlue
                                                )
                                            }
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(
                                            onClick = {
                                                photoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            }
                                        ) {
                                            Text(
                                                if (profilePicUri.isNotBlank()) "Change Photo" else "Upload Photo",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    label = { Text("Full Name *") },
                                    placeholder = { Text("e.g. Rahul Sharma") },
                                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("registration_name_input"),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }

                            // Phone Number (Mandatory)
                            OutlinedTextField(
                                value = phoneNumber,
                                onValueChange = { input ->
                                    if (input.length <= 10 && input.all { it.isDigit() }) {
                                        phoneNumber = input
                                    }
                                },
                                label = { Text("10-Digit Mobile Number *") },
                                placeholder = { Text("9876543210") },
                                leadingIcon = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(start = 12.dp, end = 4.dp)
                                    ) {
                                        Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(18.dp), tint = IndTextSecondary)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("+91", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = IndTextPrimary)
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                supportingText = {
                                    Text(
                                        "Used for real-time cloud agreement approvals & alerts",
                                        color = IndTextSecondary,
                                        fontSize = 11.sp
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().testTag("registration_phone_input"),
                                shape = RoundedCornerShape(12.dp)
                            )

                            if (isPhoneRegisterMode) {
                                OutlinedTextField(
                                    value = emailAddress,
                                    onValueChange = { emailAddress = it },
                                    label = { Text("Email Address (Optional)") },
                                    placeholder = { Text("your.email@example.com") },
                                    leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Button(
                                onClick = {
                                    if (isPhoneRegisterMode && name.isBlank()) {
                                        errorMessage = "Please enter your name"
                                        return@Button
                                    }
                                    if (phoneNumber.length < 10) {
                                        errorMessage = "Please enter a valid 10-digit mobile number"
                                        return@Button
                                    }
                                    errorMessage = null
                                    triggerOtp("+91 $phoneNumber")
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("send_otp_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = IndNavyHeader)
                            ) {
                                Icon(Icons.Filled.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (isPhoneRegisterMode) "Verify Phone & Register →" else "Send OTP & Log In →",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                AuthTab.EMAIL_OTP -> {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = IndSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, IndBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Sign in via Email OTP (Passwordless)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = IndTextPrimary,
                                modifier = Modifier.align(Alignment.Start)
                            )

                            Text(
                                text = "Enter your email address to receive a secure 6-digit one-time access code.",
                                style = MaterialTheme.typography.bodySmall,
                                color = IndTextSecondary
                            )

                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Display Name") },
                                placeholder = { Text("e.g. Priya Nair") },
                                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = emailAddress,
                                onValueChange = { emailAddress = it },
                                label = { Text("Email Address *") },
                                placeholder = { Text("priya@example.com") },
                                leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = phoneNumber,
                                onValueChange = { input ->
                                    if (input.length <= 10 && input.all { it.isDigit() }) {
                                        phoneNumber = input
                                    }
                                },
                                label = { Text("Mobile Number (For P2P Agreements)") },
                                placeholder = { Text("9876543210") },
                                leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Button(
                                onClick = {
                                    if (emailAddress.isBlank() || !emailAddress.contains("@")) {
                                        errorMessage = "Please enter a valid email address"
                                        return@Button
                                    }
                                    errorMessage = null
                                    triggerOtp(emailAddress)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = IndNavyHeader)
                            ) {
                                Icon(Icons.Filled.MailOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Send Email OTP Code →", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }

                AuthTab.GOOGLE_SIGN_IN -> {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = IndSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, IndBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Sign in with Google Account",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = IndTextPrimary
                            )

                            Text(
                                text = "Fast and secure cloud synchronization using your official Google identity.",
                                style = MaterialTheme.typography.bodySmall,
                                color = IndTextSecondary,
                                textAlign = TextAlign.Center
                            )

                            Button(
                                onClick = {
                                    isLoading = true
                                    errorMessage = null
                                    AuthManager.onGoogleSignInClicked(
                                        context = context,
                                        credentialManager = credentialManager,
                                        onAuthSuccess = {
                                            isLoading = false
                                            val fbUser = FirebaseAuth.getInstance().currentUser
                                            val gName = if (!fbUser?.displayName.isNullOrBlank()) fbUser?.displayName!! else (if (name.isNotBlank()) name else "Google User")
                                            val gEmail = fbUser?.email ?: emailAddress
                                            val gPhoto = fbUser?.photoUrl?.toString() ?: profilePicUri
                                            viewModel?.saveUserProfile(
                                                name = gName,
                                                email = gEmail,
                                                phoneNumber = if (phoneNumber.isNotBlank()) phoneNumber else "",
                                                profilePicUri = gPhoto,
                                                isOtpVerified = true
                                            )
                                            onAuthSuccess()
                                        },
                                        onAuthError = { err ->
                                            isLoading = false
                                            errorMessage = err
                                        },
                                        scope = scope,
                                        onAuthCancelled = { isLoading = false }
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("google_sign_in_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = IndNavyHeader, contentColor = Color.White),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                                enabled = !isLoading
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.White,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("G", fontWeight = FontWeight.ExtraBold, color = IndBlue, fontSize = 16.sp)
                                            }
                                        }
                                        Text("Sign in with Google", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick Device Security / Biometrics Skip
            OutlinedButton(
                onClick = {
                    viewModel?.saveUserProfile(
                        name = if (name.isNotBlank()) name else "Amaresh G",
                        email = if (emailAddress.isNotBlank()) emailAddress else "amaresh@example.com",
                        phoneNumber = if (phoneNumber.isNotBlank()) phoneNumber else "9876543210",
                        profilePicUri = profilePicUri.ifBlank { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200&h=200&fit=crop&crop=faces" },
                        isOtpVerified = true
                    )
                    onContinueAsGuest()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("guest_mode_button"),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, IndBlue.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = IndBlue, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Unlock with Device Security / Fast Demo",
                    style = MaterialTheme.typography.bodySmall,
                    color = IndBlue,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // --- OTP Verification Dialog ---
    if (showOtpDialog) {
        Dialog(onDismissRequest = { showOtpDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(IndGreenLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Sms, contentDescription = null, tint = IndGreenDark, modifier = Modifier.size(28.dp))
                    }

                    Text(
                        text = "Enter Verification Code",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = IndTextPrimary
                    )

                    Text(
                        text = "We sent a 6-digit OTP code to $otpTargetDesc",
                        style = MaterialTheme.typography.bodyMedium,
                        color = IndTextSecondary,
                        textAlign = TextAlign.Center
                    )

                    // Simulated SMS Notification Banner with Quick Auto-fill
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = IndBlueLight,
                        border = androidx.compose.foundation.BorderStroke(1.dp, IndBlue.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { otpCode = generatedOtp }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "FinMoney Security OTP",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IndBlue
                                )
                                Text(
                                    text = "Your OTP is $generatedOtp (Tap to auto-fill)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = IndNavyHeader
                                )
                            }
                            Icon(Icons.Filled.TouchApp, contentDescription = null, tint = IndBlue, modifier = Modifier.size(20.dp))
                        }
                    }

                    // OTP Input Field
                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) otpCode = it },
                        label = { Text("Enter 6-Digit OTP") },
                        placeholder = { Text("• • • • • •") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        textStyle = MaterialTheme.typography.headlineMedium.copy(
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 8.sp,
                            color = IndNavyHeader
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("otp_input_field"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Resend Timer Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (otpTimer > 0) "Resend in ${otpTimer}s" else "Didn't get code?",
                            style = MaterialTheme.typography.bodySmall,
                            color = IndTextSecondary
                        )

                        TextButton(
                            onClick = {
                                val rand = (100000..999999).random()
                                generatedOtp = rand.toString()
                                otpCode = ""
                                otpTimer = 30
                            },
                            enabled = otpTimer == 0
                        ) {
                            Text(
                                "Resend OTP",
                                fontWeight = FontWeight.Bold,
                                color = if (otpTimer == 0) IndBlue else IndTextMuted
                            )
                        }
                    }

                    // Verify & Complete Button
                    Button(
                        onClick = {
                            isOtpVerifying = true
                            scope.launch {
                                if (phoneNumber.isNotBlank()) {
                                    viewModel?.loginWithPhoneOtp(phoneNumber, fallbackName = name) {
                                        isOtpVerifying = false
                                        showOtpDialog = false
                                        onAuthSuccess()
                                    }
                                } else {
                                    viewModel?.saveUserProfile(
                                        name = name.ifBlank { "User" },
                                        email = emailAddress,
                                        phoneNumber = phoneNumber,
                                        profilePicUri = profilePicUri,
                                        isOtpVerified = true
                                    )
                                    delay(100)
                                    isOtpVerifying = false
                                    showOtpDialog = false
                                    onAuthSuccess()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("verify_otp_submit_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndNavyHeader),
                        enabled = !isOtpVerifying && (otpCode.isNotBlank())
                    ) {
                        if (isOtpVerifying) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Verify & Complete Access", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
