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
 * Icon for the bottom-right pad key based on the active field and filled drafts.
 *
 * - Weight: Check when reps text is non-empty; otherwise NextReps.
 * - Reps: Check when weight is not required or weight text is non-empty; otherwise PrevWeight.
 */
fun setLogKeyboardPrimaryIcon(
    active: SetLogKeyboardField,
    weightText: String,
    repsText: String,
    showWeightInput: Boolean,
): SetLogKeyboardPrimaryIcon =
    when (active) {
        SetLogKeyboardField.Weight ->
            if (repsText.isNotEmpty()) {
                SetLogKeyboardPrimaryIcon.Check
            } else {
                SetLogKeyboardPrimaryIcon.NextReps
            }
        SetLogKeyboardField.Reps -> {
            val weightFilled = !showWeightInput || weightText.isNotEmpty()
            if (weightFilled) {
                SetLogKeyboardPrimaryIcon.Check
            } else {
                SetLogKeyboardPrimaryIcon.PrevWeight
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
    weightText: String,
    repsText: String,
    showWeightInput: Boolean,
    canComplete: Boolean,
): SetLogKeyboardAction =
    when (
        setLogKeyboardPrimaryIcon(
            active = active,
            weightText = weightText,
            repsText = repsText,
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
