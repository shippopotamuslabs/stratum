package com.stratum.domain.program

import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.domain.onboarding.LastTrainedBucket
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InferLastTrainedBucketUseCaseTest {
    @Test
    fun `no sessions returns Never or fallback`() = runTest {
        val useCase = InferLastTrainedBucketUseCase(FakeTrainingLogRepository())
        assertEquals(LastTrainedBucket.Never, useCase())
        assertEquals(
            LastTrainedBucket.WithinLastThreeMonths,
            useCase(fallback = LastTrainedBucket.WithinLastThreeMonths),
        )
    }

    @Test
    fun `recent completed session maps to WithinLastThreeMonths`() = runTest {
        val now = Instant.parse("2026-07-01T12:00:00Z")
        val repo = FakeTrainingLogRepository()
        repo.insertSession(
            session(
                id = "s1",
                startedAt = now.minus(10, ChronoUnit.DAYS),
                endedAt = now.minus(10, ChronoUnit.DAYS),
            ),
        )
        val useCase = InferLastTrainedBucketUseCase(repo)
        assertEquals(LastTrainedBucket.WithinLastThreeMonths, useCase(now = now))
    }

    @Test
    fun `old completed session maps to MoreThanThreeMonthsAgo`() = runTest {
        val now = Instant.parse("2026-07-01T12:00:00Z")
        val repo = FakeTrainingLogRepository()
        repo.insertSession(
            session(
                id = "s1",
                startedAt = now.minus(120, ChronoUnit.DAYS),
                endedAt = now.minus(120, ChronoUnit.DAYS),
            ),
        )
        val useCase = InferLastTrainedBucketUseCase(repo)
        assertEquals(LastTrainedBucket.MoreThanThreeMonthsAgo, useCase(now = now))
    }

    private fun session(
        id: String,
        startedAt: Instant,
        endedAt: Instant?,
    ): TrainingSession =
        TrainingSession(
            id = TrainingSessionId(id),
            programId = ProgramId("p1"),
            workoutId = null,
            status = TrainingSessionStatus.COMPLETED,
            kind = TrainingSessionKind.AD_HOC,
            origin = TrainingSessionOrigin.LIVE,
            cycleNumberSnapshot = 1,
            weekdaySnapshot = null,
            startedAt = startedAt,
            endedAt = endedAt,
            sourceApp = null,
        )
}
