package com.example.ui.screens

import android.app.Activity
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.zIndex
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
import com.example.BuildConfig
import com.example.R
import com.example.identity.UserIdentityBindingEngine.BindPhase
import com.example.service.FinMoneyAuthService
import com.example.ui.FinMoneyViewModel
import com.example.ui.theme.*
import com.example.util.SimDetectionHelper
import com.example.util.launchCatching
import kotlinx.coroutines.CoroutineScope
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
    var phoneVerificationId by remember { mutableStateOf("") }
    var isSendingOtp by remember { mutableStateOf(false) }
    var isPhoneVerified by remember { mutableStateOf(false) }
    var isEmailVerificationPending by remember { mutableStateOf(false) }
    var isGoogleSignedIn by remember { mutableStateOf(false) }
    var isGoogleLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    // Onboarding Step State:
    // 0 = Feature Highlights Carousel / Landing
    // 1 = Phone Number Entry
    // 2 = OTP Verification
    // 3 = Profile Personalization
    // 4 = Email verification (Firebase)
    var currentStep by remember { mutableStateOf(0) }
    var termsAccepted by remember { mutableStateOf(true) }

    fun triggerOtpDispatch(phoneNumber: String, forceResend: Boolean = false) {
        val digits = phoneNumber.filter { it.isDigit() }.takeLast(10)
        if (digits.length < 10) {
            errorText = "Please enter a valid 10-digit mobile number."
            return
        }
        val activity = context as? ComponentActivity
        if (activity == null) {
            errorText = "Unable to start phone verification on this device."
            return
        }
        if (!termsAccepted) {
            errorText = "Please accept the Terms of Service and Privacy Policy to continue."
            return
        }

        isSendingOtp = true
        errorText = null
        statusMessage = null
        otpInput = ""
        phoneVerificationId = ""
        coroutineScope.launchCatching(
            tag = "AuthScreen",
            onError = { e ->
                Log.e("AuthScreen", "Auth flow error", e)
                errorText = e.localizedMessage ?: e.message ?: "Something went wrong. Please try again."
            }
        ) {
            try {
                val collision = viewModel.validateIdentifierOwnership(phone = digits, email = emailInput.trim())
                if (collision.isFailure) {
                    errorText = collision.exceptionOrNull()?.message
                    return@launchCatching
                }

                val dispatch = viewModel.sendLoginPhoneOtp(activity, digits, forceResend)
                if (dispatch.isFailure) {
                    errorText = FinMoneyAuthService.userMessageForPhoneAuthError(dispatch.exceptionOrNull(), context)
                    return@launchCatching
                }

                val result = dispatch.getOrNull() ?: return@launchCatching
                if (result.instantSignIn) {
                    val reserve = viewModel.reservePhoneAfterVerification(digits)
                    if (reserve.isFailure) {
                        errorText = reserve.exceptionOrNull()?.message
                        return@launchCatching
                    }
                    isPhoneVerified = true
                    statusMessage = "Mobile number verified successfully! ✓"
                    currentStep = 3
                    return@launchCatching
                }

                phoneVerificationId = result.verificationId ?: ""
                if (phoneVerificationId.isBlank()) {
                    errorText = "Could not start OTP verification. Please try again."
                    return@launchCatching
                }
                statusMessage = "OTP sent via SMS to +91 ${digits.takeLast(10)}."
                currentStep = 2
            } catch (e: Exception) {
                Log.e("AuthScreen", "OTP dispatch crashed", e)
                errorText = e.localizedMessage ?: e.message ?: "Could not send OTP. Please try again."
            } finally {
                isSendingOtp = false
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            viewModel.syncVerificationStatusFromFirebase()
        } catch (e: Exception) {
            Log.w("AuthScreen", "Verification sync skipped: ${e.message}")
        }
    }

    // Resume INDmoney-style verification if user dropped off mid onboarding
    LaunchedEffect(
        currentUser?.userId,
        currentUser?.isOtpVerified,
        currentUser?.isEmailVerified,
        currentUser?.phoneNumber,
        currentUser?.email
    ) {
        val profile = currentUser ?: return@LaunchedEffect
        val isPlaceholder = profile.userId == "user_default" &&
            profile.phoneNumber.isBlank() &&
            profile.email.isBlank()
        if (isPlaceholder || (profile.isOtpVerified && profile.isEmailVerified)) return@LaunchedEffect

        if (profile.name.isNotBlank() && profile.name != "Signed Out") {
            nameInput = profile.name
        }
        if (profile.phoneNumber.isNotBlank()) phoneInput = profile.phoneNumber
        if (profile.email.isNotBlank()) emailInput = profile.email
        isPhoneVerified = profile.isOtpVerified

        statusMessage = "Complete mobile & email verification to access FinMoney."
        when {
            !profile.isOtpVerified -> currentStep = 1
            !profile.isEmailVerified -> currentStep = if (profile.email.isBlank()) 3 else 4
        }
    }

    fun routeAfterIdentityProviders(name: String, email: String, phoneDigits: String) {
        val gUser = Firebase.auth.currentUser
        val phoneLinked = FinMoneyAuthService.isPhoneLinkedOnFirebase(gUser)
        val emailVerified = FinMoneyAuthService.isEmailVerifiedOnFirebase(gUser)
        if (phoneDigits.isNotBlank()) phoneInput = phoneDigits
        when {
            !phoneLinked -> {
                statusMessage = "Verify your mobile number with OTP (required)."
                currentStep = 1
            }
            !emailVerified -> {
                emailInput = email
                isEmailVerificationPending = true
                currentStep = if (email.isBlank()) 3 else 4
                statusMessage = "Verify your email to activate FinMoney."
            }
            else -> {
                coroutineScope.launchCatching(
            tag = "AuthScreen",
            onError = { e ->
                Log.e("AuthScreen", "Auth flow error", e)
                errorText = e.localizedMessage ?: e.message ?: "Something went wrong. Please try again."
            }
        ) {
                    viewModel.syncVerificationStatusFromFirebase()
                    statusMessage = "Signed in as $name ✓"
                    onContinue()
                }
            }
        }
    }

    var activeCarouselIndex by remember { mutableStateOf(0) }
    var resendTimerSeconds by remember { mutableStateOf(30) }

    var isPickingPhoneHint by remember { mutableStateOf(false) }

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
                isPhoneVerified = false
                statusMessage = "Number selected. Tap Get OTP to verify via SMS."
                errorText = null
            }
        }
    }

    fun requestPhoneNumberFromDevice() {
        val activity = context as? ComponentActivity
        if (activity == null) {
            errorText = "Cannot open number picker on this device."
            return
        }
        isPickingPhoneHint = true
        SimDetectionHelper.requestPhoneNumberHint(
            activity = activity,
            onSuccess = { pendingIntent ->
                isPickingPhoneHint = false
                phoneNumberHintLauncher.launch(IntentSenderRequest.Builder(pendingIntent).build())
            },
            onFailure = { e ->
                isPickingPhoneHint = false
                Log.w("AuthScreen", "Phone hint unavailable: ${e.message}")
                errorText = "Could not open the number picker. Type your 10-digit mobile number manually."
            }
        )
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

        val credentialManager = CredentialManager.create(context)
        val signInOption = if (clientId.isNotBlank()) {
            GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        } else null

        coroutineScope.launchCatching(
            tag = "AuthScreen",
            onError = { e ->
                Log.e("AuthScreen", "Auth flow error", e)
                errorText = e.localizedMessage ?: e.message ?: "Something went wrong. Please try again."
            }
        ) {
            try {
                if (signInOption == null) {
                    val fbUser = Firebase.auth.currentUser
                    val fbEmail = fbUser?.email?.takeIf { it.isNotBlank() }
                    if (fbUser != null && fbEmail != null) {
                        val gName = fbUser.displayName ?: nameInput.ifBlank { "FinMoney User" }
                        val gEmail = fbEmail
                        val gPhoto = fbUser.photoUrl?.toString()
                        nameInput = gName
                        emailInput = gEmail
                        profilePicUri = gPhoto ?: ""
                        isGoogleSignedIn = true

                        val cleanPhone = phoneInput.filter { it.isDigit() }
                        val phoneDigits = FinMoneyAuthService.phoneDigitsFromFirebase(fbUser).ifBlank { cleanPhone.takeLast(10) }
                        val regResult = viewModel.registerOrAuthenticateUser(
                            name = gName,
                            email = gEmail,
                            phone = phoneDigits,
                            profilePicUri = gPhoto,
                            isOtpVerified = FinMoneyAuthService.isPhoneLinkedOnFirebase(fbUser),
                            isEmailVerified = FinMoneyAuthService.isEmailVerifiedOnFirebase(fbUser)
                        )
                        if (regResult.isSuccess) {
                            routeAfterIdentityProviders(gName, gEmail, phoneDigits)
                        } else {
                            errorText = regResult.exceptionOrNull()?.message
                        }
                    } else {
                        errorText = "Google Sign-In is unavailable. Please sign in using Mobile Number OTP."
                    }
                    return@launchCatching
                }

                val hostActivity = context as? ComponentActivity
                if (hostActivity == null) {
                    errorText = "Google Sign-In is unavailable on this screen."
                    return@launchCatching
                }
                val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()
                val result = credentialManager.getCredential(hostActivity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdToken.idToken
                    val linkResult = viewModel.linkGoogleIdToken(idToken)
                    if (linkResult.isFailure) {
                        errorText = linkResult.exceptionOrNull()?.localizedMessage ?: "Google sign-in failed."
                        return@launchCatching
                    }

                    val gUser = Firebase.auth.currentUser
                    val detectedName = gUser?.displayName ?: googleIdToken.displayName ?: nameInput.ifBlank { "FinMoney User" }
                    val detectedEmail = gUser?.email ?: googleIdToken.id ?: emailInput
                    val detectedPhoto = gUser?.photoUrl?.toString() ?: googleIdToken.profilePictureUri?.toString() ?: profilePicUri

                    nameInput = detectedName
                    emailInput = detectedEmail
                    profilePicUri = detectedPhoto
                    isGoogleSignedIn = true

                    val cleanPhone = phoneInput.filter { it.isDigit() }
                    val phoneDigits = FinMoneyAuthService.phoneDigitsFromFirebase(gUser).ifBlank { cleanPhone.takeLast(10) }
                    if (phoneDigits.isNotBlank()) phoneInput = phoneDigits

                    val googleBindCheck = viewModel.validateIdentifierOwnership(
                        phoneDigits,
                        detectedEmail,
                        BindPhase.PRE_EMAIL_BIND
                    )
                    if (googleBindCheck.isFailure) {
                        errorText = googleBindCheck.exceptionOrNull()?.message
                        return@launchCatching
                    }

                    val regResult = viewModel.registerOrAuthenticateUser(
                        name = detectedName,
                        email = detectedEmail,
                        phone = phoneDigits,
                        profilePicUri = detectedPhoto,
                        isOtpVerified = FinMoneyAuthService.isPhoneLinkedOnFirebase(gUser),
                        isEmailVerified = FinMoneyAuthService.isEmailVerifiedOnFirebase(gUser)
                    )

                    if (regResult.isSuccess) {
                        routeAfterIdentityProviders(detectedName, detectedEmail, phoneDigits)
                    } else {
                        errorText = regResult.exceptionOrNull()?.message
                    }
                } else {
                    errorText = "Invalid Google credential returned. Please try again."
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w("AuthScreen", "Google Sign-In cancelled: ${e.message}")
                statusMessage = "Google Sign-In cancelled."
            } catch (e: Exception) {
                Log.e("AuthScreen", "Google Sign-In exception: ${e.message}", e)
                val fbUser = Firebase.auth.currentUser
                val fbEmail = fbUser?.email?.takeIf { it.isNotBlank() }
                if (fbUser != null && fbEmail != null) {
                    val gName = fbUser.displayName ?: "FinMoney User"
                    val gEmail = fbEmail
                    val cleanPhone = phoneInput.filter { it.isDigit() }
                    val phoneDigits = FinMoneyAuthService.phoneDigitsFromFirebase(fbUser).ifBlank { cleanPhone.takeLast(10) }
                    val regResult = viewModel.registerOrAuthenticateUser(
                        name = gName,
                        email = gEmail,
                        phone = phoneDigits,
                        isOtpVerified = FinMoneyAuthService.isPhoneLinkedOnFirebase(fbUser),
                        isEmailVerified = FinMoneyAuthService.isEmailVerifiedOnFirebase(fbUser)
                    )
                    if (regResult.isSuccess) {
                        routeAfterIdentityProviders(gName, gEmail, phoneDigits)
                        return@launchCatching
                    }
                }
                errorText = "Google Sign-In failed: ${e.localizedMessage ?: "Unknown error"}. Please sign in with Mobile Number."
            } finally {
                isGoogleLoading = false
            }
        }
    }

    fun performDemoReviewerSignIn() {
        val reviewerName = "Play Store Reviewer"
        val reviewerEmail = "demo.reviewer@finmoney.app"
        val reviewerPhone = "9876543210"
        nameInput = reviewerName
        emailInput = reviewerEmail
        phoneInput = reviewerPhone
        isPhoneVerified = true
        isGoogleSignedIn = true
        coroutineScope.launchCatching(
            tag = "AuthScreen",
            onError = { e ->
                Log.e("AuthScreen", "Auth flow error", e)
                errorText = e.localizedMessage ?: e.message ?: "Something went wrong. Please try again."
            }
        ) {
            val result = viewModel.registerOrAuthenticateUser(
                name = reviewerName,
                email = reviewerEmail,
                phone = reviewerPhone,
                isOtpVerified = true,
                isEmailVerified = true
            )
            if (result.isSuccess) {
                statusMessage = "Logged in as Play Store Reviewer ✓"
                onContinue()
            } else {
                errorText = result.exceptionOrNull()?.message
            }
        }
    }

    val featureSlides = remember {
        listOf(
            FinAuthSlide(
                title = "Automated Salary & Income Tracker",
                subtitle = "Auto-detect salary credits, organize monthly earnings & optimize your flexible cash flow.",
                badgeText = "💵 SMART SALARY TRACKING",
                highlightStat = "100% Automated Income",
                icon = Icons.Filled.AccountBalance
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
                        onPhoneChange = { raw ->
                            val digits = raw.filter { it.isDigit() }
                            phoneInput = when {
                                digits.length > 10 && digits.startsWith("91") -> digits.takeLast(10)
                                digits.length > 10 -> digits.takeLast(10)
                                else -> digits
                            }
                        },
                        isPickingPhoneHint = isPickingPhoneHint,
                        onPickPhoneFromDevice = { requestPhoneNumberFromDevice() },
                        onSendOtp = {
                            if (!isSendingOtp) triggerOtpDispatch(phoneInput)
                        },
                        isSendingOtp = isSendingOtp,
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
                            val digits = phoneInput.filter { it.isDigit() }
                            triggerOtpDispatch(digits, forceResend = true)
                            resendTimerSeconds = 30
                        },
                        onVerifyOtp = {
                            coroutineScope.launchCatching(
            tag = "AuthScreen",
            onError = { e ->
                Log.e("AuthScreen", "Auth flow error", e)
                errorText = e.localizedMessage ?: e.message ?: "Something went wrong. Please try again."
            }
        ) {
                                val cleanPhone = phoneInput.filter { it.isDigit() }.takeLast(10)
                                val confirm = viewModel.confirmLoginPhoneOtp(phoneVerificationId, otpInput, cleanPhone)
                                if (confirm.isSuccess) {
                                    isPhoneVerified = true
                                    statusMessage = "Mobile number verified successfully! ✓"
                                    errorText = null
                                    currentStep = 3
                                    return@launchCatching
                                }
                                if (BuildConfig.DEBUG && cleanPhone == "9876543210" && otpInput == "123456") {
                                    isPhoneVerified = true
                                    nameInput = "Play Store Reviewer"
                                    emailInput = "demo.reviewer@finmoney.app"
                                    currentStep = 3
                                    return@launchCatching
                                }
                                errorText = confirm.exceptionOrNull()?.localizedMessage
                                    ?: "Invalid OTP. Enter the 6-digit code from SMS."
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
                            val cleanEmail = emailInput.trim().lowercase()
                            if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
                                errorText = "Please enter a valid email address."
                                return@FinAuthProfileSetupStep
                            }
                            val cleanPhone = phoneInput.filter { it.isDigit() }.takeLast(10)
                            if (!isPhoneVerified) {
                                errorText = "Verify your mobile number before continuing."
                                currentStep = 1
                                return@FinAuthProfileSetupStep
                            }
                            coroutineScope.launchCatching(
            tag = "AuthScreen",
            onError = { e ->
                Log.e("AuthScreen", "Auth flow error", e)
                errorText = e.localizedMessage ?: e.message ?: "Something went wrong. Please try again."
            }
        ) {
                                val collision = viewModel.validateIdentifierOwnership(
                                    cleanPhone,
                                    cleanEmail,
                                    BindPhase.PRE_EMAIL_BIND
                                )
                                if (collision.isFailure) {
                                    errorText = collision.exceptionOrNull()?.message
                                    return@launchCatching
                                }
                                val bind = viewModel.bindEmailAndSendVerification(cleanEmail)
                                if (bind.isFailure) {
                                    errorText = bind.exceptionOrNull()?.localizedMessage ?: "Could not send verification email."
                                    return@launchCatching
                                }
                                val emailVerifiedNow = viewModel.refreshEmailVerificationStatus().getOrDefault(false)
                                val result = viewModel.registerOrAuthenticateUser(
                                    name = nameInput.trim(),
                                    email = cleanEmail,
                                    phone = cleanPhone,
                                    profilePicUri = profilePicUri,
                                    isOtpVerified = true,
                                    isEmailVerified = emailVerifiedNow
                                )
                                if (result.isFailure) {
                                    errorText = result.exceptionOrNull()?.message ?: "Registration failed."
                                    return@launchCatching
                                }
                                errorText = null
                                if (emailVerifiedNow) {
                                    viewModel.syncVerificationStatusFromFirebase().getOrNull()
                                    onContinue()
                                } else {
                                    isEmailVerificationPending = true
                                    statusMessage = "Verification link sent to $cleanEmail"
                                    currentStep = 4
                                }
                            }
                        },
                        errorText = errorText
                    )

                    4 -> FinAuthEmailVerificationStep(
                        email = emailInput.trim(),
                        statusMessage = statusMessage,
                        errorText = errorText,
                        onResendEmail = {
                            coroutineScope.launchCatching(
            tag = "AuthScreen",
            onError = { e ->
                Log.e("AuthScreen", "Auth flow error", e)
                errorText = e.localizedMessage ?: e.message ?: "Something went wrong. Please try again."
            }
        ) {
                                val resend = viewModel.bindEmailAndSendVerification(emailInput.trim())
                                statusMessage = if (resend.isSuccess) {
                                    "Verification email resent."
                                } else {
                                    null
                                }
                                errorText = resend.exceptionOrNull()?.localizedMessage
                            }
                        },
                        onRefreshVerified = {
                            coroutineScope.launchCatching(
            tag = "AuthScreen",
            onError = { e ->
                Log.e("AuthScreen", "Auth flow error", e)
                errorText = e.localizedMessage ?: e.message ?: "Something went wrong. Please try again."
            }
        ) {
                                val verified = viewModel.refreshEmailVerificationStatus().getOrDefault(false)
                                if (!verified) {
                                    errorText = "Email not verified yet. Open the link in your inbox, then tap again."
                                    return@launchCatching
                                }
                                val cleanPhone = phoneInput.filter { it.isDigit() }.takeLast(10)
                                viewModel.registerOrAuthenticateUser(
                                    name = nameInput.trim(),
                                    email = emailInput.trim(),
                                    phone = cleanPhone,
                                    profilePicUri = profilePicUri,
                                    isOtpVerified = true,
                                    isEmailVerified = true
                                )
                                viewModel.syncVerificationStatusFromFirebase()
                                errorText = null
                                onContinue()
                            }
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
                        .padding(top = AppTheme.dimensions.medium)
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.app_icon_logo),
                        contentDescription = "FinMoney App Icon",
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(18.dp))
                    )

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
                        text = "Your smart salary tracker, recurring expense manager & Khaata peer ledger.",
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
                                    Text("PERSONAL FINANCE", style = AppTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = AppTheme.colors.accentCyan)
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(AppTheme.dimensions.extraSmall)) {
                                    Text("SMART MONTHLY BUDGET & LEDGER", style = AppTheme.typography.labelSmall, color = AppTheme.colors.accentCyan, letterSpacing = 0.5.sp)
                                    Text("Automated Salary Tracking, Debits & Khaata", style = AppTheme.typography.titleSmall, color = Color.White)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("• 100% Encrypted", style = AppTheme.typography.caption, color = AppTheme.colors.textSecondary)
                                    Text("• Bank-Grade Privacy", style = AppTheme.typography.caption, color = AppTheme.colors.textSecondary)
                                    Text("• Zero Manual Dues", style = AppTheme.typography.caption, color = AppTheme.colors.success, fontWeight = FontWeight.Bold)
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
    isPickingPhoneHint: Boolean,
    onPickPhoneFromDevice: () -> Unit,
    onSendOtp: () -> Unit,
    isSendingOtp: Boolean,
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
            text = "Enter your mobile number",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = FinAuthTextPrimary,
            textAlign = TextAlign.Center
        )

        // Clean Big Phone Display Field (+91 00000 00000)
        OutlinedTextField(
            value = phoneInput,
            onValueChange = onPhoneChange,
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

        OutlinedButton(
            onClick = onPickPhoneFromDevice,
            enabled = !isPickingPhoneHint,
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, IndBlue.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isPickingPhoneHint) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.ContactPhone, contentDescription = null, tint = IndCyan, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "Use number on this phone (no call permission)",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = FinAuthTextPrimary
            )
        }

        if (statusMessage != null) {
            Text(statusMessage, color = IndGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }

        if (errorText != null) {
            Text(errorText, color = IndRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }

        // Clean Terms & Conditions Checkbox
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onTermsChange(!termsAccepted) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(
                checked = termsAccepted,
                onCheckedChange = onTermsChange,
                colors = CheckboxDefaults.colors(checkedColor = IndBlue, checkmarkColor = Color.White)
            )
            Text(
                text = "I agree to the Terms of Service & Privacy Policy of FinMoney.",
                fontSize = 12.sp,
                color = FinAuthTextSecondary,
                lineHeight = 16.sp
            )
        }

        Button(
            onClick = onSendOtp,
            enabled = termsAccepted && !isSendingOtp,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = IndBlue, contentColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            if (isSendingOtp) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Get OTP", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
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
            placeholder = { Text("000000", color = FinAuthTextSecondary.copy(alpha = 0.3f)) },
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
            text = "Setup Your Account Profile",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = FinAuthTextPrimary,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Enter your name and email to personalize your FinMoney ledger and budget.",
            fontSize = 13.sp,
            color = FinAuthTextSecondary,
            textAlign = TextAlign.Center
        )

        OutlinedTextField(
            value = nameInput,
            onValueChange = onNameChange,
            label = { Text("Full Name", color = FinAuthTextSecondary) },
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
            label = { Text("Email Address for Bank Statements & Reports", color = FinAuthTextSecondary) },
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
            Text("Complete Profile & Enter App", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun FinAuthEmailVerificationStep(
    email: String,
    statusMessage: String?,
    errorText: String?,
    onResendEmail: () -> Unit,
    onRefreshVerified: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(IndBlue, IndBlueDark))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.MarkEmailUnread, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
        }

        Text(
            text = "Verify your email",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = FinAuthTextPrimary,
            textAlign = TextAlign.Center
        )

        Text(
            text = "We sent a secure verification link to $email. Open it on this device, then return here.",
            fontSize = 13.sp,
            color = FinAuthTextSecondary,
            textAlign = TextAlign.Center
        )

        if (statusMessage != null) {
            Text(statusMessage, color = IndGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
        if (errorText != null) {
            Text(errorText, color = IndRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }

        OutlinedButton(
            onClick = onResendEmail,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text("Resend verification email")
        }

        Button(
            onClick = onRefreshVerified,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = IndBlue, contentColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("I've verified — Continue", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
