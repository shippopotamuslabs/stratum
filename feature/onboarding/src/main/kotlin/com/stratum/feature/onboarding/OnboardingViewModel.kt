package com.stratum.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.importing.CancelHistoricalImportUseCase
import com.stratum.domain.importing.ExecuteHistoricalImportUseCase
import com.stratum.domain.importing.HistoricalImportOrchestrationEvent
import com.stratum.domain.importing.HistoricalImportResumeState
import com.stratum.domain.importing.ImportFailureKind
import com.stratum.domain.importing.ImportFileInput
import com.stratum.domain.importing.ImportProgress
import com.stratum.domain.importing.ImportProgressPhase
import com.stratum.domain.importing.RefreshPersistedImportStateUseCase
import com.stratum.domain.lift.ApplyGymPresetUseCase
import com.stratum.domain.lift.SetSelectedPresetUseCase
import com.stratum.domain.logging.LogErrorUseCase
import com.stratum.domain.onboarding.AdvanceOnboardingStepResult
import com.stratum.domain.onboarding.AdvanceOnboardingStepUseCase
import com.stratum.domain.onboarding.CompleteOnboardingUseCase
import com.stratum.domain.onboarding.GetOnboardingStepCapabilitiesUseCase
import com.stratum.domain.onboarding.GetOnboardingWizardStateUseCase
import com.stratum.domain.onboarding.OnboardingPath
import com.stratum.domain.onboarding.OnboardingStep
import com.stratum.domain.onboarding.OnboardingStepDirection
import com.stratum.domain.onboarding.OnboardingWizardState
import com.stratum.domain.onboarding.SpecializationPreference
import com.stratum.domain.onboarding.UpdateSpecializationUseCase
import com.stratum.domain.onboarding.UpdateUserTrainingProfileUseCase
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.core.ui.trainingsetup.ScheduleDraftUi
import com.stratum.core.ui.trainingsetup.withTrainingSlotToggled
import com.stratum.core.ui.trainingsetup.withSixDayCycleLengthSelected
import com.stratum.core.ui.trainingsetup.withTrainingDayCount
import com.stratum.domain.onboarding.AdditiveSetEdit
import com.stratum.domain.onboarding.ValidateMinimumVolumeStepUseCase
import com.stratum.domain.onboarding.toggleInAdditiveSetEdit
import com.stratum.domain.program.ActivateProgramUseCase
import com.stratum.domain.program.GenerateProgramUseCase
import com.stratum.domain.program.ProgramGenerationProgress
import com.stratum.domain.program.ProgramGenerationProgressPhase
import com.stratum.domain.program.importing.ImportProgramTemplateUseCase
import com.stratum.domain.program.importing.ProgramTemplateImportPhase
import com.stratum.domain.program.schedule.GenerationCycleDefaults
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
// Wizard exposes many step handlers (profile/import/generate); threshold is a product of surface width.
@Suppress("TooManyFunctions", "LargeClass")
class OnboardingViewModel @Inject constructor(
    private val getOnboardingWizardState: GetOnboardingWizardStateUseCase,
    private val advanceOnboardingStep: AdvanceOnboardingStepUseCase,
    private val getOnboardingStepCapabilities: GetOnboardingStepCapabilitiesUseCase,
    private val applyGymPreset: ApplyGymPresetUseCase,
    private val setSelectedPreset: SetSelectedPresetUseCase,
    private val executeHistoricalImport: ExecuteHistoricalImportUseCase,
    private val cancelHistoricalImport: CancelHistoricalImportUseCase,
    private val refreshPersistedImportState: RefreshPersistedImportStateUseCase,
    private val updateUserTrainingProfile: UpdateUserTrainingProfileUseCase,
    private val updateSpecialization: UpdateSpecializationUseCase,
    private val generateProgram: GenerateProgramUseCase,
    private val activateProgram: ActivateProgramUseCase,
    private val completeOnboarding: CompleteOnboardingUseCase,
    private val importProgramTemplate: ImportProgramTemplateUseCase,
    private val logError: LogErrorUseCase,
) : ViewModel() {

    private val edits = MutableStateFlow(ProfileEdit())
    private val specializationEdits = MutableStateFlow(SpecializationEdit())
    private val ephemeral = MutableStateFlow(OnboardingEphemeralState.Initial)
    private val importResumeState = MutableStateFlow<HistoricalImportResumeState?>(null)
    private var importJob: Job? = null
    private var generateJob: Job? = null
    private var importProgramJob: Job? = null

    private val wizardState: StateFlow<OnboardingWizardState> =
        getOnboardingWizardState().stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = OnboardingWizardState.Initial,
        )

    private val effectiveProfile: StateFlow<UserTrainingProfile> =
        combine(wizardState, edits) { current, edit ->
            (current.profile ?: UserTrainingProfile.Empty).applyEdit(edit)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UserTrainingProfile.Empty,
        )

    private val effectiveSpecialization: StateFlow<SpecializationPreference> =
        combine(wizardState, specializationEdits) { current, edit ->
            current.specialization.applyEdit(edit)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SpecializationPreference.Empty,
        )

    val uiState: StateFlow<OnboardingUiState> =
        combine(
            wizardState,
            effectiveProfile,
            edits,
            effectiveSpecialization,
            ephemeral,
        ) { current, profile, edit, spec, ephemeralState ->
            UiCombineInputs(current, profile, edit.sessionLength, spec, ephemeralState)
        }.combine(importResumeState) { inputs, resume ->
            val path = inputs.current.onboardingPath
            val onGenerateScheduleSurface =
                path == OnboardingPath.Generate ||
                    inputs.current.currentStep in
                    setOf(
                        OnboardingStep.Schedule,
                        OnboardingStep.Specialization,
                        OnboardingStep.MinimumVolume,
                    )
            val trainCount =
                if (onGenerateScheduleSurface) {
                    inputs.ephemeral.scheduleTrainingDayCount
                        ?: inputs.profile.generationTrainingDayCount
                        ?: ScheduleDraftUi.DEFAULT_TRAINING_DAY_COUNT
                } else {
                    null
                }
            val cycle =
                if (onGenerateScheduleSurface && trainCount != null) {
                    resolveGenerationScheduleDraft(
                        trainingDayCount = trainCount,
                        cycleLength =
                            inputs.ephemeral.generationCycleLength
                                ?: inputs.profile.generationCycleLength,
                        restSlotIndices =
                            inputs.ephemeral.generationRestSlotIndices
                                ?: inputs.profile.generationRestSlotIndices
                                    .takeIf { it.isNotEmpty() },
                    )
                } else {
                    null
                }
            val capabilities = getOnboardingStepCapabilities(
                step = inputs.current.currentStep,
                profile = inputs.profile,
                specialization = inputs.specialization,
                lifts = inputs.current.lifts,
                selectedPreset = inputs.current.selectedPreset,
                usedHistoricalImport = inputs.current.usedHistoricalImport,
                path = path,
                persistedSelectedPath = inputs.current.progress?.selectedPath,
                cycleLength = cycle?.cycleLength,
                restSlotIndices = cycle?.restSlotIndices,
                trainingDayCount = trainCount,
            )
            buildOnboardingUiState(
                current = inputs.current,
                profile = inputs.profile,
                sessionLengthEdit = inputs.sessionLengthEdit,
                specialization = inputs.specialization,
                ephemeral = inputs.ephemeral,
                capabilities = capabilities,
                importResumeScalars = resume.toResumeScalars(),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = OnboardingUiState.Initial,
        )

    init {
        viewModelScope.launch { applyRefreshPersistedImportState() }
    }

    fun onTrainingAgeSelected(label: String?) {
        edits.value = edits.value.copy(trainingAge = label?.let(::trainingAgeFromLabel))
    }

    fun onLastTrainedSelected(label: String?) {
        edits.value = edits.value.copy(lastTrained = label?.let(::lastTrainedFromLabel))
    }

    fun onTrainingDayCountChanged(count: Int) {
        val draft =
            effectiveProfile.value
                .toScheduleDraft(
                    sessionLengthEdit = edits.value.sessionLength,
                    generationCycleLength = ephemeral.value.generationCycleLength,
                    generationRestSlotIndices = ephemeral.value.generationRestSlotIndices,
                    trainingDayCount = ephemeral.value.scheduleTrainingDayCount,
                )
                .withTrainingDayCount(count)
        // Slot identity only — profile cycle-space is written on schedule flush / generate.
        ephemeral.update {
            it.copy(
                scheduleTrainingDayCount = draft.trainingDayCount,
                generationCycleLength = draft.cycleLength,
                generationRestSlotIndices = draft.restSlotIndices,
            )
        }
    }

    fun onSessionPresetSelected(value: String?) {
        ensureCycleSpaceSynced()
        edits.value = edits.value.copy(
            sessionLength = when (value) {
                null -> SessionLengthEdit.Active(minutes = null, customMode = false)
                ScheduleDraftUi.CUSTOM_KEY -> SessionLengthEdit.Active(
                    minutes = resolveCustomSessionMinutes(
                        sessionLengthEdit = edits.value.sessionLength,
                        persistedMinutes = effectiveProfile.value.sessionLengthMinutes,
                    ),
                    customMode = true,
                )
                // Band keys (20-40 / 40-60 / 60-90) → soft-aim midpoint minutes
                else -> {
                    val draft =
                        ScheduleDraftUi(sessionLengthSelection = value)
                    SessionLengthEdit.Active(
                        minutes = draft.resolvedSessionMinutes,
                        customMode = false,
                    )
                }
            },
        )
    }

    /** Seeds default cycle space (N + rests) when the user has not changed count yet. */
    private fun ensureCycleSpaceSynced() {
        val profile = effectiveProfile.value
        val count =
            ephemeral.value.scheduleTrainingDayCount
                ?: profile.generationTrainingDayCount
                ?: ScheduleDraftUi.DEFAULT_TRAINING_DAY_COUNT
        if (ephemeral.value.generationCycleLength == null &&
            profile.generationCycleLength == null
        ) {
            val draft =
                resolveGenerationScheduleDraft(
                    trainingDayCount = count,
                    cycleLength = null,
                    restSlotIndices = null,
                )
            ephemeral.update {
                it.copy(
                    scheduleTrainingDayCount = count,
                    generationCycleLength = draft.cycleLength,
                    generationRestSlotIndices = draft.restSlotIndices,
                )
            }
        }
    }

    private fun resolvedTrainCount(): Int =
        ephemeral.value.scheduleTrainingDayCount
            ?: effectiveProfile.value.generationTrainingDayCount
            ?: ScheduleDraftUi.DEFAULT_TRAINING_DAY_COUNT

    private fun resolvedCycleHints(): Pair<Int?, Set<Int>?> =
        (ephemeral.value.generationCycleLength ?: effectiveProfile.value.generationCycleLength) to
            (
                ephemeral.value.generationRestSlotIndices
                    ?: effectiveProfile.value.generationRestSlotIndices.takeIf { it.isNotEmpty() }
                )

    fun onCustomSessionMinutesChange(minutes: Int) {
        edits.value = edits.value.copy(
            sessionLength = SessionLengthEdit.Active(minutes = minutes, customMode = true),
        )
    }

    /** Toggle between 8-day rotating (default) and 7-day fixed week for 6 train days. */
    fun onSixDayCycleLengthSelected(cycleLength: Int) {
        val (lengthHint, restsHint) = resolvedCycleHints()
        val draft =
            resolveGenerationScheduleDraft(
                trainingDayCount = resolvedTrainCount(),
                cycleLength = lengthHint,
                restSlotIndices = restsHint,
            ).withSixDayCycleLengthSelected(cycleLength)
        ephemeral.update {
            it.copy(
                scheduleTrainingDayCount = resolvedTrainCount(),
                generationCycleLength = draft.cycleLength,
                generationRestSlotIndices = draft.restSlotIndices,
            )
        }
    }

    /** Toggle a training slot index within the generation cycle space. */
    fun onTrainingSlotToggled(slotIndex: Int) {
        val (lengthHint, restsHint) = resolvedCycleHints()
        val draft =
            resolveGenerationScheduleDraft(
                trainingDayCount = resolvedTrainCount(),
                cycleLength = lengthHint,
                restSlotIndices = restsHint,
            ).withTrainingSlotToggled(slotIndex)
        ephemeral.update {
            it.copy(
                scheduleTrainingDayCount = resolvedTrainCount(),
                generationCycleLength = draft.cycleLength,
                generationRestSlotIndices = draft.restSlotIndices,
            )
        }
    }

    fun onMuscleToggled(label: String) {
        val muscle = muscleGroupFromLabel(label) ?: return
        val current = effectiveSpecialization.value.specializedMuscles
        val persisted = wizardState.value.specialization.specializedMuscles
        specializationEdits.value = specializationEdits.value.copy(
            musclesEdit = toggleInAdditiveSetEdit(
                item = muscle,
                currentEffective = current,
                persistedBase = persisted,
                currentEdit = specializationEdits.value.musclesEdit,
            ),
        )
    }

    fun onMinimumVolumeMuscleToggled(label: String) {
        val muscle = muscleGroupFromLabel(label) ?: return
        if (muscle in effectiveSpecialization.value.specializedMuscles) return
        val current = effectiveSpecialization.value.minimumVolumeMuscles
        val persisted = wizardState.value.specialization.minimumVolumeMuscles
        specializationEdits.value = specializationEdits.value.copy(
            minimumVolumeMusclesEdit = toggleInAdditiveSetEdit(
                item = muscle,
                currentEffective = current,
                persistedBase = persisted,
                currentEdit = specializationEdits.value.minimumVolumeMusclesEdit,
            ),
        )
    }

    fun onSuggestedMinimumVolumeToggled(label: String) = onMinimumVolumeMuscleToggled(label)

    fun onPresetSelected(label: String?) {
        if (ephemeral.value.importProgress?.let { progress ->
                progress.phase != ImportProgressPhaseUi.Complete &&
                    progress.phase != ImportProgressPhaseUi.Failed
            } == true
        ) {
            return
        }
        val preset = gymPresetFromLabel(label)
        viewModelScope.launch {
            ephemeral.update {
                it.copy(
                    applyingPreset = true,
                    importError = null,
                    presetApplyError = null,
                )
            }
            importResumeState.value = null
            val result =
                if (preset != null) {
                    applyGymPreset(preset)
                } else {
                    setSelectedPreset(null)
                }
            ephemeral.update {
                it.copy(
                    applyingPreset = false,
                    presetApplyError = result.exceptionOrNull().toPresetApplyErrorMessage()
                        .takeIf { result.isFailure },
                )
            }
        }
    }

    fun onImportFilesSelected(files: List<ImportFileInput>) {
        launchHistoricalImport {
            importResumeState.value = null
            collectHistoricalImport(files = files, resume = null)
        }
    }

    fun onRetryImportFromFailedFile() {
        launchHistoricalImport {
            val resume = importResumeState.value?.takeIf { it.isResumable() } ?: return@launchHistoricalImport
            collectHistoricalImport(files = resume.files, resume = resume)
        }
    }

    fun onCancelImport() {
        viewModelScope.launch {
            importJob?.cancel()
            importJob = null
            importResumeState.value = null
            val refresh = cancelHistoricalImport().toEphemeralImportFields()
            ephemeral.update {
                it.copy(
                    importProgress = null,
                    importError = null,
                    importUnsupportedFile = false,
                    historicalImportMaxTimelineIndex = 0,
                    hasCompletedHistoricalImport = refresh.hasCompletedHistoricalImport,
                    importInsightsSummary = refresh.importInsightsSummary,
                )
            }
        }
    }

    private fun launchHistoricalImport(block: suspend () -> Unit) {
        importJob?.cancel()
        importJob =
            viewModelScope.launch {
                try {
                    block()
                } catch (_: CancellationException) {
                    // Stop requested — onCancelImport resets UI.
                }
            }
    }

    private suspend fun collectHistoricalImport(
        files: List<ImportFileInput>,
        resume: HistoricalImportResumeState?,
    ) {
        ephemeral.update {
            it.copy(
                importError = null,
                importUnsupportedFile = false,
                historicalImportMaxTimelineIndex = 0,
            )
        }
        executeHistoricalImport(files = files, resume = resume).collect { event ->
            when (event) {
                is HistoricalImportOrchestrationEvent.ValidationFailed ->
                    ephemeral.update {
                        it.copy(
                            importError = event.reason.toUserMessage(),
                            importUnsupportedFile = false,
                            importProgress = ImportProgress(ImportProgressPhase.Failed).toUi(),
                        )
                    }
                is HistoricalImportOrchestrationEvent.Progress -> {
                    val ui = event.progress.toUi()
                    val index = ui.phase.historicalImportTimelineIndex()
                    ephemeral.update {
                        it.copy(
                            importProgress = ui,
                            historicalImportMaxTimelineIndex =
                                maxOf(it.historicalImportMaxTimelineIndex, index),
                        )
                    }
                }
                is HistoricalImportOrchestrationEvent.Completed -> {
                    importResumeState.value = null
                    ephemeral.update {
                        it.copy(
                            importProgress = ImportProgress(ImportProgressPhase.Complete).toUi(),
                            importUnsupportedFile = false,
                            historicalImportMaxTimelineIndex = historicalImportTimelineSteps.size,
                            hasCompletedHistoricalImport = event.refresh.hasCompletedHistoricalImport,
                            importInsightsSummary = event.refresh.toEphemeralImportFields().importInsightsSummary,
                        )
                    }
                    maybePrefillMinimumVolumeFromImport()
                }
                is HistoricalImportOrchestrationEvent.Failed -> {
                    importResumeState.value = event.resume
                    val unsupported = event.kind == ImportFailureKind.UnsupportedFile
                    ephemeral.update {
                        it.copy(
                            importProgress = ImportProgress(ImportProgressPhase.Failed).toUi(),
                            // Unsupported files use fixed UI strings; never surface server diagnostics.
                            importError = event.message.takeUnless { unsupported },
                            importUnsupportedFile = unsupported,
                            hasCompletedHistoricalImport = event.refresh.hasCompletedHistoricalImport,
                            importInsightsSummary = event.refresh.toEphemeralImportFields().importInsightsSummary,
                        )
                    }
                }
            }
        }
    }

    private suspend fun applyRefreshPersistedImportState() {
        val refresh = refreshPersistedImportState().toEphemeralImportFields()
        ephemeral.update {
            it.copy(
                hasCompletedHistoricalImport = refresh.hasCompletedHistoricalImport,
                importInsightsSummary = refresh.importInsightsSummary,
            )
        }
        maybePrefillMinimumVolumeFromImport()
    }

    /**
     * When import landmarks suggest low-volume muscles, pre-select MV candidates on first visit
     * to the Minimum Volume step (e.g. after returning from Gym & Lifts post-import).
     */
    private fun maybePrefillMinimumVolumeFromImport() {
        val ep = ephemeral.value
        if (ep.minimumVolumeImportPrefillApplied) return
        if (wizardState.value.currentStep != OnboardingStep.MinimumVolume) return
        val persisted = wizardState.value.specialization
        if (persisted.minimumVolumeMuscles.isNotEmpty()) {
            ephemeral.update { it.copy(minimumVolumeImportPrefillApplied = true) }
            return
        }
        val suggestions = suggestMinimumVolumeMuscleLabels(
            importInsights = ep.importInsightsSummary,
            preference = effectiveSpecialization.value,
        )
        if (suggestions.isEmpty()) return
        val musclesToAdd =
            suggestions.mapNotNull(::muscleGroupFromLabel)
                .filter { muscle ->
                    muscle !in persisted.minimumVolumeMuscles &&
                        muscle !in effectiveSpecialization.value.minimumVolumeMuscles
                }
                .take(ValidateMinimumVolumeStepUseCase.MAX_MINIMUM_VOLUME)
        if (musclesToAdd.isEmpty()) return
        specializationEdits.value = specializationEdits.value.copy(
            minimumVolumeMusclesEdit = specializationEdits.value.minimumVolumeMusclesEdit.copy(
                added = specializationEdits.value.minimumVolumeMusclesEdit.added + musclesToAdd,
            ),
        )
        ephemeral.update { it.copy(minimumVolumeImportPrefillApplied = true) }
    }

    fun next() {
        val current = wizardState.value
        val path = current.onboardingPath
        if (current.currentStep == OnboardingStep.Schedule) {
            ensureCycleSpaceSynced()
        }
        val trainCount =
            ephemeral.value.scheduleTrainingDayCount
                ?: effectiveProfile.value.generationTrainingDayCount
                ?: ScheduleDraftUi.DEFAULT_TRAINING_DAY_COUNT
        val cycle =
            resolveGenerationScheduleDraft(
                trainingDayCount = trainCount,
                cycleLength =
                    ephemeral.value.generationCycleLength
                        ?: effectiveProfile.value.generationCycleLength,
                restSlotIndices =
                    ephemeral.value.generationRestSlotIndices
                        ?: effectiveProfile.value.generationRestSlotIndices
                            .takeIf { it.isNotEmpty() },
            )
        viewModelScope.launch {
            val result = advanceOnboardingStep(
                currentStep = current.currentStep,
                direction = OnboardingStepDirection.Forward,
                profile = effectiveProfile.value,
                specialization = effectiveSpecialization.value,
                lifts = current.lifts,
                selectedPreset = current.selectedPreset,
                usedHistoricalImport = current.usedHistoricalImport,
                path = path,
                persistedSelectedPath = current.progress?.selectedPath,
                cycleLength = cycle.cycleLength,
                restSlotIndices = cycle.restSlotIndices,
                trainingDayCount = trainCount,
            )
            when (result) {
                AdvanceOnboardingStepResult.ValidationFailed ->
                    ephemeral.update { it.copy(showErrors = true) }
                is AdvanceOnboardingStepResult.Success -> {
                    edits.value = ProfileEdit()
                    specializationEdits.value = SpecializationEdit()
                    ephemeral.update { it.copy(showErrors = false, navigationError = null) }
                    if (result.newStep == OnboardingStep.MinimumVolume) {
                        maybePrefillMinimumVolumeFromImport()
                    }
                }
                AdvanceOnboardingStepResult.NoOp -> Unit
                AdvanceOnboardingStepResult.PersistenceFailed ->
                    ephemeral.update {
                        it.copy(navigationError = OnboardingProgramFlowOps.MSG_NAVIGATION_FAILED)
                    }
            }
        }
    }

    fun back() {
        val current = wizardState.value
        val path = current.onboardingPath
        viewModelScope.launch {
            val result = advanceOnboardingStep(
                currentStep = current.currentStep,
                direction = OnboardingStepDirection.Backward,
                profile = effectiveProfile.value,
                specialization = effectiveSpecialization.value,
                lifts = current.lifts,
                selectedPreset = current.selectedPreset,
                usedHistoricalImport = current.usedHistoricalImport,
                path = path,
                persistedSelectedPath = current.progress?.selectedPath,
            )
            when (result) {
                is AdvanceOnboardingStepResult.Success -> {
                    edits.value = ProfileEdit()
                    specializationEdits.value = SpecializationEdit()
                    ephemeral.update { it.copy(navigationError = null) }
                    if (result.newStep == OnboardingStep.MinimumVolume) {
                        maybePrefillMinimumVolumeFromImport()
                    }
                }
                AdvanceOnboardingStepResult.PersistenceFailed ->
                    ephemeral.update {
                        it.copy(navigationError = OnboardingProgramFlowOps.MSG_NAVIGATION_FAILED)
                    }
                else -> Unit
            }
        }
    }

    /**
     * Advance from [OnboardingStep.ChoosePath] down the chosen [path]. The use case handles clearing
     * schedule/specialization data when committing to Import.
     */
    fun selectPath(path: OnboardingPath) {
        val current = wizardState.value
        if (current.currentStep != OnboardingStep.ChoosePath) return
        viewModelScope.launch {
            val result = advanceOnboardingStep(
                currentStep = current.currentStep,
                direction = OnboardingStepDirection.Forward,
                profile = effectiveProfile.value,
                specialization = effectiveSpecialization.value,
                lifts = current.lifts,
                selectedPreset = current.selectedPreset,
                usedHistoricalImport = current.usedHistoricalImport,
                path = path,
            )
            when (result) {
                is AdvanceOnboardingStepResult.Success -> {
                    edits.value = ProfileEdit()
                    specializationEdits.value = SpecializationEdit()
                    ephemeral.update { it.copy(navigationError = null) }
                }
                AdvanceOnboardingStepResult.PersistenceFailed ->
                    ephemeral.update {
                        it.copy(navigationError = OnboardingProgramFlowOps.MSG_NAVIGATION_FAILED)
                    }
                else -> Unit
            }
        }
    }

    fun navigationErrorShown() {
        ephemeral.update { it.copy(navigationError = null) }
    }

    fun generate() {
        if (ephemeral.value.generateState == GenerateState.Loading) return
        ephemeral.update {
            it.copy(
                generateState = GenerateState.Loading,
                generateProgress =
                    ProgramGenerationProgress(ProgramGenerationProgressPhase.PreparingPayload).toUi(),
                generateMaxTimelineIndex = 0,
                generateErrorMessage = null,
            )
        }
        generateJob?.cancel()
        generateJob =
            viewModelScope.launch {
                try {
                    // Flush Review profile/specialization + durable cycle-space before generation
                    // reads Room (LoadProgramGenerationContextUseCase).
                    val trainCount =
                        ephemeral.value.scheduleTrainingDayCount
                            ?: effectiveProfile.value.generationTrainingDayCount
                            ?: ScheduleDraftUi.DEFAULT_TRAINING_DAY_COUNT
                    val cycle =
                        resolveGenerationScheduleDraft(
                            trainingDayCount = trainCount,
                            cycleLength =
                                ephemeral.value.generationCycleLength
                                    ?: effectiveProfile.value.generationCycleLength,
                            restSlotIndices =
                                ephemeral.value.generationRestSlotIndices
                                    ?: effectiveProfile.value.generationRestSlotIndices
                                        .takeIf { it.isNotEmpty() },
                        )
                    val profileForGen =
                        effectiveProfile.value.copy(
                            weekdays = emptySet(),
                            generationTrainingDayCount = trainCount,
                            generationCycleLength = cycle.cycleLength,
                            generationRestSlotIndices = cycle.restSlotIndices,
                        )
                    updateUserTrainingProfile(profileForGen)
                    updateSpecialization(effectiveSpecialization.value)
                    val generateResult =
                        generateProgram(
                            restSlotIndices = cycle.restSlotIndices,
                            cycleLength = cycle.cycleLength,
                        ) { progress ->
                            withContext(Dispatchers.Main.immediate) {
                                publishGenerateProgress(progress)
                            }
                        }
                    val program = generateResult.getOrElse { error ->
                        logGenerateFailure(error)
                        ephemeral.update(OnboardingProgramFlowOps::generateFailed)
                        return@launch
                    }
                    withContext(Dispatchers.Main.immediate) {
                        publishGenerateProgress(
                            ProgramGenerationProgress(ProgramGenerationProgressPhase.SavingProgram),
                        )
                    }
                    val activateResult = activateProgram(program)
                    activateResult.exceptionOrNull()?.let { error ->
                        logGenerateFailure(error)
                        ephemeral.update(OnboardingProgramFlowOps::generateFailed)
                        return@launch
                    }
                    completeOnboarding(profileForGen)
                    withContext(Dispatchers.Main.immediate) {
                        publishGenerateProgress(
                            ProgramGenerationProgress(ProgramGenerationProgressPhase.Complete),
                        )
                    }
                    ephemeral.update {
                        it.copy(
                            generateState = GenerateState.Idle,
                            generateProgress = null,
                            generateMaxTimelineIndex = 0,
                            generateErrorMessage = null,
                        )
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    logGenerateFailure(error)
                    ephemeral.update(OnboardingProgramFlowOps::generateFailed)
                }
            }
    }

    private fun publishGenerateProgress(progress: ProgramGenerationProgress) {
        ephemeral.update { OnboardingProgramFlowOps.withGenerateProgress(it, progress) }
    }

    /**
     * Import a program-template file (CSV/XLSX). Mirrors [generate]: success flips the wizard's
     * `isComplete` so the existing `LaunchedEffect(state.isComplete)` consumes it and routes to the
     * preview-mode Programs screen. A rejection (file is not a program) and a failure are surfaced
     * as distinct UI states.
     */
    fun importExistingProgram(file: ImportFileInput) {
        if (ephemeral.value.importProgramState is ImportProgramState.Loading) return
        ephemeral.update {
            it.copy(
                importProgramState = ImportProgramState.Loading(ImportProgramPhaseUi.Uploading),
                importMaxTimelineIndex = ImportProgramPhaseUi.Uploading.activeImportTimelineIndex(),
            )
        }
        importProgramJob?.cancel()
        importProgramJob =
            viewModelScope.launch {
                try {
                    val result =
                        importProgramTemplate(file.bytes, file.fileName) { phase ->
                            withContext(Dispatchers.Main.immediate) {
                                publishImportProgramProgress(phase)
                            }
                        }
                    result.exceptionOrNull()?.let { error -> setImportProgramError(error) }
                    if (result.isSuccess) {
                        ephemeral.update {
                            it.copy(
                                importProgramState = ImportProgramState.Idle,
                                importMaxTimelineIndex = 0,
                            )
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    setImportProgramError(error)
                }
            }
    }

    /**
     * Surface an import error without starting a job — used when the file itself cannot be read
     * (e.g. a virtual doc that won't materialize). Logs the detail via [LogErrorUseCase] and
     * shows the generic error UI; the raw [message] is never displayed to the user.
     */
    fun showImportProgramError(message: String?) {
        logError(
            OnboardingProgramFlowOps.TAG_IMPORT,
            OnboardingProgramFlowOps.fileReadFailureLogMessage(message),
        )
        ephemeral.update {
            it.copy(
                importProgramState = ImportProgramState.Error(null),
                importMaxTimelineIndex = 0,
            )
        }
    }

    private fun publishImportProgramProgress(phase: ProgramTemplateImportPhase) {
        ephemeral.update { OnboardingProgramFlowOps.withImportProgramProgress(it, phase) }
    }

    private fun setImportProgramError(error: Throwable) {
        logError(
            OnboardingProgramFlowOps.TAG_IMPORT,
            OnboardingProgramFlowOps.importFailureLogMessage(error),
            error,
        )
        ephemeral.update { OnboardingProgramFlowOps.importProgramFailed(it, error) }
    }

    private fun logGenerateFailure(error: Throwable) {
        logError(OnboardingProgramFlowOps.TAG, OnboardingProgramFlowOps.MSG_GENERATE_FAILED, error)
    }
}
