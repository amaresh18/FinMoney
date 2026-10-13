package com.example.domain.model

/**
 * Standardized Unidirectional Data Flow (UDF) Presentation State.
 * Follows enterprise Clean Architecture and Material Design 3 guidelines.
 */
sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data object Empty : UiState<Nothing>
    data class Error(val message: String, val cause: Throwable? = null) : UiState<Nothing>
}

/**
 * Single-shot UI side-effects (e.g. snackbars, toasts, navigation intents).
 */
sealed interface UiEffect {
    data class ShowToast(val message: String, val isLong: Boolean = false) : UiEffect
    data class ShowSnackbar(val message: String, val actionLabel: String? = null) : UiEffect
    data class Navigate(val route: String) : UiEffect
    data object NavigateBack : UiEffect
}
