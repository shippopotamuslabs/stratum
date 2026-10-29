package com.stratum.application.training

import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Lift
import com.stratum.application.lift.LiftRepository
import com.stratum.domain.logging.LoggingPort
import com.stratum.application.program.ProgramRepository
import com.stratum.domain.program.ProgressionScheme
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import com.stratum.domain.training.LogSetCommand
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.training.LoggedLiftContextFactory
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.SetEquipmentSource

class LogSetUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val liftRepository: LiftRepository,
    private val programRepository: ProgramRepository,
    private val loggedLiftContextFactory: LoggedLiftContextFactory,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(command: LogSetCommand): Result<LoggedSet> = runCatching {
        trainingLogRepository.requireInProgressSession(command.sessionId)

        val lift =
            liftRepository.observeLifts().first().firstOrNull { it.id == command.liftId }
                ?: throw IllegalArgumentException("Lift ${command.liftId.value} not found")

        val liftContext = resolveLiftContext(command, lift)

        val loggedSet =
            LoggedSet(
                id = LoggedSetId(UUID.randomUUID().toString()),
                sessionId = command.sessionId,
                exerciseNameRaw = lift.name,
                liftId = command.liftId,
                equipmentId = when (val source = command.equipment) {
                    SetEquipmentSource.FromLift -> lift.equipmentId
                    is SetEquipmentSource.Explicit -> source.equipmentId
                },
                setIndex = command.setIndex,
                weight = command.weight,
                reps = command.reps,
                rir = command.rir,
                // Raw value in; LoggedSet owns the normalization rule (STR-168).
                partialReps = command.partialReps,
                completedAt = command.completedAt,
                liftContext = liftContext,
            )

        trainingLogRepository.insertSet(loggedSet)
        loggedSet
    }.onFailure { error ->
        loggingPort.error(TAG, "Failed to log set: ${error.message}", error)
    }

    /**
     * Template lifts resolve progression/set fields from the program.
     * Mid-session adds and ad-hoc empty sessions use ephemeral workout-lift ids that are
     * not on the template until the user opts to keep changes at finalize — fall back to a
     * session-only context that still stamps [LogSetCommand.workoutLiftId] for amend/unlog
     * matching.
     */
    private suspend fun resolveLiftContext(command: LogSetCommand, lift: Lift): LoggedLiftContext {
        val workoutLiftId = command.workoutLiftId
            ?: return loggedLiftContextFactory.fromLiftCatalog(lift, lift.name)

        val program = programRepository.getProgram()
        val workoutLift = program
            ?.workouts
            ?.flatMap { it.workoutLifts }
            ?.firstOrNull { it.id == workoutLiftId }

        return if (workoutLift != null) {
            val templateSet = workoutLift.sets.firstOrNull { it.position == command.setIndex }
            val base = loggedLiftContextFactory.fromLiveTemplate(lift, workoutLift, templateSet)
            // Prefer live slot shape when the session path stamped it. Template lookup by
            // setIndex is wrong after renumber (minis between match backoffs) and would
            // mis-label auto minis as the trailing template row — which then pollutes
            // fatigue slope + post-workout rep-range gates.
            if (command.setType != null) {
                base.copy(
                    setType = command.setType,
                    repMin = command.repMin,
                    repMax = command.repMax,
                    rirGoal = command.rirGoal,
                )
            } else {
                base
            }
        } else {
            // Overlay/ad-hoc lift: default Double matches LiveWorkoutOverlayOps.addLifts.
            loggedLiftContextFactory.fromLiveSessionLift(
                lift = lift,
                workoutLiftId = workoutLiftId,
                progressionScheme = ProgressionScheme.Double,
                setType = command.setType,
                repMin = command.repMin,
                repMax = command.repMax,
                rirGoal = command.rirGoal,
            )
        }
    }

    private companion object {
        const val TAG = "LogSetUseCase"
    }
}
