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
        // Imported sessions are produced through the model factory (STR-166) so the
        // imported shape — COMPLETED, end time possibly unknown — is owned by the model.
        // No per-day workout title in the import schema; calendar shows DEFAULT ("Workout")
        // via TrainingSessionDisplayName — never a lift name.
        TrainingSession.imported(
            id = TrainingSessionId(id),
            programId = programId,
            startedAt = startedAt,
            endedAt = endedAt,
            sourceApp = sourceApp,
            programNameSnapshot = "Import History",
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
            // Raw value in; LoggedSet owns the normalization rule (STR-168).
            partialReps = partialReps,
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
