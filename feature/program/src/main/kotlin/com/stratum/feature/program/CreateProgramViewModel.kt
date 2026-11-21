package com.stratum.feature.program

import com.stratum.core.infrastructure.telemetry.ProgramGenerationTelemetryLogger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.application.lift.ObserveLiftCatalogUseCase
import com.stratum.core.infrastructure.telemetry.SynthesisTelemetry
import com.stratum.core.infrastructure.telemetry.TelemetryRouter
import com.stratum.application.onboarding.CompleteOnboardingUseCase
import com.stratum.application.onboarding.GetUserTrainingProfileUseCase
import com.stratum.application.onboarding.UpdateSpecializationUseCase
import com.stratum.application.onboarding.UpdateUserTrainingProfileUseCase
import com.stratum.application.program.ActivateProgramUseCase
import com.stratum.application.program.ComputeWorkoutMuscleVolumeUseCase
import com.stratum.application.program.EstimateWorkoutDurationMinutesUseCase
import com.stratum.application.program.GenerateProgramUseCase
import com.stratum.application.program.GetProgramByIdUseCase
import com.stratum.application.program.InferLastTrainedBucketUseCase
import com.stratum.application.program.SaveProgramToLibraryUseCase
import com.stratum.application.program.UpdateExistingProgramUseCase
import com.stratum.domain.program.identity.ProgramId
import com.stratum.application.training.AbandonTrainingSessionUseCase
import com.stratum.application.training.ObserveActiveSessionUseCase
import com.stratum.core.ui.component.SupersetLinkDirection
import com.stratum.domain.workout.EditSessionEvent
import com.stratum.domain.workout.WorkoutEditSession
import com.stratum.feature.program.ui.EditLiftState
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
// STR-216: STR-207 §2 exception — `userMessage` transient-snackbar side-flow
// alongside `uiState`; bless-vs-migrate decision recorded on STR-216.
@Suppress("LongParameterList", "TooManyFunctions", "ForbiddenMultipleStateFlowInViewModel")
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
    logAnalytics: AnalyticsRecorder,
    telemetryRouter: TelemetryRouter,
    diagnosticDrain: DiagnosticDrain,
    generationLogger: ProgramGenerationTelemetryLogger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CreateProgramUiState(programId = UUID.randomUUID().toString()),
    )
    val uiState: StateFlow<CreateProgramUiState> = _uiState.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    /** Bumps only when a new sheet undo snackbar is published, not when it is dismissed. */
    private val _sheetUndoEpoch = MutableStateFlow(0)
    val sheetUndoEpoch: StateFlow<Int> = _sheetUndoEpoch.asStateFlow()

    private val _sheetClipboard = MutableStateFlow<ProgramSheetClipboard?>(null)

    /** Sheet tray. Not on [CreateProgramUiState], so save and [toDomainProgram] never see it. */
    internal val sheetClipboard: StateFlow<ProgramSheetClipboard?> = _sheetClipboard.asStateFlow()

    private var sheetLiftUndo: ProgramSheetUndo? = null

    private var finishOnboarding: Boolean = false
    private var entryConfigured: Boolean = false
    private var configuredProgramId: String? = null
    private var configuredStartInScratch: Boolean = false

    private val activeSessionGuard =
        CreateProgramActiveSessionGuard(
            state = _uiState,
            observeActiveSession = observeActiveSession,
            abandonTrainingSession = abandonTrainingSession,
            diagnosticDrain = diagnosticDrain,
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
            diagnosticDrain = diagnosticDrain,
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
            logAnalytics = logAnalytics,
            synthesisTelemetry = SynthesisTelemetry(logAnalytics::invoke),
            telemetryRouter = telemetryRouter,
            generationLogger = generationLogger,
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

    fun showSheet() = editor.showSheet()

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

    // Embedded-editor mutations: pure [EditWorkoutLiftMutations] via controller apply path
    // (kept on the façade, not the editor controller, to stay under TooManyFunctions).

    fun removeSelectedLift(workoutLiftId: String) {
        editor.updateSelectedWorkout { loaded ->
            EditWorkoutLiftMutations.removeLift(loaded, workoutLiftId)
        }
    }

    fun userMessageShown() {
        _userMessage.value = null
    }

    /** Sheet cut. Removes immediately. A pending cut is put back first, unless its day is gone. Not an undo entry. */
    fun beginSheetCut(dayId: String, workoutLiftId: String) {
        if (!returnPendingCut()) return
        val days = _uiState.value.days
        val day = days.firstOrNull { candidate -> candidate.id == dayId } ?: return
        val edit = day.editState ?: return
        if (day.isRest) return
        val index = edit.lifts.indexOfFirst { lift -> lift.workoutLiftId == workoutLiftId }
        if (index < 0) return
        val next =
            days.map { candidate ->
                if (candidate.id != dayId) {
                    candidate
                } else {
                    candidate.copy(
                        editState =
                            edit.copy(
                                lifts = removeLiftAndCleanupSupersets(edit.lifts, workoutLiftId),
                                isDirty = true,
                            ),
                    )
                }
            }
        editor.applyEditorDays(next)
        _sheetClipboard.value =
            ProgramSheetClipboard(
                kind = ProgramSheetClipboardKind.Cut,
                fromDayId = dayId,
                lift = edit.lifts[index],
                originalIndex = index,
                sourceLiftsBefore = edit.lifts,
            )
    }

    /** Sheet copy. Holds a snapshot and does not change the workout. A pending cut is put back first. */
    fun beginSheetCopy(dayId: String, workoutLiftId: String) {
        if (!returnPendingCut()) return
        val edit =
            _uiState.value.days.firstOrNull { candidate -> candidate.id == dayId }?.editState
                ?: return
        val index = edit.lifts.indexOfFirst { lift -> lift.workoutLiftId == workoutLiftId }
        if (index < 0) return
        _sheetClipboard.value =
            ProgramSheetClipboard(
                kind = ProgramSheetClipboardKind.Copy,
                fromDayId = dayId,
                lift = edit.lifts[index],
                originalIndex = index,
                sourceLiftsBefore = edit.lifts,
            )
    }

    /** Cancel. A cut is inserted back into the current list. A copy only clears the tray. Not an undo entry. */
    fun cancelSheetClipboard() {
        returnPendingCut()
    }

    fun pasteSheetLift(toDayId: String, message: String) {
        val clip = _sheetClipboard.value ?: return
        val days = _uiState.value.days
        val target = days.firstOrNull { day -> day.id == toDayId } ?: return
        val targetEdit = target.editState ?: return
        if (target.isRest) return
        var pastedWorkoutLiftId = clip.lift.workoutLiftId
        val next =
            when (clip.kind) {
                ProgramSheetClipboardKind.Cut ->
                    moveProgramSheetLift(
                        days = days,
                        fromDayId = clip.fromDayId,
                        toDayId = toDayId,
                        workoutLiftId = clip.lift.workoutLiftId,
                        targetPosition = targetEdit.lifts.size,
                        detachedLift = clip.lift,
                    )
                ProgramSheetClipboardKind.Copy ->
                    copyProgramSheetLift(
                        days = days,
                        fromDayId = clip.fromDayId,
                        toDayId = toDayId,
                        workoutLiftId = clip.lift.workoutLiftId,
                        targetPosition = targetEdit.lifts.size,
                        sourceLift = clip.lift,
                        newWorkoutLiftId = {
                            newWorkoutLiftId().also { generated -> pastedWorkoutLiftId = generated }
                        },
                    )
            }
        if (next === days) return
        _sheetClipboard.value = null
        sheetLiftUndo = sheetPasteUndo(clip, toDayId, pastedWorkoutLiftId)
        publishSheetUndo(publishSheetUndoMessage(currentEpoch = _sheetUndoEpoch.value, message = message))
        editor.applyEditorDays(next)
    }

    /**
     * Sheet delete. Records the lift and its index for Undo, then the existing remove.
     * Tabbed delete does not call this, so it does not gain a snackbar.
     */
    fun removeSheetLift(dayId: String, workoutLiftId: String, message: String) {
        val day = _uiState.value.days.firstOrNull { candidate -> candidate.id == dayId } ?: return
        val lifts = day.editState?.lifts ?: return
        val index = lifts.indexOfFirst { lift -> lift.workoutLiftId == workoutLiftId }
        if (day.isRest || index < 0) return
        sheetLiftUndo = sheetDeleteUndo(dayId, lifts[index], index, lifts)
        publishSheetUndo(publishSheetUndoMessage(currentEpoch = _sheetUndoEpoch.value, message = message))
        editor.selectDay(dayId)
        removeSelectedLift(workoutLiftId)
    }

    fun undoSheetLiftChange() {
        val decision =
            consumeSheetUndo(
                isSaving = _uiState.value.isSaving,
                undo = sheetLiftUndo,
                days = _uiState.value.days,
            )
        if (!decision.consumed) return
        sheetLiftUndo = null
        _userMessage.value = null
        val restored = decision.days
        if (restored != null) {
            editor.applyEditorDays(restored)
        }
        val clip = _sheetClipboard.value ?: return
        val cutLiftReturned =
            clip.kind == ProgramSheetClipboardKind.Cut &&
                _uiState.value.days.any { day ->
                    day.editState?.lifts?.any { lift -> lift.workoutLiftId == clip.lift.workoutLiftId } == true
                }
        if (cutLiftReturned) {
            _sheetClipboard.value = null
        }
    }

    fun dismissSheetLiftUndo() {
        sheetLiftUndo = null
        _userMessage.value = null
    }

    /**
     * Puts a pending cut back into the current day. Returns false when that day is
     * gone so the clipboard stays and the lift can still be pasted.
     */
    private fun returnPendingCut(): Boolean {
        val clip = _sheetClipboard.value ?: return true
        if (clip.kind != ProgramSheetClipboardKind.Cut) {
            _sheetClipboard.value = null
            return true
        }
        val days = _uiState.value.days
        val next =
            returnCutLiftToCurrentDay(
                days = days,
                fromDayId = clip.fromDayId,
                lift = clip.lift,
                originalIndex = clip.originalIndex,
                sourceLiftsBefore = clip.sourceLiftsBefore,
            )
        if (next === days) return false
        _sheetClipboard.value = null
        sheetLiftUndo =
            rebaseSheetUndoForReturnedCut(
                undo = sheetLiftUndo,
                fromDayId = clip.fromDayId,
                lift = clip.lift,
                originalIndex = clip.originalIndex,
                sourceLiftsBefore = clip.sourceLiftsBefore,
            )
        editor.applyEditorDays(next)
        return true
    }

    private fun publishSheetUndo(published: SheetUndoPublish) {
        // Message first, then the epoch, so the snackbar effect reads this text.
        _userMessage.value = published.message
        _sheetUndoEpoch.value = published.epoch
    }

    fun linkSupersetWithPrevious(workoutLiftId: String) {
        editor.updateSelectedWorkout { loaded ->
            EditWorkoutLiftMutations.linkSuperset(
                loaded = loaded,
                workoutLiftId = workoutLiftId,
                direction = SupersetLinkDirection.Previous,
            )
        }
    }

    fun linkSupersetWithNext(workoutLiftId: String) {
        editor.updateSelectedWorkout { loaded ->
            EditWorkoutLiftMutations.linkSuperset(
                loaded = loaded,
                workoutLiftId = workoutLiftId,
                direction = SupersetLinkDirection.Next,
            )
        }
    }

    fun removeFromSuperset(workoutLiftId: String) {
        editor.updateSelectedWorkout { loaded ->
            EditWorkoutLiftMutations.removeFromSuperset(loaded, workoutLiftId)
        }
    }

    fun setUseRestTimerBetweenLifts(workoutLiftId: String, enabled: Boolean) {
        editor.updateSelectedWorkout { loaded ->
            EditWorkoutLiftMutations.setUseRestTimerBetweenLifts(loaded, workoutLiftId, enabled)
        }
    }

    fun addSetToSelectedLift(workoutLiftId: String) {
        editor.updateSelectedWorkout { loaded ->
            EditWorkoutLiftMutations.addSet(loaded, workoutLiftId)
        }
    }

    fun removeSetFromSelectedWorkout(setId: String) {
        editor.updateSelectedWorkout { loaded ->
            EditWorkoutLiftMutations.removeSet(loaded, setId)
        }
    }

    fun setRepBandOnSelectedWorkout(setId: String, repMin: Int, repMax: Int) {
        editor.updateSelectedWorkout { loaded ->
            EditWorkoutLiftMutations.updateSet(loaded, setId) { set ->
                set.copy(repMin = repMin, repMax = repMax)
            }
        }
    }

    fun setRirGoalOnSelectedWorkout(setId: String, rirGoal: Int) {
        editor.updateSelectedWorkout { loaded ->
            EditWorkoutLiftMutations.updateSet(loaded, setId) { set -> set.copy(rirGoal = rirGoal) }
        }
    }

    fun setRestSecondsOnSelectedWorkout(setId: String, restSeconds: Int) {
        editor.updateSelectedWorkout { loaded ->
            EditWorkoutLiftMutations.updateSet(loaded, setId) { set ->
                set.copy(restSeconds = restSeconds)
            }
        }
    }

    fun setRestSecondsForAllSetsOnSelectedLift(workoutLiftId: String, restSeconds: Int) {
        editor.updateSelectedWorkout { loaded ->
            EditWorkoutLiftMutations.setRestSecondsForAllSetsOnLift(
                loaded = loaded,
                workoutLiftId = workoutLiftId,
                restSeconds = restSeconds,
            )
        }
    }

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
