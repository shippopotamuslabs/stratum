package com.stratum.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.onboarding.CompleteOnboardingUseCase
import com.stratum.domain.onboarding.GetOnboardingStateUseCase
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.ObserveSpecializationUseCase
import com.stratum.domain.onboarding.OnboardingState
import com.stratum.domain.onboarding.OnboardingStep
import com.stratum.domain.onboarding.ProfileValidationResult
import com.stratum.domain.onboarding.SaveOnboardingProgressUseCase
import com.stratum.domain.onboarding.ScheduleValidationResult
import com.stratum.domain.onboarding.SpecializationPreference
import com.stratum.domain.onboarding.SpecializationValidationResult
import com.stratum.domain.onboarding.UpdateSpecializationUseCase
import com.stratum.domain.onboarding.UpdateUserTrainingProfileUseCase
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.onboarding.ValidateProfileStepUseCase
import com.stratum.domain.onboarding.ValidateScheduleStepUseCase
import com.stratum.domain.onboarding.ValidateSpecializationStepUseCase
import com.stratum.domain.onboarding.Weekday
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
    private val getOnboardingState: GetOnboardingStateUseCase,
    private val saveOnboardingProgress: SaveOnboardingProgressUseCase,
    private val updateUserTrainingProfile: UpdateUserTrainingProfileUseCase,
    private val observeSpecialization: ObserveSpecializationUseCase,
    private val updateSpecialization: UpdateSpecializationUseCase,
    private val completeOnboarding: CompleteOnboardingUseCase,
    private val validateProfileStep: ValidateProfileStepUseCase,
    private val validateScheduleStep: ValidateScheduleStepUseCase,
    private val validateSpecializationStep: ValidateSpecializationStepUseCase,
) : ViewModel() {

    /** Uncommitted profile field edits not yet persisted. */
    private val edits = MutableStateFlow(ProfileEdit())

    /** Uncommitted specialization edits not yet persisted. */
    private val specializationEdits = MutableStateFlow(SpecializationEdit())

    /** Whether field errors should be shown (after first Next attempt). */
    private val showErrors = MutableStateFlow(false)

    /**
     * Persisted onboarding state — eagerly collected so [.value] is always current for
     * navigation write paths, regardless of whether a UI collector is active.
     */
    private val state: StateFlow<OnboardingState> =
        getOnboardingState().stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = OnboardingState.Initial,
        )

    /**
     * The effective profile draft = persisted profile + uncommitted edits. Eagerly collected
     * so [.value] is the single authoritative source for both rendering and navigation. The
     * first real emission carries the persisted profile, so a reopened onboarding shows
     * last-saved values with no seed race or blocking read.
     */
    private val effectiveProfile: StateFlow<UserTrainingProfile> =
        combine(state, edits) { current, edit ->
            (current.profile ?: UserTrainingProfile.Empty).applyEdit(edit)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UserTrainingProfile.Empty,
        )

    /**
     * Persisted specialization preference — eagerly collected for navigation write paths.
     */
    private val specializationState: StateFlow<SpecializationPreference> =
        observeSpecialization().stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SpecializationPreference.Empty,
        )

    /**
     * Effective specialization = persisted preference + uncommitted edits. Mirrors
     * [effectiveProfile]: the single authoritative source for rendering and navigation.
     */
    private val effectiveSpecialization: StateFlow<SpecializationPreference> =
        combine(specializationState, specializationEdits) { current, edit ->
            current.applyEdit(edit)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SpecializationPreference.Empty,
        )

    val uiState: StateFlow<OnboardingUiState> =
        combine(state, effectiveProfile, effectiveSpecialization, showErrors) { current, profile, spec, errs ->
            OnboardingUiState(
                step = current.currentStep,
                isComplete = current.isComplete,
                profileDraft = profile.toProfileDraft().copy(showErrors = errs),
                scheduleDraft = profile.toScheduleDraft().copy(showErrors = errs),
                specializationDraft = spec.toSpecializationDraft().copy(showErrors = errs),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = OnboardingUiState.Initial,
        )

    // --- Profile step mutations -------------------------------------------------

    fun onTrainingAgeSelected(label: String?) {
        edits.value = edits.value.copy(trainingAge = label?.let(::trainingAgeFromLabel))
    }

    fun onLastTrainedSelected(label: String?) {
        edits.value = edits.value.copy(lastTrained = label?.let(::lastTrainedFromLabel))
    }

    // --- Schedule step mutations ------------------------------------------------

    fun onWeekdayToggled(shortLabel: String) {
        val day = Weekday.entries.firstOrNull { it.toShortLabel() == shortLabel } ?: return
        val current = effectiveProfile.value.weekdays
        val isCurrentlySelected = day in current
        val persisted = state.value.profile?.weekdays ?: emptySet()
        val isPersisted = day in persisted

        edits.value = if (isCurrentlySelected) {
            if (isPersisted) {
                edits.value.copy(
                    weekdaysRemoved = edits.value.weekdaysRemoved + day,
                    weekdaysAdded = edits.value.weekdaysAdded - day
                )
            } else {
                edits.value.copy(
                    weekdaysAdded = edits.value.weekdaysAdded - day
                )
            }
        } else {
            if (isPersisted) {
                edits.value.copy(
                    weekdaysRemoved = edits.value.weekdaysRemoved - day
                )
            } else {
                edits.value.copy(
                    weekdaysAdded = edits.value.weekdaysAdded + day,
                    weekdaysRemoved = edits.value.weekdaysRemoved - day
                )
            }
        }
    }

    fun onSessionPresetSelected(value: String?) {
        val minutes = value?.let { if (it == ScheduleDraftUi.CUSTOM_KEY) null else it.toIntOrNull() }
        edits.value = edits.value.copy(sessionLengthMinutes = minutes)
    }

    fun onCustomSessionChange(input: String) {
        edits.value = edits.value.copy(sessionLengthMinutes = input.filter { it.isDigit() }.toIntOrNull())
    }

    // --- Specialization step mutations -----------------------------------------

    /**
     * Toggle a muscle's Specialize state by its display label. Toggling a selected muscle
     * removes it from the specialization set.
     */
    fun onMuscleToggled(label: String) {
        val muscle = muscleGroupFromLabel(label) ?: return
        val current = effectiveSpecialization.value
        val isCurrentlySelected = muscle in current.specializedMuscles
        val persisted = specializationState.value
        val isPersisted = muscle in persisted.specializedMuscles

        specializationEdits.value = if (isCurrentlySelected) {
            if (isPersisted) {
                specializationEdits.value.copy(
                    removed = specializationEdits.value.removed + muscle,
                    added = specializationEdits.value.added - muscle
                )
            } else {
                specializationEdits.value.copy(
                    added = specializationEdits.value.added - muscle
                )
            }
        } else {
            if (isPersisted) {
                specializationEdits.value.copy(
                    removed = specializationEdits.value.removed - muscle
                )
            } else {
                specializationEdits.value.copy(
                    added = specializationEdits.value.added + muscle,
                    removed = specializationEdits.value.removed - muscle
                )
            }
        }
    }

    // --- Navigation -------------------------------------------------------------

    fun next() {
        val step = state.value.currentStep
        val profile = effectiveProfile.value
        val specialization = effectiveSpecialization.value
        val stepValid = when (step) {
            OnboardingStep.Profile -> validateProfileStep(profile) is ProfileValidationResult.Valid
            OnboardingStep.Schedule -> validateScheduleStep(profile) is ScheduleValidationResult.Valid
            OnboardingStep.Specialization ->
                validateSpecializationStep(specialization) is SpecializationValidationResult.Valid
            else -> true
        }
        if (!stepValid) {
            showErrors.value = true
            return
        }
        val nextStep = step.next() ?: return
        viewModelScope.launch {
            updateUserTrainingProfile(profile)
            updateSpecialization(specialization)
            saveOnboardingProgress(nextStep)
            edits.value = ProfileEdit()
            specializationEdits.value = SpecializationEdit()
            showErrors.value = false
        }
    }

    fun back() {
        val previousStep = state.value.currentStep.previous() ?: return
        viewModelScope.launch {
            updateUserTrainingProfile(effectiveProfile.value)
            updateSpecialization(effectiveSpecialization.value)
            saveOnboardingProgress(previousStep)
            edits.value = ProfileEdit()
            specializationEdits.value = SpecializationEdit()
        }
    }

    fun complete() {
        viewModelScope.launch { completeOnboarding(effectiveProfile.value) }
    }

    /**
     * Uncommitted profile edits. Scalar fields use null = "untouched, defer to persisted".
     * Weekdays are tracked additively (added/removed sets) so toggling composes correctly
     * against the persisted base.
     */
    private data class ProfileEdit(
        val trainingAge: com.stratum.domain.onboarding.TrainingAge? = null,
        val lastTrained: com.stratum.domain.onboarding.LastTrainedBucket? = null,
        val weekdaysAdded: Set<Weekday> = emptySet(),
        val weekdaysRemoved: Set<Weekday> = emptySet(),
        val sessionLengthMinutes: Int? = null,
    )

    private fun UserTrainingProfile.applyEdit(edit: ProfileEdit): UserTrainingProfile = copy(
        trainingAge = edit.trainingAge ?: trainingAge,
        lastTrained = edit.lastTrained ?: lastTrained,
        weekdays = (weekdays + edit.weekdaysAdded) - edit.weekdaysRemoved,
        sessionLengthMinutes = edit.sessionLengthMinutes ?: sessionLengthMinutes,
    )

    /**
     * Uncommitted specialization edits, tracked additively against the persisted base so a
     * toggle composes correctly when the user backs out and returns.
     */
    private data class SpecializationEdit(
        val added: Set<MuscleGroup> = emptySet(),
        val removed: Set<MuscleGroup> = emptySet(),
    )

    private fun SpecializationPreference.applyEdit(edit: SpecializationEdit): SpecializationPreference {
        val base = levels.toMutableMap()
        edit.added.forEach { base[it] = com.stratum.domain.onboarding.SpecializationLevel.Specialize }
        edit.removed.forEach { base.remove(it) }
        return copy(levels = base)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
