package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.service.BankSyncManager
import com.example.ui.FinMoneyViewModel
import com.example.ui.MainScreen
import com.example.ui.theme.FinMoneyTheme
import com.example.ui.theme.IndBackground
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

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
                    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
                    val shouldOpenPending by openPendingState

                    val isLoggedIn = userProfile != null &&
                        userProfile?.name != "Signed Out" &&
                        (userProfile?.isOtpVerified == true || userProfile?.isEmailVerified == true || !userProfile?.phoneNumber.isNullOrBlank() || !userProfile?.email.isNullOrBlank() || com.google.firebase.Firebase.auth.currentUser != null)

                    if (isLoggedIn) {
                        MainScreen(
                            initialOpenPending = shouldOpenPending,
                            onPendingConsumed = { openPendingState.value = false }
                        )
                    } else {
                        com.example.ui.screens.AuthScreen(
                            viewModel = viewModel,
                            onContinue = {
                                // Transition to MainScreen upon auth completion
                            }
                        )
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
