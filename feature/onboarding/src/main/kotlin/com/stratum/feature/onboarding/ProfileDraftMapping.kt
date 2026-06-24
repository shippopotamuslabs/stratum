package com.stratum.feature.onboarding

import com.stratum.domain.onboarding.LastTrainedBucket
import com.stratum.domain.onboarding.TrainingAge
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.onboarding.Weekday

/**
 * Maps the domain [UserTrainingProfile] draft to UI-shaped presentation snapshots.
 * Composables only ever see [ProfileDraftUi] / [ScheduleDraftUi] (ARCHITECTURE.md §4.3).
 */
internal fun UserTrainingProfile.toProfileDraft(): ProfileDraftUi =
    ProfileDraftUi(
        trainingAgeSelection = trainingAge?.toDisplayLabel(),
        lastTrainedSelection = lastTrained?.toDisplayLabel(),
    )

internal fun UserTrainingProfile.toScheduleDraft(): ScheduleDraftUi {
    val isPreset = sessionLengthMinutes != null && sessionLengthMinutes in SESSION_PRESET_VALUES.keys
    return ScheduleDraftUi(
        selectedWeekdays = weekdays.map { it.toShortLabel() }.toSet(),
        sessionLengthSelection = sessionLengthMinutes?.let {
            if (isPreset) SESSION_PRESET_VALUES[it] else ScheduleDraftUi.CUSTOM_KEY
        },
        customSessionInput = if (sessionLengthMinutes != null && !isPreset) {
            sessionLengthMinutes.toString()
        } else {
            ""
        },
    )
}

private val SESSION_PRESET_VALUES: Map<Int, String> = mapOf(45 to "45", 60 to "60", 75 to "75")

/**
 * Training-age chip labels. Year ranges are UI guidance grounded in empirical training-age
 * frameworks (see the [com.stratum.domain.onboarding.TrainingAge] KDoc) — the enum itself
 * is crisp and is what the ViewModel round-trips.
 */
internal fun TrainingAge.toDisplayLabel(): String = when (this) {
    TrainingAge.Beginner -> "0–2 years"
    TrainingAge.EarlyIntermediate -> "2–4 years"
    TrainingAge.Intermediate -> "4–7 years"
    TrainingAge.LateIntermediate -> "7–10 years"
    TrainingAge.Advanced -> "10+ years"
}

internal fun trainingAgeFromLabel(label: String): TrainingAge? =
    TrainingAge.entries.firstOrNull { it.toDisplayLabel() == label }

internal fun LastTrainedBucket.toDisplayLabel(): String = when (this) {
    LastTrainedBucket.Never -> "Never"
    LastTrainedBucket.WithinLastMonth -> "Within the last month"
    LastTrainedBucket.OneToSixMonthsAgo -> "1–6 months ago"
    LastTrainedBucket.SixToTwelveMonthsAgo -> "6–12 months ago"
    LastTrainedBucket.MoreThanAYearAgo -> "Over a year ago"
}

internal fun lastTrainedFromLabel(label: String): LastTrainedBucket? =
    LastTrainedBucket.entries.firstOrNull { it.toDisplayLabel() == label }

internal fun Weekday.toShortLabel(): String = when (this) {
    Weekday.Monday -> "Mon"
    Weekday.Tuesday -> "Tue"
    Weekday.Wednesday -> "Wed"
    Weekday.Thursday -> "Thu"
    Weekday.Friday -> "Fri"
    Weekday.Saturday -> "Sat"
    Weekday.Sunday -> "Sun"
}
