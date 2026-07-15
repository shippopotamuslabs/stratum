package com.stratum.feature.program

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.onboarding.CompleteOnboardingUseCase
import com.stratum.domain.onboarding.GetUserTrainingProfileUseCase
import com.stratum.domain.onboarding.UpdateSpecializationUseCase
import com.stratum.domain.onboarding.UpdateUserTrainingProfileUseCase
import com.stratum.domain.program.ActivateProgramUseCase
import com.stratum.domain.program.ComputeWorkoutMuscleVolumeUseCase
import com.stratum.domain.program.EstimateWorkoutDurationMinutesUseCase
import com.stratum.domain.program.GenerateProgramUseCase
import com.stratum.domain.program.GetProgramByIdUseCase
import com.stratum.domain.program.InferLastTrainedBucketUseCase
import com.stratum.domain.program.SaveProgramToLibraryUseCase
import com.stratum.domain.program.UpdateExistingProgramUseCase
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.training.AbandonTrainingSessionUseCase
import com.stratum.domain.training.ObserveActiveSessionUseCase
import com.stratum.domain.workout.EditSessionEvent
import com.stratum.domain.workout.WorkoutEditSession
import com.stratum.feature.program.ui.EditWorkoutUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Thin façade for create-program: nav entry + shared [uiState].
 *
 * Product surfaces live in:
 * - [CreateProgramGenerateController] — prefs + generate
 * - [CreateProgramEditorController] — day/lift editor + save/activate
 */
