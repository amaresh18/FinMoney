package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.service.BankSyncManager
import com.example.ui.FinMoneyViewModel
import com.example.ui.MainScreen
import com.example.ui.components.BrandLogo
import com.example.ui.theme.FinMoneyTheme
import com.example.ui.theme.IndBackground
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

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
                            profile.isOtpVerified &&
                            profile.isEmailVerified

                        Crossfade(
                            targetState = isLoggedIn,
                            label = "AppAuthTransition"
                        ) { loggedIn ->
                            if (loggedIn) {
                                val openLoanCloudId by openLoanCloudIdState
                                val openLoanId by openLoanIdState
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
