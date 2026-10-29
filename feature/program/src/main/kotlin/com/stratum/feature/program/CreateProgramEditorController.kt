package com.stratum.feature.program

import com.stratum.domain.lift.Lift
import com.stratum.application.onboarding.CompleteOnboardingUseCase
import com.stratum.application.onboarding.GetUserTrainingProfileUseCase
import com.stratum.application.program.ActivateProgramUseCase
import com.stratum.application.program.ComputeWorkoutMuscleVolumeUseCase
import com.stratum.application.program.EstimateWorkoutDurationMinutesUseCase
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgressionScheme
import com.stratum.application.program.SaveProgramToLibraryUseCase
import com.stratum.application.program.UpdateExistingProgramUseCase
import com.stratum.domain.workout.WorkoutEditSession
import com.stratum.feature.program.ui.DEFAULT_REST_SECONDS
import com.stratum.feature.program.ui.EditLiftState
import com.stratum.feature.program.ui.EditWorkoutUiState
import com.stratum.feature.program.ui.PLACEHOLDER_LIFT_NAME
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Editor path: day strip, lift catalog edits, derived metrics, and save/activate.
 * Separated from generate prefs so schedule/editor changes do not share one god-class body.
 * Active-session discard confirmations live in [CreateProgramActiveSessionGuard].
 */
