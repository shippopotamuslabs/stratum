package com.stratum.domain.program.identity

import com.stratum.domain.lift.identity.EntityId

/** Type-safe identifier for a [com.stratum.domain.program.Program]. */
@JvmInline
value class ProgramId(override val value: String) : EntityId {
    init {
        require(value.isNotBlank()) { "ProgramId cannot be blank" }
    }
}

/** Type-safe identifier for a [com.stratum.domain.program.Workout]. */
@JvmInline
value class WorkoutId(override val value: String) : EntityId {
    init {
        require(value.isNotBlank()) { "WorkoutId cannot be blank" }
    }
}

/** Type-safe identifier for a [com.stratum.domain.program.WorkoutLift]. */
@JvmInline
value class WorkoutLiftId(override val value: String) : EntityId {
    init {
        require(value.isNotBlank()) { "WorkoutLiftId cannot be blank" }
    }
}

/** Type-safe identifier for a [com.stratum.domain.program.LiftSet]. */
@JvmInline
value class LiftSetId(override val value: String) : EntityId {
    init {
        require(value.isNotBlank()) { "LiftSetId cannot be blank" }
    }
}
