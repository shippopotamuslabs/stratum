package com.stratum.core.ui.component.workoutlog

/** Which set-log field the custom keyboard is editing. */
enum class SetLogKeyboardField {
    Weight,
    Reps,
}

/** Primary (bottom-right) control shown on the set-log pad. */
enum class SetLogKeyboardPrimaryIcon {
    /** Advance weight → reps. */
    NextReps,

    /** Return reps → weight. */
    PrevWeight,

    /** Complete set (or dismiss when complete is unavailable). */
    Check,
}

/** Result of tapping the primary (bottom-right) pad action. */
enum class SetLogKeyboardAction {
    Dismiss,
    FocusWeight,
    FocusReps,
    Complete,
}

/**
 * Appends a pad key to [current]. Digits always append; `.` appends only when
 * [allowDecimal] and the value does not already contain a decimal point.
 */
fun appendSetLogKey(
    current: String,
    key: Char,
    allowDecimal: Boolean,
): String =
    when {
        key.isDigit() -> current + key
        key == '.' && allowDecimal && !current.contains('.') -> current + "."
        else -> current
    }

/** Drops the last character, or returns [current] when empty. */
fun backspaceSetLogValue(current: String): String =
    if (current.isEmpty()) current else current.dropLast(1)

/**
 * Whether a weight recommendation is available for complete-gate purposes.
 * Bodyweight rows have no weight lever — treated as always satisfied.
 */
fun hasWeightRecommendation(
    showWeightInput: Boolean,
    recommendedWeightAmount: Double,
    weightPlaceholder: String,
    assistMode: Boolean = false,
): Boolean {
    if (!showWeightInput) return true
    val magnitude =
        if (assistMode) {
            kotlin.math.abs(recommendedWeightAmount)
        } else {
            recommendedWeightAmount
        }
    return magnitude > 0.0 || weightPlaceholder.isNotEmpty()
}

/** Whether a reps recommendation / placeholder can stand in for typed reps. */
fun hasRepsRecommendation(
    recommendedReps: Int,
    repsPlaceholder: String,
): Boolean = recommendedReps > 0 || repsPlaceholder.isNotEmpty()

/** True when [text] parses as a weight the log path can persist (not junk like `"abc"`). */
fun isUsableWeightText(text: String): Boolean = text.toDoubleOrNull() != null

/** True when [text] parses as integer reps. */
fun isUsableRepsText(text: String): Boolean = text.toIntOrNull() != null

/**
 * Single shared gate for **committing** a set (keyboard Check + enabling check-to-complete).
 *
 * - Weight: bodyweight always OK; else **parseable** typed text **or** weight recommendation.
 * - Reps: **parseable** typed text **or** reps recommendation / placeholder.
 * - RIR is always present on the row — not part of the gate.
 *
 * Uncheck / unlog is separate — use [isSetLogCheckboxEnabled].
 */
fun canCompleteSetLog(
    showWeightInput: Boolean,
    weightText: String,
    hasWeightRecommendation: Boolean,
    repsText: String,
    hasRepsRecommendation: Boolean,
): Boolean {
    val weightOk =
        !showWeightInput ||
            isUsableWeightText(weightText) ||
            hasWeightRecommendation
    val repsOk = isUsableRepsText(repsText) || hasRepsRecommendation
    return weightOk && repsOk
}

/**
 * Checkbox enablement: always allow interaction on a **completed** row (to unlog), else
 * require [canComplete] so incomplete rows cannot check without weight/reps (or recs).
 */
fun isSetLogCheckboxEnabled(
    isComplete: Boolean,
    canComplete: Boolean,
): Boolean = isComplete || canComplete

/**
 * Derives [canCompleteSetLog] from the presentation fields already on a set row.
 */
fun canCompleteSetLogFromFields(
    showWeightInput: Boolean,
    weightText: String,
    weightPlaceholder: String,
    recommendedWeightAmount: Double,
    assistMode: Boolean,
    repsText: String,
    repsPlaceholder: String,
    recommendedReps: Int,
): Boolean =
    canCompleteSetLog(
        showWeightInput = showWeightInput,
        weightText = weightText,
        hasWeightRecommendation =
            hasWeightRecommendation(
                showWeightInput = showWeightInput,
                recommendedWeightAmount = recommendedWeightAmount,
                weightPlaceholder = weightPlaceholder,
                assistMode = assistMode,
            ),
        repsText = repsText,
        hasRepsRecommendation =
            hasRepsRecommendation(
                recommendedReps = recommendedReps,
                repsPlaceholder = repsPlaceholder,
            ),
    )

/**
 * Icon for the bottom-right pad key.
 *
 * When [canComplete] is true → always Check (same rule as the row checkbox).
 * Otherwise navigate toward the unfilled field: weight → reps, reps → weight.
 */
fun setLogKeyboardPrimaryIcon(
    active: SetLogKeyboardField,
    canComplete: Boolean,
    showWeightInput: Boolean,
): SetLogKeyboardPrimaryIcon =
    if (canComplete) {
        SetLogKeyboardPrimaryIcon.Check
    } else {
        when (active) {
            SetLogKeyboardField.Weight -> SetLogKeyboardPrimaryIcon.NextReps
            SetLogKeyboardField.Reps ->
                if (showWeightInput) {
                    SetLogKeyboardPrimaryIcon.PrevWeight
                } else {
                    // Bodyweight without a reps value/rec — Check dismisses (cannot complete).
                    SetLogKeyboardPrimaryIcon.Check
                }
        }
    }

/**
 * Action for the bottom-right pad key.
 *
 * When the icon is [SetLogKeyboardPrimaryIcon.Check]: [Complete] if [canComplete], else [Dismiss].
 * Navigation icons map to [FocusReps] / [FocusWeight].
 */
fun resolveSetLogKeyboardAction(
    active: SetLogKeyboardField,
    canComplete: Boolean,
    showWeightInput: Boolean,
): SetLogKeyboardAction =
    when (
        setLogKeyboardPrimaryIcon(
            active = active,
            canComplete = canComplete,
            showWeightInput = showWeightInput,
        )
    ) {
        SetLogKeyboardPrimaryIcon.NextReps -> SetLogKeyboardAction.FocusReps
        SetLogKeyboardPrimaryIcon.PrevWeight -> SetLogKeyboardAction.FocusWeight
        SetLogKeyboardPrimaryIcon.Check ->
            if (canComplete) {
                SetLogKeyboardAction.Complete
            } else {
                SetLogKeyboardAction.Dismiss
            }
    }

