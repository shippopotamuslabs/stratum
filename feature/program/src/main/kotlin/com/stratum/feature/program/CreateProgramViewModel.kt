package com.stratum.feature.program

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.core.ui.progress.activeTimelineIndex
import com.stratum.core.ui.progress.toUi
import com.stratum.core.ui.trainingsetup.MinimumVolumeDraftUi
import com.stratum.core.ui.trainingsetup.ScheduleDraftUi
import com.stratum.core.ui.trainingsetup.SpecializationDraftUi
import com.stratum.core.ui.trainingsetup.TrainingSetupValidationConstants
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.resolvedDefaultRestSeconds
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.onboarding.SpecializationPreference
import com.stratum.domain.onboarding.CompleteOnboardingUseCase
import com.stratum.domain.onboarding.GetUserTrainingProfileUseCase
import com.stratum.domain.onboarding.UpdateSpecializationUseCase
import com.stratum.domain.onboarding.UpdateUserTrainingProfileUseCase
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.ActivateProgramUseCase
import com.stratum.domain.program.ComputeWorkoutMuscleVolumeUseCase
import com.stratum.domain.program.EstimateWorkoutDurationMinutesUseCase
import com.stratum.domain.program.GenerateProgramUseCase
import com.stratum.domain.program.GetProgramByIdUseCase
import com.stratum.domain.program.InferLastTrainedBucketUseCase
import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgramGenerationProgress
import com.stratum.domain.program.ProgramGenerationProgressPhase
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SaveProgramToLibraryUseCase
import com.stratum.domain.program.SetType
import com.stratum.domain.program.UpdateExistingProgramUseCase
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutSupersetGroupId
import com.stratum.domain.workout.EditSessionEvent
import com.stratum.domain.workout.WorkoutEditSession
import com.stratum.feature.program.ui.DEFAULT_REST_SECONDS
import com.stratum.feature.program.ui.EditLiftState
import com.stratum.feature.program.ui.EditWorkoutUiState
import com.stratum.feature.program.ui.PLACEHOLDER_LIFT_NAME
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
@Suppress("TooManyFunctions", "LargeClass")
class CreateProgramViewModel @Inject constructor(
    private val generateProgram: GenerateProgramUseCase,
    private val activateProgram: ActivateProgramUseCase,
    private val saveProgramToLibrary: SaveProgramToLibraryUseCase,
    private val updateExistingProgram: UpdateExistingProgramUseCase,
    private val getProgramById: GetProgramByIdUseCase,
    private val updateUserTrainingProfile: UpdateUserTrainingProfileUseCase,
    private val updateSpecialization: UpdateSpecializationUseCase,
    private val getUserTrainingProfile: GetUserTrainingProfileUseCase,
    private val completeOnboarding: CompleteOnboardingUseCase,
    private val inferLastTrained: InferLastTrainedBucketUseCase,
    private val observeLiftCatalog: ObserveLiftCatalogUseCase,
    private val computeWorkoutMuscleVolume: ComputeWorkoutMuscleVolumeUseCase,
    private val estimateWorkoutDurationMinutes: EstimateWorkoutDurationMinutesUseCase,
    private val workoutEditSession: WorkoutEditSession,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CreateProgramUiState(programId = UUID.randomUUID().toString()),
    )
    val uiState: StateFlow<CreateProgramUiState> = _uiState.asStateFlow()

    private var generateJob: Job? = null
    private var liftCatalog: List<Lift> = emptyList()
    private var liftNamesById: Map<String, String> = emptyMap()
    private var compoundByLiftId: Map<String, Boolean> = emptyMap()
    private var finishOnboarding: Boolean = false
    private var entryConfigured: Boolean = false
    private var configuredProgramId: String? = null
    private var configuredStartInScratch: Boolean = false

    init {
        viewModelScope.launch {
            observeLiftCatalog().collect { lifts ->
                liftCatalog = lifts
                liftNamesById = lifts.associate { lift -> lift.id.value to lift.name }
                compoundByLiftId = lifts.associate { lift -> lift.id.value to lift.isCompound }
                _uiState.update { state -> state.withEditorDerivedMetrics() }
            }
        }
        viewModelScope.launch {
            workoutEditSession.events.collect { event ->
                when (event) {
                    is EditSessionEvent.LiftSelected -> handleLiftSelected(event.workoutId, event.workoutLiftId, event.liftId)
                    is EditSessionEvent.LiftsSelected -> handleLiftsSelected(event.workoutId, event.liftIds, event.asSuperset)
                    is EditSessionEvent.LiftsReordered -> handleLiftsReordered(event.workoutId, event.orderedWorkoutLiftIds)
                }
            }
        }
    }

    /**
     * Applies nav-entry options once (scratch jump / onboarding completion / edit existing).
     * Safe to call from [LaunchedEffect] when the route is composed.
     *
     * Re-runs when the target [programId] / scratch mode differs (defensive if the ViewModel
     * is ever activity-scoped). Always clears a stale discard sheet on entry.
     */
    fun configureEntry(
        startInScratch: Boolean,
        finishOnboarding: Boolean,
        programId: String? = null,
    ) {
        val sameEntry =
            entryConfigured &&
                configuredProgramId == programId &&
                configuredStartInScratch == startInScratch
        if (sameEntry) {
            // Stale showDiscardConfirm can survive a previous back-confirm if the VM was reused.
            if (_uiState.value.showDiscardConfirm) {
                _uiState.update { it.copy(showDiscardConfirm = false) }
            }
            return
        }
        entryConfigured = true
        configuredProgramId = programId
        configuredStartInScratch = startInScratch
        this.finishOnboarding = finishOnboarding
        when {
            programId != null -> {
                viewModelScope.launch {
                    // Wait for a non-empty catalog so lift names/compound flags resolve on first paint.
                    observeLiftCatalog().first { lifts -> lifts.isNotEmpty() }
                    val program = getProgramById(ProgramId(programId))
                    if (program != null) {
                        loadProgramIntoEditor(program, markDirty = false, isExisting = true)
                    } else {
                        _uiState.update {
                            it.copy(
                                loadErrorMessage = "Program not found.",
                                step = CreateProgramStep.ChoosePath,
                                showDiscardConfirm = false,
                            )
                        }
                    }
                }
            }
            startInScratch -> selectScratchPath()
            else ->
                // Fresh create path: clear any leftover dialog from a reused VM.
                _uiState.update { it.copy(showDiscardConfirm = false) }
        }
    }

    fun selectGeneratePath() {
        loadedProgramMeta = null
        _uiState.update {
            it.copy(
                path = CreateProgramPath.Generate,
                step = CreateProgramStep.Specialization,
            )
        }
    }

    fun selectScratchPath() {
        loadedProgramMeta = null
        val restId = UUID.randomUUID().toString()
        _uiState.update {
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

    fun onMuscleToggled(label: String) {
        _uiState.update { state ->
            val selected = state.specializationDraft.selectedMuscleLabels.toMutableSet()
            if (label in selected) {
                selected.remove(label)
            } else if (selected.size < TrainingSetupValidationConstants.MAX_SPECIALIZE) {
                selected.add(label)
            }
            state.copy(
                specializationDraft = state.specializationDraft.copy(selectedMuscleLabels = selected),
            )
        }
    }

    fun onMinimumVolumeMuscleToggled(label: String) {
        _uiState.update { state ->
            val selected = state.minimumVolumeDraft.selectedMuscleLabels.toMutableSet()
            if (label in selected) {
                selected.remove(label)
            } else if (selected.size < TrainingSetupValidationConstants.MAX_MINIMUM_VOLUME) {
                selected.add(label)
            }
            state.copy(
                minimumVolumeDraft = state.minimumVolumeDraft.copy(selectedMuscleLabels = selected),
            )
        }
    }

    fun onSuggestedMinimumVolumeToggled(label: String) = onMinimumVolumeMuscleToggled(label)

    fun onWeekdayToggled(shortLabel: String) {
        _uiState.update { state ->
            val selected = state.scheduleDraft.selectedWeekdays.toMutableSet()
            if (shortLabel in selected) selected.remove(shortLabel) else selected.add(shortLabel)
            state.copy(scheduleDraft = state.scheduleDraft.copy(selectedWeekdays = selected))
        }
    }

    fun onSessionPresetSelected(key: String?) {
        _uiState.update { state ->
            state.copy(scheduleDraft = state.scheduleDraft.copy(sessionLengthSelection = key))
        }
    }

    fun onCustomSessionMinutesChange(minutes: Int) {
        _uiState.update { state ->
            state.copy(scheduleDraft = state.scheduleDraft.copy(customSessionMinutes = minutes))
        }
    }

    fun nextFromPreferences() {
        when (_uiState.value.step) {
            CreateProgramStep.Specialization ->
                _uiState.update { it.copy(step = CreateProgramStep.MinimumVolume) }
            CreateProgramStep.MinimumVolume ->
                _uiState.update { it.copy(step = CreateProgramStep.Schedule) }
            CreateProgramStep.Schedule -> generate()
            else -> Unit
        }
    }

    fun back() {
        val state = _uiState.value
        when (state.step) {
            CreateProgramStep.ChoosePath -> Unit
            CreateProgramStep.Specialization ->
                _uiState.update { it.copy(step = CreateProgramStep.ChoosePath, path = null) }
            CreateProgramStep.MinimumVolume ->
                _uiState.update { it.copy(step = CreateProgramStep.Specialization) }
            CreateProgramStep.Schedule ->
                _uiState.update { it.copy(step = CreateProgramStep.MinimumVolume) }
            CreateProgramStep.Generating -> Unit
            // Editor back is owned by CreateProgramRoute (needs nav pop on clean leave).
            CreateProgramStep.Editor -> Unit
        }
    }

    /**
     * Shows discard confirmation when leaving the editor with something to lose.
     * Clean library edits ([isEditingExisting] + not dirty) do not prompt — the route should pop.
     *
     * @return true if the caller should navigate away immediately; false if a confirm UI was shown.
     */
    fun requestDiscard(): Boolean {
        val state = _uiState.value
        if (state.step != CreateProgramStep.Editor) {
            return true
        }
        // Loaded library program, no local edits — leave without a sheet.
        if (state.isEditingExisting && !state.isDirty) {
            return true
        }
        // New draft or dirty edit — confirm before leaving.
        _uiState.update { it.copy(showDiscardConfirm = true) }
        return false
    }

    fun dismissDiscard() {
        _uiState.update { it.copy(showDiscardConfirm = false) }
    }

    fun generate() {
        if (_uiState.value.generateState == CreateProgramGenerateState.Loading) return
        val schedule = _uiState.value.scheduleDraft
        if (schedule.selectedWeekdays.isEmpty() || schedule.resolvedSessionMinutes == null) {
            _uiState.update {
                it.copy(scheduleDraft = it.scheduleDraft.copy(showErrors = true))
            }
            return
        }
        _uiState.update {
            it.copy(
                step = CreateProgramStep.Generating,
                generateState = CreateProgramGenerateState.Loading,
                generateErrorMessage = null,
                generateProgress =
                    ProgramGenerationProgress(ProgramGenerationProgressPhase.PreparingPayload).toUi(),
                generateMaxTimelineIndex = 0,
            )
        }
        generateJob?.cancel()
        generateJob =
            viewModelScope.launch {
                try {
                    flushPreferencesForGeneration()
                    val result =
                        generateProgram { progress ->
                            withContext(Dispatchers.Main.immediate) {
                                val ui = progress.toUi()
                                val index = ui.phase.activeTimelineIndex()
                                _uiState.update { state ->
                                    state.copy(
                                        generateProgress = ui,
                                        generateMaxTimelineIndex =
                                            maxOf(state.generateMaxTimelineIndex, index),
                                    )
                                }
                            }
                        }
                    val program =
                        result.getOrElse { error ->
                            _uiState.update {
                                it.copy(
                                    generateState = CreateProgramGenerateState.Error,
                                    generateErrorMessage = error.message ?: "Generation failed",
                                    step = CreateProgramStep.Schedule,
                                )
                            }
                            return@launch
                        }
                    loadProgramIntoEditor(program)
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (error: Exception) {
                    _uiState.update {
                        it.copy(
                            generateState = CreateProgramGenerateState.Error,
                            generateErrorMessage = error.message,
                            step = CreateProgramStep.Schedule,
                        )
                    }
                }
            }
    }

    fun selectDay(dayId: String) {
        _uiState.update { it.copy(selectedDayId = dayId) }
    }

    fun addDay() {
        val id = UUID.randomUUID().toString()
        _uiState.update { state ->
            val position = state.days.size
            val day =
                CreateProgramDayUi(
                    id = id,
                    name = "Rest",
                    position = position,
                    isRest = true,
                    editState = null,
                )
            state.copy(
                days = state.days + day,
                selectedDayId = id,
                isDirty = true,
            )
        }
    }

    fun removeSelectedDay() {
        val selectedId = _uiState.value.selectedDayId ?: return
        _uiState.update { state ->
            if (state.days.size <= 1) return@update state
            val remaining =
                state.days
                    .filterNot { day -> day.id == selectedId }
                    .mapIndexed { index, day -> day.copy(position = index) }
            state.copy(
                days = remaining,
                selectedDayId = remaining.firstOrNull()?.id,
                isDirty = true,
            ).withEditorDerivedMetrics()
        }
    }

    fun reorderDays(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            if (fromIndex !in state.days.indices || toIndex !in state.days.indices) return@update state
            val mutable = state.days.toMutableList()
            val moved = mutable.removeAt(fromIndex)
            mutable.add(toIndex, moved)
            state.copy(
                days = mutable.mapIndexed { index, day -> day.copy(position = index) },
                isDirty = true,
            )
        }
    }

    fun showRenameProgram(show: Boolean) {
        _uiState.update { it.copy(showRenameProgram = show) }
    }

    fun showRenameWorkout(show: Boolean) {
        _uiState.update { it.copy(showRenameWorkout = show) }
    }

    fun renameProgram(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        _uiState.update {
            it.copy(programName = trimmed, showRenameProgram = false, isDirty = true)
        }
    }

    fun renameSelectedWorkout(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val selectedId = _uiState.value.selectedDayId ?: return
        _uiState.update { state ->
            state.copy(
                days =
                    state.days.map { day ->
                        if (day.id != selectedId) {
                            day
                        } else {
                            day.copy(
                                name = trimmed,
                                isRest = false,
                                editState =
                                    day.editState?.copy(workoutName = trimmed)
                                        ?: emptyWorkoutEditState(day.id, trimmed),
                            )
                        }
                    },
                showRenameWorkout = false,
                isDirty = true,
            )
        }
    }

    fun addExerciseToSelectedDay(
        onNavigateToPicker: (workoutId: String, workoutLiftId: String?, liftId: String?, liftName: String?) -> Unit,
    ) {
        convertSelectedRestToWorkoutIfNeeded()
        val day = _uiState.value.selectedDay ?: return
        val edit = day.editState ?: return
        onNavigateToPicker(edit.workoutId, null, null, null)
    }

    fun updateSelectedWorkout(transform: (EditWorkoutUiState.Loaded) -> EditWorkoutUiState.Loaded) {
        val selectedId = _uiState.value.selectedDayId ?: return
        _uiState.update { state ->
            state.copy(
                days =
                    state.days.map { day ->
                        if (day.id != selectedId || day.editState == null) {
                            day
                        } else {
                            day.copy(editState = transform(day.editState), isRest = false)
                        }
                    },
                isDirty = true,
            ).withEditorDerivedMetrics()
        }
    }

    fun startReorderLifts(onNavigate: (workoutId: String) -> Unit) {
        val day = _uiState.value.selectedDay ?: return
        val edit = day.editState ?: return
        val items = edit.lifts.map { com.stratum.domain.workout.ReorderLiftItem(it.workoutLiftId, it.name) }
        workoutEditSession.setReorderLifts(items)
        onNavigate(edit.workoutId)
    }

    fun saveToLibrary(onDone: () -> Unit) {
        persist(mode = PersistMode.SaveToLibrary, onDone = onDone)
    }

    fun saveChanges(onDone: () -> Unit) {
        persist(mode = PersistMode.SavePreservingActive, onDone = onDone)
    }

    fun activate(onDone: () -> Unit) {
        persist(mode = PersistMode.Activate, onDone = onDone)
    }

    private enum class PersistMode {
        SaveToLibrary,
        SavePreservingActive,
        Activate,
    }

    private fun persist(mode: PersistMode, onDone: () -> Unit) {
        val state = _uiState.value
        if (!state.canSave || state.isSaving) {
            _uiState.update {
                it.copy(saveErrorMessage = if (!state.canSave) "Add at least one workout with an exercise" else null)
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveErrorMessage = null) }
            val draft = state.toDomainProgram(loadedProgramMeta)
            val result =
                when (mode) {
                    PersistMode.Activate -> activateProgram(draft.copy(isActive = true))
                    PersistMode.SavePreservingActive -> updateExistingProgram(draft)
                    PersistMode.SaveToLibrary ->
                        if (state.isEditingExisting) {
                            // Library edit: stay inactive without demoting anything else.
                            updateExistingProgram(draft.copy(isActive = false))
                        } else {
                            saveProgramToLibrary(draft)
                        }
                }
            result
                .onSuccess {
                    if (finishOnboarding && mode == PersistMode.Activate) {
                        // First-run path: only Activate completes onboarding (user must have a
                        // training program). Save to Library alone leaves them on this screen.
                        completeOnboarding(getUserTrainingProfile())
                    }
                    if (mode == PersistMode.Activate) {
                        loadedProgramMeta = draft.copy(isActive = true).toLoadedProgramMeta()
                    } else if (mode == PersistMode.SavePreservingActive) {
                        loadedProgramMeta = draft.toLoadedProgramMeta()
                    } else if (state.isEditingExisting) {
                        loadedProgramMeta = draft.copy(isActive = false).toLoadedProgramMeta()
                    }
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            isDirty = false,
                            originalIsActive =
                                when (mode) {
                                    PersistMode.Activate -> true
                                    PersistMode.SaveToLibrary -> false
                                    PersistMode.SavePreservingActive -> it.originalIsActive
                                },
                        )
                    }
                    if (!finishOnboarding || mode == PersistMode.Activate) {
                        onDone()
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            saveErrorMessage = error.message ?: "Couldn't save program. Try again.",
                        )
                    }
                }
        }
    }

    private suspend fun flushPreferencesForGeneration() {
        val state = _uiState.value
        val existing = getUserTrainingProfile()
        val lastTrained = inferLastTrained(fallback = existing.lastTrained)
        val weekdays =
            state.scheduleDraft.selectedWeekdays.mapNotNull { label -> weekdayFromShort(label) }.toSet()
        val minutes = state.scheduleDraft.resolvedSessionMinutes
        updateUserTrainingProfile(
            existing.copy(
                lastTrained = lastTrained,
                weekdays = weekdays,
                sessionLengthMinutes = minutes,
            ),
        )
        val specialized =
            state.specializationDraft.selectedMuscleLabels.mapNotNull { label -> muscleFromLabel(label) }
        val minimum =
            state.minimumVolumeDraft.selectedMuscleLabels.mapNotNull { label -> muscleFromLabel(label) }
        val levels =
            buildMap {
                specialized.forEach { muscle -> put(muscle, SpecializationLevel.Specialize) }
                minimum.forEach { muscle -> put(muscle, SpecializationLevel.MinimumVolume) }
            }
        updateSpecialization(SpecializationPreference(levels = levels))
    }

    private fun loadProgramIntoEditor(
        program: Program,
        markDirty: Boolean = true,
        isExisting: Boolean = false,
    ) {
        val days = program.toCreateProgramDays(liftNamesById, compoundByLiftId)
        loadedProgramMeta = program.toLoadedProgramMeta()
        _uiState.update {
            it.copy(
                step = CreateProgramStep.Editor,
                path = CreateProgramPath.Scratch,
                generateState = CreateProgramGenerateState.Idle,
                programId = program.id.value,
                programName = program.name,
                days = days,
                selectedDayId = days.firstOrNull { day -> !day.isRest }?.id ?: days.firstOrNull()?.id,
                isDirty = markDirty,
                isEditingExisting = isExisting,
                originalIsActive = program.isActive,
                loadErrorMessage = null,
                // Never carry a discard sheet into a freshly loaded editor.
                showDiscardConfirm = false,
            ).withEditorDerivedMetrics()
        }
    }

    private var loadedProgramMeta: LoadedProgramMeta? = null

    private fun convertSelectedRestToWorkoutIfNeeded() {
        val state = _uiState.value
        val selected = state.selectedDay ?: return
        if (!selected.isRest && selected.editState != null) return
        val name =
            if (selected.isRest || selected.name == "Rest") {
                "Workout ${state.days.count { !it.isRest } + 1}"
            } else {
                selected.name
            }
        _uiState.update { current ->
            current.copy(
                days =
                    current.days.map { day ->
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
            weekdayLabel = "",
            lifts = emptyList(),
            isDirty = true,
        )

    private fun handleLiftSelected(workoutId: String, workoutLiftId: String?, liftId: String) {
        val catalog = liftCatalog.firstOrNull { it.id.value == liftId }
        val liftName = catalog?.name ?: liftNamesById[liftId] ?: PLACEHOLDER_LIFT_NAME
        val isCompound = catalog?.isCompound ?: compoundByLiftId[liftId] ?: false
        val restSeconds = catalog?.resolvedDefaultRestSeconds() ?: DEFAULT_REST_SECONDS
        _uiState.update { state ->
            state.copy(
                days =
                    state.days.map { day ->
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

    private fun handleLiftsSelected(workoutId: String, liftIds: List<String>, asSuperset: Boolean) {
        if (liftIds.isEmpty()) return
        val groupId =
            if (asSuperset && liftIds.size >= 2) {
                newSupersetGroupId()
            } else {
                null
            }
        _uiState.update { state ->
            state.copy(
                days =
                    state.days.map { day ->
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

    private fun handleLiftsReordered(workoutId: String, orderedIds: List<String>) {
        _uiState.update { state ->
            state.copy(
                days =
                    state.days.map { day ->
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

    /**
     * Recomputes per-day workout volume, program-scoped volume, and duration estimates so the
     * editor chrome (volume row + summary header) stays live while drafting.
     */
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

    private fun weekdayFromShort(label: String): Weekday? =
        when (label) {
            "Mon" -> Weekday.Monday
            "Tue" -> Weekday.Tuesday
            "Wed" -> Weekday.Wednesday
            "Thu" -> Weekday.Thursday
            "Fri" -> Weekday.Friday
            "Sat" -> Weekday.Saturday
            "Sun" -> Weekday.Sunday
            else -> null
        }

    private fun muscleFromLabel(label: String): MuscleGroup? =
        MuscleGroup.entries.firstOrNull { muscle -> muscle.displayLabel == label }

}
