package com.stratum.domain.training

import com.stratum.domain.program.ProgramRepository
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class StartTrainingSessionUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val programRepository: ProgramRepository,
) {
    suspend operator fun invoke(request: StartTrainingSessionRequest): Result<TrainingSession> = runCatching {
        val activeSession = trainingLogRepository.getActiveSession()
        if (activeSession != null) {
            throw ActiveTrainingSessionExistsException(activeSession.id)
        }

        val program =
            programRepository.getProgram()
                ?: error("No active program found to start training session")
        val now = Instant.now()

        val session =
            when (request) {
                is StartTrainingSessionRequest.ProgramWorkout -> {
                    val workout =
                        program.workouts.firstOrNull { it.id == request.workoutId }
                            ?: throw IllegalArgumentException("Workout ${request.workoutId.value} not found in program")
                    TrainingSession(
                        id = TrainingSessionId(UUID.randomUUID().toString()),
                        programId = program.id,
                        workoutId = request.workoutId,
                        status = TrainingSessionStatus.IN_PROGRESS,
                        kind = TrainingSessionKind.PROGRAM_WORKOUT,
                        origin = TrainingSessionOrigin.LIVE,
                        cycleNumberSnapshot = program.currentCycleNumber,
                        weekdaySnapshot = workout.weekday,
                        startedAt = now,
                        endedAt = null,
                        sourceApp = null,
                    )
                }
                StartTrainingSessionRequest.AdHocEmpty ->
                    TrainingSession(
                        id = TrainingSessionId(UUID.randomUUID().toString()),
                        programId = program.id,
                        workoutId = null,
                        status = TrainingSessionStatus.IN_PROGRESS,
                        kind = TrainingSessionKind.AD_HOC,
                        origin = TrainingSessionOrigin.LIVE,
                        cycleNumberSnapshot = program.currentCycleNumber,
                        weekdaySnapshot = null,
                        startedAt = now,
                        endedAt = null,
                        sourceApp = null,
                    )
            }

        trainingLogRepository.insertSession(session)
        session
    }
}
