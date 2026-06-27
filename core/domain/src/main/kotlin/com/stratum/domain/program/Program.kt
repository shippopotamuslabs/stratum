package com.stratum.domain.program

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import java.time.Instant

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
    val workouts: List<Workout>,
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
)
