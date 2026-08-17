package com.stratum.domain.logging

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * ViewModel coroutine scope with a [CoroutineExceptionHandler] so uncaught `viewModelScope.launch`
 * failures are recorded via [LogErrorUseCase] instead of tearing down the process.
 *
 * Pass this to `ViewModel(viewModelScope = loggedViewModelScope(...))`. Hilt ViewModels should
 * inject [LogErrorUseCase]; tests may omit it and use [SilentLoggingPort].
 */
fun loggedViewModelScope(
    tag: String,
    logError: LogErrorUseCase = LogErrorUseCase(SilentLoggingPort),
): CoroutineScope =
    CoroutineScope(
        SupervisorJob() +
            Dispatchers.Main.immediate +
            CoroutineExceptionHandler { _, throwable ->
                logError(tag, "Unhandled coroutine failure: ${throwable.message}", throwable)
            },
    )

/**
 * Launches [block] and records unexpected failures via [logError] without cancelling siblings.
 * Rethrows [CancellationException].
 */
fun CoroutineScope.launchLogged(
    logError: LogErrorUseCase,
    tag: String,
    message: String,
    block: suspend CoroutineScope.() -> Unit,
): Job =
    launch {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            logError(tag, "$message: ${error.message}", error)
        }
    }

/** No-op [LoggingPort] for ViewModel tests that construct without Hilt. */
object SilentLoggingPort : LoggingPort {
    override fun debug(tag: String, message: String) = Unit

    override fun info(tag: String, message: String) = Unit

    override fun warning(tag: String, message: String) = Unit

    override fun error(tag: String, message: String, throwable: Throwable?) = Unit
}
