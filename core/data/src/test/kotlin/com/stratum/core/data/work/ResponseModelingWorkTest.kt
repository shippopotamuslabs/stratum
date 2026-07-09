package com.stratum.core.data.work

import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.response.FailOnVolumeResponseProfilePersistencePort
import com.stratum.core.testing.response.FakeResponseProfileMetadataRepository
import com.stratum.core.testing.response.FakeResponseProfilePersistencePort
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.importing.ImportAnalysisMetadata
import com.stratum.domain.importing.ImportAnalysisMetadataRepository
import com.stratum.domain.importing.LiftTrainingInsightsRepository
import com.stratum.domain.importing.MuscleVolumeBaselineRepository
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.response.DefaultResponseObservationExtractor
import com.stratum.domain.response.EmptyExperimentRegistry
import com.stratum.domain.response.ExtractResponseProfileUseCase
import com.stratum.domain.response.RE_EXTRACT_MIN_COMPLETED_WORKOUTS
import com.stratum.domain.response.ResponseProfileMetadata
import com.stratum.domain.response.ResponseProfilePersistencePort
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ResponseModelingWork] (the extractable core of [ResponseModelingWorker]).
 * Does not spin up WorkManager — exercises the completed-workout gate and success/failure mapping.
 */
class ResponseModelingWorkTest {

    @Test
    @DisplayName("skips when completed workouts since last extract are below the threshold")
    fun skipsWhenFewerThanThresholdWorkouts() = runTest {
        val lastExtracted = Instant.parse("2025-05-01T00:00:00Z")
        val metadataRepo = FakeResponseProfileMetadataRepository()
        metadataRepo.save(metadata(lastExtracted = lastExtracted))
        val trainingLog = FakeTrainingLogRepository()
        // 5 new completed workouts — below RE_EXTRACT_MIN_COMPLETED_WORKOUTS (20).
        seedCompletedWorkouts(trainingLog, count = 5, after = lastExtracted)

        val persistence = FakeResponseProfilePersistencePort(metadataRepository = metadataRepo)
        val work = buildWork(persistence = persistence, trainingLog = trainingLog)

        val outcome = work.run()

        assertEquals(ResponseModelingWork.Outcome.Skipped, outcome)
        assertEquals(lastExtracted, metadataRepo.get()?.lastExtractedAt)
    }

    @Test
    @DisplayName("runs when completed workouts since last extract meet the threshold")
    fun runsWhenEnoughWorkouts() = runTest {
        val lastExtracted = Instant.parse("2025-05-01T00:00:00Z")
        val metadataRepo = FakeResponseProfileMetadataRepository()
        metadataRepo.save(metadata(lastExtracted = lastExtracted))
        val trainingLog = FakeTrainingLogRepository()
        seedCompletedWorkouts(trainingLog, count = RE_EXTRACT_MIN_COMPLETED_WORKOUTS, after = lastExtracted)

        val persistence = FakeResponseProfilePersistencePort(metadataRepository = metadataRepo)
        val work = buildWork(persistence = persistence, trainingLog = trainingLog)

        val outcome = work.run()

        assertEquals(ResponseModelingWork.Outcome.Success, outcome)
        assertTrue(metadataRepo.get()!!.lastExtractedAt.isAfter(lastExtracted))
        assertTrue(persistence.epochRepository.getAll().isNotEmpty())
    }

    @Test
    @DisplayName("idle calendar time alone does not trigger extract")
    fun monthOffDoesNotTrigger() = runTest {
        val lastExtracted = Instant.parse("2025-01-01T00:00:00Z")
        val metadataRepo = FakeResponseProfileMetadataRepository()
        metadataRepo.save(metadata(lastExtracted = lastExtracted))
        // No sessions after last extract — a multi-month layoff should stay quiet.
        val trainingLog = FakeTrainingLogRepository()
        trainingLog.seedSession(
            WorkoutLoggingFixtures.completedLiveSession(id = "before").copy(
                startedAt = lastExtracted.minus(7, ChronoUnit.DAYS),
                endedAt = lastExtracted.minus(7, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS),
                status = TrainingSessionStatus.COMPLETED,
            ),
        )

        val work = buildWork(
            persistence = FakeResponseProfilePersistencePort(metadataRepository = metadataRepo),
            trainingLog = trainingLog,
        )

        assertEquals(ResponseModelingWork.Outcome.Skipped, work.run())
    }

