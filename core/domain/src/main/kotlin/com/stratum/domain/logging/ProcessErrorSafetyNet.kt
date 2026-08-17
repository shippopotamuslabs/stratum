package com.stratum.domain.logging

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * Last-resort failure reporting so unexpected crashes always leave a stack via [LoggingPort]
 * (Logcat + Crashlytics breadcrumbs through the platform adapter).
 *
 * Install once at process start. Chains to the previous uncaught handler so the system crash
 * dialog and Firebase Crashlytics fatal path still run.
 */
@Singleton
class ProcessErrorSafetyNet @Inject constructor(
    private val loggingPort: LoggingPort,
) {
    /**
     * Logs the throwable without a Crashlytics non-fatal, then delegates to the previous default
     * handler. Safe to call once from [android.app.Application.onCreate].
     */
    fun installUncaughtExceptionHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val exceptionName = throwable::class.simpleName ?: "Throwable"
                val message = throwable.message ?: "no message"
                loggingPort.errorSkippingNonFatal(
                    TAG_UNCAUGHT,
                    "Uncaught $exceptionName in thread ${thread.name}: $message",
                    throwable,
                )
            } finally {
                previous?.uncaughtException(thread, throwable)
            }
        }
    }

    /**
     * Handler for process-lifetime scopes ([com.stratum.domain.di.ApplicationScope]).
     * Does not rethrow — a SupervisorJob parent keeps siblings alive; the failure is recorded.
     */
    fun applicationCoroutineExceptionHandler(): CoroutineExceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            loggingPort.error(
                TAG_APPLICATION_SCOPE,
                "Unhandled coroutine failure: ${throwable.message}",
                throwable,
            )
        }

    private companion object {
        const val TAG_UNCAUGHT = "Uncaught"
        const val TAG_APPLICATION_SCOPE = "ApplicationScope"
    }
}
