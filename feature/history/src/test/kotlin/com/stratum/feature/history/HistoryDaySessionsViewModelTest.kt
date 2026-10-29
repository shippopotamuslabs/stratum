package com.stratum.feature.history

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Weight
import com.stratum.application.training.ComputeLoggedSessionMuscleVolumeUseCase
import com.stratum.application.training.GetWorkoutSessionDetailUseCase
import com.stratum.domain.training.LoggedSet
import com.stratum.application.training.ObserveWorkoutHistoryCalendarUseCase
import com.stratum.application.training.TrainingLogRepository
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.feature.history.ui.HistoryDaySessionsUiState
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class HistoryDaySessionsViewModelTest {

    @Test
    @DisplayName("Load maps multiple sessions for a local day newest-first")
    fun loadMapsMultipleSessions() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val morning =
            WorkoutLoggingFixtures.completedLiveSession(id = "ts-morning").copy(
                workoutNameSnapshot = "Upper A",
                startedAt = Instant.parse("2025-11-02T10:00:00Z"),
                endedAt = Instant.parse("2025-11-02T11:00:00Z"),
            )
        val evening =
            WorkoutLoggingFixtures.completedLiveSession(id = "ts-evening").copy(
                workoutNameSnapshot = "Lower B",
                startedAt = Instant.parse("2025-11-02T18:00:00Z"),
                endedAt = Instant.parse("2025-11-02T19:00:00Z"),
            )
        val logRepo =
            FakeTrainingLogRepository(
                initialSessions = listOf(morning, evening),
                programRepository = programRepo,
            )
        seedSet(logRepo, "ts-morning", "set-m")
        seedSet(logRepo, "ts-evening", "set-e")

        val vm = buildViewModel(logRepo, programRepo)

        // UTC so 2025-11-02T* maps to that local calendar day.
        vm.load(dateIso = "2025-11-02", zoneId = ZoneOffset.UTC)
        advanceUntilIdle()

        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state is HistoryDaySessionsUiState.Loaded)
            val loaded = state as HistoryDaySessionsUiState.Loaded
            assertEquals(listOf("ts-evening", "ts-morning"), loaded.sessions.map { it.sessionId })
            assertEquals("Lower B", loaded.sessions[0].workoutName)
            assertEquals("Upper A", loaded.sessions[1].workoutName)
            assertTrue(loaded.dateTitle.isNotBlank())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Empty day yields error state")
    fun emptyDayError() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val logRepo =
            FakeTrainingLogRepository(
                initialSessions = emptyList(),
                programRepository = programRepo,
            )
        val vm = buildViewModel(logRepo, programRepo)

        vm.load(dateIso = "2025-11-02", zoneId = ZoneOffset.UTC)
        advanceUntilIdle()

        vm.uiState.test {
            assertEquals(HistoryDaySessionsUiState.Error, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Invalid date ISO yields error state")
    fun invalidDateError() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val logRepo =
            FakeTrainingLogRepository(
                initialSessions = emptyList(),
                programRepository = programRepo,
            )
        val vm = buildViewModel(logRepo, programRepo)

        vm.load(dateIso = "not-a-date")
        advanceUntilIdle()

        vm.uiState.test {
            assertEquals(HistoryDaySessionsUiState.Error, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Partial detail failure fails closed to Error")
    fun partialFailureFailsClosed() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val good =
            WorkoutLoggingFixtures.completedLiveSession(id = "ts-good").copy(
                workoutNameSnapshot = "Upper A",
                startedAt = Instant.parse("2025-11-02T10:00:00Z"),
                endedAt = Instant.parse("2025-11-02T11:00:00Z"),
            )
        val bad =
            WorkoutLoggingFixtures.completedLiveSession(id = "ts-bad").copy(
                workoutNameSnapshot = "Lower B",
                startedAt = Instant.parse("2025-11-02T18:00:00Z"),
                endedAt = Instant.parse("2025-11-02T19:00:00Z"),
            )
        val base =
            FakeTrainingLogRepository(
                initialSessions = listOf(good, bad),
                programRepository = programRepo,
            )
        seedSet(base, "ts-good", "set-g")
        seedSet(base, "ts-bad", "set-b")
        // Calendar still sees both (sets exist); getSession hides ts-bad so detail fails for one id.
        val logRepo =
            object : TrainingLogRepository by base {
                override suspend fun getSession(id: TrainingSessionId) =
                    if (id.value == "ts-bad") null else base.getSession(id)
            }

        val vm = buildViewModel(logRepo, programRepo)
        vm.load(dateIso = "2025-11-02", zoneId = ZoneOffset.UTC)
        advanceUntilIdle()

        vm.uiState.test {
            assertEquals(HistoryDaySessionsUiState.Error, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun buildViewModel(
        logRepo: TrainingLogRepository,
        programRepo: FakeProgramRepository,
    ) = HistoryDaySessionsViewModel(
        observeWorkoutHistoryCalendar = ObserveWorkoutHistoryCalendarUseCase(logRepo),
        getWorkoutSessionDetail =
            GetWorkoutSessionDetailUseCase(
                trainingLogRepository = logRepo,
                programRepository = programRepo,
                computeLoggedSessionMuscleVolume = ComputeLoggedSessionMuscleVolumeUseCase(),
                loggingPort = FakeLoggingPort(),
            ),
    )

    private suspend fun seedSet(
        logRepo: FakeTrainingLogRepository,
        sessionId: String,
        setId: String,
    ) {
        logRepo.insertSet(
            LoggedSet(
                id = LoggedSetId(setId),
                sessionId = TrainingSessionId(sessionId),
                exerciseNameRaw = "Bench",
                liftId = WorkoutLoggingFixtures.liftBenchId,
                setIndex = 0,
                weight = Weight(135.0, Weight.WeightUnit.Lb),
                reps = 8,
                rir = 2,
                completedAt = Instant.parse("2025-11-02T10:10:00Z"),
                liftContext = WorkoutLoggingFixtures.importLiftContext(),
                isPersonalRecord = false,
            ),
        )
    }
}
