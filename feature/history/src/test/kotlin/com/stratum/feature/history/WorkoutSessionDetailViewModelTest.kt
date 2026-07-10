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
import com.stratum.feature.history.ui.WorkoutSessionDetailUiState
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
class WorkoutSessionDetailViewModelTest {

    @Test
    @DisplayName("Load maps domain detail to presentation labels")
    fun loadMapsDetail() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val session =
            WorkoutLoggingFixtures.completedLiveSession(id = "ts-1").copy(
                workoutNameSnapshot = "Upper A",
            )
        val logRepo =
            FakeTrainingLogRepository(
                initialSessions = listOf(session),
                programRepository = programRepo,
            )
        logRepo.insertSet(
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
            ),
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
            )

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

}
