package com.stratum.feature.onboarding

import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.Lift
import com.stratum.domain.onboarding.SpecializationPreference
import com.stratum.domain.onboarding.UserTrainingProfile

/**
 * Maps the aggregate onboarding state to the read-only [ReviewDraftUi] (OS-5). This is a
 * pure derivation over the same domain streams that feed the editable drafts — no new reads,
 * no new use cases. Composables only ever see [ReviewDraftUi] (ARCHITECTURE.md §4.3).
 *
 * Label rendering reuses the per-step mapping extensions
 * ([TrainingAge.toDisplayLabel], [LastTrainedBucket.toDisplayLabel], [Weekday.toShortLabel],
 * [com.stratum.domain.onboarding.MuscleGroup.toDisplayLabel], [GymPreset.toDisplayLabel])
 * so the Review summary stays visually consistent with the step the user answered it on.
 */
internal fun UserTrainingProfile.toReviewProfileSection(
    specialization: SpecializationPreference,
    lifts: List<Lift>,
    selectedPreset: GymPreset?,
): ReviewDraftUi = ReviewDraftUi(
    trainingAge = trainingAge?.toDisplayLabel(),
    lastTrained = lastTrained?.toDisplayLabel(),
    weekdays = weekdays.sortedBy { it.ordinal }.map { it.toShortLabel() },
    sessionMinutes = sessionLengthMinutes,
    // Calendar order keeps the summary stable regardless of the order the user tapped days.
    specialization = specialization.specializedMuscles
        .sortedBy { it.ordinal }
        .map { it.toDisplayLabel() },
    presetLabel = selectedPreset?.toDisplayLabel(),
    enabledLiftCount = lifts.count { it.enabled },
)
