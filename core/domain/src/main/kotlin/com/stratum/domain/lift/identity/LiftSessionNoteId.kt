package com.stratum.domain.lift.identity

import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.identity.TrainingSessionId

/**
 * Type-safe identifier for a [com.stratum.domain.lift.LiftSessionNote] row.
 */
@JvmInline
value class LiftSessionNoteId(val value: String) {
    init {
        require(value.isNotBlank()) { "LiftSessionNoteId cannot be blank" }
    }

    companion object {
        /** Stable id for one slot so devices do not mint competing unique-index rows. */
        fun forSlot(
            sessionId: TrainingSessionId,
            workoutLiftId: WorkoutLiftId,
        ): LiftSessionNoteId = LiftSessionNoteId("${sessionId.value}::${workoutLiftId.value}")
    }
}
