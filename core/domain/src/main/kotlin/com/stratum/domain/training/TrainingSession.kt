package com.stratum.domain.training

import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant

enum class TrainingSessionStatus {
    IN_PROGRESS,
    COMPLETED,
}

enum class TrainingSessionKind {
    PROGRAM_WORKOUT,
    REST_DAY,
    AD_HOC,
}

enum class TrainingSessionOrigin {
    IMPORT,
    LIVE,
}

data class TrainingSession(
    val id: TrainingSessionId,
    val programId: ProgramId,
    val workoutId: WorkoutId?,
    val status: TrainingSessionStatus,
    val kind: TrainingSessionKind,
    val origin: TrainingSessionOrigin,
    val cycleNumberSnapshot: Int,
    val weekdaySnapshot: Weekday?,
    val startedAt: Instant,
    val endedAt: Instant?,
    val sourceApp: String?,
)
