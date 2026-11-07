package com.stratum.domain.coroutines

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
 *
 * Domain has no logging capability (STR-237): the failure record is boundary-owned.
 * The collecting boundary passes [onFailure], invoked exactly once per unexpected
 * failure with the legacy tag, the rendered message, and the real throwable; the
 * boundary records it through its own wiring.
 */
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun <T> Flow<T>.collectLatestRestarting(
    onFailure: (tag: String, message: String, error: Throwable) -> Unit,
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
            onFailure(tag, "Collector failed: ${error.message}", error)
            delay(restartDelayMs)
        }
    }
}
