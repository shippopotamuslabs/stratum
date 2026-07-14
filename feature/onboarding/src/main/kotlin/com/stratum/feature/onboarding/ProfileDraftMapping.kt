package com.stratum.feature.onboarding

import com.stratum.core.ui.trainingsetup.ScheduleDraftUi
import com.stratum.core.ui.trainingsetup.TrainingSetupValidationConstants
import com.stratum.core.ui.trainingsetup.withSyncedCycleSpace
import com.stratum.domain.onboarding.LastTrainedBucket
import com.stratum.domain.onboarding.TrainingAge
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.schedule.GenerationCycleDefaults

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
    generationCycleLength: Int? = null,
    generationRestSlotIndices: Set<Int>? = null,
    trainingDayCount: Int? = null,
): ScheduleDraftUi {
    val persistedPreset =
        sessionLengthMinutes != null && sessionLengthMinutes in ScheduleDraftUi.AIM_TO_BAND_KEY.keys
    val selection = when (sessionLengthEdit) {
        is SessionLengthEdit.Active -> when {
            sessionLengthEdit.customMode -> ScheduleDraftUi.CUSTOM_KEY
            sessionLengthEdit.minutes == null -> null
            sessionLengthEdit.minutes in ScheduleDraftUi.AIM_TO_BAND_KEY.keys ->
                ScheduleDraftUi.AIM_TO_BAND_KEY.getValue(sessionLengthEdit.minutes)
            else -> ScheduleDraftUi.CUSTOM_KEY
        }
        SessionLengthEdit.Unchanged ->
            sessionLengthMinutes?.let {
                if (persistedPreset) {
                    ScheduleDraftUi.AIM_TO_BAND_KEY.getValue(it)
                } else {
                    ScheduleDraftUi.CUSTOM_KEY
                }
            }
    }
    val customMinutes = when {
        selection != ScheduleDraftUi.CUSTOM_KEY -> DEFAULT_CUSTOM_SESSION_MINUTES
        sessionLengthEdit is SessionLengthEdit.Active && sessionLengthEdit.customMode ->
            sessionLengthEdit.minutes ?: DEFAULT_CUSTOM_SESSION_MINUTES
        sessionLengthMinutes != null && !persistedPreset -> checkNotNull(sessionLengthMinutes)
        else -> DEFAULT_CUSTOM_SESSION_MINUTES
    }
    val dayCount =
        (
            trainingDayCount
                ?: this.generationTrainingDayCount
                ?: ScheduleDraftUi.DEFAULT_TRAINING_DAY_COUNT
            ).coerceIn(
            ScheduleDraftUi.MIN_TRAINING_DAY_COUNT,
            TrainingSetupValidationConstants.MAX_TRAINING_DAYS,
        )
    val cycle =
        resolveGenerationScheduleDraft(
            trainingDayCount = dayCount,
            cycleLength = generationCycleLength ?: this.generationCycleLength,
            restSlotIndices =
                generationRestSlotIndices
                    ?: this.generationRestSlotIndices.takeIf { rests -> rests.isNotEmpty() },
        )
    return ScheduleDraftUi(
        trainingDayCount = dayCount,
        sessionLengthSelection = selection,
        customSessionMinutes = customMinutes,
        cycleLength = cycle.cycleLength,
        restSlotIndices = cycle.restSlotIndices,
    ).withSyncedCycleSpace()
}

/**
 * Resolves generation cycle-space as a [ScheduleDraftUi] fragment so shared cycle ops
 * ([withSixDayCycleLengthSelected], [withTrainingSlotToggled]) can run for any train count.
 * Identity is train count + rest slots — not calendar weekdays.
 */
internal fun resolveGenerationScheduleDraft(
    trainingDayCount: Int,
    cycleLength: Int?,
    restSlotIndices: Set<Int>?,
): ScheduleDraftUi {
    val n =
        trainingDayCount.coerceIn(
            ScheduleDraftUi.MIN_TRAINING_DAY_COUNT,
            TrainingSetupValidationConstants.MAX_TRAINING_DAYS,
        )
    val length = cycleLength ?: GenerationCycleDefaults.defaultCycleLength(n)
    val rests =
        restSlotIndices
            ?: ScheduleDraftUi.defaultRestSlotsForCycleLength(length, trainingDayCount = n)
    return ScheduleDraftUi(
        trainingDayCount = n,
        cycleLength = length,
        restSlotIndices = rests,
    ).withSyncedCycleSpace()
}

/** @deprecated Prefer [resolveGenerationScheduleDraft] for all train counts. */
internal fun resolveSixDayScheduleDraft(
    trainingDayCount: Int,
    cycleLength: Int?,
    restSlotIndices: Set<Int>?,
): ScheduleDraftUi? {
    if (trainingDayCount != 6) return null
    return resolveGenerationScheduleDraft(trainingDayCount, cycleLength, restSlotIndices)
}

/** @deprecated Prefer [resolveGenerationScheduleDraft]. */
internal fun resolveSixDayScheduleUi(
    trainingDayCount: Int,
    cycleLength: Int?,
    restSlotIndices: Set<Int>?,
): SixDayScheduleUi? {
    if (trainingDayCount != 6) return null
    val draft = resolveGenerationScheduleDraft(trainingDayCount, cycleLength, restSlotIndices)
    return SixDayScheduleUi(
        cycleLength = draft.cycleLength!!,
        restSlotIndices = draft.restSlotIndices,
    )
}

internal data class SixDayScheduleUi(
    val cycleLength: Int,
    val restSlotIndices: Set<Int>,
)

/** Soft-aim minutes for known UI bands (30 / 50 / 75). */
internal val SESSION_PRESET_MINUTES: Set<Int> = ScheduleDraftUi.AIM_TO_BAND_KEY.keys

internal const val DEFAULT_CUSTOM_SESSION_MINUTES: Int =
    com.stratum.core.ui.trainingsetup.DEFAULT_CUSTOM_SESSION_MINUTES

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
    LastTrainedBucket.WithinLastThreeMonths -> "Within the last 3 months"
    LastTrainedBucket.MoreThanThreeMonthsAgo -> "More than 3 months ago"
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