@HiltViewModel
@Suppress("TooManyFunctions") // Thin route façade: delegates generate + editor controller APIs.
class CreateProgramViewModel @Inject constructor(
    generateProgram: GenerateProgramUseCase,
    activateProgram: ActivateProgramUseCase,
    saveProgramToLibrary: SaveProgramToLibraryUseCase,
    updateExistingProgram: UpdateExistingProgramUseCase,
    private val getProgramById: GetProgramByIdUseCase,
    updateUserTrainingProfile: UpdateUserTrainingProfileUseCase,
    updateSpecialization: UpdateSpecializationUseCase,
    getUserTrainingProfile: GetUserTrainingProfileUseCase,
    completeOnboarding: CompleteOnboardingUseCase,
    inferLastTrained: InferLastTrainedBucketUseCase,
    private val observeLiftCatalog: ObserveLiftCatalogUseCase,
    computeWorkoutMuscleVolume: ComputeWorkoutMuscleVolumeUseCase,
    estimateWorkoutDurationMinutes: EstimateWorkoutDurationMinutesUseCase,
    private val workoutEditSession: WorkoutEditSession,
    observeActiveSession: ObserveActiveSessionUseCase,
    abandonTrainingSession: AbandonTrainingSessionUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CreateProgramUiState(programId = UUID.randomUUID().toString()),
    )
    val uiState: StateFlow<CreateProgramUiState> = _uiState.asStateFlow()

    private var finishOnboarding: Boolean = false
    private var entryConfigured: Boolean = false
    private var configuredProgramId: String? = null
    private var configuredStartInScratch: Boolean = false

    private val activeSessionGuard =
        CreateProgramActiveSessionGuard(
            state = _uiState,
            observeActiveSession = observeActiveSession,
            abandonTrainingSession = abandonTrainingSession,
        )

    private val editor =
        CreateProgramEditorController(
            state = _uiState,
            scope = viewModelScope,
            activateProgram = activateProgram,
            saveProgramToLibrary = saveProgramToLibrary,
            updateExistingProgram = updateExistingProgram,
            completeOnboarding = completeOnboarding,
            getUserTrainingProfile = getUserTrainingProfile,
            computeWorkoutMuscleVolume = computeWorkoutMuscleVolume,
            estimateWorkoutDurationMinutes = estimateWorkoutDurationMinutes,
            workoutEditSession = workoutEditSession,
            activeSessionGuard = activeSessionGuard,
            finishOnboarding = { finishOnboarding },
        )

    private val generate =
        CreateProgramGenerateController(
            state = _uiState,
            scope = viewModelScope,
            generateProgram = generateProgram,
            updateUserTrainingProfile = updateUserTrainingProfile,
            updateSpecialization = updateSpecialization,
            getUserTrainingProfile = getUserTrainingProfile,
            inferLastTrained = inferLastTrained,
            onProgramGenerated = { program ->
                editor.loadProgramIntoEditor(program, markDirty = true, isExisting = false)
            },
        )

    init {
        viewModelScope.launch {
            observeLiftCatalog().collect { lifts ->
                editor.liftCatalog = lifts
                editor.liftNamesById = lifts.associate { lift -> lift.id.value to lift.name }
                editor.compoundByLiftId =
                    lifts.associate { lift -> lift.id.value to lift.isCompound }
                editor.refreshDerivedMetrics()
            }
        }
        viewModelScope.launch {
            workoutEditSession.events.collect { event ->
                when (event) {
                    is EditSessionEvent.LiftSelected ->
                        editor.handleLiftSelected(
                            event.workoutId,
                            event.workoutLiftId,
                            event.liftId,
                        )
                    is EditSessionEvent.LiftsSelected ->
                        editor.handleLiftsSelected(
                            event.workoutId,
                            event.liftIds,
                            event.asSuperset,
                        )
                    is EditSessionEvent.LiftsReordered ->
                        editor.handleLiftsReordered(
                            event.workoutId,
                            event.orderedWorkoutLiftIds,
                        )
                }
            }
        }
    }

    /**
     * Applies nav-entry options once (scratch jump / onboarding completion / edit existing).
     * Safe to call from [LaunchedEffect] when the route is composed.
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
                    observeLiftCatalog().first { lifts -> lifts.isNotEmpty() }
                    val program = getProgramById(ProgramId(programId))
                    if (program != null) {
                        editor.loadProgramIntoEditor(program, markDirty = false, isExisting = true)
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
            startInScratch -> editor.selectScratchPath()
            else -> _uiState.update { it.copy(showDiscardConfirm = false) }
        }
    }

    // --- Generate path ---

    fun selectGeneratePath() {
        editor.clearLoadedMeta()
        generate.selectGeneratePath()
    }

    fun onMuscleToggled(label: String) = generate.onMuscleToggled(label)

    fun onMinimumVolumeMuscleToggled(label: String) = generate.onMinimumVolumeMuscleToggled(label)

    fun onSuggestedMinimumVolumeToggled(label: String) = onMinimumVolumeMuscleToggled(label)

    fun onTrainingDayCountChanged(count: Int) = generate.onTrainingDayCountChanged(count)

    fun onSessionPresetSelected(key: String?) = generate.onSessionPresetSelected(key)

    fun onCustomSessionMinutesChange(minutes: Int) = generate.onCustomSessionMinutesChange(minutes)

    fun onSixDayCycleLengthSelected(cycleLength: Int) =
        generate.onSixDayCycleLengthSelected(cycleLength)

    fun onTrainingSlotToggled(slotIndex: Int) = generate.onTrainingSlotToggled(slotIndex)

    fun nextFromPreferences() = generate.nextFromPreferences()

    fun generate() = generate.generate()

    // --- Editor path ---

    fun selectScratchPath() = editor.selectScratchPath()

    fun selectDay(dayId: String) = editor.selectDay(dayId)

    fun addDay() = editor.addDay()

    fun removeSelectedDay() = editor.removeSelectedDay()

    fun reorderDaysByIds(orderedDayIds: List<String>) = editor.reorderDaysByIds(orderedDayIds)

    fun showRenameProgram(show: Boolean) = editor.showRenameProgram(show)

    fun showRenameWorkout(show: Boolean) = editor.showRenameWorkout(show)

    fun renameProgram(name: String) = editor.renameProgram(name)

    fun renameSelectedWorkout(name: String) = editor.renameSelectedWorkout(name)

    fun addExerciseToSelectedDay(
        onNavigateToPicker: (workoutId: String, workoutLiftId: String?, liftId: String?, liftName: String?) -> Unit,
    ) = editor.addExerciseToSelectedDay(onNavigateToPicker)

    fun updateSelectedWorkout(transform: (EditWorkoutUiState.Loaded) -> EditWorkoutUiState.Loaded) =
        editor.updateSelectedWorkout(transform)

    fun startReorderLifts(onNavigate: (workoutId: String) -> Unit) =
        editor.startReorderLifts(onNavigate)

    fun saveToLibrary(onDone: () -> Unit) =
        editor.persist(CreateProgramPersistMode.SaveToLibrary, onDone)

    fun saveChanges(onDone: () -> Unit) =
        editor.persist(CreateProgramPersistMode.SavePreservingActive, onDone)

    fun activate(onDone: () -> Unit) =
        editor.persist(CreateProgramPersistMode.Activate, onDone)

    fun requestDiscard(): Boolean = editor.requestDiscard()

    fun dismissDiscard() = editor.dismissDiscard()

    fun confirmActiveSessionDiscard() {
        viewModelScope.launch {
            when (activeSessionGuard.confirmAbandon()) {
                ActiveSessionDiscardReason.RemoveSelectedDay -> editor.applyRemoveSelectedDay()
                ActiveSessionDiscardReason.SaveChanges,
                ActiveSessionDiscardReason.Activate,
                ActiveSessionDiscardReason.SaveToLibrary,
                -> {
                    // Session already abandoned — re-enter persist without another confirm.
                    val pending = activeSessionGuard.takePendingPersist()
                    if (pending != null) {
                        editor.persist(mode = pending.first, onDone = pending.second)
                    }
                }
                null -> Unit
            }
        }
    }

    fun dismissActiveSessionDiscard() = activeSessionGuard.dismiss()

    fun back() {
        when (_uiState.value.step) {
            CreateProgramStep.ChoosePath -> Unit
            CreateProgramStep.Specialization,
            CreateProgramStep.MinimumVolume,
            CreateProgramStep.Schedule,
            -> generate.backFromPreferences()
            CreateProgramStep.Generating -> Unit
            // Editor back is owned by CreateProgramRoute (needs nav pop on clean leave).
            CreateProgramStep.Editor -> Unit
        }
    }
}
