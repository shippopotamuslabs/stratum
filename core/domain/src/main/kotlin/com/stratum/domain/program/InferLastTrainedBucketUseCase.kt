package com.stratum.domain.program

import com.stratum.domain.onboarding.LastTrainedBucket
import com.stratum.domain.training.TrainingLogRepository
import com.stratum.domain.training.TrainingSessionStatus
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Infers [LastTrainedBucket] from real session history so create-program generate flow can skip
 * the onboarding "when did you last train?" question.
 */
class InferLastTrainedBucketUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
) {
    suspend operator fun invoke(
        now: Instant = Instant.now(),
        fallback: LastTrainedBucket? = null,
    ): LastTrainedBucket {
        val latestEnded =
            trainingLogRepository.getAllSessions()
                .asSequence()
                .filter { session -> session.status == TrainingSessionStatus.COMPLETED }
                .mapNotNull { session -> session.endedAt ?: session.startedAt }
                .maxOrNull()
        if (latestEnded == null) {
            return fallback ?: LastTrainedBucket.Never
        }
        val daysSince = ChronoUnit.DAYS.between(latestEnded, now)
        return if (daysSince <= THREE_MONTHS_DAYS) {
            LastTrainedBucket.WithinLastThreeMonths
        } else {
            LastTrainedBucket.MoreThanThreeMonthsAgo
        }
    }

    private companion object {
        const val THREE_MONTHS_DAYS = 90L
    }
}
