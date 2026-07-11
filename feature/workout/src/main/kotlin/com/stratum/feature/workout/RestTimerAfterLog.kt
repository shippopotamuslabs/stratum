package com.stratum.feature.workout

import com.stratum.feature.workout.notification.RestTimerDefaults

/**
 * Pure decision for whether the live rest timer should start after logging a set, and
 * for how many seconds. Keeps Compose free of the superset / rest-between-lifts rules.
 *
 * @return rest duration in seconds, or `null` when the timer should not start.
 */
fun restSecondsAfterLoggingSet(
    setRestSeconds: Int,
    isSupersetMember: Boolean,
    useRestTimerBetweenLifts: Boolean,
    isLastInSupersetChain: Boolean,
    defaultRestSeconds: Int = RestTimerDefaults.DEFAULT_REST_SECONDS,
): Int? {
    val shouldStart =
        when {
            !isSupersetMember -> true
            useRestTimerBetweenLifts -> true
            else -> isLastInSupersetChain
        }
    if (!shouldStart) return null
    // Prefer the set's configured rest; fall back when missing/zero (legacy or unset).
    return if (setRestSeconds > 0) setRestSeconds else defaultRestSeconds
}
