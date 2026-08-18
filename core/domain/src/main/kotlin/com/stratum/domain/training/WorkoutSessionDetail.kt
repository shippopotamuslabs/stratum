package com.stratum.domain.training

import com.stratum.domain.lift.Weight
import com.stratum.domain.program.MuscleVolumeSummary
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant

/** Full read model for the workout history session detail screen. */
data class WorkoutSessionDetail(
    val sessionId: TrainingSessionId,
    val workoutName: String,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val completedAt: Instant,
    val durationMillis: Long,
    val totalSets: Int,
    val personalRecordCount: Int,
    val muscleVolumes: List<MuscleVolumeSummary>,
    val lifts: List<WorkoutSessionLiftDetail>,
)

data class WorkoutSessionLiftDetail(
    val liftName: String,
    val sets: List<WorkoutSessionSetDetail>,
)

data class WorkoutSessionSetDetail(
    val setIndex: Int,
    val weight: Weight,
    val reps: Int,
    val rir: Int?,
    val isPersonalRecord: Boolean,
)
