package com.stratum.domain.workout

import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutSupersetGroupId

/** A projected lift within a [WorkoutStructureProjection]. */
data class WorkoutLiftProjection(
    val workoutLiftId: WorkoutLiftId,
    val liftId: LiftId,
    val liftName: String,
    val progressionScheme: ProgressionScheme,
    val slots: List<SetSlot>,
    val supersetGroupId: WorkoutSupersetGroupId? = null,
    /** Template-assigned equipment (null = inherit the lift's). Swaps override at merge. */
    val equipmentId: EquipmentId? = null,
    val loadType: LoadType = LoadType.Weighted,
    val useRestTimerBetweenLifts: Boolean = false,
    val isPlateLoaded: Boolean = false,
    val isDoubleSided: Boolean = true,
    /**
     * Ordered pinned notes on the [com.stratum.domain.program.WorkoutLift] template slot.
     * Falls back to the legacy single [com.stratum.domain.program.WorkoutLift.pinnedNote]
     * when the slot predates list storage.
     */
    val workoutPinnedNotes: List<String> = emptyList(),
    /**
     * Ordered pinned notes on the catalog [com.stratum.domain.lift.Lift].
     * Falls back to the legacy single [com.stratum.domain.lift.Lift.pinnedNote].
     */
    val liftPinnedNotes: List<String> = emptyList(),
)

/**
 * Structure projection read model (D1). Built by `ProjectWorkoutStructureUseCase` —
 * runtime view of `WorkoutLift`/`LiftSet` ordering plus dynamic adds/deletes;
 * carries set type, rep range, RIR, top-set marking — **no load**.
 */
data class WorkoutStructureProjection(
    val workoutId: WorkoutId,
    val workoutName: String,
    val lifts: List<WorkoutLiftProjection>,
)
