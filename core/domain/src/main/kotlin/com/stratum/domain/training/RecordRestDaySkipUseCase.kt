package com.stratum.domain.training

import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.ProgramRepository
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class RecordRestDaySkipUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val programRepository: ProgramRepository,
) {
    suspend operator fun invoke(weekday: Weekday): Result<TrainingSession> = runCatching {
        val program =
            programRepository.getProgram()
                ?: error("No active program found to record rest day skip")
        val now = Instant.now()

        val session =
            TrainingSession(
                id = TrainingSessionId(UUID.randomUUID().toString()),
                programId = program.id,
                workoutId = null,
                status = TrainingSessionStatus.COMPLETED,
                kind = TrainingSessionKind.REST_DAY,
                origin = TrainingSessionOrigin.LIVE,
                cycleNumberSnapshot = program.currentCycleNumber,
                weekdaySnapshot = weekday,
                startedAt = now,
                endedAt = now,
                sourceApp = null,
            )

        trainingLogRepository.persistSessionWithCycleEffects(
            session = session,
            cyclePointerUpdate = null,
            cycleCompletedEvent = null,
        )

        session
    }
}
