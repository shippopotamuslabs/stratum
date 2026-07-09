package com.stratum.domain.training

import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.LiftRepository
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.program.ProgramRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class LogSetUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val liftRepository: LiftRepository,
    private val programRepository: ProgramRepository,
    private val loggedLiftContextFactory: LoggedLiftContextFactory,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(command: LogSetCommand): Result<LoggedSet> = runCatching {
        val session =
            trainingLogRepository.getSession(command.sessionId)
                ?: throw IllegalArgumentException("Training session ${command.sessionId.value} not found")
        if (session.status != TrainingSessionStatus.IN_PROGRESS) {
            throw TrainingSessionNotInProgressException(command.sessionId, session.status)
        }

        val lift =
            liftRepository.observeLifts().first().firstOrNull { it.id == command.liftId }
                ?: throw IllegalArgumentException("Lift ${command.liftId.value} not found")

        val liftContext =
            if (command.workoutLiftId != null) {
                val program =
                    programRepository.getProgram()
                        ?: error("No active program found to resolve workout lift")
                val workoutLift =
                    program.workouts
                        .flatMap { it.workoutLifts }
                        .firstOrNull { it.id == command.workoutLiftId }
                        ?: throw IllegalArgumentException("Workout lift ${command.workoutLiftId.value} not found")
                val templateSet = workoutLift.sets.firstOrNull { it.position == command.setIndex }
                loggedLiftContextFactory.fromLiveTemplate(lift, workoutLift, templateSet)
            } else {
                loggedLiftContextFactory.fromLiftCatalog(lift, lift.name)
            }

        val loggedSet =
            LoggedSet(
                id = LoggedSetId(UUID.randomUUID().toString()),
                sessionId = command.sessionId,
                exerciseNameRaw = lift.name,
                liftId = command.liftId,
                setIndex = command.setIndex,
                weight = command.weight,
                reps = command.reps,
                rir = command.rir,
                completedAt = command.completedAt,
                liftContext = liftContext,
            )

        trainingLogRepository.insertSet(loggedSet)
        loggedSet
    }.onFailure { error ->
        loggingPort.error(TAG, "Failed to log set: ${error.message}", error)
    }

    private companion object {
        const val TAG = "LogSetUseCase"
    }
}
