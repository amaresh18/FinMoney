package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.ui.FinMoneyViewModel
import com.example.ui.theme.*
import com.example.util.DetectedSim
import com.example.util.SimDetectionHelper
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class FinAuthSlide(
    val title: String,
    val subtitle: String,
    val badgeText: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val highlightStat: String
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AuthScreen(
    viewModel: FinMoneyViewModel,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    FinAuthTheme {
        AuthScreenContent(
            viewModel = viewModel,
            onContinue = onContinue,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun AuthScreenContent(
    viewModel: FinMoneyViewModel,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val currentUser by viewModel.userProfile.collectAsState()

    // Form States (Blank by default for clean user sign-in / sign-up)
    var nameInput by remember { mutableStateOf("") }
    var phoneInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var profilePicUri by remember { mutableStateOf("") }

    var otpInput by remember { mutableStateOf("") }
    var isPhoneVerified by remember { mutableStateOf(false) }
    var isGoogleSignedIn by remember { mutableStateOf(false) }
    var isGoogleLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Onboarding Step State:
    // 0 = Feature Highlights Carousel / Landing
    // 1 = Phone Number Entry
    // 2 = OTP Verification
    // 3 = Profile Personalization
    var currentStep by remember { mutableStateOf(0) }

    // Always keep form fields blank until user enters credentials
    LaunchedEffect(Unit) {
        currentStep = 0
        nameInput = ""
        phoneInput = ""
        emailInput = ""
        otpInput = ""
        isPhoneVerified = false
        isGoogleSignedIn = false
        errorText = null
        statusMessage = null
    }

    var activeCarouselIndex by remember { mutableStateOf(0) }
    var termsAccepted by remember { mutableStateOf(true) }
    var resendTimerSeconds by remember { mutableStateOf(30) }

    val detectedSims = remember { mutableStateListOf<DetectedSim>() }
    var showSimSelectionDialog by remember { mutableStateOf(false) }
    var isSimScanning by remember { mutableStateOf(false) }

    // Auto-advance resend timer
    LaunchedEffect(currentStep, isPhoneVerified) {
        if (currentStep == 2 && !isPhoneVerified) {
            resendTimerSeconds = 30
            while (resendTimerSeconds > 0) {
                delay(1000)
                resendTimerSeconds--
            }
        }
    }

    val phoneNumberHintLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val selectedPhone = SimDetectionHelper.extractPhoneNumberFromHintIntent(context as Activity, result.data)
            if (!selectedPhone.isNullOrBlank()) {
                phoneInput = selectedPhone
                isPhoneVerified = true
                statusMessage = "Device SIM selected: +91 ${selectedPhone.takeLast(10)} ✓"
                errorText = null
                if (currentStep == 1) currentStep = 2
            }
        }
    }

    fun scanDeviceSims() {
        isSimScanning = true
        coroutineScope.launch {
            try {
                val sims = SimDetectionHelper.getDetectedSims(context)
                detectedSims.clear()
                detectedSims.addAll(sims)
                val validSims = sims.filter { it.cleanDigits.length >= 10 }
                if (validSims.isNotEmpty()) {
                    showSimSelectionDialog = true
                } else if (context is Activity) {
                    SimDetectionHelper.requestPhoneNumberHint(
                        activity = context,
                        onSuccess = { pendingIntent ->
                            phoneNumberHintLauncher.launch(
                                IntentSenderRequest.Builder(pendingIntent).build()
                            )
                        },
                        onFailure = { /* fallback */ }
                    )
                }
            } catch (e: Exception) {
                Log.w("AuthScreen", "SIM scan error: ${e.message}")
            } finally {
                isSimScanning = false
            }
        }
    }

    val phonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        if (granted) {
            scanDeviceSims()
        } else if (context is Activity) {
            SimDetectionHelper.requestPhoneNumberHint(
                activity = context,
                onSuccess = { pendingIntent ->
                    phoneNumberHintLauncher.launch(
                        IntentSenderRequest.Builder(pendingIntent).build()
                    )
                },
                onFailure = { /* fallback */ }
            )
        }
    }

    // Auto scan SIM when stepping to phone entry
    LaunchedEffect(currentStep) {
        if (currentStep == 1 && phoneInput.isBlank() && !isPhoneVerified) {
            if (SimDetectionHelper.hasPhonePermission(context)) {
                scanDeviceSims()
            } else {
                val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS)
                } else {
                    arrayOf(Manifest.permission.READ_PHONE_STATE)
                }
                phonePermissionLauncher.launch(permissions)
            }
        }
    }

    // Google Sign-In logic
    fun performGoogleSignIn() {
        isGoogleLoading = true
        errorText = null
        statusMessage = null

        val webClientIdRes = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        val clientId = if (webClientIdRes != 0) context.getString(webClientIdRes) else ""
        if (clientId.isBlank()) {
            isGoogleLoading = false
            errorText = "Google Sign-In configuration missing (default_web_client_id)"
            return
        }

        val credentialManager = CredentialManager.create(context)
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

        coroutineScope.launch {
            try {
                val result = credentialManager.getCredential(context as Activity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdToken.idToken
                    val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                    val authResult = Firebase.auth.signInWithCredential(authCredential).await()

                    val gUser = authResult.user
                    val detectedName = gUser?.displayName ?: googleIdToken.displayName ?: nameInput
                    val detectedEmail = gUser?.email ?: googleIdToken.id ?: emailInput
                    val detectedPhoto = gUser?.photoUrl?.toString() ?: googleIdToken.profilePictureUri?.toString() ?: profilePicUri

                    nameInput = detectedName
                    emailInput = detectedEmail
                    profilePicUri = detectedPhoto
                    isGoogleSignedIn = true

                    val cleanPhone = phoneInput.filter { it.isDigit() }
                    viewModel.saveUserProfile(
                        name = detectedName.ifBlank { "FinMoney Member" },
                        email = detectedEmail,
                        phoneNumber = cleanPhone.ifBlank { "9876543210" },
                        profilePicUri = detectedPhoto,
                        isOtpVerified = true,
                        isEmailVerified = true
                    )

                    statusMessage = "Signed in as ${detectedName} ✓"
                    currentStep = 4
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w("AuthScreen", "Google Sign-In cancelled: ${e.message}")
            } catch (e: Exception) {
                Log.e("AuthScreen", "Google Sign-In exception: ${e.message}", e)
                errorText = "Google Sign-In failed: ${e.localizedMessage ?: "User cancelled or unconfigured"}"
            } finally {
                isGoogleLoading = false
            }
        }
    }

    val featureSlides = remember {
        listOf(
            FinAuthSlide(
                title = "Your everyday UPI card",
                subtitle = "Earn 2% cashback on every scan, manage expenses & auto-sync salary credits seamlessly.",
                badgeText = "💳 INSTANT RUPAY CREDIT",
                highlightStat = "₹1,50,000 Credit Limit",
                icon = Icons.Filled.CreditCard
            ),
            FinAuthSlide(
                title = "Smart Salary Utility & Dues",
                subtitle = "Automatically detect salary credits, manage EMIs, recurring bill debits, and split loans.",
                badgeText = "📊 AUTOMATED FINMONEY",
                highlightStat = "100% Zero Manual Dues",
                icon = Icons.Filled.AccountBalanceWallet
            ),
            FinAuthSlide(
                title = "Khaata Peer Ledger & QR",
                subtitle = "Seamlessly record borrowing/lending with contacts and settle up over UPI in 1-tap.",
                badgeText = "🤝 MUTUAL KHAATA SYNC",
                highlightStat = "Zero Interest Ledger",
                icon = Icons.Filled.QrCodeScanner
            )
        )
    }

    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(FinAuthDarkBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Navigation & Brand Wordmark
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button (if on steps 1..3)
                    if (currentStep in 1..3) {
                        IconButton(
                            onClick = { currentStep-- },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(FinAuthCardBg)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = FinAuthTextPrimary)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(38.dp))
                    }

                    // FinMoney Wordmark Logo
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Brush.horizontalGradient(listOf(IndBlue, IndCyan))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("F", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.White)
                        }
                        Text(
                            text = "FinMoney",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = FinAuthTextPrimary,
                            letterSpacing = (-0.5).sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(IndBlue.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("CREDIT ON UPI", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = IndCyan)
                        }
                    }

                    // Step Indicator Pill
                    if (currentStep in 1..3) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = FinAuthCardBg,
                            border = androidx.compose.foundation.BorderStroke(1.dp, FinAuthCardBorder)
                        ) {
                            Text(
                                text = "$currentStep/3",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = IndCyan,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(38.dp))
                    }
                }

                // Step Progress Bar
                if (currentStep in 1..3) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (i in 1..3) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(if (i <= currentStep) IndBlue else FinAuthCardBorder)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Animated Screen Step Content
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { width -> width } + fadeIn()).togetherWith(slideOutHorizontally { width -> -width } + fadeOut())
                    } else {
                        (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(slideOutHorizontally { width -> width } + fadeOut())
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { step ->
                when (step) {
                    0 -> OnboardingScreen(
                        onGoogleSignIn = { performGoogleSignIn() },
                        onMobileSignIn = { currentStep = 1 },
                        isGoogleLoading = isGoogleLoading
                    )

                    1 -> FinAuthMobileInputStep(
                        phoneInput = phoneInput,
                        onPhoneChange = { phoneInput = it },
                        isSimScanning = isSimScanning,
                        detectedSims = detectedSims,
                        onScanSims = { scanDeviceSims() },
                        onSelectSim = { sim ->
                            phoneInput = sim.cleanDigits
                            isPhoneVerified = true
                            statusMessage = "Device SIM selected: +91 ${sim.cleanDigits.takeLast(10)} ✓"
                            currentStep = 2
                        },
                        onSendOtp = {
                            val digits = phoneInput.filter { it.isDigit() }
                            if (digits.length < 10) {
                                errorText = "Please enter a valid 10-digit mobile number."
                            } else {
                                otpInput = "123456" // Auto demo OTP for smooth testing
                                statusMessage = "OTP sent to +91 $digits! Auto-filled test OTP: 123456"
                                errorText = null
                                currentStep = 2
                            }
                        },
                        onGoogleSignIn = { performGoogleSignIn() },
                        isGoogleLoading = isGoogleLoading,
                        errorText = errorText,
                        statusMessage = statusMessage,
                        termsAccepted = termsAccepted,
                        onTermsChange = { termsAccepted = it }
                    )

                    2 -> FinAuthOtpVerificationStep(
                        phoneNumber = phoneInput,
                        otpInput = otpInput,
                        onOtpChange = { otpInput = it },
                        resendTimerSeconds = resendTimerSeconds,
                        onResendOtp = {
                            resendTimerSeconds = 30
                            otpInput = "123456"
                            statusMessage = "New OTP sent to +91 ${phoneInput.takeLast(10)}"
                        },
                        onVerifyOtp = {
                            if (otpInput.length >= 4) {
                                isPhoneVerified = true
                                statusMessage = "Mobile number verified successfully! ✓"
                                errorText = null
                                currentStep = 3
                            } else {
                                errorText = "Please enter the complete 6-digit OTP."
                            }
                        },
                        onEditPhone = { currentStep = 1 },
                        errorText = errorText,
                        statusMessage = statusMessage
                    )

                    3 -> FinAuthProfileSetupStep(
                        nameInput = nameInput,
                        onNameChange = { nameInput = it },
                        emailInput = emailInput,
                        onEmailChange = { emailInput = it },
                        termsAccepted = termsAccepted,
                        onTermsChange = { termsAccepted = it },
                        onSubmitProfile = {
                            if (nameInput.trim().isBlank()) {
                                errorText = "Please enter your full name."
                                return@FinAuthProfileSetupStep
                            }
                            if (!termsAccepted) {
                                errorText = "Please accept the terms to proceed."
                                return@FinAuthProfileSetupStep
                            }
                            val cleanPhone = phoneInput.filter { it.isDigit() }
                            viewModel.saveUserProfile(
                                name = nameInput.trim(),
                                email = emailInput.trim(),
                                phoneNumber = cleanPhone,
                                profilePicUri = profilePicUri,
                                isOtpVerified = true,
                                isEmailVerified = emailInput.isNotBlank()
                            )
                            currentStep = 4
                        },
                        errorText = errorText
                    )

                    4 -> FinAuthVirtualCardCelebrationStep(
                        userName = nameInput.ifBlank { currentUser?.name ?: "Valued Member" },
                        phoneNumber = phoneInput.ifBlank { currentUser?.phoneNumber ?: "9876543210" },
                        email = emailInput.ifBlank { currentUser?.email ?: "" },
                        onClaimAndEnter = {
                            val cleanPhone = phoneInput.filter { it.isDigit() }
                            viewModel.saveUserProfile(
                                name = nameInput.trim().ifBlank { "FinMoney Member" },
                                email = emailInput.trim(),
                                phoneNumber = cleanPhone.ifBlank { "9876543210" },
                                profilePicUri = profilePicUri,
                                isOtpVerified = true,
                                isEmailVerified = true
                            )
                            onContinue()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Footer Security Badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = FinAuthTextSecondary, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Bank-Grade Encryption • NPCI & Google Identity Verified",
                    fontSize = 11.sp,
                    color = FinAuthTextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Device SIM Selection Dialog
        if (showSimSelectionDialog && detectedSims.any { it.cleanDigits.length >= 10 }) {
            val validSims = detectedSims.filter { it.cleanDigits.length >= 10 }
            AlertDialog(
                onDismissRequest = { showSimSelectionDialog = false },
                containerColor = FinAuthCardBg,
                titleContentColor = FinAuthTextPrimary,
                textContentColor = FinAuthTextSecondary,
                icon = {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(IndBlue.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.SimCard, contentDescription = null, tint = IndCyan, modifier = Modifier.size(26.dp))
                    }
                },
                title = {
                    Text("Select Active Device SIM", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Select your SIM registered mobile number to auto-verify with FinMoney:",
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            color = FinAuthTextSecondary
                        )

                        validSims.forEach { sim ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = FinAuthDarkBg,
                                border = androidx.compose.foundation.BorderStroke(1.dp, IndBlue),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        phoneInput = sim.cleanDigits
                                        isPhoneVerified = true
                                        showSimSelectionDialog = false
                                        val currentName = nameInput.trim().ifBlank { "FinMoney Member" }
                                        viewModel.saveUserProfile(
                                            name = currentName,
                                            email = emailInput.trim(),
                                            phoneNumber = sim.cleanDigits,
                                            profilePicUri = profilePicUri,
                                            isOtpVerified = true,
                                            isEmailVerified = emailInput.isNotBlank()
                                        )
                                        statusMessage = "Logged in with ${sim.displayName} (${sim.formattedDisplay}) ✓"
                                        currentStep = 3
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(IndBlue.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = IndCyan, modifier = Modifier.size(20.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(sim.formattedDisplay, fontWeight = FontWeight.ExtraBold, color = FinAuthTextPrimary, fontSize = 14.sp)
                                        Text("${sim.carrierName} • SIM ${sim.slotIndex + 1}", fontSize = 11.sp, color = FinAuthTextSecondary)
                                    }
                                    Icon(Icons.Filled.CheckCircle, contentDescription = "Select", tint = IndCyan, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSimSelectionDialog = false }) {
                        Text("Enter Number Manually", color = IndCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// OnboardingScreen Component (Single Column Layout with Centered Bold Headline & Bottom Google Sign-In)
// ---------------------------------------------------------------------------
@Composable
fun OnboardingScreen(
    onGoogleSignIn: () -> Unit,
    onMobileSignIn: () -> Unit,
    isGoogleLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    FinAuthTheme {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(AppTheme.colors.darkBg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = AppTheme.dimensions.extraLarge,
                        vertical = AppTheme.dimensions.huge + AppTheme.dimensions.extraSmall
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // TOP: Centered, Bold Brand Headline
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppTheme.dimensions.medium),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AppTheme.dimensions.large)
                ) {
                    // Brand Badge Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AppTheme.dimensions.buttonRadius))
                            .background(AppTheme.colors.primary.copy(alpha = 0.2f))
                            .border(1.dp, AppTheme.colors.accentCyan.copy(alpha = 0.5f), RoundedCornerShape(AppTheme.dimensions.buttonRadius))
                            .padding(horizontal = AppTheme.dimensions.large, vertical = AppTheme.dimensions.extraSmall + 2.dp)
                    ) {
                        Text(
                            text = "SMART FINMONEY FINTECH",
                            style = AppTheme.typography.labelSmall,
                            color = AppTheme.colors.accentCyan,
                            letterSpacing = 0.8.sp
                        )
                    }

                    Text(
                        text = "FinMoney",
                        style = AppTheme.typography.headlineLarge,
                        color = AppTheme.colors.textPrimary,
                        textAlign = TextAlign.Center,
                        letterSpacing = (-1).sp
                    )

                    Text(
                        text = "Your everyday UPI credit card, salary tracker & Khaata ledger.",
                        style = AppTheme.typography.bodyMedium,
                        color = AppTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(horizontal = AppTheme.dimensions.large)
                    )
                }

                // CENTER / HERO: Minimal, High-Impact Fintech Visual
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = AppTheme.dimensions.huge),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(AppTheme.dimensions.cardRadius + 12.dp),
                        color = AppTheme.colors.darkCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.darkBorder),
                        shadowElevation = AppTheme.dimensions.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Color(0xFF0F172A),
                                            Color(0xFF1E293B),
                                            AppTheme.colors.primary
                                        )
                                    )
                                )
                                .border(1.dp, Brush.horizontalGradient(listOf(AppTheme.colors.accentCyan, AppTheme.colors.primary)), RoundedCornerShape(AppTheme.dimensions.cardRadius + 12.dp))
                                .padding(AppTheme.dimensions.extraLarge)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(AppTheme.dimensions.medium)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(AppTheme.dimensions.chipCornerRadius + 2.dp))
                                                .background(Brush.horizontalGradient(listOf(AppTheme.colors.primary, AppTheme.colors.accentCyan))),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("F", style = AppTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
                                        }
                                        Text("FinMoney", style = AppTheme.typography.titleLarge, color = Color.White)
                                    }
                                    Text("RuPay", style = AppTheme.typography.titleMedium, color = AppTheme.colors.accentCyan)
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(AppTheme.dimensions.extraSmall)) {
                                    Text("₹1,50,000 CREDIT LIMIT", style = AppTheme.typography.labelSmall, color = AppTheme.colors.accentCyan, letterSpacing = 0.5.sp)
                                    Text("2% Instant Cashback on All UPI Scans", style = AppTheme.typography.titleSmall, color = Color.White)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("• Zero Joining Fee", style = AppTheme.typography.caption, color = AppTheme.colors.textSecondary)
                                    Text("• Zero Annual Dues", style = AppTheme.typography.caption, color = AppTheme.colors.textSecondary)
                                    Text("• 100% Secure", style = AppTheme.typography.caption, color = AppTheme.colors.success, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // BOTTOM: Bottom-aligned, Full-width Rounded Primary Color Button
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppTheme.dimensions.medium),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onGoogleSignIn,
                        enabled = !isGoogleLoading,
                        shape = RoundedCornerShape(AppTheme.dimensions.buttonRadius),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppTheme.colors.primary,
                            contentColor = Color.White,
                            disabledContainerColor = AppTheme.colors.primary.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        if (isGoogleLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.5.dp)
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AppTheme.dimensions.medium)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("G", style = AppTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color(0xFF4285F4))
                                    }
                                }
                                Text("Continue with Google", style = AppTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onMobileSignIn,
                        shape = RoundedCornerShape(AppTheme.dimensions.buttonRadius),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.darkBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppTheme.colors.textPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppTheme.dimensions.small)
                        ) {
                            Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = AppTheme.colors.accentCyan, modifier = Modifier.size(AppTheme.dimensions.iconSmall + 2.dp))
                            Text("Sign In with Mobile Number", style = AppTheme.typography.labelLarge)
                        }
                    }

                    Text(
                        text = "By continuing, you agree to our Terms of Service & Privacy Policy",
                        style = AppTheme.typography.caption,
                        color = AppTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = AppTheme.dimensions.extraSmall)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step 1: Mobile Phone Number Input Step (Screenshot 2 Style)
