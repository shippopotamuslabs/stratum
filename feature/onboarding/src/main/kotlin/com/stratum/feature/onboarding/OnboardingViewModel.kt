package com.stratum.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.importing.HistoricalImportEvent
import com.stratum.domain.importing.HistoricalImportLimits
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
import com.stratum.domain.onboarding.CompleteOnboardingUseCase
import com.stratum.domain.onboarding.GetOnboardingWizardStateUseCase
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.OnboardingStep
import com.stratum.domain.onboarding.OnboardingStepDirection
import com.stratum.domain.onboarding.OnboardingWizardState
import com.stratum.domain.onboarding.SpecializationPreference
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.onboarding.ValidateCurrentOnboardingStepUseCase
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.onboarding.applyAdditiveSetEdit
import com.stratum.domain.onboarding.toggleInAdditiveSetEdit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    private val completeOnboarding: CompleteOnboardingUseCase,
) : ViewModel() {

    private val edits = MutableStateFlow(ProfileEdit())
    private val specializationEdits = MutableStateFlow(SpecializationEdit())
    private val showErrors = MutableStateFlow(false)
    private val applyingPreset = MutableStateFlow(false)
    private val importProgress = MutableStateFlow<ImportProgress?>(null)
    private val importError = MutableStateFlow<String?>(null)
    private val hasCompletedHistoricalImport = MutableStateFlow(false)
    private val importInsightsSummary = MutableStateFlow<ImportInsightsSummaryUi?>(null)

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
            combine(
                showErrors,
                applyingPreset,
                importProgress,
                importError,
                hasCompletedHistoricalImport,
            ) { errs, presetApplying, progress, importErr, hasCompleted ->
                ImportUiFlagsPartial(errs, presetApplying, progress, importErr, hasCompleted)
            }.combine(importInsightsSummary) { partial, insightsSummary ->
                ImportUiFlags(
                    showErrors = partial.showErrors,
                    presetApplying = partial.presetApplying,
                    importProgress = partial.importProgress,
                    importError = partial.importError,
                    hasCompletedHistoricalImport = partial.hasCompletedHistoricalImport,
                    importInsightsSummary = insightsSummary,
                )
            },
        ) { current, profile, edit, spec, importFlags ->
            val step = current.currentStep.toWizardStepUi()
            val lifts = current.lifts
            val preset = current.selectedPreset
            val stepValid = validateCurrentStep(
                step = current.currentStep,
                profile = profile,
                specialization = spec,
                lifts = lifts,
                selectedPreset = preset,
                usedHistoricalImport = current.usedHistoricalImport,
            ).isValid
            val importing = importFlags.importProgress?.let { progress ->
                progress.phase != ImportProgressPhase.Complete &&
                    progress.phase != ImportProgressPhase.Failed
            } == true
            val canAdvance = !importFlags.presetApplying && !importing && stepValid
            val gymLiftsValid = validateCurrentStep(
                step = OnboardingStep.GymAndLifts,
                profile = profile,
                specialization = spec,
                lifts = lifts,
                selectedPreset = preset,
                usedHistoricalImport = current.usedHistoricalImport,
            ).isValid

            OnboardingUiState(
                step = step,
                isComplete = current.isComplete,
                canAdvance = canAdvance,
                canGoBack = current.currentStep.previous() != null,
                profileDraft = profile.toProfileDraft().copy(showErrors = importFlags.showErrors),
                scheduleDraft = profile.toScheduleDraft(edit.sessionLength).copy(showErrors = importFlags.showErrors),
                specializationDraft = spec.toSpecializationDraft().copy(showErrors = importFlags.showErrors),
                gymLiftsDraft = lifts.toGymLiftsDraft(
                    selectedPreset = preset,
                    usedHistoricalImport = current.usedHistoricalImport,
                    hasCompletedHistoricalImport = importFlags.hasCompletedHistoricalImport,
                    importProgress = importFlags.importProgress,
                    importError = importFlags.importError,
                    importInsightsSummary = importFlags.importInsightsSummary,
                    liftsValid = gymLiftsValid,
                    showErrors = importFlags.showErrors,
                ),
                reviewDraft = profile.toReviewProfileSection(spec, lifts, preset),
                presetOptions = allGymPresetOptions(),
            )
        }.stateIn(
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
        if (importProgress.value?.let { progress ->
                progress.phase != ImportProgressPhase.Complete &&
                    progress.phase != ImportProgressPhase.Failed
            } == true
        ) {
            return
        }
        val preset = gymPresetFromLabel(label)
        viewModelScope.launch {
            applyingPreset.value = true
            importError.value = null
            try {
                if (preset != null) {
                    applyGymPreset(preset)
                } else {
                    setSelectedPreset(null)
                }
            } finally {
                applyingPreset.value = false
            }
        }
    }

    fun onImportFilesSelected(files: List<ImportFileInput>) {
        viewModelScope.launch {
            val validationError = validateImportFiles(files)
            if (validationError != null) {
                importError.value = validationError
                importProgress.value = ImportProgress(ImportProgressPhase.Failed)
                return@launch
            }
            importProgress.value = ImportProgress(ImportProgressPhase.Uploading)
            importError.value = null
            runFullHistoricalImport.run(files).collect { event ->
                when (event) {
                    is HistoricalImportEvent.Progress -> importProgress.value = event.progress
                    is HistoricalImportEvent.Completed -> {
                        importProgress.value = ImportProgress(ImportProgressPhase.Complete)
                        refreshPersistedImportState()
                        refreshImportInsightsSummary()
                    }
                    is HistoricalImportEvent.Failed -> {
                        rollbackHistoricalImport()
                        importProgress.value = ImportProgress(ImportProgressPhase.Failed)
                        importError.value = event.message
                        refreshPersistedImportState()
                    }
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
        hasCompletedHistoricalImport.value = reconcileHistoricalImport()
        if (!hasCompletedHistoricalImport.value) {
            importInsightsSummary.value = null
        }
    }

    private suspend fun refreshImportInsightsSummary() {
        if (!hasCompletedHistoricalImport.value) {
            importInsightsSummary.value = null
            return
        }
        importInsightsSummary.value = loadHistoricalImportSummary()?.toImportInsightsSummaryUi()
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
                AdvanceOnboardingStepResult.ValidationFailed -> showErrors.value = true
                is AdvanceOnboardingStepResult.Success -> {
                    edits.value = ProfileEdit()
                    specializationEdits.value = SpecializationEdit()
                    showErrors.value = false
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

    fun complete() {
        viewModelScope.launch { completeOnboarding(effectiveProfile.value) }
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

    private data class ImportUiFlagsPartial(
        val showErrors: Boolean,
        val presetApplying: Boolean,
        val importProgress: ImportProgress?,
        val importError: String?,
        val hasCompletedHistoricalImport: Boolean,
    )

    private data class ImportUiFlags(
        val showErrors: Boolean,
        val presetApplying: Boolean,
        val importProgress: ImportProgress?,
        val importError: String?,
        val hasCompletedHistoricalImport: Boolean,
        val importInsightsSummary: ImportInsightsSummaryUi?,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
