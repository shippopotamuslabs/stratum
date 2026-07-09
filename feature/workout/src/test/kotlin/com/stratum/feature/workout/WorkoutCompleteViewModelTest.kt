package com.stratum.feature.workout

import com.stratum.core.testing.logging.FakeLoggingPort
import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.training.EstimatedOneRm
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.lift.Weight
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.training.GetWorkoutSummaryUseCase
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.feature.workout.ui.WorkoutCompleteUiState
import java.time.Instant
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Presentation-mapping tests for [WorkoutCompleteViewModel]. State-based assertions on
 * `uiState` only (UNIT_TESTING.md §1). The VM is wired with a real
 * [GetWorkoutSummaryUseCase] against `:core:testing` fakes — no MockK.
 */
@ExtendWith(MainDispatcherRule::class)
@DisplayName("WorkoutCompleteViewModel presentation mapping")
class WorkoutCompleteViewModelTest {

    @Test
    @DisplayName("Loaded state maps lift name, performance, RIR, and e1RM for a loaded set")
    fun loadedStateMapsBestSetFields() = runTest {
        val viewModel = buildViewModelWithBenchSet(
            weight = 185.0,
            reps = 3,
            rir = 1,
        )

        viewModel.loadSummary("ts-1")
        advanceUntilIdle()

        val loaded = viewModel.uiState.value as WorkoutCompleteUiState.Loaded
        val bestSet = loaded.bestSets.single()
        assertEquals("Barbell Bench Press", bestSet.liftName)
        assertEquals("185 × 3", bestSet.performanceLabel)
        assertEquals("1", bestSet.rirLabel)
        assertEquals(expectedOneRmLabel(185.0, 3, 1), bestSet.estimatedOneRmLabel)
    }

    @Test
    @DisplayName("Null RIR renders as an em dash")
    fun nullRirRendersAsDash() = runTest {
        val viewModel = buildViewModelWithBenchSet(
            weight = 135.0,
            reps = 5,
            rir = null,
        )

        viewModel.loadSummary("ts-1")
        advanceUntilIdle()

        val bestSet = (viewModel.uiState.value as WorkoutCompleteUiState.Loaded).bestSets.single()
        assertEquals("—", bestSet.rirLabel)
    }

    @Test
    @DisplayName("Bodyweight set (weight 0.0) performance label shows reps only")
    fun bodyweightPerformanceLabelOmitsWeight() = runTest {
        val viewModel = buildViewModelWithBenchSet(
            weight = 0.0,
            reps = 8,
            rir = 2,
        )

        viewModel.loadSummary("ts-1")
        advanceUntilIdle()

        val bestSet = (viewModel.uiState.value as WorkoutCompleteUiState.Loaded).bestSets.single()
        assertEquals("8", bestSet.performanceLabel)
    }

    @Test
    @DisplayName("isPersonalRecord is always false until PR detection ships")
    fun personalRecordAlwaysFalseToday() = runTest {
        val viewModel = buildViewModelWithBenchSet(
            weight = 225.0,
            reps = 1,
            rir = 0,
        )

        viewModel.loadSummary("ts-1")
        advanceUntilIdle()

        val bestSet = (viewModel.uiState.value as WorkoutCompleteUiState.Loaded).bestSets.single()
        assertFalse(bestSet.isPersonalRecord)
    }

    @Test
    @DisplayName("Loaded state flows through uiState Turbine stream")
    fun loadedStateObservableViaTurbine() = runTest {
        val viewModel = buildViewModelWithBenchSet(
            weight = 185.0,
            reps = 3,
            rir = 1,
        )

        viewModel.uiState.test {
            // Initial state is Loading before loadSummary is called.
            assertEquals(WorkoutCompleteUiState.Loading, awaitItem())
            viewModel.loadSummary("ts-1")
            val loaded = awaitItem() as WorkoutCompleteUiState.Loaded
            assertEquals("Workout A", loaded.workoutName)
            // 3,600,000 ms → 1h, formatDuration emits HH:MM:SS for hour durations.
            assertEquals("1:00:00", loaded.durationFormatted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Mirrors `GetWorkoutSummaryUseCaseTest.bestSetPerLift` fixtures: completed live session
     * for "Workout A", bench lift catalog entry, one logged bench set. Sets are seeded via
     * the constructor (non-suspend) so the helper can run outside a coroutine.
     */
    private fun buildViewModelWithBenchSet(
        weight: Double,
        reps: Int,
        rir: Int?,
    ): WorkoutCompleteViewModel {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val session = WorkoutLoggingFixtures.completedLiveSession(id = "ts-1")
        val benchSet = LoggedSet(
            id = LoggedSetId("set-1"),
            sessionId = TrainingSessionId("ts-1"),
            exerciseNameRaw = "Barbell Bench Press",
            liftId = WorkoutLoggingFixtures.liftBenchId,
            setIndex = 0,
            weight = Weight(weight, Weight.WeightUnit.Lb),
            reps = reps,
            rir = rir,
            completedAt = Instant.parse("2025-01-01T10:05:00Z"),
            liftContext = WorkoutLoggingFixtures.importLiftContext(),
        )
        val logRepo = FakeTrainingLogRepository(
            initialSessions = listOf(session),
            initialSets = listOf(benchSet),
            programRepository = programRepo,
        )
        val liftRepo = FakeLiftRepository(initialLifts = listOf(benchLift()))
        val useCase = GetWorkoutSummaryUseCase(logRepo, programRepo, ObserveLiftCatalogUseCase(liftRepo), FakeLoggingPort())
        return WorkoutCompleteViewModel(useCase)
    }

    /** Matches the VM's [WorkoutCompleteViewModel.formatWeight] rounding (1 decimal, no trailing .0). */
    private fun expectedOneRmLabel(weight: Double, reps: Int, rir: Int? = null): String {
        val e1Rm = EstimatedOneRm.calculate(weight, reps + (rir ?: 0))
        val rounded = (e1Rm * 10).toInt() / 10.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }

    private fun benchLift(): Lift = Lift(
        id = WorkoutLoggingFixtures.liftBenchId,
        name = "Barbell Bench Press",
        movementPatterns = setOf(MovementPattern.HorizontalPress),
        primaryMuscles = setOf(MuscleGroup.Chest),
        secondaryMuscles = emptySet(),
        incrementTiers = emptyList(),
        presetTags = emptySet(),
        enabled = true,
    )
}
