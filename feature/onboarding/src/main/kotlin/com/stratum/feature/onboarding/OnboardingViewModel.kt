package com.stratum.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.onboarding.CompleteOnboardingUseCase
import com.stratum.domain.onboarding.GetOnboardingStateUseCase
import com.stratum.domain.onboarding.OnboardingState
import com.stratum.domain.onboarding.OnboardingStep
import com.stratum.domain.onboarding.ProfileValidationResult
import com.stratum.domain.onboarding.SaveOnboardingProgressUseCase
import com.stratum.domain.onboarding.ScheduleValidationResult
import com.stratum.domain.onboarding.UpdateUserTrainingProfileUseCase
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.onboarding.ValidateProfileStepUseCase
import com.stratum.domain.onboarding.ValidateScheduleStepUseCase
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
    private val completeOnboarding: CompleteOnboardingUseCase,
    private val validateProfileStep: ValidateProfileStepUseCase,
    private val validateScheduleStep: ValidateScheduleStepUseCase,
) : ViewModel() {

    /** Uncommitted field edits not yet persisted. */
    private val edits = MutableStateFlow(ProfileEdit())

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

    val uiState: StateFlow<OnboardingUiState> =
        combine(state, effectiveProfile, showErrors) { current, profile, errs ->
            OnboardingUiState(
                step = current.currentStep,
                isComplete = current.isComplete,
                profileDraft = profile.toProfileDraft().copy(showErrors = errs),
                scheduleDraft = profile.toScheduleDraft().copy(showErrors = errs),
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
        edits.value = if (day in current) {
            edits.value.copy(weekdaysRemoved = edits.value.weekdaysRemoved + day)
        } else {
            edits.value.copy(weekdaysAdded = edits.value.weekdaysAdded + day)
        }
    }

    fun onSessionPresetSelected(value: String?) {
        val minutes = value?.let { if (it == ScheduleDraftUi.CUSTOM_KEY) null else it.toIntOrNull() }
        edits.value = edits.value.copy(sessionLengthMinutes = minutes)
    }

    fun onCustomSessionChange(input: String) {
        edits.value = edits.value.copy(sessionLengthMinutes = input.filter { it.isDigit() }.toIntOrNull())
    }

    // --- Navigation -------------------------------------------------------------

    fun next() {
        val step = state.value.currentStep
        val profile = effectiveProfile.value
        val stepValid = when (step) {
            OnboardingStep.Profile -> validateProfileStep(profile) is ProfileValidationResult.Valid
            OnboardingStep.Schedule -> validateScheduleStep(profile) is ScheduleValidationResult.Valid
            else -> true
        }
        if (!stepValid) {
            showErrors.value = true
            return
        }
        val nextStep = step.next() ?: return
        viewModelScope.launch {
            updateUserTrainingProfile(profile)
            saveOnboardingProgress(nextStep)
            edits.value = ProfileEdit()
            showErrors.value = false
        }
    }

    fun back() {
        val previousStep = state.value.currentStep.previous() ?: return
        viewModelScope.launch {
            updateUserTrainingProfile(effectiveProfile.value)
            saveOnboardingProgress(previousStep)
            edits.value = ProfileEdit()
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

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