    @Test
    @DisplayName("rest days and in-progress sessions do not count toward the threshold")
    fun onlyCompletedWorkoutsCount() = runTest {
        val lastExtracted = Instant.parse("2025-05-01T00:00:00Z")
        val metadataRepo = FakeResponseProfileMetadataRepository()
        metadataRepo.save(metadata(lastExtracted = lastExtracted))
        val trainingLog = FakeTrainingLogRepository()
        // 19 real workouts + rest days + in-progress — still under threshold.
        seedCompletedWorkouts(trainingLog, count = RE_EXTRACT_MIN_COMPLETED_WORKOUTS - 1, after = lastExtracted)
        trainingLog.seedSession(
            WorkoutLoggingFixtures.completedLiveSession(id = "rest").copy(
                kind = TrainingSessionKind.REST_DAY,
                startedAt = lastExtracted.plus(1, ChronoUnit.DAYS),
                endedAt = lastExtracted.plus(1, ChronoUnit.DAYS),
                status = TrainingSessionStatus.COMPLETED,
            ),
        )
        trainingLog.seedSession(
            WorkoutLoggingFixtures.inProgressSession(id = "wip").copy(
                startedAt = lastExtracted.plus(2, ChronoUnit.DAYS),
            ),
        )

        val work = buildWork(
            persistence = FakeResponseProfilePersistencePort(metadataRepository = metadataRepo),
            trainingLog = trainingLog,
        )

        assertEquals(ResponseModelingWork.Outcome.Skipped, work.run())
        assertEquals(
            RE_EXTRACT_MIN_COMPLETED_WORKOUTS - 1,
            ResponseModelingWork.countCompletedWorkoutsSince(
                trainingLog.getAllSessions(),
                lastExtracted,
            ),
        )
    }

    @Test
    @DisplayName("runs when never extracted (null metadata)")
    fun runsWhenNoMetadata() = runTest {
        val persistence = FakeResponseProfilePersistencePort()
        val work = buildWork(persistence = persistence, trainingLog = FakeTrainingLogRepository())

        val outcome = work.run()

        assertEquals(ResponseModelingWork.Outcome.Success, outcome)
        assertTrue(persistence.metadataRepository.get() != null)
    }

    @Test
    @DisplayName("maps extract failure to Failed so the worker can Result.retry()")
    fun mapsFailure() = runTest {
        val lastExtracted = Instant.parse("2025-05-01T00:00:00Z")
        val metadataRepo = FakeResponseProfileMetadataRepository()
        metadataRepo.save(metadata(lastExtracted = lastExtracted))
        val trainingLog = FakeTrainingLogRepository()
        seedCompletedWorkouts(trainingLog, count = RE_EXTRACT_MIN_COMPLETED_WORKOUTS, after = lastExtracted)
        val persistence = FailOnVolumeResponseProfilePersistencePort()
        // FailOnVolume has its own metadata repo — seed gate via training log only; metadata on
        // FailOnVolume is empty so gate would not skip. Use Fake that throws instead.
        val work = ResponseModelingWork(
            extractResponseProfile = ExtractResponseProfileUseCase(
                trainingLogRepository = trainingLog,
                liftRepository = FakeLiftRepository(),
                liftTrainingInsightsRepository = EmptyLiftInsights(),
                muscleVolumeBaselineRepository = EmptyVolumeBaselines(),
                importAnalysisMetadataRepository = FixedImportMetadata(null),
                responseProfilePersistence = FailOnVolumeResponseProfilePersistencePort(),
                extractor = DefaultResponseObservationExtractor(),
                experimentRegistry = EmptyExperimentRegistry(),
            ),
            metadataRepository = metadataRepo,
            trainingLogRepository = trainingLog,
        )

        val outcome = work.run()

        assertTrue(outcome is ResponseModelingWork.Outcome.Failed)
    }

