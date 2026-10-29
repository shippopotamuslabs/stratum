package com.stratum.domain.program.schedule

import com.stratum.domain.program.Workout
import com.stratum.domain.onboarding.Weekday

/**
 * One index in a program's cycle space of length [com.stratum.domain.program.Program.cycleLength].
 *
 * Training slots hold a template [Workout]; rest slots are first-class (count toward cycle
 * completion). Weekday is never part of slot identity — see [CycleProjection].
 */
sealed class CycleSlot {
    abstract val slotIndex: Int

    data class Training(
        override val slotIndex: Int,
        val workout: Workout,
    ) : CycleSlot()

    data class Rest(
        override val slotIndex: Int,
    ) : CycleSlot()
}
