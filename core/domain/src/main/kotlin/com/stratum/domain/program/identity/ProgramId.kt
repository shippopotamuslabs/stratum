package com.stratum.domain.program.identity

import com.stratum.domain.lift.identity.EntityId

/** Type-safe identifier for a [com.stratum.domain.program.Program]. */
@JvmInline
value class ProgramId(override val value: String) : EntityId {
    init {
        require(value.isNotBlank()) { "ProgramId cannot be blank" }
    }

    val isImportHistory: Boolean
        get() = value == IMPORT_HISTORY_VALUE

    companion object {
        /** Stable id for the import-history FK parent (never a user training program). */
        const val IMPORT_HISTORY_VALUE: String = "import-history"

        val ImportHistory: ProgramId = ProgramId(IMPORT_HISTORY_VALUE)
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

/** Type-safe identifier linking [com.stratum.domain.program.WorkoutLift]s in a superset/giant set. */
@JvmInline
value class WorkoutSupersetGroupId(override val value: String) : EntityId {
    init {
        require(value.isNotBlank()) { "WorkoutSupersetGroupId cannot be blank" }
    }
}

/**
 * Type-safe identifier for a single row in the per-slot workout note list
 * (multi-note successor of the legacy `workout_lifts.pinned_note` column).
 */
@JvmInline
value class WorkoutLiftNoteId(override val value: String) : EntityId {
    init {
        require(value.isNotBlank()) { "WorkoutLiftNoteId cannot be blank" }
    }
}
