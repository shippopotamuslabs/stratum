package com.stratum.domain.importing

import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.domain.importing.identity.HistoricalSessionId
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExtractTrainingInsightsUseCaseTest {
    @Test
    fun `persists insights when history exists`() = runTest {
        val squatId = LiftId("hack-squat")
        val curlId = LiftId("db-curl")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(
            listOf(
                lift(squatId, "Hack Squat", MovementPattern.SquatPattern, MuscleGroup.Quad),
                lift(curlId, "DB Curl", MovementPattern.HorizontalPull, MuscleGroup.Bicep),
            ),
        )
        val t0 = Instant.parse("2025-01-01T10:00:00Z")
        val sessions = (1..5).map { index ->
            HistoricalSession(
                id = HistoricalSessionId("session-$index"),
                startedAt = t0.plusSeconds(index * 86_400L),
                endedAt = null,
                sourceApp = "test",
            )
        }
        val sets = buildList {
            sessions.forEachIndexed { sessionIndex, session ->
                repeat(3) { setIndex ->
                    add(
                        LoggedSet(
                            id = LoggedSetId("${session.id.value}-squat-$setIndex"),
                            sessionId = session.id,
                            exerciseNameRaw = "Hack Squat",
                            liftId = squatId,
                            setIndex = setIndex + 1,
                            weight = Weight(100.0 + sessionIndex * 5, Weight.WeightUnit.Lb),
                            reps = 8,
                            rir = 2,
                            completedAt = session.startedAt.plusSeconds((setIndex * 120).toLong()),
                        ),
                    )
                }
            }
        }
        val historyRepo = InMemoryTrainingHistoryRepository(sessions, sets)
        val insightsRepo = InMemoryLiftInsightsRepository()
        val useCase = ExtractTrainingInsightsUseCase(
            trainingHistoryRepository = historyRepo,
            liftTrainingInsightsRepository = insightsRepo,
            supersetAffinityRepository = InMemorySupersetRepository(),
            muscleVolumeBaselineRepository = InMemoryVolumeRepository(),
            importAnalysisMetadataRepository = InMemoryMetadataRepository(),
            liftRepository = liftRepo,
        )

        val summary = useCase().getOrThrow()

        assertTrue(summary.liftsWithInsights > 0)
        assertTrue(insightsRepo.stored.isNotEmpty())
    }

    private fun lift(
        id: LiftId,
        name: String,
        pattern: MovementPattern,
        muscle: MuscleGroup,
    ): Lift = Lift(
        id = id,
        name = name,
        movementPattern = pattern,
        primaryMuscles = setOf(muscle),
        secondaryMuscles = emptySet(),
        incrementTiers = emptyList(),
        presetTags = emptySet(),
        allowWeightChangeBetweenSets = false,
        enabled = true,
    )

    private class InMemoryTrainingHistoryRepository(
        private val sessions: List<HistoricalSession>,
        private val sets: List<LoggedSet>,
    ) : TrainingHistoryRepository {
        override suspend fun replaceAll(sessions: List<HistoricalSession>, sets: List<LoggedSet>) = Unit
        override suspend fun getAllSessions(): List<HistoricalSession> = sessions
        override suspend fun getAllSets(): List<LoggedSet> = sets
        override suspend fun getSetsInWindow(windowStart: Instant, windowEnd: Instant): List<LoggedSet> =
            sets.filter { it.completedAt in windowStart..windowEnd }
    }

    private class InMemoryLiftInsightsRepository : LiftTrainingInsightsRepository {
        val stored = mutableListOf<LiftTrainingInsights>()
        override suspend fun replaceAll(insights: List<LiftTrainingInsights>) {
            stored.clear()
            stored.addAll(insights)
        }
        override suspend fun getAll(): List<LiftTrainingInsights> = stored
        override suspend fun getByLiftId(liftId: LiftId): LiftTrainingInsights? =
            stored.firstOrNull { it.liftId == liftId }
    }

    private class InMemorySupersetRepository : SupersetAffinityRepository {
        override suspend fun replaceAll(affinities: List<SupersetAffinity>) = Unit
        override suspend fun getAll(): List<SupersetAffinity> = emptyList()
    }

    private class InMemoryVolumeRepository : MuscleVolumeBaselineRepository {
        override suspend fun replaceAll(baselines: List<MuscleVolumeBaseline>) = Unit
        override suspend fun getAll(): List<MuscleVolumeBaseline> = emptyList()
    }

    private class InMemoryMetadataRepository : ImportAnalysisMetadataRepository {
        override suspend fun save(metadata: ImportAnalysisMetadata) = Unit

        override suspend fun get(): ImportAnalysisMetadata? = null

        override suspend fun clear() = Unit
    }
}
