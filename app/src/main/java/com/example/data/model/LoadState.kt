package com.example.data.model

sealed class LoadState<out T> {
    object Loading : LoadState<Nothing>()
    data class Success<out T>(val data: T) : LoadState<T>()
    data class Error(val message: String, val cause: Throwable? = null) : LoadState<Nothing>()
}
