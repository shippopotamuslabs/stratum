package com.stratum.feature.history

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.lift.FakeWeightDisplayPreferencesRepository
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.response.FakeResponseModelingSchedulerPort
import com.stratum.core.testing.response.FakeResponseProfileMetadataRepository
import com.stratum.core.testing.response.FakeResponseProfilePersistencePort
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.application.importing.ExtractTrainingInsightsUseCase
import com.stratum.domain.importing.ImportAnalysisMetadata
import com.stratum.application.importing.ImportAnalysisMetadataRepository
import com.stratum.domain.importing.LiftTrainingInsights
import com.stratum.application.importing.LiftTrainingInsightsRepository
import com.stratum.domain.importing.MuscleVolumeBaseline
import com.stratum.application.importing.MuscleVolumeBaselineRepository
import com.stratum.domain.importing.SupersetAffinity
import com.stratum.application.importing.SupersetAffinityRepository
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.application.lift.ObserveWeightDisplayPreferencesUseCase
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.application.training.GetCompletedSessionForEditUseCase
import com.stratum.domain.training.LoggedSet
import com.stratum.application.training.RefreshWeeklySeriesUseCase
import com.stratum.application.training.UpdateCompletedSessionSetsUseCase
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.feature.history.ui.EditHistoricalWorkoutEvent
import com.stratum.feature.history.ui.EditHistoricalWorkoutUiState
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class EditHistoricalWorkoutViewModelTest {

    @Test
    @DisplayName("Load maps completed session sets into editable presentation state")
    fun loadMapsSets() = runTest {
        val env = buildEnv()
        env.vm.load("ts-1")
        advanceUntilIdle()

        val state = env.vm.uiState.value as EditHistoricalWorkoutUiState.Loaded
        assertEquals("Upper A", state.workoutName)
        assertFalse(state.isDirty)
        assertEquals(1, state.lifts.size)
        assertEquals("135", state.lifts.single().sets.single().weightText)
        assertEquals("8", state.lifts.single().sets.single().repsText)
    }

    @Test
    @DisplayName("Editing weight marks dirty; confirm persists and navigates back")
    fun editAndConfirm() = runTest {
        val env = buildEnv()
        env.vm.load("ts-1")
        advanceUntilIdle()

        val setKey =
            (env.vm.uiState.value as EditHistoricalWorkoutUiState.Loaded)
                .lifts.single().sets.single().setKey
        val liftKey =
            (env.vm.uiState.value as EditHistoricalWorkoutUiState.Loaded).lifts.single().key

        env.vm.onWeightChange(liftKey, setKey, "155")
        assertTrue((env.vm.uiState.value as EditHistoricalWorkoutUiState.Loaded).isDirty)

        env.vm.events.test {
            env.vm.confirm()
            advanceUntilIdle()
            assertEquals(EditHistoricalWorkoutEvent.NavigateBack, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        val stored = env.logRepo.getSetsForSession(TrainingSessionId("ts-1")).single()
        assertEquals(155.0, stored.weight.amount)
    }

    @Test
    @DisplayName("Confirm persists partialReps; blank text stores null")
    fun confirmPersistsPartialReps() = runTest {
        val env = buildEnv()
        env.vm.load("ts-1")
        advanceUntilIdle()

        val loaded = env.vm.uiState.value as EditHistoricalWorkoutUiState.Loaded
        val setKey = loaded.lifts.single().sets.single().setKey
        val liftKey = loaded.lifts.single().key

        env.vm.onPartialRepsChange(liftKey, setKey, "2")
        env.vm.events.test {
            env.vm.confirm()
            advanceUntilIdle()
            assertEquals(EditHistoricalWorkoutEvent.NavigateBack, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, env.logRepo.getSetsForSession(TrainingSessionId("ts-1")).single().partialReps)

        env.vm.load("ts-1")
        advanceUntilIdle()
        val reloaded = env.vm.uiState.value as EditHistoricalWorkoutUiState.Loaded
        env.vm.onPartialRepsChange(
            reloaded.lifts.single().key,
            reloaded.lifts.single().sets.single().setKey,
            "",
        )
        env.vm.events.test {
            env.vm.confirm()
            advanceUntilIdle()
            assertEquals(EditHistoricalWorkoutEvent.NavigateBack, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(
            null,
            env.logRepo.getSetsForSession(TrainingSessionId("ts-1")).single().partialReps,
        )
    }

    @Test
    @DisplayName("Confirm preserves set equipmentId through the edit round-trip")
    fun confirmPreservesEquipmentId() = runTest {
        val equipmentId = EquipmentId("eq-cable")
        val env = buildEnv(seedEquipmentId = equipmentId)
        env.vm.load("ts-1")
        advanceUntilIdle()

        val loaded = env.vm.uiState.value as EditHistoricalWorkoutUiState.Loaded
        val setKey = loaded.lifts.single().sets.single().setKey
        val liftKey = loaded.lifts.single().key

        env.vm.onWeightChange(liftKey, setKey, "155")
        env.vm.events.test {
            env.vm.confirm()
            advanceUntilIdle()
            assertEquals(EditHistoricalWorkoutEvent.NavigateBack, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        val stored = env.logRepo.getSetsForSession(TrainingSessionId("ts-1")).single()
        assertEquals(155.0, stored.weight.amount)
        assertEquals(equipmentId, stored.equipmentId)
    }

    @Test
    @DisplayName("Add set increases draft rows and marks dirty")
    fun addSetMarksDirty() = runTest {
        val env = buildEnv()
        env.vm.load("ts-1")
        advanceUntilIdle()
        val liftKey =
            (env.vm.uiState.value as EditHistoricalWorkoutUiState.Loaded).lifts.single().key

        env.vm.onAddSet(liftKey)
        val loaded = env.vm.uiState.value as EditHistoricalWorkoutUiState.Loaded
        assertEquals(2, loaded.lifts.single().sets.size)
        assertTrue(loaded.isDirty)
    }

    @Test
    @DisplayName("Confirm with blank reps sets userMessage and does not persist")
    fun confirmBlankRepsValidation() = runTest {
        val env = buildEnv()
        env.vm.load("ts-1")
        advanceUntilIdle()

        val loaded = env.vm.uiState.value as EditHistoricalWorkoutUiState.Loaded
        val setKey = loaded.lifts.single().sets.single().setKey
        val liftKey = loaded.lifts.single().key

        env.vm.onRepsChange(liftKey, setKey, "")
        env.vm.confirm()
        advanceUntilIdle()

        assertEquals("Enter reps for Barbell Bench Press", env.vm.userMessage.value)
        val stored = env.logRepo.getSetsForSession(TrainingSessionId("ts-1")).single()
        assertEquals(8, stored.reps)
    }

    @Test
    @DisplayName("Confirm with blank weight sets userMessage and does not persist")
    fun confirmBlankWeightValidation() = runTest {
        val env = buildEnv()
        env.vm.load("ts-1")
        advanceUntilIdle()

        val loaded = env.vm.uiState.value as EditHistoricalWorkoutUiState.Loaded
        val setKey = loaded.lifts.single().sets.single().setKey
        val liftKey = loaded.lifts.single().key

        env.vm.onWeightChange(liftKey, setKey, "")
        env.vm.confirm()
        advanceUntilIdle()

        assertEquals("Enter weight for Barbell Bench Press", env.vm.userMessage.value)
        val stored = env.logRepo.getSetsForSession(TrainingSessionId("ts-1")).single()
        assertEquals(135.0, stored.weight.amount)
    }

    private suspend fun buildEnv(seedEquipmentId: EquipmentId? = null): Env {
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
                equipmentId = seedEquipmentId,
                setIndex = 0,
                weight = Weight(135.0, Weight.WeightUnit.Lb),
                reps = 8,
                rir = 2,
                completedAt = Instant.parse("2025-01-01T10:10:00Z"),
                liftContext = WorkoutLoggingFixtures.importLiftContext(),
            ),
        )
        val liftRepo = FakeLiftRepository(listOf(WorkoutLoggingFixtures.benchLift()))
        val metadataRepo = FakeResponseProfileMetadataRepository()
        val persistence = FakeResponseProfilePersistencePort(metadataRepository = metadataRepo)
        val update =
            UpdateCompletedSessionSetsUseCase(
                trainingLogRepository = logRepo,
                localDerivedStages =
                    com.stratum.core.testing.training.localDerivedTrainingStages(
                        trainingLog = logRepo,
                        liftRepository = liftRepo,
                        persistence = persistence,
                    ),
                metadataRepository = metadataRepo,
                responseModelingScheduler = FakeResponseModelingSchedulerPort(),
                loggingPort = FakeLoggingPort(),
                refreshScope = CoroutineScope(Dispatchers.Unconfined),
            )
        val vm =
            EditHistoricalWorkoutViewModel(
                getCompletedSessionForEdit =
                    GetCompletedSessionForEditUseCase(
                        trainingLogRepository = logRepo,
                        programRepository = programRepo,
                        liftRepository = liftRepo,
                        loggingPort = FakeLoggingPort(),
                    ),
                updateCompletedSessionSets = update,
                observeWeightDisplayPreferences =
                    ObserveWeightDisplayPreferencesUseCase(FakeWeightDisplayPreferencesRepository()),
            )
        return Env(vm, logRepo)
    }

    private data class Env(
        val vm: EditHistoricalWorkoutViewModel,
        val logRepo: FakeTrainingLogRepository,
    )
}
