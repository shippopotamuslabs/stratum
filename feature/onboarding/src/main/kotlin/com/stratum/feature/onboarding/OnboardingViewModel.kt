package com.stratum.feature.onboarding

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.importing.CancelHistoricalImportUseCase
import com.stratum.domain.importing.ExecuteHistoricalImportUseCase
import com.stratum.domain.importing.HistoricalImportOrchestrationEvent
import com.stratum.domain.importing.HistoricalImportResumeState
import com.stratum.domain.importing.ImportFileInput
import com.stratum.domain.importing.ImportProgress
import com.stratum.domain.importing.ImportProgressPhase
import com.stratum.domain.importing.RefreshPersistedImportStateUseCase
import com.stratum.domain.lift.ApplyGymPresetUseCase
import com.stratum.domain.lift.SetSelectedPresetUseCase
import com.stratum.domain.onboarding.AdditiveSetEdit
import com.stratum.domain.onboarding.AdvanceOnboardingStepResult
import com.stratum.domain.onboarding.AdvanceOnboardingStepUseCase
import com.stratum.domain.onboarding.GetOnboardingStepCapabilitiesUseCase
import com.stratum.domain.onboarding.GetOnboardingWizardStateUseCase
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.OnboardingPath
import com.stratum.domain.onboarding.OnboardingStep
import com.stratum.domain.onboarding.OnboardingStepDirection
import com.stratum.domain.onboarding.OnboardingWizardState
import com.stratum.domain.onboarding.SpecializationPreference
import com.stratum.domain.onboarding.CompleteOnboardingUseCase
import com.stratum.domain.onboarding.UpdateSpecializationUseCase
import com.stratum.domain.onboarding.UpdateUserTrainingProfileUseCase
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.onboarding.ValidateMinimumVolumeStepUseCase
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.onboarding.applyAdditiveSetEdit
import com.stratum.domain.onboarding.toggleInAdditiveSetEdit
import com.stratum.domain.program.ActivateProgramUseCase
import com.stratum.domain.program.GenerateProgramUseCase
import com.stratum.domain.program.ProgramGenerationProgress
import com.stratum.domain.program.ProgramGenerationProgressPhase
import com.stratum.domain.program.importing.ImportProgramTemplateUseCase
import com.stratum.domain.program.importing.ProgramTemplateImportPhase
import com.stratum.domain.program.importing.ProgramTemplateRejectedException
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
@Suppress("TooManyFunctions")
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
            val capabilities = getOnboardingStepCapabilities(
                step = inputs.current.currentStep,
                profile = inputs.profile,
                specialization = inputs.specialization,
                lifts = inputs.current.lifts,
                selectedPreset = inputs.current.selectedPreset,
                usedHistoricalImport = inputs.current.usedHistoricalImport,
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

    fun onWeekdayToggled(shortLabel: String) {
        val day = Weekday.entries.firstOrNull { it.toShortLabel() == shortLabel } ?: return
        val current = effectiveProfile.value.weekdays
        val persisted = wizardState.value.profile?.weekdays ?: emptySet()
        edits.value = edits.value.copy(
            weekdaysEdit = toggleInAdditiveSetEdit(
                item = day,
                currentEffective = current,
                persistedBase = persisted,
                currentEdit = edits.value.weekdaysEdit,
            ),
        )
    }

    fun onSessionPresetSelected(value: String?) {
        edits.value = edits.value.copy(
            sessionLength = when (value) {
                null -> SessionLengthEdit.Active(minutes = null, customMode = false)
                ScheduleDraftUi.CUSTOM_KEY -> SessionLengthEdit.Active(
                    minutes = resolveCustomSessionMinutes(),
                    customMode = true,
                )
                else -> SessionLengthEdit.Active(minutes = value.toIntOrNull(), customMode = false)
            },
        )
    }

    fun onCustomSessionMinutesChange(minutes: Int) {
        edits.value = edits.value.copy(
            sessionLength = SessionLengthEdit.Active(minutes = minutes, customMode = true),
        )
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
        ephemeral.update { it.copy(importError = null) }
        executeHistoricalImport(files = files, resume = resume).collect { event ->
            when (event) {
                is HistoricalImportOrchestrationEvent.ValidationFailed ->
                    ephemeral.update {
                        it.copy(
                            importError = event.reason.toUserMessage(),
                            importProgress = ImportProgress(ImportProgressPhase.Failed).toUi(),
                        )
                    }
                is HistoricalImportOrchestrationEvent.Progress ->
                    ephemeral.update { it.copy(importProgress = event.progress.toUi()) }
                is HistoricalImportOrchestrationEvent.Completed -> {
                    importResumeState.value = null
                    ephemeral.update {
                        it.copy(
                            importProgress = ImportProgress(ImportProgressPhase.Complete).toUi(),
                            hasCompletedHistoricalImport = event.refresh.hasCompletedHistoricalImport,
                            importInsightsSummary = event.refresh.toEphemeralImportFields().importInsightsSummary,
                        )
                    }
                    maybePrefillMinimumVolumeFromImport()
                }
                is HistoricalImportOrchestrationEvent.Failed -> {
                    importResumeState.value = event.resume
                    ephemeral.update {
                        it.copy(
                            importProgress = ImportProgress(ImportProgressPhase.Failed).toUi(),
                            importError = event.message,
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
                    ephemeral.update { it.copy(navigationError = MSG_NAVIGATION_FAILED) }
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
                    ephemeral.update { it.copy(navigationError = MSG_NAVIGATION_FAILED) }
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
                    ephemeral.update { it.copy(navigationError = MSG_NAVIGATION_FAILED) }
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
                    // Flush the Review-step profile/specialization snapshot before generation reads
                    // persisted onboarding state (LoadProgramGenerationContextUseCase). Without this,
                    // a user who explored the Import path (which clears weekdays) and then re-walked
                    // Generate could still have stale empty weekdays in Room.
                    updateUserTrainingProfile(effectiveProfile.value)
                    updateSpecialization(effectiveSpecialization.value)
                    val generateResult =
                        generateProgram { progress ->
                            withContext(Dispatchers.Main.immediate) {
                                publishGenerateProgress(progress)
                            }
                        }
                    val program = generateResult.getOrElse { error ->
                        logGenerateFailure(error)
                        ephemeral.update {
                            it.copy(
                                generateState = GenerateState.Error,
                                generateProgress = null,
                                generateMaxTimelineIndex = 0,
                                generateErrorMessage = null,
                            )
                        }
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
                        ephemeral.update {
                            it.copy(
                                generateState = GenerateState.Error,
                                generateProgress = null,
                                generateMaxTimelineIndex = 0,
                                generateErrorMessage = null,
                            )
                        }
                        return@launch
                    }
                    completeOnboarding(effectiveProfile.value)
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
                    ephemeral.update {
                        it.copy(
                            generateState = GenerateState.Error,
                            generateProgress = null,
                            generateMaxTimelineIndex = 0,
                            generateErrorMessage = null,
                        )
                    }
                }
            }
    }

    private fun publishGenerateProgress(progress: ProgramGenerationProgress) {
        val ui = progress.toUi()
        val index = ui.phase.activeTimelineIndex()
        ephemeral.update { state ->
            state.copy(
                generateProgress = ui,
                generateMaxTimelineIndex = maxOf(state.generateMaxTimelineIndex, index),
            )
        }
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
                        ephemeral.update { it.copy(importProgramState = ImportProgramState.Idle) }
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
     * (e.g. a virtual doc that won't materialize). Logs the detail via `android.util.Log` and
     * shows the generic error UI; the raw [message] is never displayed to the user.
     */
    fun showImportProgramError(message: String?) {
        logImportFailure("File read failed: ${message ?: "unknown"}")
        ephemeral.update { it.copy(importProgramState = ImportProgramState.Error(null)) }
    }

    private fun publishImportProgramProgress(phase: ProgramTemplateImportPhase) {
        ephemeral.update { state ->
            state.copy(importProgramState = ImportProgramState.Loading(phase.toUi()))
        }
    }

    private fun setImportProgramError(error: Throwable) {
        // Log the full detail for debugging; the UI shows only a generic message so raw Firebase
        // error codes (NOT_FOUND, PERMISSION_DENIED, …) never leak to the user.
        logImportFailure("Program import failed", error)
        val state = if (error is ProgramTemplateRejectedException) {
            ImportProgramState.Rejected(null)
        } else {
            ImportProgramState.Error(null)
        }
        ephemeral.update { it.copy(importProgramState = state) }
    }

    private fun logImportFailure(message: String, error: Throwable? = null) {
        try {
            if (error != null) Log.e(TAG_IMPORT, message, error) else Log.e(TAG_IMPORT, message)
        } catch (_: RuntimeException) {
            // android.util.Log is not mocked in JVM unit tests.
        }
    }

    private fun resolveCustomSessionMinutes(): Int {
        val currentEdit = edits.value.sessionLength
        if (currentEdit is SessionLengthEdit.Active && currentEdit.customMode && currentEdit.minutes != null) {
            return currentEdit.minutes
        }
        val persisted = effectiveProfile.value.sessionLengthMinutes
        if (persisted != null && persisted !in SESSION_PRESET_MINUTES) return persisted
        return DEFAULT_CUSTOM_SESSION_MINUTES
    }

    private data class ProfileEdit(
        val trainingAge: com.stratum.domain.onboarding.TrainingAge? = null,
        val lastTrained: com.stratum.domain.onboarding.LastTrainedBucket? = null,
        val weekdaysEdit: AdditiveSetEdit<Weekday> = AdditiveSetEdit(),
        val sessionLength: SessionLengthEdit = SessionLengthEdit.Unchanged,
    )

    private data class UiCombineInputs(
        val current: OnboardingWizardState,
        val profile: UserTrainingProfile,
        val sessionLengthEdit: SessionLengthEdit,
        val specialization: SpecializationPreference,
        val ephemeral: OnboardingEphemeralState,
    )

    private fun UserTrainingProfile.applyEdit(edit: ProfileEdit): UserTrainingProfile = copy(
        trainingAge = edit.trainingAge ?: trainingAge,
        lastTrained = edit.lastTrained ?: lastTrained,
        weekdays = weekdays.applyAdditiveSetEdit(edit.weekdaysEdit),
        sessionLengthMinutes = when (val sessionLength = edit.sessionLength) {
            SessionLengthEdit.Unchanged -> sessionLengthMinutes
            is SessionLengthEdit.Active -> sessionLength.minutes
        },
    )

    private data class SpecializationEdit(
        val musclesEdit: AdditiveSetEdit<MuscleGroup> = AdditiveSetEdit(),
        val minimumVolumeMusclesEdit: AdditiveSetEdit<MuscleGroup> = AdditiveSetEdit(),
    )

    private fun SpecializationPreference.applyEdit(edit: SpecializationEdit): SpecializationPreference {
        val base = levels.toMutableMap()
        edit.musclesEdit.added.forEach {
            base[it] = com.stratum.domain.onboarding.SpecializationLevel.Specialize
        }
        edit.musclesEdit.removed.forEach { removed ->
            if (base[removed] == com.stratum.domain.onboarding.SpecializationLevel.Specialize) {
                base.remove(removed)
            }
        }
        edit.minimumVolumeMusclesEdit.added.forEach {
            base[it] = com.stratum.domain.onboarding.SpecializationLevel.MinimumVolume
        }
        edit.minimumVolumeMusclesEdit.removed.forEach { removed ->
            if (base[removed] == com.stratum.domain.onboarding.SpecializationLevel.MinimumVolume) {
                base.remove(removed)
            }
        }
        return copy(levels = base)
    }

    private companion object {
        const val TAG = "OnboardingViewModel"
        const val TAG_IMPORT = "ProgramTemplateImport"
        const val MSG_NAVIGATION_FAILED = "Couldn't save progress. Try again."

        private fun logGenerateFailure(error: Throwable) {
            try {
                Log.e(TAG, "Program generation failed", error)
            } catch (_: RuntimeException) {
                // android.util.Log is not mocked in JVM unit tests.
            }
        }
    }
}
