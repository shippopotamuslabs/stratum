package com.stratum.domain.training

import com.stratum.domain.program.ProgramRepository
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import javax.inject.Inject

class FinalizeTrainingSessionUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val programRepository: ProgramRepository,
) {
    suspend operator fun invoke(sessionId: TrainingSessionId): Result<TrainingSession> = runCatching {
        val session =
            trainingLogRepository.getSession(sessionId)
                ?: throw IllegalArgumentException("Training session ${sessionId.value} not found")
        if (session.status != TrainingSessionStatus.IN_PROGRESS) {
            throw TrainingSessionNotInProgressException(sessionId, session.status)
        }

        val completed =
            session.copy(
                status = TrainingSessionStatus.COMPLETED,
                endedAt = Instant.now(),
            )

        val cycleEffects =
            if (completed.kind == TrainingSessionKind.PROGRAM_WORKOUT) {
                val program =
                    programRepository.getProgram()
                        ?: error("No active program found to advance cycle after workout completion")
                val sessions =
                    trainingLogRepository.getAllSessions()
                        .filter { it.id != session.id } + completed
                computeCycleAdvancement(program, sessions)
            } else {
                null
            }

        trainingLogRepository.persistSessionWithCycleEffects(
            session = completed,
            cyclePointerUpdate = cycleEffects?.pointerUpdate,
            cycleCompletedEvent = cycleEffects?.cycleCompletedEvent,
        )

        completed
    }
}
