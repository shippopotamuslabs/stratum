package com.stratum.application.training

import com.stratum.domain.logging.LoggingPort
import com.stratum.application.program.ProgramRepository
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.schedule.CycleSlot
import com.stratum.domain.program.schedule.resolvedCycleSlots
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import com.stratum.domain.training.StartTrainingSessionRequest
import com.stratum.domain.training.StartTrainingSessionResult
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.program.Workout
import com.stratum.domain.training.isOrphanedAgainstActiveProgram

class StartTrainingSessionUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val programRepository: ProgramRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(request: StartTrainingSessionRequest): StartTrainingSessionResult {
        return try {
            val program =
                programRepository.getProgram()
                    ?: return StartTrainingSessionResult.NoActiveProgram

            val activeSession = trainingLogRepository.getActiveSession()
            if (activeSession != null) {
                // Template rewrite can leave an IN_PROGRESS row whose workout no longer exists.
                // Auto-abandon orphans so start is not permanently blocked after a mid-session delete.
                if (activeSession.isOrphanedAgainstActiveProgram(program)) {
                    trainingLogRepository.deleteSession(activeSession.id)
                } else {
                    return StartTrainingSessionResult.ActiveSessionExists(activeSession.id)
                }
            }

            val now = Instant.now()

            val session =
                when (request) {
                    is StartTrainingSessionRequest.ProgramWorkout -> {
                        val workout =
                            program.workouts.firstOrNull { it.id == request.workoutId }
                                ?: return StartTrainingSessionResult.WorkoutNotFound(request.workoutId)
                        val slotIndex =
                            program.resolvedCycleSlots()
                                .filterIsInstance<CycleSlot.Training>()
                                .firstOrNull { it.workout.id == request.workoutId }
                                ?.slotIndex
                        TrainingSession.started(
                            id = TrainingSessionId(UUID.randomUUID().toString()),
                            programId = program.id,
                            workoutId = request.workoutId,
                            kind = TrainingSessionKind.PROGRAM_WORKOUT,
                            cycleNumberSnapshot = program.currentCycleNumber,
                            weekdaySnapshot = workout.weekday,
                            cycleSlotIndexSnapshot = slotIndex,
                            startedAt = now,
                            programNameSnapshot = program.name,
                            workoutNameSnapshot = workout.name,
                        )
                    }
                    StartTrainingSessionRequest.AdHocEmpty ->
                        TrainingSession.started(
                            id = TrainingSessionId(UUID.randomUUID().toString()),
                            programId = program.id,
                            workoutId = null,
                            kind = TrainingSessionKind.AD_HOC,
                            cycleNumberSnapshot = program.currentCycleNumber,
                            weekdaySnapshot = null,
                            startedAt = now,
                            programNameSnapshot = program.name,
                            // Stable label for bar / history / notifications (no program workout row).
                            workoutNameSnapshot = AD_HOC_WORKOUT_NAME,
                        )
                }

            trainingLogRepository.insertSession(session)
            StartTrainingSessionResult.Success(session)
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to start training session: ${t.message}", t)
            StartTrainingSessionResult.Failure(t)
        }
    }

    private companion object {
        const val TAG = "StartTrainingSession"
        const val AD_HOC_WORKOUT_NAME = "Empty Workout"
    }
}
