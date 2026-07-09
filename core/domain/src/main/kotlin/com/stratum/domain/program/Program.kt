package com.stratum.domain.program

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutSupersetGroupId
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
    /** Sessions in one full training cycle before repeating (may exceed workouts trained per calendar week). */
    val cycleLength: Int,
    /** Calendar days this program trains on; rest days are [Weekday.entries] minus this set. */
    val trainingWeekdays: Set<Weekday> = emptySet(),
    val workouts: List<Workout>,
    val currentCycleNumber: Int = 1,
    val cyclePosition: Int = 0,
    /**
     * Whether this is the user's active training program. At most one user program is active;
     * library copies and the import-history FK parent stay inactive. Multi-program `isActive`
     * authorized for create-program / library (AGENTS.md §4.5 — v1 reinstall, no migration).
     */
    val isActive: Boolean = true,
)

data class Workout(
    val id: WorkoutId,
    val programId: ProgramId,
    val name: String,
    val weekday: Weekday?,
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
)

/**
 * A single set in a [WorkoutLift]. Named `LiftSet` (not `Set`) to avoid the
 * `kotlin.collections.Set` collision in-package.
 *
 * On a `Double` progression lift, exactly one set has [isTopSet] = true and carries the rep range
 * ([repMin]/[repMax]); back-offs may omit the range (server-side convention). [rirGoal] is
 * deterministically clamped by the generation post-pass (D2).
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
)
