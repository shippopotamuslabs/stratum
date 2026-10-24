package com.stratum.domain.training

import com.stratum.domain.program.ProgramRepository
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.schedule.resolvedCycleSlots
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class RecordWorkoutSkipUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val programRepository: ProgramRepository,
) {
    suspend operator fun invoke(workoutId: WorkoutId): Result<TrainingSession> {
        val program =
            programRepository.getProgram()
                ?: return Result.failure(IllegalStateException("No active program found to record workout skip"))
        val workout =
            program.workouts.firstOrNull { it.id == workoutId }
                ?: return Result.failure(IllegalArgumentException("Workout ${workoutId.value} not found in program"))
        val now = Instant.now()

        val slotIndex =
            program.resolvedCycleSlots()
                .filterIsInstance<com.stratum.domain.program.schedule.CycleSlot.Training>()
                .firstOrNull { it.workout.id == workoutId }
                ?.slotIndex
        val session =
            TrainingSession.skipped(
                id = TrainingSessionId(UUID.randomUUID().toString()),
                programId = program.id,
                workoutId = workoutId,
                kind = TrainingSessionKind.PROGRAM_WORKOUT,
                cycleNumberSnapshot = program.currentCycleNumber,
                weekdaySnapshot = workout.weekday,
                cycleSlotIndexSnapshot = slotIndex,
                at = now,
                programNameSnapshot = program.name,
                workoutNameSnapshot = workout.name,
            )

        return runCatching {
            val baseSessions = trainingLogRepository.getAllSessions() + session
            val (ledger, autoRests) =
                sessionsWithMaterializedAutoRests(
                    program = program,
                    sessions = baseSessions,
                    now = now,
                )
            val effects = computeCycleAdvancement(program = program, sessions = ledger)

            trainingLogRepository.persistSessionsWithCycleEffects(
                sessions = listOf(session) + autoRests,
                cyclePointerUpdate = effects.pointerUpdate,
                cycleCompletedEvent = effects.cycleCompletedEvent,
            )

            session
        }
    }
}
