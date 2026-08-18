package com.stratum.feature.history

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.healthconnect.FakeHealthConnectSchedulerPort
import com.stratum.core.testing.lift.FakeWeightDisplayPreferencesRepository
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.response.FakeResponseModelingSchedulerPort
import com.stratum.core.testing.response.FakeResponseProfileMetadataRepository
import com.stratum.core.testing.response.FakeResponseProfilePersistencePort
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.ObserveWeightDisplayPreferencesUseCase
import com.stratum.domain.lift.Weight
import com.stratum.domain.training.ComputeLoggedSessionMuscleVolumeUseCase
import com.stratum.domain.training.GetWorkoutSessionDetailUseCase
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.UpdateCompletedSessionTimesUseCase
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.feature.history.ui.WorkoutSessionDetailUiState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class WorkoutSessionDetailViewModelTest {

    @Test
    @DisplayName("Load maps domain detail to presentation labels")
    fun loadMapsDetail() = runTest {
        val (vm, _) = buildViewModel()

        vm.load("ts-1")
        advanceUntilIdle()

        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state is WorkoutSessionDetailUiState.Loaded)
            val loaded = state as WorkoutSessionDetailUiState.Loaded
            assertEquals("Upper A", loaded.workoutName)
            assertEquals("1", loaded.personalRecordsLabel)
            assertEquals(1, loaded.lifts.size)
            assertTrue(loaded.lifts.single().sets.single().isPersonalRecord)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("updateWorkoutTimes persists new timestamps and updates duration without flickering to Loading")
    fun updateWorkoutTimesUpdatesStateInPlace() = runTest {
        val (vm, logRepo) = buildViewModel()

        vm.load("ts-1")
        advanceUntilIdle()

        val initialLoaded = vm.uiState.value as WorkoutSessionDetailUiState.Loaded
        assertEquals("1:00:00", initialLoaded.durationFormatted)

        // Overnight workout: Jan 1 at 22:00 -> Jan 2 at 00:30 (2h 30m)
        val startDate = LocalDate.of(2025, 1, 1)
        val startTime = LocalTime.of(22, 0)
        val finishDate = LocalDate.of(2025, 1, 2)
        val finishTime = LocalTime.of(0, 30)
        val utcZone = ZoneOffset.UTC

        vm.updateWorkoutTimes(
            startDate = startDate,
            startTime = startTime,
            finishDate = finishDate,
            finishTime = finishTime,
            zoneId = utcZone,
        )
        advanceUntilIdle()

        // State remains Loaded throughout and reflects new duration and dates
        val updatedLoaded = vm.uiState.value as WorkoutSessionDetailUiState.Loaded
        assertEquals("2:30:00", updatedLoaded.durationFormatted)
        assertEquals(startDate, updatedLoaded.startDate)
        assertEquals(startTime, updatedLoaded.startTime)
        assertEquals(finishDate, updatedLoaded.finishDate)
        assertEquals(finishTime, updatedLoaded.finishTime)

        val expectedStartInstant = startDate.atTime(startTime).atZone(utcZone).toInstant()
        val expectedEndInstant = finishDate.atTime(finishTime).atZone(utcZone).toInstant()

        val sessionInDb = logRepo.getSession(TrainingSessionId("ts-1"))!!
        assertEquals(expectedStartInstant, sessionInDb.startedAt)
        assertEquals(expectedEndInstant, sessionInDb.endedAt)

        val setsInDb = logRepo.getSetsForSession(TrainingSessionId("ts-1"))
        assertTrue(setsInDb.all { !it.completedAt.isBefore(expectedStartInstant) && !it.completedAt.isAfter(expectedEndInstant) })
    }

    @Test
    @DisplayName("updateWorkoutTimes failure surfaces message and userMessageShown clears it")
    fun updateWorkoutTimesFailureSurfacesMessage() = runTest {
        val (vm, _) = buildViewModel()

        vm.load("ts-1")
        advanceUntilIdle()

        // Attempt invalid update: finish before start
        val startDate = LocalDate.of(2025, 1, 2)
        val startTime = LocalTime.of(12, 0)
        val finishDate = LocalDate.of(2025, 1, 1)
        val finishTime = LocalTime.of(12, 0)

        vm.updateWorkoutTimes(
            startDate = startDate,
            startTime = startTime,
            finishDate = finishDate,
            finishTime = finishTime,
            zoneId = ZoneOffset.UTC,
        )
        advanceUntilIdle()

        assertEquals("Start time (2025-01-02T12:00:00Z) cannot be after finish time (2025-01-01T12:00:00Z)", vm.userMessage.value)

        vm.userMessageShown()
        assertNull(vm.userMessage.value)
    }

    private fun buildViewModel(): Pair<WorkoutSessionDetailViewModel, FakeTrainingLogRepository> {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val session =
            WorkoutLoggingFixtures.completedLiveSession(id = "ts-1").copy(
                workoutNameSnapshot = "Upper A",
                startedAt = Instant.parse("2025-01-01T10:00:00Z"),
                endedAt = Instant.parse("2025-01-01T11:00:00Z"),
            )
        val initialSet =
            LoggedSet(
                id = LoggedSetId("set-1"),
                sessionId = TrainingSessionId("ts-1"),
                exerciseNameRaw = "Bench",
                liftId = WorkoutLoggingFixtures.liftBenchId,
                setIndex = 0,
                weight = Weight(135.0, Weight.WeightUnit.Lb),
                reps = 8,
                rir = 2,
                completedAt = Instant.parse("2025-01-01T10:10:00Z"),
                liftContext = WorkoutLoggingFixtures.importLiftContext(),
                isPersonalRecord = true,
            )
        val logRepo =
            FakeTrainingLogRepository(
                initialSessions = listOf(session),
                initialSets = listOf(initialSet),
                programRepository = programRepo,
            )
        val liftRepo = FakeLiftRepository(listOf(WorkoutLoggingFixtures.benchLift()))
        val metadataRepo = FakeResponseProfileMetadataRepository()
        val persistence = FakeResponseProfilePersistencePort(metadataRepository = metadataRepo)
        val updateTimes =
            UpdateCompletedSessionTimesUseCase(
                trainingLogRepository = logRepo,
                localDerivedStages =
                    com.stratum.core.testing.training.localDerivedTrainingStages(
                        trainingLog = logRepo,
                        liftRepository = liftRepo,
                        persistence = persistence,
                    ),
                responseProfileMetadataRepository = metadataRepo,
                responseModelingScheduler = FakeResponseModelingSchedulerPort(),
                healthConnectScheduler = FakeHealthConnectSchedulerPort(),
                loggingPort = FakeLoggingPort(),
                refreshScope = CoroutineScope(Dispatchers.Unconfined),
            )
        val vm =
            WorkoutSessionDetailViewModel(
                getWorkoutSessionDetail =
                    GetWorkoutSessionDetailUseCase(
                        trainingLogRepository = logRepo,
                        programRepository = programRepo,
                        computeLoggedSessionMuscleVolume = ComputeLoggedSessionMuscleVolumeUseCase(),
                        loggingPort = FakeLoggingPort(),
                    ),
                updateCompletedSessionTimes = updateTimes,
                observeWeightDisplayPreferences =
                    ObserveWeightDisplayPreferencesUseCase(FakeWeightDisplayPreferencesRepository()),
            )
        return Pair(vm, logRepo)
    }
}
