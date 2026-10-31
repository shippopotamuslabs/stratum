package com.stratum.core.infrastructure.inference

import android.os.SystemClock
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * Elapsed-time gap that proves the device was suspended (Doze/sleep) rather than merely slow.
 * Normal poll spacing is seconds; anything beyond this threshold is treated as lost time.
 */
internal const val SUSPENSION_GAP_THRESHOLD_MS = 30_000L

/**
 * Elapsed-time source for poll budgets. Production uses [SystemClock.elapsedRealtime]
 * (monotonic — immune to user/NTP wall-clock jumps); tests inject a fake. The named
 * type keeps call sites from accidentally passing wall-clock.
 */
internal fun interface MonotonicClock {
    fun nowMs(): Long
}

internal val systemMonotonicClock: MonotonicClock =
    MonotonicClock { SystemClock.elapsedRealtime() }

/**
 * Delay between transient status retries inside [pollStatusWithTransientRetry].
 * Matches the slow poll cadence so a retry lands on the next poll tick.
 */
internal const val TRANSIENT_RETRY_DELAY_MS = 3_000L

/**
 * Single ownership of the STR-55 suspension policy: a gap between two timestamps
 * counts as device sleep (Doze) only when it dwarfs normal poll spacing. Both poll
 * loops consult this so threshold or Doze-semantics changes land in one place.
 */
internal fun isSuspensionGap(previousMs: Long, nowMs: Long): Boolean =
    nowMs - previousMs > SUSPENSION_GAP_THRESHOLD_MS

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
internal suspend fun <T> pollUntil(
    timeoutMs: Long,
    initialIntervalMs: Long = 1_500L,
    slowIntervalMs: Long = 3_000L,
    backoffAfterMs: Long = 30_000L,
    isTerminal: (T) -> Boolean,
    poll: suspend () -> T,
    clock: MonotonicClock = systemMonotonicClock,
): T {
    var deadline = clock.nowMs() + timeoutMs
    var elapsedMs = 0L
    var interval = initialIntervalMs
    // Due time for the next poll, fixed at the top of each iteration so time
    // deferred by Doze during the fetch or the delay below reads as an
    // overshoot on wake. (A single pathologically slow fetch can also read as
    // a gap; the deadline only ever extends, never shortens, so the failure
    // direction stays safe.)
    var nextPollDueAt = clock.nowMs()
    while (true) {
        val now = clock.nowMs()
        if (isSuspensionGap(nextPollDueAt, now)) {
            deadline += now - nextPollDueAt
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
