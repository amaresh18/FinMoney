package com.example.service

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthManager(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    companion object {
        private const val TAG = "AuthManager"
    }

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentUser = MutableStateFlow<FirebaseUser?>(firebaseAuth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(firebaseAuth.currentUser != null)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        val user = auth.currentUser
        _currentUser.value = user
        _isAuthenticated.value = user != null
        _isLoading.value = false
        if (user != null) {
            Log.i(TAG, "🔐 [AuthManager] Auth state updated: User signed in (UID=${user.uid}, Email=${user.email})")
        } else {
            Log.i(TAG, "🔓 [AuthManager] Auth state updated: User signed out / No session")
        }
    }

    init {
        try {
            firebaseAuth.addAuthStateListener(authStateListener)
            val initialUser = firebaseAuth.currentUser
            _currentUser.value = initialUser
            _isAuthenticated.value = initialUser != null
            _isLoading.value = false
        } catch (e: Exception) {
            Log.e(TAG, "❌ [AuthManager] Error adding auth state listener: ${e.message}", e)
            _isLoading.value = false
        }
    }

    fun release() {
        try {
            firebaseAuth.removeAuthStateListener(authStateListener)
        } catch (e: Exception) {
            Log.w(TAG, "Warning removing auth state listener: ${e.message}")
        }
    }
}
