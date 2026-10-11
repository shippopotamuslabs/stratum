package com.stratum.domain.workout

import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutSupersetGroupId
import com.stratum.domain.workout.myorep.MyoRepKind

/** Merge-result read model for a single set (D1). */
data class RenderedSet(
    val slotId: SetSlotId,
    val workoutLiftId: WorkoutLiftId,
    val liftId: LiftId,
    val position: Int,
    val setType: SetType,
    val isTopSet: Boolean,
    val repMin: Int,
    val repMax: Int,
    val rirGoal: Int,
    val restSeconds: Int,
    val recommendedWeight: Weight,
    val recommendedReps: Int,
    /**
     * True when [recommendedReps] was projected from the most recent performed set via a
     * live overlay recommendation (D4). False when [recommendedReps] is just the snapshot
     * baseline. The UI uses this to decide whether to show a concrete rep count or the
     * `repMin-repMax` range placeholder.
     */
    val hasLiveRepsRecommendation: Boolean = false,
    val loggedValue: LoggedSetValue?,
    val previousWeight: Weight? = null,
    val previousReps: Int? = null,
    /** Prior session RIR for this slot position (UI prev column); null when unlogged. */
    val previousRir: Int? = null,
    /** Prior session partial reps for this slot position (UI prev column); null when none. */
    val previousPartialReps: Int? = null,
    val isProjected: Boolean = false,
    /** Present for MyoRep slots so UI can distinguish floor (M) vs match (MM). */
    val myoRepKind: MyoRepKind? = null,
)

/** Merge-result read model for a single lift (D1). */
data class RenderedWorkoutLift(
    val workoutLiftId: WorkoutLiftId,
    val liftId: LiftId,
    val liftName: String,
    val progressionScheme: ProgressionScheme,
    val sets: List<RenderedSet>,
    val supersetGroupId: WorkoutSupersetGroupId? = null,
    /**
     * Equipment in use for this lift this session: live-swap override, else the
     * template assignment, else the lift default (resolved at merge). Null means
     * bodyweight / unassigned.
     */
    val equipmentId: EquipmentId? = null,
    val loadType: LoadType = LoadType.Weighted,
    val useRestTimerBetweenLifts: Boolean = false,
    val isPlateLoaded: Boolean = false,
    val isDoubleSided: Boolean = true,
    /**
     * Ordered pinned notes for display. The legacy single-note readers were removed in
     * favor of these lists (work item: show all pins on the lift card, not just the first).
     */
    val workoutPinnedNotes: List<String> = emptyList(),
    val liftPinnedNotes: List<String> = emptyList(),
)

/** The fully merged rendered workout (D1). Pure output of `MergeRenderedWorkoutUseCase`. */
data class RenderedWorkout(
    val workoutId: WorkoutId,
    val workoutName: String,
    val lifts: List<RenderedWorkoutLift>,
)
