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
    workoutFullyComplete: Boolean = false,
    defaultRestSeconds: Int = RestTimerDefaults.DEFAULT_REST_SECONDS,
): Int? {
    // Finishing the last set of the session: show Complete Workout, do not start rest chrome.
    if (workoutFullyComplete) return null

    val shouldStart =
        when {
            !isSupersetMember -> true
            useRestTimerBetweenLifts -> true
            else -> isLastInSupersetChain
        }
    if (!shouldStart) return null

    // Explicit 0 means "no rest" (e.g. Configure Rest Times). Negative is treated as unset.
    return when {
        setRestSeconds > 0 -> setRestSeconds
        setRestSeconds == 0 -> null
        else -> defaultRestSeconds
    }
}
