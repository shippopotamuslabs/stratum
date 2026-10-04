package com.stratum.core.inference

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * Wall-clock gap that proves the device was suspended (Doze/sleep) rather than merely slow.
 * Normal poll spacing is seconds; anything beyond this threshold is treated as lost time.
 */
internal const val SUSPENSION_GAP_THRESHOLD_MS = 30_000L

/**
 * Delay between transient status retries inside [pollStatusWithTransientRetry].
 * Matches the slow poll cadence so a retry lands on the next poll tick.
 */
internal const val TRANSIENT_RETRY_DELAY_MS = 3_000L

/**
 * Shared backoff poll loop used by cloud job adapters (import, synthesis, …).
 *
 * Defaults match historical import: 1.5s initially, then 3s after [backoffAfterMs].
 *
 * STR-55: the timeout budget covers active polling only. When the device sleeps mid-poll
 * (Doze defers the [delay] for minutes), the overshoot past the next due poll extends the
 * deadline instead of burning the budget — on wake the loop re-polls the still-running
 * server job instead of reporting a timeout.
 */
suspend fun <T> pollUntil(
    timeoutMs: Long,
    initialIntervalMs: Long = 1_500L,
    slowIntervalMs: Long = 3_000L,
    backoffAfterMs: Long = 30_000L,
    isTerminal: (T) -> Boolean,
    poll: suspend () -> T,
    nowMs: () -> Long = System::currentTimeMillis,
): T {
    var deadline = nowMs() + timeoutMs
    var elapsedMs = 0L
    var interval = initialIntervalMs
    // Due time for the next poll, fixed at the top of each iteration so a clock
    // jump during the fetch or the delay below reads as an overshoot on wake.
    // (A single pathologically slow fetch can also read as a gap; the deadline
    // only ever extends, never shortens, so the failure direction stays safe.)
    var nextPollDueAt = nowMs()
    while (true) {
        val now = nowMs()
        val overshoot = now - nextPollDueAt
        if (overshoot > SUSPENSION_GAP_THRESHOLD_MS) {
            deadline += overshoot
        }
        if (now >= deadline) break
        nextPollDueAt = now + interval
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