internal class CreateProgramEditorController(
    private val state: MutableStateFlow<CreateProgramUiState>,
    private val scope: CoroutineScope,
    private val activateProgram: ActivateProgramUseCase,
    private val saveProgramToLibrary: SaveProgramToLibraryUseCase,
    private val updateExistingProgram: UpdateExistingProgramUseCase,
    private val completeOnboarding: CompleteOnboardingUseCase,
    private val getUserTrainingProfile: GetUserTrainingProfileUseCase,
    private val computeWorkoutMuscleVolume: ComputeWorkoutMuscleVolumeUseCase,
    private val estimateWorkoutDurationMinutes: EstimateWorkoutDurationMinutesUseCase,
    private val workoutEditSession: WorkoutEditSession,
    private val activeSessionGuard: CreateProgramActiveSessionGuard,
    private val finishOnboarding: () -> Boolean,
) {
    var loadedProgramMeta: LoadedProgramMeta? = null
        private set

    var liftCatalog: List<Lift> = emptyList()
    var liftNamesById: Map<String, String> = emptyMap()
    var compoundByLiftId: Map<String, Boolean> = emptyMap()

    fun clearLoadedMeta() {
        loadedProgramMeta = null
    }

    fun selectScratchPath() {
        loadedProgramMeta = null
        val restId = UUID.randomUUID().toString()
        state.update {
            it.copy(
                path = CreateProgramPath.Scratch,
                step = CreateProgramStep.Editor,
                programName = "New Program",
                days =
                    listOf(
                        CreateProgramDayUi(
                            id = restId,
                            name = "Rest",
                            position = 0,
                            isRest = true,
                            editState = null,
                        ),
                    ),
                selectedDayId = restId,
                isDirty = true,
            )
        }
    }

    fun selectDay(dayId: String) {
        state.update { it.copy(selectedDayId = dayId) }
    }

    fun addDay() {
        state.update { current -> CreateProgramDayEditor.addRestDay(current) }
    }

    fun removeSelectedDay() {
        scope.launch {
            val current = state.value
            val selectedId = current.selectedDayId ?: return@launch
            if (current.days.size <= 1) return@launch
            val session = activeSessionGuard.inProgressSessionOrNull()
            if (activeSessionGuard.isActiveWorkoutDay(selectedId, session)) {
                activeSessionGuard.stageRemoveDayDiscard()
                return@launch
            }
            applyRemoveSelectedDay()
        }
    }

    fun applyRemoveSelectedDay() {
        state.update { current ->
            CreateProgramDayEditor.removeSelectedDay(current).withEditorDerivedMetrics()
        }
    }

    fun reorderDaysByIds(orderedDayIds: List<String>) {
        state.update { current ->
            CreateProgramDayEditor.reorderDaysByIds(current, orderedDayIds)
                .withEditorDerivedMetrics()
        }
    }

    fun showRenameProgram(show: Boolean) {
        state.update { it.copy(showRenameProgram = show) }
    }

    fun showRenameWorkout(show: Boolean) {
        state.update { it.copy(showRenameWorkout = show) }
    }

    fun renameProgram(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        state.update {
            it.copy(programName = trimmed, showRenameProgram = false, isDirty = true)
        }
    }

    fun renameSelectedWorkout(name: String) {
        state.update { current ->
            CreateProgramDayEditor.renameSelectedWorkout(
                state = current,
                name = name,
                emptyEdit = ::emptyWorkoutEditState,
            )
        }
    }

    fun addExerciseToSelectedDay(
        onNavigateToPicker: (workoutId: String, workoutLiftId: String?, liftId: String?, liftName: String?) -> Unit,
    ) {
        convertSelectedRestToWorkoutIfNeeded()
        val day = state.value.selectedDay ?: return
        val edit = day.editState ?: return
        onNavigateToPicker(edit.workoutId, null, null, null)
    }

    fun updateSelectedWorkout(transform: (EditWorkoutUiState.Loaded) -> EditWorkoutUiState.Loaded) {
        state.update { current ->
            CreateProgramDayEditor.updateSelectedWorkout(current, transform)
                .withEditorDerivedMetrics()
        }
    }

    fun startReorderLifts(onNavigate: (workoutId: String) -> Unit) {
        val day = state.value.selectedDay ?: return
        val edit = day.editState ?: return
        val items =
            edit.lifts.map { lift ->
                com.stratum.domain.workout.ReorderLiftItem(lift.workoutLiftId, lift.name)
            }
        workoutEditSession.setReorderLifts(items)
        onNavigate(edit.workoutId)
    }

    fun requestDiscard(): Boolean {
        val current = state.value
        if (current.step != CreateProgramStep.Editor) {
            return true
        }
        if (current.isEditingExisting && !current.isDirty) {
            return true
        }
        state.update { it.copy(showDiscardConfirm = true) }
        return false
    }

    fun dismissDiscard() {
        state.update { it.copy(showDiscardConfirm = false) }
    }

    /**
     * Saves or activates the draft. When an in-progress session would become unresumable, stages a
     * confirm dialog via [CreateProgramActiveSessionGuard] instead of persisting immediately.
     */
    fun persist(mode: CreateProgramPersistMode, onDone: () -> Unit) {
        val current = state.value
        if (!current.canSave || current.isSaving) {
            state.update {
                it.copy(
                    saveErrorMessage =
                        if (!current.canSave) {
                            "Add at least one workout with an exercise"
                        } else {
                            null
                        },
                )
            }
            return
        }
        scope.launch {
            val draft = current.toDomainProgram(loadedProgramMeta)
            val session = activeSessionGuard.inProgressSessionOrNull()
            if (
                session != null &&
                activeSessionGuard.wouldDiscardActiveSession(session, draft, mode, current)
            ) {
                activeSessionGuard.stagePersistDiscard(mode = mode, onDone = onDone)
                return@launch
            }
            persistNow(mode = mode, onDone = onDone)
        }
    }

    fun loadProgramIntoEditor(
        program: Program,
        markDirty: Boolean = true,
        isExisting: Boolean = false,
    ) {
        val days = program.toCreateProgramDays(liftNamesById, compoundByLiftId)
        loadedProgramMeta = program.toLoadedProgramMeta()
        state.update {
            it.copy(
                step = CreateProgramStep.Editor,
                path = CreateProgramPath.Scratch,
                generateState = CreateProgramGenerateState.Idle,
                programId = program.id.value,
                programName = program.name,
                days = days,
                selectedDayId =
                    days.firstOrNull { day -> !day.isRest }?.id ?: days.firstOrNull()?.id,
                isDirty = markDirty,
                isEditingExisting = isExisting,
                originalIsActive = program.isActive,
                loadErrorMessage = null,
                showDiscardConfirm = false,
            ).withEditorDerivedMetrics()
        }
    }

    fun refreshDerivedMetrics() {
        state.update { it.withEditorDerivedMetrics() }
    }

    fun handleLiftSelected(workoutId: String, workoutLiftId: String?, liftId: String) {
        val catalog = liftCatalog.firstOrNull { it.id.value == liftId }
        val liftName = catalog?.name ?: liftNamesById[liftId] ?: PLACEHOLDER_LIFT_NAME
        val isCompound = catalog?.isCompound ?: compoundByLiftId[liftId] ?: false
        val restSeconds = catalog?.resolvedDefaultRestSeconds() ?: DEFAULT_REST_SECONDS
        state.update { current ->
            current.copy(
                days =
                    current.days.map { day ->
                        val edit = day.editState
                        if (edit == null || edit.workoutId != workoutId) {
                            day
                        } else if (workoutLiftId == null) {
                            val newLift =
                                EditLiftState(
                                    workoutLiftId = newWorkoutLiftId(),
                                    liftId = liftId,
                                    name = liftName,
                                    position = edit.lifts.size,
                                    sets =
                                        listOf(
                                            defaultEditSetState(1, restSeconds = restSeconds),
                                        ),
                                    isCompound = isCompound,
                                    progressionScheme = ProgressionScheme.Double,
                                )
                            day.copy(
                                isRest = false,
                                editState = edit.copy(lifts = edit.lifts + newLift, isDirty = true),
                            )
                        } else {
                            day.copy(
                                editState =
                                    edit.copy(
                                        lifts =
                                            edit.lifts.map { lift ->
                                                if (lift.workoutLiftId == workoutLiftId) {
                                                    lift.copy(
                                                        liftId = liftId,
                                                        name = liftName,
                                                        isCompound = isCompound,
                                                        sets =
                                                            lift.sets.map { set ->
                                                                set.copy(restSeconds = restSeconds)
                                                            },
                                                    )
                                                } else {
                                                    lift
                                                }
                                            },
                                        isDirty = true,
                                    ),
                            )
                        }
                    },
                isDirty = true,
            ).withEditorDerivedMetrics()
        }
    }

    fun handleLiftsSelected(workoutId: String, liftIds: List<String>, asSuperset: Boolean) {
        if (liftIds.isEmpty()) return
        val groupId =
            if (asSuperset && liftIds.size >= 2) {
                newSupersetGroupId()
            } else {
                null
            }
        state.update { current ->
            current.copy(
                days =
                    current.days.map { day ->
                        val edit = day.editState
                        if (edit == null || edit.workoutId != workoutId) {
                            day
                        } else {
                            val startPosition = edit.lifts.size
                            val newLifts =
                                liftIds.mapIndexed { index, liftId ->
                                    val catalog = liftCatalog.firstOrNull { it.id.value == liftId }
                                    val restSeconds =
                                        catalog?.resolvedDefaultRestSeconds() ?: DEFAULT_REST_SECONDS
                                    EditLiftState(
                                        workoutLiftId = newWorkoutLiftId(),
                                        liftId = liftId,
                                        name =
                                            catalog?.name
                                                ?: liftNamesById[liftId]
                                                ?: PLACEHOLDER_LIFT_NAME,
                                        position = startPosition + index,
                                        sets =
                                            listOf(
                                                defaultEditSetState(
                                                    1,
                                                    restSeconds = restSeconds,
                                                ),
                                            ),
                                        isCompound =
                                            catalog?.isCompound
                                                ?: compoundByLiftId[liftId]
                                                ?: false,
                                        progressionScheme = ProgressionScheme.Double,
                                        supersetGroupId = groupId,
                                        useRestTimerBetweenLifts = false,
                                    )
                                }
                            day.copy(
                                isRest = false,
                                editState = edit.copy(lifts = edit.lifts + newLifts, isDirty = true),
                            )
                        }
                    },
                isDirty = true,
            ).withEditorDerivedMetrics()
        }
    }

    fun handleLiftsReordered(workoutId: String, orderedIds: List<String>) {
        state.update { current ->
            current.copy(
                days =
                    current.days.map { day ->
                        val edit = day.editState
                        if (edit == null || edit.workoutId != workoutId) {
                            day
                        } else {
                            val byId = edit.lifts.associateBy { it.workoutLiftId }
                            val reordered =
                                orderedIds.mapNotNull { byId[it] }
                                    .mapIndexed { index, lift -> lift.copy(position = index) }
                            day.copy(editState = edit.copy(lifts = reordered, isDirty = true))
                        }
                    },
                isDirty = true,
            ).withEditorDerivedMetrics()
        }
    }

    private suspend fun persistNow(mode: CreateProgramPersistMode, onDone: () -> Unit) {
        val current = state.value
        state.update { it.copy(isSaving = true, saveErrorMessage = null) }
        val draft = current.toDomainProgram(loadedProgramMeta)
        val result =
            when (mode) {
                CreateProgramPersistMode.Activate -> activateProgram(draft.activate())
                CreateProgramPersistMode.SavePreservingActive -> updateExistingProgram(draft)
                CreateProgramPersistMode.SaveToLibrary ->
                    if (current.isEditingExisting) {
                        updateExistingProgram(draft.deactivate())
                    } else {
                        saveProgramToLibrary(draft)
                    }
            }
        result
            .onSuccess {
                if (finishOnboarding() && mode == CreateProgramPersistMode.Activate) {
                    completeOnboarding(getUserTrainingProfile())
                }
                if (mode == CreateProgramPersistMode.Activate) {
                    loadedProgramMeta = draft.activate().toLoadedProgramMeta()
                } else if (mode == CreateProgramPersistMode.SavePreservingActive) {
                    loadedProgramMeta = draft.toLoadedProgramMeta()
                } else if (current.isEditingExisting) {
                    loadedProgramMeta = draft.deactivate().toLoadedProgramMeta()
                }
                state.update {
                    it.copy(
                        isSaving = false,
                        isDirty = false,
                        originalIsActive =
                            when (mode) {
                                CreateProgramPersistMode.Activate -> true
                                CreateProgramPersistMode.SaveToLibrary -> false
                                CreateProgramPersistMode.SavePreservingActive -> it.originalIsActive
                            },
                    )
                }
                if (!finishOnboarding() || mode == CreateProgramPersistMode.Activate) {
                    onDone()
                }
            }
            .onFailure { error ->
                state.update {
                    it.copy(
                        isSaving = false,
                        saveErrorMessage = error.message ?: "Couldn't save program. Try again.",
                    )
                }
            }
    }

    private fun convertSelectedRestToWorkoutIfNeeded() {
        val current = state.value
        val selected = current.selectedDay ?: return
        if (!selected.isRest && selected.editState != null) return
        val name =
            if (selected.isRest || selected.name == "Rest") {
                "Workout ${current.days.count { !it.isRest } + 1}"
            } else {
                selected.name
            }
        state.update { snap ->
            snap.copy(
                days =
                    snap.days.map { day ->
                        if (day.id != selected.id) {
                            day
                        } else {
                            day.copy(
                                isRest = false,
                                name = name,
                                editState = emptyWorkoutEditState(day.id, name),
                            )
                        }
                    },
                isDirty = true,
            ).withEditorDerivedMetrics()
        }
    }

    private fun emptyWorkoutEditState(workoutId: String, name: String): EditWorkoutUiState.Loaded =
        EditWorkoutUiState.Loaded(
            workoutId = workoutId,
            workoutName = name,
            lifts = emptyList(),
            isDirty = true,
        )

    private fun CreateProgramUiState.withEditorDerivedMetrics(): CreateProgramUiState {
        if (step != CreateProgramStep.Editor) return this
        if (days.none { day -> day.editState != null }) return this
        val draftProgram = toDomainProgram(loadedProgramMeta)
        return copy(
            days =
                days.map { day ->
                    val edit = day.editState ?: return@map day
                    day.copy(
                        editState =
                            edit.withDerivedMetrics(
                                liftCatalog = liftCatalog,
                                program = draftProgram,
                                computeWorkoutMuscleVolume = computeWorkoutMuscleVolume,
                                estimateWorkoutDurationMinutes = estimateWorkoutDurationMinutes,
                            ),
                    )
                },
        )
    }
}
