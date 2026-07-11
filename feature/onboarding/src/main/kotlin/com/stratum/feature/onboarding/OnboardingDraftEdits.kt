package com.stratum.feature.onboarding

import com.stratum.domain.onboarding.AdditiveSetEdit
import com.stratum.domain.onboarding.LastTrainedBucket
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.OnboardingWizardState
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.onboarding.SpecializationPreference
import com.stratum.domain.onboarding.TrainingAge
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.onboarding.applyAdditiveSetEdit

/**
 * In-memory draft edits for the profile / schedule steps. Applied on top of the persisted
 * wizard profile until [OnboardingViewModel.next] flushes them via the advance use case.
 */
internal data class ProfileEdit(
    val trainingAge: TrainingAge? = null,
    val lastTrained: LastTrainedBucket? = null,
    val weekdaysEdit: AdditiveSetEdit<Weekday> = AdditiveSetEdit(),
    val sessionLength: SessionLengthEdit = SessionLengthEdit.Unchanged,
)

/**
 * In-memory draft edits for specialization / minimum-volume steps.
 */
internal data class SpecializationEdit(
    val musclesEdit: AdditiveSetEdit<MuscleGroup> = AdditiveSetEdit(),
    val minimumVolumeMusclesEdit: AdditiveSetEdit<MuscleGroup> = AdditiveSetEdit(),
)

/** Bundle for the multi-flow UI-state [combine] so the lambda stays under parameter limits. */
internal data class UiCombineInputs(
    val current: OnboardingWizardState,
    val profile: UserTrainingProfile,
    val sessionLengthEdit: SessionLengthEdit,
    val specialization: SpecializationPreference,
    val ephemeral: OnboardingEphemeralState,
)

internal fun UserTrainingProfile.applyEdit(edit: ProfileEdit): UserTrainingProfile = copy(
    trainingAge = edit.trainingAge ?: trainingAge,
    lastTrained = edit.lastTrained ?: lastTrained,
    weekdays = weekdays.applyAdditiveSetEdit(edit.weekdaysEdit),
    sessionLengthMinutes = when (val sessionLength = edit.sessionLength) {
        SessionLengthEdit.Unchanged -> sessionLengthMinutes
        is SessionLengthEdit.Active -> sessionLength.minutes
    },
)

internal fun SpecializationPreference.applyEdit(edit: SpecializationEdit): SpecializationPreference {
    val base = levels.toMutableMap()
    edit.musclesEdit.added.forEach {
        base[it] = SpecializationLevel.Specialize
    }
    edit.musclesEdit.removed.forEach { removed ->
        if (base[removed] == SpecializationLevel.Specialize) {
            base.remove(removed)
        }
    }
    edit.minimumVolumeMusclesEdit.added.forEach {
        base[it] = SpecializationLevel.MinimumVolume
    }
    edit.minimumVolumeMusclesEdit.removed.forEach { removed ->
        if (base[removed] == SpecializationLevel.MinimumVolume) {
            base.remove(removed)
        }
    }
    return copy(levels = base)
}

internal fun resolveCustomSessionMinutes(
    sessionLengthEdit: SessionLengthEdit,
    persistedMinutes: Int?,
): Int {
    if (sessionLengthEdit is SessionLengthEdit.Active &&
        sessionLengthEdit.customMode &&
        sessionLengthEdit.minutes != null
    ) {
        return sessionLengthEdit.minutes
    }
    if (persistedMinutes != null && persistedMinutes !in SESSION_PRESET_MINUTES) {
        return persistedMinutes
    }
    return DEFAULT_CUSTOM_SESSION_MINUTES
}
