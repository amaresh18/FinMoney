package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.FinMoneyViewModel
import com.example.ui.MainScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.FinMoneyTheme
import com.example.ui.theme.IndBackground
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.util.PhoneNotificationHelper

class MainActivity : ComponentActivity() {
    private val viewModel: FinMoneyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PhoneNotificationHelper.initChannel(this)

        setContent {
            FinMoneyTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = IndBackground
                ) {
                    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
                    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
                    var isGuestMode by remember { mutableStateOf(false) }
                    var isLocalLoggedIn by remember { mutableStateOf(false) }

                    // Request notification permission on Android 13+
                    val notifPermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { _ -> }

                    LaunchedEffect(Unit) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }

                    DisposableEffect(Unit) {
                        val listener = FirebaseAuth.AuthStateListener { auth ->
                            currentUser = auth.currentUser
                        }
                        Firebase.auth.addAuthStateListener(listener)
                        onDispose {
                            Firebase.auth.removeAuthStateListener(listener)
                        }
                    }

                    // A user is authenticated if Firebase has a user, or the local userProfile is OTP-verified,
                    // or guest mode is chosen, or local login was completed in this session
                    val isProfileVerified = userProfile?.isOtpVerified == true && !userProfile?.name.isNullOrBlank()
                    val isAuthenticated = currentUser != null || isProfileVerified || isGuestMode || isLocalLoggedIn

                    if (!isAuthenticated) {
                        AuthScreen(
                            viewModel = viewModel,
                            onAuthSuccess = {
                                isLocalLoggedIn = true
                                currentUser = Firebase.auth.currentUser
                            },
                            onContinueAsGuest = {
                                isGuestMode = true
                            }
                        )
                    } else {
                        MainScreen(
                            viewModel = viewModel,
                            onSignOut = {
                                currentUser = null
                                isGuestMode = false
                                isLocalLoggedIn = false
                                viewModel.clearUserProfile()
                            }
                        )
                    }
                }
            }
        }
    }
}
