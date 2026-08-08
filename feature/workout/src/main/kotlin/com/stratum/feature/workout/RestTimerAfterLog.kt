package com.stratum.feature.workout

import com.stratum.domain.program.SetType
import com.stratum.domain.workout.intensifier.RestDefaultForTransition
import com.stratum.domain.workout.myorep.MyoRepKind
import com.stratum.feature.workout.notification.RestTimerDefaults

/**
 * Pure decision for whether the live rest timer should start after logging a set, and
 * for how many seconds. Keeps Compose free of the superset / rest-between-lifts rules.
 *
 * Myo dual-edge is **role-based** (not magnitude heuristics on rest seconds):
 * - [nextIsMyoMini] → short mini rest (15s, or the mini's programmed rest when [isMyoMini])
 * - cluster finished after a mini → [liftDefaultRestSeconds] (main inter-set rest)
 * - cluster finished after match working → [setRestSeconds] (stores main rest)
 * - cluster finished after floor activation (no mini next) → [liftDefaultRestSeconds]
 *   (floor rows store mini rest for intra-cluster; exit uses main)
 *
 * Prefer computing the final duration in the session runtime and shipping it on
 * [com.stratum.feature.workout.ui.WorkoutUiEvent.SetLogged.restSeconds].
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
    /** True when the set just logged is an auto-generated myo mini. */
    isMyoMini: Boolean = false,
    /** Kind of the logged myo set (null when not myo). */
    myoRepKind: MyoRepKind? = null,
    /** True when the next visible set is an auto-generated myo mini. */
    nextIsMyoMini: Boolean = false,
    nextIsDropSet: Boolean = false,
    liftDefaultRestSeconds: Int = defaultRestSeconds,
    /**
     * Legacy alias: when true, treated as [nextIsMyoMini] only if [nextIsMyoMini] is false.
     * Prefer [nextIsMyoMini] — do **not** set this for “next is match backoff”.
     */
    nextIsMyoSet: Boolean = false,
): Int? {
    if (workoutFullyComplete) return null

    val shouldStart =
        when {
            !isSupersetMember -> true
            useRestTimerBetweenLifts -> true
            else -> isLastInSupersetChain
        }
    if (!shouldStart) return null

    // Only minis continue the short-rest cluster. Match backoffs are working sets (main rest).
    val clusterContinues = nextIsMyoMini || nextIsMyoSet

    val effectiveRestSeconds =
        if (isMyoSet) {
            val nextType =
                when {
                    nextIsDropSet -> SetType.DropSet
                    clusterContinues -> SetType.MyoRep
                    else -> null
                }
            val miniRest =
                when {
                    isMyoMini && setRestSeconds > 0 -> setRestSeconds
                    else -> RestDefaultForTransition.MYO_MINI_DEFAULT_REST_SECONDS
                }
            val mainRest =
                when {
                    // Match working rows store main inter-set rest.
                    !isMyoMini && myoRepKind == MyoRepKind.RepMatching && setRestSeconds > 0 ->
                        setRestSeconds
                    // Floor activation stores mini rest; cluster exit uses lift main rest.
                    // Minis always end the cluster with lift main rest.
                    else -> liftDefaultRestSeconds
                }
            RestDefaultForTransition.restAfterMyoLog(
                miniRestSeconds = miniRest,
                clusterContinues = clusterContinues,
                liftDefaultRestSeconds = liftDefaultRestSeconds,
                nextType = nextType,
                mainRestSeconds = mainRest,
            )
        } else {
            setRestSeconds
        }

    return when {
        effectiveRestSeconds > 0 -> effectiveRestSeconds
        effectiveRestSeconds == 0 -> null
        else -> defaultRestSeconds
    }
}
