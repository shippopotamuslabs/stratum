package com.stratum.core.ui.trainingsetup

import com.stratum.domain.program.schedule.GenerationCycleDefaults
import com.stratum.domain.program.schedule.materializeGeneratedCycleSpace

/** Default custom session length when the user first picks Custom. */
const val DEFAULT_CUSTOM_SESSION_MINUTES = 90

/** Soft-aim midpoints persisted as [com.stratum.domain.onboarding.UserTrainingProfile.sessionLengthMinutes]. */
const val SESSION_BAND_AIM_SHORT = 30
const val SESSION_BAND_AIM_MEDIUM = 50
const val SESSION_BAND_AIM_LONG = 75

/** UI band keys (segmented control). */
const val SESSION_BAND_KEY_SHORT = "20-40"
const val SESSION_BAND_KEY_MEDIUM = "40-60"
const val SESSION_BAND_KEY_LONG = "60-90"


/**
 * UI-shaped snapshot of the Schedule preferences step.
 *
 * Flow: pick **how many** training days (stepper, default 4) → place **training** slots in the
 * cycle → session length. Domain stores [restSlotIndices] as the complement (not a schedule-mode
 * enum). No weekday multi-select.
 *
 * Cycle rules: N&lt;6 → fixed 7-slot week; N=6 → 8 (default) or 7.
 */
data class ScheduleDraftUi(
    /** Target training days per cycle. Default 4; range 1..[MAX_TRAINING_DAYS]. */
    val trainingDayCount: Int = DEFAULT_TRAINING_DAY_COUNT,
    val sessionLengthSelection: String? = null,
    val customSessionMinutes: Int = DEFAULT_CUSTOM_SESSION_MINUTES,
    val showErrors: Boolean = false,
    /**
     * Full cycle length = train + rest. Always set for gen-ready drafts.
     * Default 4 train → 7-slot week with 3 rests.
     */
    val cycleLength: Int? = GenerationCycleDefaults.defaultCycleLength(DEFAULT_TRAINING_DAY_COUNT),
    /** Rest slot indices in `[0, cycleLength)`. */
    val restSlotIndices: Set<Int> =
        GenerationCycleDefaults.defaultRestSlotIndices(
            cycleLength = GenerationCycleDefaults.defaultCycleLength(DEFAULT_TRAINING_DAY_COUNT),
            trainingDayCount = DEFAULT_TRAINING_DAY_COUNT,
        ),
) {
    /** Soft-aim minutes written to the profile (band midpoint or custom value). */
    val resolvedSessionMinutes: Int?
        get() = when (sessionLengthSelection) {
            null -> null
            CUSTOM_KEY -> customSessionMinutes
            SESSION_BAND_KEY_SHORT -> SESSION_BAND_AIM_SHORT
            SESSION_BAND_KEY_MEDIUM -> SESSION_BAND_AIM_MEDIUM
            SESSION_BAND_KEY_LONG -> SESSION_BAND_AIM_LONG
            else -> sessionLengthSelection.toIntOrNull()
        }

    /** Rest placement UI is always shown for valid gen day counts. */
    val showRestPlacement: Boolean
        get() =
            trainingDayCount in MIN_TRAINING_DAY_COUNT..TrainingSetupValidationConstants.MAX_TRAINING_DAYS

    /** Segment control 8-day vs 7-day only when training six days. */
    val showSixDayCycleOptions: Boolean
        get() = trainingDayCount == TrainingSetupValidationConstants.MAX_TRAINING_DAYS

    /** Expected rest count for current cycle (R = cycleLength − N). */
    val expectedRestCount: Int
        get() {
            val length = cycleLength ?: return GenerationCycleDefaults.defaultRestCount(trainingDayCount)
            return (length - trainingDayCount).coerceAtLeast(0)
        }

    /**
     * Training slot indices derived as complement of [restSlotIndices] in `[0, cycleLength)`.
     * This is what the UI selects; rests are stored for domain materialize.
     */
    val trainingSlotIndices: Set<Int>
        get() {
            val length = cycleLength ?: return emptySet()
            return (0 until length).filterNot { index -> index in restSlotIndices }.toSet()
        }

    val hasRestPlacementError: Boolean
        get() {
            if (!showErrors || !showRestPlacement) return false
            val length = cycleLength ?: return true
            return !restSlotIndicesAreValidFor(length)
        }

    val sessionLengthErrorKind: SessionLengthErrorKind?
        get() {
            if (!showErrors) return null
            val minutes = resolvedSessionMinutes
            return when {
                sessionLengthSelection == null -> SessionLengthErrorKind.Required
                minutes == null -> SessionLengthErrorKind.NotANumber
                minutes < TrainingSetupValidationConstants.MIN_SESSION_MINUTES ->
                    SessionLengthErrorKind.TooShort
                minutes > TrainingSetupValidationConstants.MAX_SESSION_MINUTES ->
                    SessionLengthErrorKind.TooLong
                else -> null
            }
        }

    companion object {
        const val CUSTOM_KEY = "custom"
        const val DEFAULT_TRAINING_DAY_COUNT = 4
        const val MIN_TRAINING_DAY_COUNT = 1

        val Empty = ScheduleDraftUi()

        val BAND_KEYS: List<String> =
            listOf(SESSION_BAND_KEY_SHORT, SESSION_BAND_KEY_MEDIUM, SESSION_BAND_KEY_LONG, CUSTOM_KEY)

        /** Soft-aim minutes → band key for restoring UI from profile. */
        val AIM_TO_BAND_KEY: Map<Int, String> =
            mapOf(
                SESSION_BAND_AIM_SHORT to SESSION_BAND_KEY_SHORT,
                SESSION_BAND_AIM_MEDIUM to SESSION_BAND_KEY_MEDIUM,
                SESSION_BAND_AIM_LONG to SESSION_BAND_KEY_LONG,
            )

        val DEFAULT_SIX_DAY_CYCLE_LENGTH: Int =
            GenerationCycleDefaults.SIX_DAY_ROTATING_CYCLE_LENGTH

        val DEFAULT_SIX_DAY_REST_SLOTS: Set<Int> =
            GenerationCycleDefaults.SIX_DAY_ROTATING_DEFAULT_RESTS

        val SIX_DAY_FIXED_WEEK_CYCLE_LENGTH: Int =
            GenerationCycleDefaults.SIX_DAY_FIXED_WEEK_CYCLE_LENGTH

        /**
         * @deprecated Slot identity does not use synthetic weekday labels. Always empty.
         * Kept so call sites compile during migration; prefer [trainingDayCount] + rest slots.
         */

        fun defaultRestSlotsForCycleLength(
            cycleLength: Int,
            trainingDayCount: Int,
        ): Set<Int> =
            GenerationCycleDefaults.defaultRestSlotIndicesForCycleLength(
                cycleLength = cycleLength,
                trainingDayCount = trainingDayCount,
            )

        /**
         * Default cycle length + rests via [materializeGeneratedCycleSpace] (sole application
         * of gen defaults) with [GenerationCycleDefaults] as the explicit rest pattern source.
         */
        fun defaultCycleForTrainingDays(trainingDayCount: Int): Pair<Int, Set<Int>> {
            val length = GenerationCycleDefaults.defaultCycleLength(trainingDayCount)
            val rests =
                GenerationCycleDefaults.defaultRestSlotIndices(
                    cycleLength = length,
                    trainingDayCount = trainingDayCount,
                )
            val space =
                materializeGeneratedCycleSpace(
                    trainingSlotCount = trainingDayCount,
                    restSlotIndicesHint = rests,
                    cycleLengthHint = length,
                )
            return space.cycleLength to space.restSlotIndices
        }
    }
}

