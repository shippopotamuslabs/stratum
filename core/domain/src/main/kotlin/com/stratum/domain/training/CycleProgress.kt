package com.stratum.domain.training

import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.Program
import com.stratum.domain.program.Workout
import com.stratum.domain.program.identity.WorkoutId

data class CycleProgress(
    val program: Program?,
    val activeSession: TrainingSession?,
    val completedWorkoutIdsThisCycle: Set<WorkoutId>,
    val completedRestDayWeekdaysThisCycle: Set<Weekday>,
    val nextWorkout: Workout?,
    val isCycleComplete: Boolean,
)