    @Test
    @DisplayName("All-scope extract includes live sessions (not import-only)")
    fun allScopeIncludesLiveSessions() = runTest {
        val persistence = FakeResponseProfilePersistencePort()
        val trainingLog = FakeTrainingLogRepository()
        val importSession = WorkoutLoggingFixtures.completedLiveSession(id = "import-1").copy(
            origin = TrainingSessionOrigin.IMPORT,
            startedAt = Instant.parse("2025-01-01T00:00:00Z"),
            endedAt = Instant.parse("2025-01-01T01:00:00Z"),
        )
        val liveSession = WorkoutLoggingFixtures.completedLiveSession(id = "live-1").copy(
            origin = TrainingSessionOrigin.LIVE,
            startedAt = Instant.parse("2025-03-01T00:00:00Z"),
            endedAt = Instant.parse("2025-03-01T01:00:00Z"),
        )
        val importSet = WorkoutLoggingFixtures.loggedSet(id = "s-import", sessionId = importSession.id)
        val liveSet = WorkoutLoggingFixtures.loggedSet(id = "s-live", sessionId = liveSession.id)
        trainingLog.replaceAll(listOf(importSession, liveSession), listOf(importSet, liveSet))

        // Never extracted → gate open; All scope should span live window end.
        val work = buildWork(
            persistence = persistence,
            trainingLog = trainingLog,
            importMetadata = ImportAnalysisMetadata(
                windowStart = Instant.parse("2025-01-01T00:00:00Z"),
                windowEnd = Instant.parse("2025-01-15T00:00:00Z"),
                provenLiftIds = listOf(LiftId("lift-1")),
                unmatchedExerciseNames = emptyList(),
                totalSessions = 1,
                totalSets = 1,
            ),
        )

        val outcome = work.run()

        assertEquals(ResponseModelingWork.Outcome.Success, outcome)
        val meta = persistence.metadataRepository.get()!!
        assertTrue(
            !meta.analysisWindowEnd.isBefore(liveSession.startedAt),
            "All-scope window should include live session start",
        )
    }

    private fun metadata(lastExtracted: Instant): ResponseProfileMetadata =
        ResponseProfileMetadata(
            epochsAnalyzed = 1,
            analysisWindowStart = lastExtracted.minus(60, ChronoUnit.DAYS),
            analysisWindowEnd = lastExtracted,
            lastExtractedAt = lastExtracted,
        )

    private suspend fun seedCompletedWorkouts(
        trainingLog: FakeTrainingLogRepository,
        count: Int,
        after: Instant,
    ) {
        repeat(count) { i ->
            val start = after.plus((i + 1).toLong(), ChronoUnit.DAYS)
            trainingLog.seedSession(
                WorkoutLoggingFixtures.completedLiveSession(id = "w-$i").copy(
                    startedAt = start,
                    endedAt = start.plus(1, ChronoUnit.HOURS),
                    status = TrainingSessionStatus.COMPLETED,
                    kind = if (i % 2 == 0) TrainingSessionKind.PROGRAM_WORKOUT else TrainingSessionKind.AD_HOC,
                ),
            )
        }
    }

    private fun buildWork(
        persistence: ResponseProfilePersistencePort,
        trainingLog: FakeTrainingLogRepository,
        importMetadata: ImportAnalysisMetadata? = null,
    ): ResponseModelingWork {
        val extract = ExtractResponseProfileUseCase(
            trainingLogRepository = trainingLog,
            liftRepository = FakeLiftRepository(),
            liftTrainingInsightsRepository = EmptyLiftInsights(),
            muscleVolumeBaselineRepository = EmptyVolumeBaselines(),
            importAnalysisMetadataRepository = FixedImportMetadata(importMetadata),
            responseProfilePersistence = persistence,
            extractor = DefaultResponseObservationExtractor(),
            experimentRegistry = EmptyExperimentRegistry(),
        )
        val metadataRepo = when (persistence) {
            is FakeResponseProfilePersistencePort -> persistence.metadataRepository
            is FailOnVolumeResponseProfilePersistencePort -> persistence.metadataRepository
            else -> FakeResponseProfileMetadataRepository()
        }
        return ResponseModelingWork(extract, metadataRepo, trainingLog)
    }

    private class EmptyLiftInsights : LiftTrainingInsightsRepository {
        override suspend fun replaceAll(insights: List<com.stratum.domain.importing.LiftTrainingInsights>) = Unit
        override suspend fun getAll(): List<com.stratum.domain.importing.LiftTrainingInsights> = emptyList()
        override suspend fun getByLiftId(liftId: LiftId) = null
    }

    private class EmptyVolumeBaselines : MuscleVolumeBaselineRepository {
        override suspend fun replaceAll(baselines: List<com.stratum.domain.importing.MuscleVolumeBaseline>) = Unit
        override suspend fun getAll(): List<com.stratum.domain.importing.MuscleVolumeBaseline> = emptyList()
    }

    private class FixedImportMetadata(
        private val metadata: ImportAnalysisMetadata?,
    ) : ImportAnalysisMetadataRepository {
        override suspend fun save(metadata: ImportAnalysisMetadata) = Unit
        override suspend fun get(): ImportAnalysisMetadata? = metadata
        override suspend fun clear() = Unit
    }
}
