package com.example.util

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Launches a coroutine and routes unexpected failures to [onError] instead of crashing the app.
 */
inline fun CoroutineScope.launchCatching(
    tag: String = "CoroutineSafety",
    crossinline onError: (Throwable) -> Unit = { Log.e(tag, "Coroutine failed: ${it.message}", it) },
    crossinline block: suspend CoroutineScope.() -> Unit
) = launch launchCatching@{
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        onError(e)
    }
}