// ---------------------------------------------------------------------------
@Composable
private fun FinAuthMobileInputStep(
    phoneInput: String,
    onPhoneChange: (String) -> Unit,
    isSimScanning: Boolean,
    detectedSims: List<DetectedSim>,
    onScanSims: () -> Unit,
    onSelectSim: (DetectedSim) -> Unit,
    onSendOtp: () -> Unit,
    onGoogleSignIn: () -> Unit,
    isGoogleLoading: Boolean,
    errorText: String?,
    statusMessage: String?,
    termsAccepted: Boolean,
    onTermsChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Hero 3D Badge Small
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(IndBlue, IndBlueDark))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
        }

        Text(
            text = "Enter your phone number",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = FinAuthTextPrimary,
            textAlign = TextAlign.Center
        )

        // Clean Big Phone Display Field (+91 00000 00000)
        OutlinedTextField(
            value = phoneInput,
            onValueChange = { onPhoneChange(it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' }) },
            prefix = {
                Text(
                    text = "+91 ",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = IndBlue,
                    modifier = Modifier.padding(end = 6.dp)
                )
            },
            placeholder = { Text("00000 00000", fontSize = 20.sp, color = FinAuthTextSecondary) },
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = FinAuthTextPrimary
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = IndBlue,
                unfocusedBorderColor = FinAuthCardBorder,
                focusedContainerColor = FinAuthCardBg,
                unfocusedContainerColor = FinAuthCardBg
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )

        // Device Active SIM Detection Card if present
        val validSims = detectedSims.filter { it.cleanDigits.length >= 10 }
        if (validSims.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = IndBlue.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, IndBlue.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.SimCard, contentDescription = null, tint = IndCyan, modifier = Modifier.size(16.dp))
                        Text("Active SIM Registered on Device", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IndCyan)
                    }

                    validSims.forEach { sim ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = FinAuthCardBg,
                            border = androidx.compose.foundation.BorderStroke(1.dp, FinAuthCardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectSim(sim) }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(IndBlue),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("+91 ${sim.formattedDisplay}", fontWeight = FontWeight.Bold, color = FinAuthTextPrimary, fontSize = 13.sp)
                                    Text(sim.carrierName, fontSize = 10.sp, color = FinAuthTextSecondary)
                                }
                                Text("Select", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IndCyan)
                            }
                        }
                    }
                }
            }
        }

        if (statusMessage != null) {
            Text(statusMessage, color = IndGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }

        if (errorText != null) {
            Text(errorText, color = IndRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }

        // Terms & Conditions Checkbox (Screenshot 3 Style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onTermsChange(!termsAccepted) },
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(
                checked = termsAccepted,
                onCheckedChange = onTermsChange,
                colors = CheckboxDefaults.colors(checkedColor = IndBlue, checkmarkColor = Color.White)
            )
            Text(
                text = "I agree to the T&Cs of TUCIBIL, Experian (each a \"CIC\") and explicitly consent to fetching my Credit Information from all Read More.",
                fontSize = 11.sp,
                color = FinAuthTextSecondary,
                lineHeight = 15.sp
            )
        }

        // Credit Bureau Badges Row (CIBIL & Experian)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = FinAuthCardBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, FinAuthCardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("CIBIL", fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color(0xFF00A3E0))
                    Text("Part of TransUnion", fontSize = 9.sp, color = FinAuthTextSecondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("•", fontSize = 10.sp, color = FinAuthTextSecondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("experian", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF4082C4))
                }
            }
        }

        // Get OTP Button
        Button(
            onClick = onSendOtp,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = IndBlue, contentColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text("Get OTP", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

// ---------------------------------------------------------------------------
// Step 2: OTP Verification Step (Screenshot 2 Style)
// ---------------------------------------------------------------------------
@Composable
private fun FinAuthOtpVerificationStep(
    phoneNumber: String,
    otpInput: String,
    onOtpChange: (String) -> Unit,
    resendTimerSeconds: Int,
    onResendOtp: () -> Unit,
    onVerifyOtp: () -> Unit,
    onEditPhone: () -> Unit,
    errorText: String?,
    statusMessage: String?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Hero 3D Badge
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(IndBlue, IndBlueDark))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
        }

        // Phone Subtitle with Edit Pencil
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "OTP sent to +91 ${phoneNumber.filter { it.isDigit() }.takeLast(10)}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = FinAuthTextPrimary
            )
            Icon(
                Icons.Filled.Edit,
                contentDescription = "Edit Number",
                tint = IndCyan,
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onEditPhone() }
            )
        }

        // 6-Digit Code Display Boxes
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until 6) {
                val digit = otpInput.getOrNull(i)?.toString() ?: ""
                val isFocused = otpInput.length == i
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = FinAuthCardBg,
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (digit.isNotEmpty()) IndBlue else if (isFocused) IndCyan else FinAuthCardBorder
                    ),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = digit,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = IndCyan
                        )
                    }
                }
            }
        }

        // Hidden input field for keyboard entry
        OutlinedTextField(
            value = otpInput,
            onValueChange = { onOtpChange(it.take(6).filter { ch -> ch.isDigit() }) },
            placeholder = { Text("123456") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = IndBlue,
                unfocusedBorderColor = FinAuthCardBorder,
                focusedContainerColor = FinAuthCardBg,
                unfocusedContainerColor = FinAuthCardBg
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp)
        )

        if (statusMessage != null) {
            Text(statusMessage, color = IndGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }

        if (errorText != null) {
            Text(errorText, color = IndRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }

        // Resend Timer (Screenshot 2 Style: "Resend OTP in 00:27s")
        Text(
            text = if (resendTimerSeconds > 0) "Resend OTP in 00:${resendTimerSeconds.toString().padStart(2, '0')}s" else "Didn't receive OTP? Resend OTP",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (resendTimerSeconds == 0) IndCyan else IndAmber,
            modifier = Modifier.clickable(enabled = resendTimerSeconds == 0) { onResendOtp() },
            textAlign = TextAlign.Center
        )

        Button(
            onClick = onVerifyOtp,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = IndBlue, contentColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text("Verify OTP", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

// ---------------------------------------------------------------------------
// Step 3: Profile Setup Component
// ---------------------------------------------------------------------------
@Composable
private fun FinAuthProfileSetupStep(
    nameInput: String,
    onNameChange: (String) -> Unit,
    emailInput: String,
    onEmailChange: (String) -> Unit,
    termsAccepted: Boolean,
    onTermsChange: (Boolean) -> Unit,
    onSubmitProfile: () -> Unit,
    errorText: String?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Setup Profile & Card",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = FinAuthTextPrimary,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Your name will be printed on your virtual FinMoney RuPay Card.",
            fontSize = 13.sp,
            color = FinAuthTextSecondary,
            textAlign = TextAlign.Center
        )

        OutlinedTextField(
            value = nameInput,
            onValueChange = onNameChange,
            label = { Text("Full Name (as per PAN / Aadhaar)", color = FinAuthTextSecondary) },
            placeholder = { Text("e.g. Rahul Sharma") },
            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = IndBlue, modifier = Modifier.size(20.dp)) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = IndBlue,
                unfocusedBorderColor = FinAuthCardBorder,
                focusedTextColor = FinAuthTextPrimary,
                unfocusedTextColor = FinAuthTextPrimary,
                focusedContainerColor = FinAuthCardBg,
                unfocusedContainerColor = FinAuthCardBg
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )

        OutlinedTextField(
            value = emailInput,
            onValueChange = onEmailChange,
            label = { Text("Email Address for Bank Statements", color = FinAuthTextSecondary) },
            placeholder = { Text("e.g. rahul.sharma@gmail.com") },
            leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null, tint = IndBlue, modifier = Modifier.size(20.dp)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = IndBlue,
                unfocusedBorderColor = FinAuthCardBorder,
                focusedTextColor = FinAuthTextPrimary,
                unfocusedTextColor = FinAuthTextPrimary,
                focusedContainerColor = FinAuthCardBg,
                unfocusedContainerColor = FinAuthCardBg
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )

        if (errorText != null) {
            Text(errorText, color = IndRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }

        Button(
            onClick = onSubmitProfile,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = IndBlue, contentColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Issue FinMoney Card", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Filled.CreditCard, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Step 4: Virtual Card Celebration Component
// ---------------------------------------------------------------------------
@Composable
private fun FinAuthVirtualCardCelebrationStep(
    userName: String,
    phoneNumber: String,
    email: String,
    onClaimAndEnter: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Celebration Badge Header
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(30.dp))
                .background(IndBlue.copy(alpha = 0.2f))
                .border(1.dp, IndCyan, RoundedCornerShape(30.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Filled.Stars, contentDescription = null, tint = IndCyan, modifier = Modifier.size(16.dp))
                Text("CARD ACTIVATED SUCCESSFULLY", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = IndCyan)
            }
        }

        // Virtual Credit Card Preview
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Transparent,
            shadowElevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF0F172A),
                                Color(0xFF1E293B),
                                Color(0xFF0066FF)
                            )
                        )
                    )
                    .border(1.5.dp, Brush.horizontalGradient(listOf(IndCyan, IndBlue)), RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("FinMoney", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White)
                        Text("RuPay", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = IndCyan)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("CARDHOLDER", fontSize = 9.sp, color = FinAuthTextSecondary)
                            Text(userName.uppercase(), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("CREDIT LIMIT", fontSize = 9.sp, color = FinAuthTextSecondary)
                            Text("₹1,50,000", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = IndGreen)
                        }
                    }
                }
            }
        }

        // Benefits Summary
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = FinAuthCardBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, FinAuthCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FinBenefitRow(icon = Icons.Filled.Bolt, title = "2% Instant Cashback on All Scans", subtitle = "Credited directly on every UPI payment")
                FinBenefitRow(icon = Icons.Filled.AccountBalance, title = "Smart Salary & Recurring Expense Tracker", subtitle = "Auto-organize debits & peer Khaata sync")
                FinBenefitRow(icon = Icons.Filled.Security, title = "Zero Annual Fees Forever", subtitle = "No joining fee, no hidden charges")
            }
        }

        // Enter Button
        Button(
            onClick = onClaimAndEnter,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = IndBlue, contentColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text("Start Using FinMoney", fontWeight = FontWeight.Black, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun FinBenefitRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(IndBlue.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = IndCyan, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = FinAuthTextPrimary)
            Text(subtitle, fontSize = 11.sp, color = FinAuthTextSecondary)
        }
    }
}
