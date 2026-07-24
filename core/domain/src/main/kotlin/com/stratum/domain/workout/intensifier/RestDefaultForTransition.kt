package com.stratum.domain.workout.intensifier

import com.stratum.domain.program.DEFAULT_LIFT_SET_REST_SECONDS
import com.stratum.domain.program.SetType

/**
 * Edge-based rest defaults for intensifier sets.
 *
 * - next is Drop → 0 (no rest into a drop, including Drop→Drop)
 * - current is MyoRep → [MYO_MINI_DEFAULT_REST_SECONDS] (short intra-block rest)
 * - otherwise → [liftDefaultRestSeconds] (High fatigue 180 / else 120 when caller supplies)
 *
 * Live myo minis inherit the activation set's programmed rest when present; this helper
 * only supplies the template default when a set is authored or re-typed as MyoRep.
 */
object RestDefaultForTransition {
    const val REST_INTO_DROP_SECONDS: Int = 0
    const val MYO_MINI_DEFAULT_REST_SECONDS: Int = 15

    /**
     * Default rest stored on a set given its own type and what follows it.
     * [nextType] null means end of lift / next exercise.
     */
    fun restAfter(
        nextType: SetType?,
        liftDefaultRestSeconds: Int = DEFAULT_LIFT_SET_REST_SECONDS,
        currentType: SetType? = null,
    ): Int {
        if (nextType == SetType.DropSet) return REST_INTO_DROP_SECONDS
        if (currentType == SetType.MyoRep) return MYO_MINI_DEFAULT_REST_SECONDS
        return liftDefaultRestSeconds.coerceAtLeast(0)
    }

    /**
     * Whether the rest control should be shown when the following set is [nextType].
     * Forced-zero edges hide the control (cleaner than a locked 0:00 chip).
     */
    fun showRestControl(nextType: SetType?): Boolean =
        nextType != SetType.DropSet

    /**
     * Re-defaults rest for every set in a template after a type change.
     * [setTypes] ordered by position; returns parallel rest seconds.
     */
    fun redefaultAll(
        setTypes: List<SetType>,
        liftDefaultRestSeconds: Int = DEFAULT_LIFT_SET_REST_SECONDS,
    ): List<Int> =
        setTypes.mapIndexed { index, current ->
            val next = setTypes.getOrNull(index + 1)
            restAfter(
                nextType = next,
                liftDefaultRestSeconds = liftDefaultRestSeconds,
                currentType = current,
            )
        }
}
