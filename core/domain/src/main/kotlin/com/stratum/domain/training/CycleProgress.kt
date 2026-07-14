package com.stratum.domain.training

import com.stratum.domain.program.Program
import com.stratum.domain.program.Workout
import com.stratum.domain.program.identity.WorkoutId

data class CycleProgress(
    val program: Program?,
    val activeSession: TrainingSession?,
    val completedWorkoutIdsThisCycle: Set<WorkoutId>,
    val completedRestSlotIndicesThisCycle: Set<Int> = emptySet(),
    val nextWorkout: Workout?,
    val isCycleComplete: Boolean,
    /** Next unconsumed cycle slot index (cache-aligned with [Program.cyclePosition]). */
    val cyclePosition: Int = 0,
)
