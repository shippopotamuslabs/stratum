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

internal sealed interface SessionLengthEdit {
    data object Unchanged : SessionLengthEdit

    data class Active(
        val minutes: Int?,
        val customMode: Boolean,
    ) : SessionLengthEdit
}

internal fun UserTrainingProfile.toScheduleDraft(
    sessionLengthEdit: SessionLengthEdit = SessionLengthEdit.Unchanged,
): ScheduleDraftUi {
    val persistedPreset = sessionLengthMinutes != null && sessionLengthMinutes in SESSION_PRESET_VALUES.keys
    val selection = when (sessionLengthEdit) {
        is SessionLengthEdit.Active -> when {
            sessionLengthEdit.customMode -> ScheduleDraftUi.CUSTOM_KEY
            sessionLengthEdit.minutes == null -> null
            sessionLengthEdit.minutes in SESSION_PRESET_VALUES.keys ->
                SESSION_PRESET_VALUES.getValue(sessionLengthEdit.minutes)
            else -> ScheduleDraftUi.CUSTOM_KEY
        }
        SessionLengthEdit.Unchanged ->
            sessionLengthMinutes?.let {
                if (persistedPreset) SESSION_PRESET_VALUES.getValue(it) else ScheduleDraftUi.CUSTOM_KEY
            }
    }
    val customMinutes = when {
        selection != ScheduleDraftUi.CUSTOM_KEY -> DEFAULT_CUSTOM_SESSION_MINUTES
        sessionLengthEdit is SessionLengthEdit.Active && sessionLengthEdit.customMode ->
            sessionLengthEdit.minutes ?: DEFAULT_CUSTOM_SESSION_MINUTES
        sessionLengthMinutes != null && !persistedPreset -> checkNotNull(sessionLengthMinutes)
        else -> DEFAULT_CUSTOM_SESSION_MINUTES
    }
    return ScheduleDraftUi(
        selectedWeekdays = weekdays.map { it.toShortLabel() }.toSet(),
        sessionLengthSelection = selection,
        customSessionMinutes = customMinutes,
    )
}

internal val SESSION_PRESET_MINUTES: Set<Int> = setOf(45, 60, 75)

internal const val DEFAULT_CUSTOM_SESSION_MINUTES: Int = 90

private val SESSION_PRESET_VALUES: Map<Int, String> = mapOf(45 to "45", 60 to "60", 75 to "75")

/** Training-age chip labels exposed for [ProfileScreen]. */
val TRAINING_AGE_OPTIONS: List<String> = TrainingAge.entries.map { it.toDisplayLabel() }

/** Last-trained chip labels exposed for [ProfileScreen]. */
val LAST_TRAINED_OPTIONS: List<String> = LastTrainedBucket.entries.map { it.toDisplayLabel() }

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
