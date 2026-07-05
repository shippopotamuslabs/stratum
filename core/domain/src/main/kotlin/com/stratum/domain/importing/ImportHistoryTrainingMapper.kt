package com.stratum.domain.importing

import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.training.LoggedLiftContextFactory
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId

object ImportHistoryTrainingMapper {
    val programId: ProgramId = ProgramId("import-history")

    fun toTrainingSession(session: HistoricalSession): com.stratum.domain.training.TrainingSession =
        com.stratum.domain.training.TrainingSession(
            id = TrainingSessionId(session.id.value),
            programId = programId,
            workoutId = null,
            status = TrainingSessionStatus.COMPLETED,
            kind = TrainingSessionKind.AD_HOC,
            origin = TrainingSessionOrigin.IMPORT,
            cycleNumberSnapshot = 1,
            weekdaySnapshot = null,
            startedAt = session.startedAt,
            endedAt = session.endedAt,
            sourceApp = session.sourceApp,
        )

    fun toTrainingLoggedSet(
        set: LoggedSet,
        lifts: Map<LiftId, Lift>,
        factory: LoggedLiftContextFactory,
    ): com.stratum.domain.training.LoggedSet {
        val lift = set.liftId?.let { lifts[it] }
        val liftContext =
            if (lift != null) {
                factory.fromLiftCatalog(lift, set.exerciseNameRaw)
            } else {
                LoggedLiftContext(
                    liftName = set.exerciseNameRaw,
                    movementPatternsBitmask = set.movementPatternsSnapshot ?: 0,
                    primaryMusclesBitmask = set.primaryMusclesSnapshot ?: 0,
                    secondaryMusclesBitmask = set.secondaryMusclesSnapshot ?: 0,
                    kinematicProfile = KinematicProfile.NonAxialCompound,
                    muscleLengthBias = MuscleLengthBias.MidRange,
                )
            }
        return com.stratum.domain.training.LoggedSet(
            id = set.id,
            sessionId = TrainingSessionId(set.sessionId.value),
            exerciseNameRaw = set.exerciseNameRaw,
            liftId = set.liftId,
            setIndex = set.setIndex,
            weight = set.weight,
            reps = set.reps,
            rir = set.rir,
            completedAt = set.completedAt,
            liftContext = liftContext,
        )
    }
}
