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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        openPendingState.value = intent?.action == BankSyncManager.ACTION_OPEN_PENDING

        setContent {
            FinMoneyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = IndBackground
                ) {
                    val viewModel: FinMoneyViewModel = viewModel()
                    val userProfileState by viewModel.userProfile.collectAsStateWithLifecycle()
                    val shouldOpenPending by openPendingState

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
                        val isLoggedIn = userProfileState != null &&
                            userProfileState?.name != "Signed Out" &&
                            !userProfileState?.name.isNullOrBlank() &&
                            (userProfileState?.isOtpVerified == true || userProfileState?.isEmailVerified == true)

                        Crossfade(
                            targetState = isLoggedIn,
                            label = "AppAuthTransition"
                        ) { loggedIn ->
                            if (loggedIn) {
                                MainScreen(
                                    initialOpenPending = shouldOpenPending,
                                    onPendingConsumed = { openPendingState.value = false }
                                )
                            } else {
                                com.example.ui.screens.AuthScreen(
                                    viewModel = viewModel,
                                    onContinue = {
                                        viewModel.markUserLoggedIn()
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
        if (intent.action == BankSyncManager.ACTION_OPEN_PENDING) {
            openPendingState.value = true
        }
    }

    override fun onResume() {
        super.onResume()
        // Ensure BankNotificationListenerService is active and bound
        BankSyncManager.ensureServiceBound(this)
    }
}
