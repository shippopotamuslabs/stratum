package com.stratum.feature.history

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Weight
import com.stratum.domain.training.ComputeLoggedSessionMuscleVolumeUseCase
import com.stratum.domain.training.GetWorkoutSessionDetailUseCase
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.feature.history.ui.HistoryDaySessionsUiState
import java.time.Instant
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
    @DisplayName("Load maps multiple sessions into day list summaries")
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
        suspend fun seedSet(sessionId: String, setId: String) {
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
        seedSet("ts-morning", "set-m")
        seedSet("ts-evening", "set-e")

        val vm =
            HistoryDaySessionsViewModel(
                getWorkoutSessionDetail =
                    GetWorkoutSessionDetailUseCase(
                        trainingLogRepository = logRepo,
                        programRepository = programRepo,
                        computeLoggedSessionMuscleVolume = ComputeLoggedSessionMuscleVolumeUseCase(),
                        loggingPort = FakeLoggingPort(),
                    ),
            )

        // Newest first, matching calendar order.
        vm.load(
            dateIso = "2025-11-02",
            sessionIds = listOf("ts-evening", "ts-morning"),
        )
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
    @DisplayName("Empty session id list yields error state")
    fun emptySessionIdsError() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val logRepo =
            FakeTrainingLogRepository(
                initialSessions = emptyList(),
                programRepository = programRepo,
            )
        val vm =
            HistoryDaySessionsViewModel(
                getWorkoutSessionDetail =
                    GetWorkoutSessionDetailUseCase(
                        trainingLogRepository = logRepo,
                        programRepository = programRepo,
                        computeLoggedSessionMuscleVolume = ComputeLoggedSessionMuscleVolumeUseCase(),
                        loggingPort = FakeLoggingPort(),
                    ),
            )

        vm.load(dateIso = "2025-11-02", sessionIds = emptyList())
        advanceUntilIdle()

        vm.uiState.test {
            assertEquals(HistoryDaySessionsUiState.Error, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
