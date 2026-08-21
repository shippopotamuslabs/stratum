package com.stratum.domain.program

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutSupersetGroupId
import com.stratum.domain.workout.myorep.MyoRepKind
import java.time.Instant

/** Default inter-set rest (seconds) for generated and legacy persisted sets. */
const val DEFAULT_LIFT_SET_REST_SECONDS = 120

/**
 * Program hierarchy (vertical-slices §2.4): `Program → Workout → WorkoutLift → LiftSet`.
 *
 * This is the **template** hierarchy produced by generation (OS-7) — distinct from the logged-set
 * hierarchy in OS-6. All working sets are straight sets (single load per lift); `LiftSet` carries
 * no weight/load field (D2). `progressionScheme` on [WorkoutLift] is server-assigned from
 * `TrainingAge` during generation (D2); the model never authors it.
 */
data class Program(
    val id: ProgramId,
    val name: String,
    val createdAt: Instant,
    /**
     * Number of slots in one full cycle (training workouts **plus** rest slots).
     * Equals `workouts.size + restSlotIndices.size`. Not “training days only.”
     */
    val cycleLength: Int,
    /**
     * Denormalized projection cache of training weekdays when known. **Not** schedule identity —
     * slot index + [restSlotIndices] are authoritative. Prefer empty for rotating / unbound.
     */
    val trainingWeekdays: Set<Weekday> = emptySet(),
    val workouts: List<Workout>,
    val currentCycleNumber: Int = 1,
    /**
     * Next unconsumed slot index in `[0, cycleLength)`, derived from the session ledger
     * (cache). Completing a slot advances; full cycle resets to 0 and bumps [currentCycleNumber].
     */
    val cyclePosition: Int = 0,
    /**
     * Whether this is the user's active training program. At most one user program is active;
     * library copies and the import-history FK parent stay inactive. Multi-program `isActive`
     * authorized for create-program / library (AGENTS.md §4.5 — v1 reinstall, no migration).
     */
    val isActive: Boolean = true,
    /** Indices in `[0, cycleLength)` that are rest slots (not training). */
    val restSlotIndices: Set<Int> = emptySet(),
    /**
     * Epoch day of slot 0 for the current cycle (optional projection anchor for history /
     * skip weekday snapshots). Programs strip UI is order-only and does not display dates
     * derived from this field.
     */
    val cycleStartEpochDay: Long? = null,
)

data class Workout(
    val id: WorkoutId,
    val programId: ProgramId,
    val name: String,
    /**
     * Optional projection cache only — **not** schedule identity. Prefer null.
     * Programs strip is order-only (workout name / Rest Day).
     */
    val weekday: Weekday?,
    /** Order among training sessions in the cycle (0..T-1), not calendar slot index. */
    val position: Int,
    val workoutLifts: List<WorkoutLift>,
)

data class WorkoutLift(
    val id: WorkoutLiftId,
    val workoutId: WorkoutId,
    val liftId: LiftId,
    val progressionScheme: ProgressionScheme,
    val position: Int,
    val sets: List<LiftSet>,
    val supersetGroupId: WorkoutSupersetGroupId? = null,
    val useRestTimerBetweenLifts: Boolean = false,
    /** User-authored note pinned to this template slot across sessions of this workout. */
    val pinnedNote: String? = null,
)

/**
 * A single set in a [WorkoutLift]. Named `LiftSet` (not `Set`) to avoid the
 * `kotlin.collections.Set` collision in-package.
 *
 * Rep-range / top-set shape is scheme-dependent — see [ProgressionRepRangePolicy] (canonical):
 * Double: exactly one top set owns [repMin]/[repMax]; back-offs are `0/0` (RIR-only).
 * Linear: every working set stores the shared band; first set is the progression anchor.
 * [rirGoal] is clamped by the generation post-pass (D2).
 */
data class LiftSet(
    val id: LiftSetId,
    val workoutLiftId: WorkoutLiftId,
    val position: Int,
    val setType: SetType,
    val repMin: Int,
    val repMax: Int,
    val rirGoal: Int,
    val isTopSet: Boolean,
    val restSeconds: Int = DEFAULT_LIFT_SET_REST_SECONDS,
    /**
     * Myo-rep sub-type when [setType] is [SetType.MyoRep]; null for Standard/Drop.
     * Persisted on the program template (Room v2).
     */
    val myoRepKind: MyoRepKind? = null,
)
