package com.stratum.core.inference

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * Shared backoff poll loop used by cloud job adapters (import, synthesis, …).
 *
 * Defaults match historical import: 1.5s initially, then 3s after [backoffAfterMs].
 */
suspend fun <T> pollUntil(
    timeoutMs: Long,
    initialIntervalMs: Long = 1_500L,
    slowIntervalMs: Long = 3_000L,
    backoffAfterMs: Long = 30_000L,
    isTerminal: (T) -> Boolean,
    poll: suspend () -> T,
): T {
    val deadline = System.currentTimeMillis() + timeoutMs
    var elapsedMs = 0L
    var interval = initialIntervalMs
    while (System.currentTimeMillis() < deadline) {
        val snapshot = poll()
        if (isTerminal(snapshot)) {
            return snapshot
        }
        delay(interval.milliseconds)
        elapsedMs += interval
        if (elapsedMs > backoffAfterMs) {
            interval = slowIntervalMs
        }
    }
    error("poll timed out after ${timeoutMs}ms")
}
