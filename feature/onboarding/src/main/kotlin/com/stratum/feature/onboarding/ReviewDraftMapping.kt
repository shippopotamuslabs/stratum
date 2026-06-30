package com.stratum.feature.onboarding

import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.Lift
import com.stratum.domain.onboarding.SpecializationPreference
import com.stratum.domain.onboarding.UserTrainingProfile

/**
 * Maps the aggregate onboarding state to the read-only [ReviewDraftUi] (OS-5).
 */
internal fun UserTrainingProfile.toReviewProfileSection(
    specialization: SpecializationPreference,
    lifts: List<Lift>,
    selectedPreset: GymPreset?,
): ReviewDraftUi = ReviewDraftUi(
    trainingAge = trainingAge?.toDisplayLabel(),
    lastTrained = lastTrained?.toDisplayLabel(),
    weekdays = weekdays.sortedBy { it.ordinal }.map { it.toShortLabel() },
    specialization = specialization.specializedMuscles
        .sortedBy { it.ordinal }
        .map { it.displayLabel },
    minimumVolume = specialization.minimumVolumeMuscles
        .sortedBy { it.ordinal }
        .map { it.displayLabel },
    presetLabel = selectedPreset?.displayName,
    enabledLiftCount = lifts.count { it.enabled },
    sessionMinutes = sessionLengthMinutes,
)
