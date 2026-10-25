package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.Box
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.service.FinMoneyAuthService
import com.example.service.BankSyncManager
import com.example.ui.FinMoneyViewModel
import com.example.ui.MainScreen
import com.example.ui.components.BrandLogo
import com.example.ui.components.CloudSyncRestoreOverlay
import com.example.ui.theme.FinMoneyTheme
import com.example.ui.theme.IndBackground
import com.example.ui.theme.IndBlue
import com.example.ui.theme.IndBlueDark
import com.example.ui.theme.IndCyan
import com.example.ui.theme.FinAuthDarkBg
import com.example.util.BiometricAuthHelper
import kotlinx.coroutines.delay

class MainActivity : FragmentActivity() {

    private val openPendingState = mutableStateOf(false)
    private val openLoanCloudIdState = mutableStateOf<String?>(null)
    private val openLoanIdState = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        applyOpenIntent(intent)

        setContent {
            FinMoneyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = IndBackground
                ) {
                    val viewModel: FinMoneyViewModel = viewModel()
                    val userProfileState by viewModel.userProfile.collectAsStateWithLifecycle()
                    val cloudSyncUi by viewModel.cloudSyncUi.collectAsStateWithLifecycle()
                    val shouldOpenPending by openPendingState
                    val lifecycleOwner = LocalLifecycleOwner.current

                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                viewModel.syncVerificationStatusFromFirebaseAsync()
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    var isInitializing by remember { mutableStateOf(true) }

                    LaunchedEffect(userProfileState) {
                        // Brief settlement delay ensures smooth surface transition on app boot
                        delay(50)
                        isInitializing = false
                    }

                    if (isInitializing) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(IndBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            BrandLogo(
                                name = "FinMoney",
                                badgeSize = 64.dp
                            )
                        }
                    } else {
                        val profile = userProfileState
                        val isPlaceholderProfile = profile?.userId == "user_default" &&
                            profile.phoneNumber.isBlank() &&
                            profile.email.isBlank()
                        val isLoggedIn = profile != null &&
                            !isPlaceholderProfile &&
                            profile.name != "Signed Out" &&
                            profile.name.isNotBlank() &&
                            (profile.email.isNotBlank() || FinMoneyAuthService.currentFirebaseUser() != null)

                        Crossfade(
                            targetState = isLoggedIn,
                            label = "AppAuthTransition"
                        ) { loggedIn ->
                            if (loggedIn) {
                                val isBioCapable = remember {
                                    BiometricAuthHelper.canAuthenticate(this@MainActivity) &&
                                        BiometricAuthHelper.isBiometricEnabled(this@MainActivity)
                                }
                                var isBiometricUnlocked by remember(profile?.userId) {
                                    mutableStateOf(!isBioCapable)
                                }
                                var biometricError by remember { mutableStateOf<String?>(null) }

                                fun triggerBiometricUnlock() {
                                    biometricError = null
                                    BiometricAuthHelper.authenticate(
                                        activity = this@MainActivity,
                                        title = "Unlock FinMoney",
                                        subtitle = "Confirm fingerprint or face unlock to access financial data",
                                        onSuccess = { isBiometricUnlocked = true },
                                        onError = { error -> biometricError = error }
                                    )
                                }

                                LaunchedEffect(loggedIn, isBioCapable) {
                                    if (isBioCapable && !isBiometricUnlocked) {
                                        triggerBiometricUnlock()
                                    }
                                }

                                if (isBioCapable && !isBiometricUnlocked) {
                                    BiometricLockScreen(
                                        onUnlock = { triggerBiometricUnlock() },
                                        errorMessage = biometricError
                                    )
                                } else {
                                    val openLoanCloudId by openLoanCloudIdState
                                    val openLoanId by openLoanIdState
                                    Box(Modifier.fillMaxSize()) {
                                        MainScreen(
                                            initialOpenPending = shouldOpenPending,
                                            onPendingConsumed = { openPendingState.value = false },
                                            initialLoanCloudId = openLoanCloudId,
                                            initialLoanId = openLoanId,
                                            onLoanOpenConsumed = {
                                                openLoanCloudIdState.value = null
                                                openLoanIdState.value = null
                                            }
                                        )
                                        if (cloudSyncUi.isRunning && cloudSyncUi.showFullscreenOverlay) {
                                            CloudSyncRestoreOverlay(
                                                state = cloudSyncUi,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .zIndex(30f)
                                            )
                                        }
                                    }
                                }
                            } else {
                                com.example.ui.screens.AuthScreen(
                                    viewModel = viewModel,
                                    onContinue = {
                                        viewModel.syncVerificationStatusFromFirebaseAsync()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyOpenIntent(intent)
    }

    private fun applyOpenIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == BankSyncManager.ACTION_OPEN_PENDING) {
            openPendingState.value = true
        }
        if (intent.action == com.example.service.LoanInviteService.ACTION_OPEN_LOAN ||
            intent.data?.host == com.example.service.LoanInviteService.DEEP_LINK_HOST
        ) {
            openLoanCloudIdState.value = intent.getStringExtra(com.example.service.LoanInviteService.EXTRA_LOAN_CLOUD_ID)
                ?: intent.data?.lastPathSegment
            val id = intent.getLongExtra(com.example.service.LoanInviteService.EXTRA_LOAN_ID, 0L)
            openLoanIdState.value = if (id > 0L) id else null
        }
    }

    override fun onResume() {
        super.onResume()
        BankSyncManager.ensureServiceBound(this)
        com.example.cloud.CloudSyncOutboxManager.scheduleFlush(applicationContext)
    }
}

@Composable
private fun BiometricLockScreen(
    onUnlock: () -> Unit,
    errorMessage: String? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FinAuthDarkBg)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(IndBlue, IndBlueDark))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Fingerprint,
                    contentDescription = "Biometric Lock",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }

            Text(
                text = "FinMoney Secure Lock",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Fingerprint or face unlock is required to view your financial accounts, EMIs, and transactions.",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Red.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = errorMessage,
                        color = Color(0xFFFF8A80),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onUnlock,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IndBlue, contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Unlock with Biometrics / PIN", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}