/**
 * Sets training day count (1..6). Always applies default cycle space (N+R).
 * Does not invent calendar weekdays — identity is [ScheduleDraftUi.trainingDayCount] + rests.
 */
fun ScheduleDraftUi.withTrainingDayCount(count: Int): ScheduleDraftUi {
    val clamped =
        count.coerceIn(
            ScheduleDraftUi.MIN_TRAINING_DAY_COUNT,
            TrainingSetupValidationConstants.MAX_TRAINING_DAYS,
        )
    if (clamped == trainingDayCount && cycleLength != null && restSlotIndicesAreValidFor(cycleLength)) {
        return this
    }
    val (length, rests) = ScheduleDraftUi.defaultCycleForTrainingDays(clamped)
    return copy(
        trainingDayCount = clamped,
        cycleLength = length,
        restSlotIndices = rests,
    )
}

/**
 * Ensures schedule drafts carry a valid cycle length + rest set for the current train count.
 */
fun ScheduleDraftUi.withSyncedCycleSpace(): ScheduleDraftUi {
    val n = trainingDayCount.coerceIn(
        ScheduleDraftUi.MIN_TRAINING_DAY_COUNT,
        TrainingSetupValidationConstants.MAX_TRAINING_DAYS,
    )
    val length = cycleLength ?: GenerationCycleDefaults.defaultCycleLength(n)
    val validLength =
        when {
            n == TrainingSetupValidationConstants.MAX_TRAINING_DAYS ->
                length in setOf(
                    ScheduleDraftUi.DEFAULT_SIX_DAY_CYCLE_LENGTH,
                    ScheduleDraftUi.SIX_DAY_FIXED_WEEK_CYCLE_LENGTH,
                )
            else -> length == GenerationCycleDefaults.CLASSIC_WEEK_CYCLE_LENGTH
        }
    val resolvedLength =
        if (validLength) {
            length
        } else {
            GenerationCycleDefaults.defaultCycleLength(n)
        }
    val rests =
        if (restSlotIndicesAreValidFor(resolvedLength, n)) {
            restSlotIndices
        } else {
            ScheduleDraftUi.defaultRestSlotsForCycleLength(resolvedLength, n)
        }
    return copy(
        trainingDayCount = n,
        cycleLength = resolvedLength,
        restSlotIndices = rests,
    )
}

