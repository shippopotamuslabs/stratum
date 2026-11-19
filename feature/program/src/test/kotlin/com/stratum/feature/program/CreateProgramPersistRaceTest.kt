package com.stratum.feature.program

import com.stratum.application.onboarding.CompleteOnboardingUseCase
import com.stratum.application.onboarding.GetUserTrainingProfileUseCase
import com.stratum.application.program.ActivateProgramUseCase
import com.stratum.application.program.ComputeWorkoutMuscleVolumeUseCase
import com.stratum.application.program.EstimateWorkoutDurationMinutesUseCase
import com.stratum.application.program.ProgramRepository
import com.stratum.application.program.SaveProgramToLibraryUseCase
import com.stratum.application.program.UpdateExistingProgramUseCase
import com.stratum.application.training.AbandonTrainingSessionUseCase
import com.stratum.application.training.ObserveActiveSessionUseCase
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeOnboardingRepository
import com.stratum.core.testing.onboarding.FakeUserTrainingProfileRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.NoOpProgramChangeEventRepository
import com.stratum.domain.program.Program
import com.stratum.domain.workout.WorkoutEditSession
import com.stratum.feature.program.ui.EditLiftState
import com.stratum.feature.program.ui.EditSetState
import com.stratum.feature.program.ui.EditWorkoutUiState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class CreateProgramPersistRaceTest {

    @Test
    @DisplayName("Given days change while a save is writing then the save does not mark that draft clean or leave")
    fun divergedDaysStayDirtyAfterTheWrite() = runTest {
        val release = CompletableDeferred<Unit>()
        val repository = GatedProgramRepository(FakeProgramRepository(), release)
        val state = kotlinx.coroutines.flow.MutableStateFlow(editorState())
        val editor = editor(state, repository, this)
        var finished = false
        val writtenSetIds = setIds(state.value)

        editor.persist(CreateProgramPersistMode.SaveToLibrary) { finished = true }
        advanceUntilIdle()
        assertTrue(state.value.isSaving)

        val withExtraSet =
            state.value.days.map { day ->
                val edit = day.editState ?: return@map day
                day.copy(editState = EditWorkoutLiftMutations.addSet(edit, "bench"))
            }
        editor.applyEditorDays(withExtraSet)
        release.complete(Unit)
        advanceUntilIdle()

        val saved = repository.saved
        assertTrue(saved != null)
        assertEquals(writtenSetIds, setIds(saved!!))
        assertEquals(setIds(withExtraSet), setIds(state.value))
        assertNotEquals(writtenSetIds, setIds(state.value))
        assertTrue(state.value.isDirty)
        assertFalse(state.value.isSaving)
        assertFalse(finished)
    }

    @Test
    @DisplayName("Given undo is refused while saving then the written days are marked clean and the editor leaves")
    fun ignoredUndoLeavesTheWrittenDraftClean() = runTest {
        val release = CompletableDeferred<Unit>()
        val repository = GatedProgramRepository(FakeProgramRepository(), release)
        val state = kotlinx.coroutines.flow.MutableStateFlow(editorState())
        val editor = editor(state, repository, this)
        var finished = false
        val writtenSetIds = setIds(state.value)
        val earlierLift = lift(setId = "older-set")
        val earlier =
            ProgramSheetUndo(
                mapOf(
                    "A" to
                        listOf(
                            SheetLiftUndoStep.InsertLift(
                                lift = earlierLift,
                                originalIndex = 0,
                                sourceLiftsBefore = listOf(earlierLift),
                            ),
                        ),
                ),
            )

        editor.persist(CreateProgramPersistMode.SaveToLibrary) { finished = true }
        advanceUntilIdle()

        val decision =
            consumeSheetUndo(
                isSaving = state.value.isSaving,
                undo = earlier,
                days = state.value.days,
            )
        assertFalse(decision.consumed)
        release.complete(Unit)
        advanceUntilIdle()

        assertEquals(writtenSetIds, setIds(state.value))
        assertFalse(state.value.isDirty)
        assertFalse(state.value.isSaving)
        assertTrue(finished)
    }

    private fun editor(
        state: kotlinx.coroutines.flow.MutableStateFlow<CreateProgramUiState>,
        repository: GatedProgramRepository,
        scope: kotlinx.coroutines.CoroutineScope,
    ): CreateProgramEditorController {
        val events = NoOpProgramChangeEventRepository()
        val drain = DiagnosticDrain(FakeLoggingPort())
        val trainingLog = FakeTrainingLogRepository()
        return CreateProgramEditorController(
            state = state,
            scope = scope,
            activateProgram = ActivateProgramUseCase(repository, events),
            saveProgramToLibrary = SaveProgramToLibraryUseCase(repository, events),
            updateExistingProgram = UpdateExistingProgramUseCase(repository),
            completeOnboarding =
                CompleteOnboardingUseCase(
                    FakeOnboardingRepository(),
                    FakeUserTrainingProfileRepository(),
                ),
            getUserTrainingProfile = GetUserTrainingProfileUseCase(FakeUserTrainingProfileRepository()),
            computeWorkoutMuscleVolume = ComputeWorkoutMuscleVolumeUseCase(),
            estimateWorkoutDurationMinutes = EstimateWorkoutDurationMinutesUseCase(),
            workoutEditSession = WorkoutEditSession(),
            activeSessionGuard =
                CreateProgramActiveSessionGuard(
                    state = state,
                    observeActiveSession = ObserveActiveSessionUseCase(trainingLog),
                    abandonTrainingSession = AbandonTrainingSessionUseCase(trainingLog),
                    diagnosticDrain = drain,
                ),
            finishOnboarding = { false },
            diagnosticDrain = drain,
        )
    }

    private fun editorState(): CreateProgramUiState {
        val day =
            CreateProgramDayUi(
                id = "A",
                name = "Push",
                position = 0,
                isRest = false,
                editState =
                    EditWorkoutUiState.Loaded(
                        workoutId = "A",
                        workoutName = "Push",
                        lifts = listOf(lift(setId = "set-1")),
                    ),
            )
        return CreateProgramUiState(
            step = CreateProgramStep.Editor,
            programId = "program-1",
            programName = "Hypertrophy",
            days = listOf(day),
            selectedDayId = "A",
            isDirty = true,
        )
    }

    private fun lift(setId: String): EditLiftState =
        EditLiftState(
            workoutLiftId = "bench",
            liftId = "catalog-bench",
            name = "Bench Press",
            position = 0,
            sets =
                listOf(
                    EditSetState(
                        setId = setId,
                        setNumber = 1,
                        repMin = 8,
                        repMax = 12,
                        restSeconds = 90,
                        rirGoal = 2,
                    ),
                ),
        )

    private fun setIds(state: CreateProgramUiState): List<String> = setIds(state.days)

    private fun setIds(days: List<CreateProgramDayUi>): List<String> =
        days.flatMap { day -> day.editState?.lifts.orEmpty() }
            .flatMap { lift -> lift.sets }
            .map { set -> set.setId }

    private fun setIds(program: Program): List<String> =
        program.workouts
            .flatMap { workout -> workout.workoutLifts }
            .flatMap { lift -> lift.sets }
            .map { set -> set.id.value }

    private class GatedProgramRepository(
        private val delegate: ProgramRepository,
        private val release: CompletableDeferred<Unit>,
    ) : ProgramRepository by delegate {
        var saved: Program? = null

        override suspend fun saveToLibrary(program: Program) {
            saved = program
            release.await()
            delegate.saveToLibrary(program)
        }
    }
}
