package com.stratum.domain.training

import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant

data class LoggedSet(
    val id: LoggedSetId,
    val sessionId: TrainingSessionId,
    val exerciseNameRaw: String,
    val liftId: LiftId?,
    val setIndex: Int,
    val weight: Weight,
    val reps: Int,
    val rir: Int?,
    val completedAt: Instant,
    val liftContext: LoggedLiftContext,
    val isPersonalRecord: Boolean = false,
    val estimatedOneRm: Double? = null,
    /** Incomplete extra reps after [reps]; null/0 means none logged. Not used in progression. */
    val partialReps: Int? = null,
)

/** Treats null and non-positive values as "no partials logged." */
fun Int?.normalizedPartialReps(): Int? = this?.takeIf { it > 0 }
