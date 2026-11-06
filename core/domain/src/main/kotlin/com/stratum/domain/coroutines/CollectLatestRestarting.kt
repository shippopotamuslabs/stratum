package com.stratum.domain.logging

import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive

internal const val DEFAULT_COLLECTOR_RESTART_DELAY_MS = 1_000L

/**
 * Collects [this] with [collectLatest], restarting after unexpected failures so a single
 * emission/throw does not kill a process-lifetime listener. Completes when the upstream
 * completes normally. [CancellationException] is not swallowed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun <T> Flow<T>.collectLatestRestarting(
    loggingPort: LoggingPort,
    tag: String,
    restartDelayMs: Long = DEFAULT_COLLECTOR_RESTART_DELAY_MS,
    collector: suspend (value: T) -> Unit,
) {
    while (coroutineContext.isActive) {
        try {
            collectLatest(collector)
            break
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            loggingPort.error(tag, "Collector failed: ${error.message}", error)
            delay(restartDelayMs)
        }
    }
}
