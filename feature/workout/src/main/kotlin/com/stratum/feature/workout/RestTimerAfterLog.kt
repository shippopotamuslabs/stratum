package com.stratum.feature.workout

import com.stratum.domain.program.SetType
import com.stratum.domain.workout.intensifier.RestDefaultForTransition
import com.stratum.feature.workout.notification.RestTimerDefaults

/**
 * Pure decision for whether the live rest timer should start after logging a set, and
 * for how many seconds. Keeps Compose free of the superset / rest-between-lifts rules.
 *
 * Myo dual-edge: when [isMyoSet] is true, [setRestSeconds] is treated as **mini rest**
 * (intra-cluster). If [nextIsMyoSet], the timer uses that mini rest; when the cluster
 * ends, the timer uses [liftDefaultRestSeconds] instead (unless next is a drop → 0).
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
    isMyoSet: Boolean = false,
    nextIsMyoSet: Boolean = false,
    nextIsDropSet: Boolean = false,
    liftDefaultRestSeconds: Int = defaultRestSeconds,
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

    val effectiveRestSeconds =
        if (isMyoSet) {
            val nextType =
                when {
                    nextIsDropSet -> SetType.DropSet
                    nextIsMyoSet -> SetType.MyoRep
                    else -> null
                }
            RestDefaultForTransition.restAfterMyoLog(
                miniRestSeconds = setRestSeconds,
                clusterContinues = nextIsMyoSet,
                liftDefaultRestSeconds = liftDefaultRestSeconds,
                nextType = nextType,
            )
        } else {
            setRestSeconds
        }

    // Explicit 0 means "no rest" (e.g. Configure Rest Times). Negative is treated as unset.
    return when {
        effectiveRestSeconds > 0 -> effectiveRestSeconds
        effectiveRestSeconds == 0 -> null
        else -> defaultRestSeconds
    }
}
