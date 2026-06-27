package com.stratum.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.importing.HistoricalImportEvent
import com.stratum.domain.importing.HistoricalImportLimits
import com.stratum.domain.importing.HistoricalImportResumeState
import com.stratum.domain.importing.HistoricalImportRunnerPort
import com.stratum.domain.importing.ImportFileInput
import com.stratum.domain.importing.ImportProgress
import com.stratum.domain.importing.ImportProgressPhase
import com.stratum.domain.importing.LoadHistoricalImportSummaryUseCase
import com.stratum.domain.importing.ReconcileHistoricalImportUseCase
import com.stratum.domain.importing.RollbackHistoricalImportUseCase
import com.stratum.domain.lift.ApplyGymPresetUseCase
import com.stratum.domain.lift.SetSelectedPresetUseCase
import com.stratum.domain.onboarding.AdditiveSetEdit
import com.stratum.domain.onboarding.AdvanceOnboardingStepResult
import com.stratum.domain.onboarding.AdvanceOnboardingStepUseCase
import com.stratum.domain.onboarding.GetOnboardingWizardStateUseCase
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.OnboardingStepDirection
import com.stratum.domain.onboarding.OnboardingWizardState
import com.stratum.domain.onboarding.SpecializationPreference
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.onboarding.ValidateCurrentOnboardingStepUseCase
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.onboarding.applyAdditiveSetEdit
import com.stratum.domain.onboarding.toggleInAdditiveSetEdit
import com.stratum.domain.program.GenerateProgramUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val getOnboardingWizardState: GetOnboardingWizardStateUseCase,
    private val advanceOnboardingStep: AdvanceOnboardingStepUseCase,
    private val validateCurrentStep: ValidateCurrentOnboardingStepUseCase,
    private val applyGymPreset: ApplyGymPresetUseCase,
    private val setSelectedPreset: SetSelectedPresetUseCase,
    private val runFullHistoricalImport: HistoricalImportRunnerPort,
    private val rollbackHistoricalImport: RollbackHistoricalImportUseCase,
    private val reconcileHistoricalImport: ReconcileHistoricalImportUseCase,
    private val loadHistoricalImportSummary: LoadHistoricalImportSummaryUseCase,
    private val generateProgram: GenerateProgramUseCase,
) : ViewModel() {

    private val edits = MutableStateFlow(ProfileEdit())
    private val specializationEdits = MutableStateFlow(SpecializationEdit())
    private val ephemeral = MutableStateFlow(OnboardingEphemeralState.Initial)
    private var importJob: Job? = null
    private var generateJob: Job? = null

    init {
        viewModelScope.launch {
            refreshPersistedImportState()
            refreshImportInsightsSummary()
        }
    }

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
            buildOnboardingUiState(
                current = current,
                profile = profile,
                sessionLengthEdit = edit.sessionLength,
                specialization = spec,
                ephemeral = ephemeralState,
                validateCurrentStep = validateCurrentStep,
            )
        }.distinctUntilChanged().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = OnboardingUiState.Initial,
        )

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

    private fun resolveCustomSessionMinutes(): Int {
        val currentEdit = edits.value.sessionLength
        if (currentEdit is SessionLengthEdit.Active && currentEdit.customMode && currentEdit.minutes != null) {
            return currentEdit.minutes
        }
        val persisted = effectiveProfile.value.sessionLengthMinutes
        if (persisted != null && persisted !in SESSION_PRESET_MINUTES) return persisted
        return DEFAULT_CUSTOM_SESSION_MINUTES
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

    fun onPresetSelected(label: String?) {
        if (ephemeral.value.importProgress?.let { progress ->
                progress.phase != ImportProgressPhase.Complete &&
                    progress.phase != ImportProgressPhase.Failed
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
                    importResumeState = null,
                    presetApplyError = null,
                )
            }
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
            val validationError = validateImportFiles(files)
            if (validationError != null) {
                ephemeral.update {
                    it.copy(
                        importError = validationError,
                        importProgress = ImportProgress(ImportProgressPhase.Failed),
                    )
                }
            } else {
                ephemeral.update { it.copy(importResumeState = null) }
                runHistoricalImport(files = files, resume = null)
            }
        }
    }

    fun onRetryImportFromFailedFile() {
        launchHistoricalImport {
            val resume = ephemeral.value.importResumeState?.takeIf { it.isResumable() }
            if (resume != null) {
                runHistoricalImport(files = resume.files, resume = resume)
            }
        }
    }

    fun onCancelImport() {
        viewModelScope.launch {
            importJob?.cancel()
            importJob = null
            resetImportState()
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

    private suspend fun resetImportState() {
        rollbackHistoricalImport()
        ephemeral.update {
            it.copy(
                importResumeState = null,
                importProgress = null,
                importError = null,
            )
        }
        refreshPersistedImportState()
    }

    private suspend fun runHistoricalImport(
        files: List<ImportFileInput>,
        resume: HistoricalImportResumeState?,
    ) {
        ephemeral.update {
            it.copy(
                importProgress = ImportProgress(ImportProgressPhase.Uploading),
                importError = null,
            )
        }
        runFullHistoricalImport.run(files = files, resume = resume).collect { event ->
            when (event) {
                is HistoricalImportEvent.Progress ->
                    ephemeral.update { it.copy(importProgress = event.progress) }
                is HistoricalImportEvent.Completed -> {
                    ephemeral.update {
                        it.copy(
                            importResumeState = null,
                            importProgress = ImportProgress(ImportProgressPhase.Complete),
                        )
                    }
                    refreshPersistedImportState()
                    refreshImportInsightsSummary()
                }
                is HistoricalImportEvent.Failed -> {
                    rollbackHistoricalImport()
                    ephemeral.update {
                        it.copy(
                            importResumeState = event.resume,
                            importProgress = ImportProgress(ImportProgressPhase.Failed),
                            importError = event.message,
                        )
                    }
                    refreshPersistedImportState()
                }
            }
        }
    }

    private fun validateImportFiles(files: List<ImportFileInput>): String? {
        if (files.isEmpty()) {
            return "No files selected."
        }
        if (files.size > HistoricalImportLimits.MAX_FILES) {
            return "Select at most ${HistoricalImportLimits.MAX_FILES} files."
        }
        val oversized = files.firstOrNull { it.bytes.size > HistoricalImportLimits.MAX_FILE_BYTES }
        if (oversized != null) {
            return "${oversized.fileName} exceeds the ${HistoricalImportLimits.MAX_FILE_SIZE_MB} MB limit."
        }
        return null
    }

    private suspend fun refreshPersistedImportState() {
        val hasCompleted = reconcileHistoricalImport()
        ephemeral.update {
            it.copy(
                hasCompletedHistoricalImport = hasCompleted,
                importInsightsSummary = if (hasCompleted) it.importInsightsSummary else null,
            )
        }
    }

    private suspend fun refreshImportInsightsSummary() {
        if (!ephemeral.value.hasCompletedHistoricalImport) {
            ephemeral.update { it.copy(importInsightsSummary = null) }
            return
        }
        ephemeral.update {
            it.copy(importInsightsSummary = loadHistoricalImportSummary()?.toImportInsightsSummaryUi())
        }
    }

    fun next() {
        val current = wizardState.value
        viewModelScope.launch {
            val result = advanceOnboardingStep(
                currentStep = current.currentStep,
                direction = OnboardingStepDirection.Forward,
                profile = effectiveProfile.value,
                specialization = effectiveSpecialization.value,
                lifts = current.lifts,
                selectedPreset = current.selectedPreset,
                usedHistoricalImport = current.usedHistoricalImport,
            )
            when (result) {
                AdvanceOnboardingStepResult.ValidationFailed ->
                    ephemeral.update { it.copy(showErrors = true) }
                is AdvanceOnboardingStepResult.Success -> {
                    edits.value = ProfileEdit()
                    specializationEdits.value = SpecializationEdit()
                    ephemeral.update { it.copy(showErrors = false) }
                }
                AdvanceOnboardingStepResult.NoOp -> Unit
            }
        }
    }

    fun back() {
        val current = wizardState.value
        viewModelScope.launch {
            val result = advanceOnboardingStep(
                currentStep = current.currentStep,
                direction = OnboardingStepDirection.Backward,
                profile = effectiveProfile.value,
                specialization = effectiveSpecialization.value,
                lifts = current.lifts,
                selectedPreset = current.selectedPreset,
                usedHistoricalImport = current.usedHistoricalImport,
            )
            if (result is AdvanceOnboardingStepResult.Success) {
                edits.value = ProfileEdit()
                specializationEdits.value = SpecializationEdit()
            }
        }
    }

    /**
     * Triggered by the Generate step CTA (OS-7 D4). Runs [GenerateProgramUseCase] (load payload →
     * generate → persist → complete onboarding), surfacing Loading then Idle (success) or Error
     * (failure). On success the use case marks onboarding complete, which flips `isComplete` in
     * [wizardState] and is consumed by the route's `LaunchedEffect(state.isComplete)`. On failure
     * onboarding stays incomplete (PROG-GEN-601 mutation-defense).
     */
    fun generate() {
        if (ephemeral.value.generateState == GenerateState.Loading) return
        // Set Loading synchronously before launching so the Loading emission is observable even
        // under an unconfined test dispatcher (the coroutine below would otherwise race ahead and
        // the first collection would see only the terminal state).
        ephemeral.update {
            it.copy(
                generateState = GenerateState.Loading,
                generateErrorMessage = null,
            )
        }
        generateJob?.cancel()
        generateJob =
            viewModelScope.launch {
                val result = generateProgram()
                ephemeral.update {
                    it.copy(
                        generateState = if (result.isSuccess) GenerateState.Idle else GenerateState.Error,
                        generateErrorMessage = result.exceptionOrNull().toGenerateErrorMessage()
                            .takeIf { result.isFailure },
                    )
                }
            }
    }

    private data class ProfileEdit(
        val trainingAge: com.stratum.domain.onboarding.TrainingAge? = null,
        val lastTrained: com.stratum.domain.onboarding.LastTrainedBucket? = null,
        val weekdaysEdit: AdditiveSetEdit<Weekday> = AdditiveSetEdit(),
        val sessionLength: SessionLengthEdit = SessionLengthEdit.Unchanged,
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
    )

    private fun SpecializationPreference.applyEdit(edit: SpecializationEdit): SpecializationPreference {
        val base = levels.toMutableMap()
        edit.musclesEdit.added.forEach {
            base[it] = com.stratum.domain.onboarding.SpecializationLevel.Specialize
        }
        edit.musclesEdit.removed.forEach { base.remove(it) }
        return copy(levels = base)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