/** @deprecated Use [withSyncedCycleSpace]; kept as alias for six-day call sites during migration. */
fun ScheduleDraftUi.withSyncedSixDayCycleSpace(): ScheduleDraftUi = withSyncedCycleSpace()

/** True when day count + rest placement + session length are ready for generation. */
fun ScheduleDraftUi.isReadyForGeneration(): Boolean {
    if (resolvedSessionMinutes == null) return false
    if (trainingDayCount !in
        ScheduleDraftUi.MIN_TRAINING_DAY_COUNT..TrainingSetupValidationConstants.MAX_TRAINING_DAYS
    ) {
        return false
    }
    val length = cycleLength ?: return false
    return restSlotIndicesAreValidFor(length)
}

/**
 * Toggle cycle length for 6 train days: 8 (2 rests) vs 7 (1 rest). No-op when N ≠ 6.
 */
fun ScheduleDraftUi.withSixDayCycleLengthSelected(cycleLength: Int): ScheduleDraftUi {
    if (!showSixDayCycleOptions) return this
    if (cycleLength != ScheduleDraftUi.DEFAULT_SIX_DAY_CYCLE_LENGTH &&
        cycleLength != ScheduleDraftUi.SIX_DAY_FIXED_WEEK_CYCLE_LENGTH
    ) {
        return this
    }
    return copy(
        cycleLength = cycleLength,
        restSlotIndices =
            ScheduleDraftUi.defaultRestSlotsForCycleLength(
                cycleLength = cycleLength,
                trainingDayCount = trainingDayCount,
            ),
    )
}

/**
 * Toggle a **training** slot. Keeps train count at [ScheduleDraftUi.trainingDayCount] by
 * replacing the selected train slot **closest** to the new tap when already at capacity
 * (ties → lower index). Updates [ScheduleDraftUi.restSlotIndices] as the complement.
 */
fun ScheduleDraftUi.withTrainingSlotToggled(slotIndex: Int): ScheduleDraftUi {
    val length = cycleLength ?: return this
    if (slotIndex !in 0 until length) return this
    val expectedTrain = trainingDayCount
    if (expectedTrain <= 0 || expectedTrain > length) return this
    val current = trainingSlotIndices.toMutableSet()
    if (slotIndex in current) {
        current.remove(slotIndex)
    } else if (current.size < expectedTrain) {
        current.add(slotIndex)
    } else {
        val drop =
            current.minWithOrNull(
                compareBy<Int> { index -> kotlin.math.abs(index - slotIndex) }
                    .thenBy { index -> index },
            ) ?: return this
        current.remove(drop)
        current.add(slotIndex)
    }
    val rests = (0 until length).filterNot { index -> index in current }.toSet()
    return copy(restSlotIndices = rests)
}

fun ScheduleDraftUi.restSlotIndicesAreValidFor(
    length: Int,
    trainCount: Int = trainingDayCount,
): Boolean {
    val expectedRests = length - trainCount
    if (expectedRests <= 0) return false
    val allowed = GenerationCycleDefaults.allowedRestCounts(trainCount)
    if (expectedRests !in allowed) return false
    return restSlotIndices.isNotEmpty() &&
        restSlotIndices.size == expectedRests &&
        restSlotIndices.all { index -> index in 0 until length }
}

enum class SessionLengthErrorKind {
    Required,
    CustomBlank,
    NotANumber,
    TooShort,
    TooLong,
}

/**
 * UI-shaped snapshot of the Specialization preferences step.
 */
data class SpecializationDraftUi(
    val selectedMuscleLabels: Set<String> = emptySet(),
    val saturatedSpecializeWarningLabels: List<String> = emptyList(),
    val showErrors: Boolean = false,
) {
    val specializedCount: Int get() = selectedMuscleLabels.size

    val atCap: Boolean get() = specializedCount >= TrainingSetupValidationConstants.MAX_SPECIALIZE

    val isValid: Boolean get() = specializedCount <= TrainingSetupValidationConstants.MAX_SPECIALIZE

    companion object {
        val Empty = SpecializationDraftUi()
    }
}

/**
 * UI-shaped snapshot of the Minimum Volume preferences step.
 */
data class MinimumVolumeDraftUi(
    val selectedMuscleLabels: Set<String> = emptySet(),
    val excludedMuscleLabels: Set<String> = emptySet(),
    val suggestedMuscleLabels: Set<String> = emptySet(),
    val showErrors: Boolean = false,
) {
    val selectedCount: Int get() = selectedMuscleLabels.size

    val atCap: Boolean get() = selectedCount >= TrainingSetupValidationConstants.MAX_MINIMUM_VOLUME

    val isValid: Boolean get() = selectedCount <= TrainingSetupValidationConstants.MAX_MINIMUM_VOLUME

    companion object {
        val Empty = MinimumVolumeDraftUi()
    }
}
