package com.stratum.application.importing

import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.core.testing.training.withStart
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionOrigin
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.stratum.domain.dashboard.windowStart
import com.stratum.domain.importing.ImportAnalysisMetadata
import com.stratum.domain.importing.ImportHistoryTrainingMapper
import com.stratum.domain.importing.LiftTrainingInsights
import com.stratum.domain.importing.MuscleVolumeBaseline
import com.stratum.domain.importing.SupersetAffinity

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
            WorkoutLoggingFixtures.importTrainingSession(
                id = "session-$index",
                startedAt = t0.plusSeconds(index * 86_400L),
                endedAt = null,
            )
        }
        val sets = buildList {
            sessions.forEachIndexed { sessionIndex, session ->
                repeat(3) { setIndex ->
                    add(
                        importSet(
                            id = "${session.id.value}-squat-$setIndex",
                            session = session,
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
        val trainingLogRepo = FakeTrainingLogRepository()
        trainingLogRepo.replaceImportHistory(sessions, sets)
        val insightsRepo = InMemoryLiftInsightsRepository()
        val useCase = ExtractTrainingInsightsUseCase(
            trainingLogRepository = trainingLogRepo,
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

    @Test
    fun `scope All analyzes live sessions while ImportOnly ignores them`() = runTest {
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
        // Import history: 5 sessions of Hack Squat.
        val importSessions = (1..5).map { index ->
            WorkoutLoggingFixtures.importTrainingSession(
                id = "import-$index",
                startedAt = t0.plusSeconds(index * 86_400L),
                endedAt = null,
            )
        }
        val importSets = buildList {
            importSessions.forEachIndexed { sessionIndex, session ->
                repeat(3) { setIndex ->
                    add(
                        importSet(
                            id = "${session.id.value}-squat-$setIndex",
                            session = session,
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
        // Live history: 5 sessions of DB Curl (a different lift, so we can tell them apart).
        val liveSessions = (1..5).map { index ->
            WorkoutLoggingFixtures.completedLiveSession(id = "live-$index")
                .withStart(t0.plusSeconds((index + 10) * 86_400L))
        }
        val liveSets = buildList {
            liveSessions.forEachIndexed { sessionIndex, session ->
                repeat(3) { setIndex ->
                    add(
                        importSet(
                            id = "${session.id.value}-curl-$setIndex",
                            session = session,
                            exerciseNameRaw = "DB Curl",
                            liftId = curlId,
                            setIndex = setIndex + 1,
                            weight = Weight(40.0 + sessionIndex * 2, Weight.WeightUnit.Lb),
                            reps = 10,
                            rir = 2,
                            completedAt = session.startedAt.plusSeconds((setIndex * 120).toLong()),
                        ),
                    )
                }
            }
        }
        val trainingLogRepo = FakeTrainingLogRepository()
        trainingLogRepo.replaceAll(importSessions + liveSessions, importSets + liveSets)

        val liftInsightsRepo = InMemoryLiftInsightsRepository()
        val metadataRepo = InMemoryMetadataRepository()
        val useCase = ExtractTrainingInsightsUseCase(
            trainingLogRepository = trainingLogRepo,
            liftTrainingInsightsRepository = liftInsightsRepo,
            supersetAffinityRepository = InMemorySupersetRepository(),
            muscleVolumeBaselineRepository = InMemoryVolumeRepository(),
            importAnalysisMetadataRepository = metadataRepo,
            liftRepository = liftRepo,
        )

        // ImportOnly sees only Hack Squat.
        useCase().getOrThrow()
        val importOnlyLifts = liftInsightsRepo.stored.map { it.liftId }.toSet()
        assertEquals(setOf(squatId), importOnlyLifts, "ImportOnly should analyze only imported lifts")

        // All sees both Hack Squat and DB Curl.
        useCase(scope = InsightsAnalysisScope.All).getOrThrow()
        val allLifts = liftInsightsRepo.stored.map { it.liftId }.toSet()
        assertEquals(setOf(squatId, curlId), allLifts, "All scope should analyze imported + live lifts")
    }

    @Test
    fun `scope All carries forward provenLiftIds from existing metadata`() = runTest {
        val squatId = LiftId("hack-squat")
        val provenId = LiftId("proven-bench")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(
            listOf(
                lift(squatId, "Hack Squat", MovementPattern.SquatPattern, MuscleGroup.Quad),
                lift(provenId, "Bench Press", MovementPattern.HorizontalPress, MuscleGroup.Chest),
            ),
        )
        val t0 = Instant.parse("2025-01-01T10:00:00Z")
        val sessions = (1..5).map { index ->
            WorkoutLoggingFixtures.importTrainingSession(
                id = "session-$index",
                startedAt = t0.plusSeconds(index * 86_400L),
                endedAt = null,
            )
        }
        val sets = buildList {
            sessions.forEachIndexed { sessionIndex, session ->
                repeat(3) { setIndex ->
                    add(
                        importSet(
                            id = "${session.id.value}-squat-$setIndex",
                            session = session,
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
        val trainingLogRepo = FakeTrainingLogRepository()
        trainingLogRepo.replaceImportHistory(sessions, sets)
        val metadataRepo = InMemoryMetadataRepository().apply {
            stored = ImportAnalysisMetadata(
                windowStart = t0,
                windowEnd = t0.plusSeconds(5 * 86_400L),
                provenLiftIds = listOf(provenId),
                unmatchedExerciseNames = emptyList(),
                totalSessions = 5,
                totalSets = 15,
            )
        }
        val useCase = ExtractTrainingInsightsUseCase(
            trainingLogRepository = trainingLogRepo,
            liftTrainingInsightsRepository = InMemoryLiftInsightsRepository(),
            supersetAffinityRepository = InMemorySupersetRepository(),
            muscleVolumeBaselineRepository = InMemoryVolumeRepository(),
            importAnalysisMetadataRepository = metadataRepo,
            liftRepository = liftRepo,
        )

        useCase(scope = InsightsAnalysisScope.All).getOrThrow()

        assertEquals(
            listOf(provenId),
            metadataRepo.stored?.provenLiftIds,
            "All-scope refresh must preserve previously proven lift ids",
        )
    }

    private fun importSet(
        id: String,
        session: TrainingSession,
        exerciseNameRaw: String,
        liftId: LiftId,
        setIndex: Int,
        weight: Weight,
        reps: Int,
        rir: Int?,
        completedAt: Instant,
    ): LoggedSet =
        ImportHistoryTrainingMapper.parseSet(
            id = LoggedSetId(id),
            sessionId = session.id,
            exerciseNameRaw = exerciseNameRaw,
            setIndex = setIndex,
            weight = weight,
            reps = reps,
            rir = rir,
            completedAt = completedAt,
        ).copy(liftId = liftId)

    private fun lift(
        id: LiftId,
        name: String,
        pattern: MovementPattern,
        muscle: MuscleGroup,
    ): Lift = Lift(
        id = id,
        name = name,
        movementPatterns = setOf(pattern),
        primaryMuscles = setOf(muscle),
        secondaryMuscles = emptySet(),
        incrementTiers = emptyList(),
        presetTags = emptySet(),
        enabled = true,
    )

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
        var stored: ImportAnalysisMetadata? = null
        override suspend fun save(metadata: ImportAnalysisMetadata) { stored = metadata }

        override suspend fun get(): ImportAnalysisMetadata? = stored

        override suspend fun clear() { stored = null }
    }
}
