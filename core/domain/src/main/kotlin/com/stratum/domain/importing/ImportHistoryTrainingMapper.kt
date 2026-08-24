package com.stratum.domain.importing

import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.training.LoggedLiftContextFactory
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.normalizedPartialReps
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant

object ImportHistoryTrainingMapper {
    val programId: ProgramId = ProgramId("import-history")

    fun parseSession(
        id: String,
        startedAt: Instant,
        endedAt: Instant?,
        sourceApp: String?,
    ): TrainingSession =
        TrainingSession(
            id = TrainingSessionId(id),
            programId = programId,
            workoutId = null,
            status = TrainingSessionStatus.COMPLETED,
            kind = TrainingSessionKind.AD_HOC,
            origin = TrainingSessionOrigin.IMPORT,
            cycleNumberSnapshot = 1,
            weekdaySnapshot = null,
            startedAt = startedAt,
            endedAt = endedAt,
            sourceApp = sourceApp,
            programNameSnapshot = "Import History",
            // No per-day workout title in the import schema; calendar shows DEFAULT ("Workout")
            // via TrainingSessionDisplayName — never a lift name.
            workoutNameSnapshot = null,
        )

    fun parseSet(
        id: LoggedSetId,
        sessionId: TrainingSessionId,
        exerciseNameRaw: String,
        setIndex: Int,
        weight: Weight,
        reps: Int,
        rir: Int?,
        completedAt: Instant,
        partialReps: Int? = null,
    ): LoggedSet =
        LoggedSet(
            id = id,
            sessionId = sessionId,
            exerciseNameRaw = exerciseNameRaw,
            liftId = null,
            setIndex = setIndex,
            weight = weight,
            reps = reps,
            rir = rir,
            completedAt = completedAt,
            liftContext = placeholderLiftContext(exerciseNameRaw),
            partialReps = partialReps.normalizedPartialReps(),
        )

    fun enrichLiftContext(
        set: LoggedSet,
        factory: LoggedLiftContextFactory,
        lifts: Map<LiftId, Lift>,
    ): LoggedSet {
        val lift = set.liftId?.let { lifts[it] } ?: return set
        return set.copy(liftContext = factory.fromLiftCatalog(lift, set.exerciseNameRaw))
    }

    private fun placeholderLiftContext(liftName: String): LoggedLiftContext =
        LoggedLiftContext(
            liftName = liftName,
            movementPatternsBitmask = 0,
            primaryMusclesBitmask = 0,
            secondaryMusclesBitmask = 0,
            fatigueProfile = FatigueProfile.Medium,
            muscleLengthBias = MuscleLengthBias.MidRange,
        )
}
