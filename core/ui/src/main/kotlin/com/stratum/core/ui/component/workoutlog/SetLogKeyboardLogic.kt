package com.stratum.core.ui.component.workoutlog

/** Which set-log field the custom keyboard is editing. */
enum class SetLogKeyboardField {
    Weight,
    Reps,
}

/** Which bound of a rep-range pair the slim pad is editing. */
enum class RepRangeKeyboardField {
    Min,
    Max,
}

/** Toggles between min and max fields on the rep-range pad "over" key. */
fun toggleRepRangeField(active: RepRangeKeyboardField): RepRangeKeyboardField =
    when (active) {
        RepRangeKeyboardField.Min -> RepRangeKeyboardField.Max
        RepRangeKeyboardField.Max -> RepRangeKeyboardField.Min
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
 * Editable numeric buffer with a caret index in `0..text.length` (between characters).
 * Used by custom set-log / rep-range pads so insert and backspace are not end-only.
 */
data class NumericEditBuffer(
    val text: String,
    val cursor: Int,
) {
    /** Caret clamped into a valid range for [text]. */
    fun coerced(): NumericEditBuffer {
        val clamped = cursor.coerceIn(0, text.length)
        return if (clamped == cursor) this else copy(cursor = clamped)
    }
}

/**
 * Inserts a pad key at the caret. Digits always insert; `.` inserts only when
 * [allowDecimal] and the value does not already contain a decimal point.
 * Cursor advances past the inserted character on success.
 */
fun insertNumericKey(
    buffer: NumericEditBuffer,
    key: Char,
    allowDecimal: Boolean,
): NumericEditBuffer {
    val current = buffer.coerced()
    val insert =
        when {
            key.isDigit() -> key.toString()
            key == '.' && allowDecimal && !current.text.contains('.') -> "."
            else -> return current
        }
    val index = current.cursor
    val nextText = current.text.substring(0, index) + insert + current.text.substring(index)
    return NumericEditBuffer(text = nextText, cursor = index + insert.length)
}

/**
 * Deletes the character before the caret, or no-ops when the caret is at index 0.
 */
fun backspaceNumericBuffer(buffer: NumericEditBuffer): NumericEditBuffer {
    val current = buffer.coerced()
    if (current.cursor <= 0) return current
    val index = current.cursor
    val nextText = current.text.removeRange(index - 1, index)
    return NumericEditBuffer(text = nextText, cursor = index - 1)
}

/** @deprecated Prefer [insertNumericKey]. Kept as a name alias for set-log call sites. */
fun insertSetLogKey(
    buffer: NumericEditBuffer,
    key: Char,
    allowDecimal: Boolean,
): NumericEditBuffer = insertNumericKey(buffer, key, allowDecimal)

/** @deprecated Prefer [backspaceNumericBuffer]. */
fun backspaceSetLogBuffer(buffer: NumericEditBuffer): NumericEditBuffer =
    backspaceNumericBuffer(buffer)

/**
 * Appends a pad key to [current] (caret at end). Digits always append; `.` appends only when
 * [allowDecimal] and the value does not already contain a decimal point.
 */
fun appendNumericKey(
    current: String,
    key: Char,
    allowDecimal: Boolean,
): String =
    insertNumericKey(
        buffer = NumericEditBuffer(text = current, cursor = current.length),
        key = key,
        allowDecimal = allowDecimal,
    ).text

/** Drops the last character, or returns [current] when empty. */
fun backspaceNumericValue(current: String): String =
    backspaceNumericBuffer(NumericEditBuffer(text = current, cursor = current.length)).text

/** @deprecated Prefer [appendNumericKey]. */
fun appendSetLogKey(
    current: String,
    key: Char,
    allowDecimal: Boolean,
): String = appendNumericKey(current, key, allowDecimal)

/** @deprecated Prefer [backspaceNumericValue]. */
fun backspaceSetLogValue(current: String): String = backspaceNumericValue(current)

/**
 * Parses a rep-range pad draft into a non-negative int for ViewModel commit.
 * Empty / non-numeric drafts become 0 (caller runs applyBand only on intentional commit).
 */
fun parseRepRangeDraft(text: String): Int = text.toIntOrNull()?.coerceAtLeast(0) ?: 0

/**
 * Why a rep-range draft cannot be committed. UI maps these to copy; pure logic stays string-free.
 *
 * Floor matches [com.stratum.domain.program.ProgressionRepRangePolicy.MIN_STORED_REP_MIN].
 */
enum class RepRangeDraftIssue {
    /** Draft satisfies min ≥ floor and max ≥ min + 1. */
    None,

    /** Blank field(s) on leave/collapse — must enter a value. */
    MissingValue,

    /** Typed value 0..floor-1 (0 is a real invalid value, not blank). */
    BelowMinFloor,

    /** Both sides set but max ≤ min (must be at least min + 1). */
    MaxNotAboveMin,
}

/**
 * Live + commit validation for min/max draft strings on the rep-range pad.
 *
 * - **Blank** is soft-valid while typing; invalid only when [requireCompleteBand] (hide / leave).
 * - **0–4** (any typed value &lt; [minFloor]) is always invalid.
 * - When both sides parse, **max must be ≥ min + 1**.
 */
data class RepRangeDraftValidation(
    val minInvalid: Boolean,
    val maxInvalid: Boolean,
    val issue: RepRangeDraftIssue,
) {
    val isValid: Boolean get() = issue == RepRangeDraftIssue.None && !minInvalid && !maxInvalid
}

/**
 * @param minFloor Minimum allowed rep target (domain [MIN_STORED_REP_MIN], default 5).
 * @param requireCompleteBand When true (hide, leave field session), blank sides are invalid.
 */
fun validateRepRangeDrafts(
    minText: String,
    maxText: String,
    minFloor: Int = com.stratum.domain.program.ProgressionRepRangePolicy.MIN_STORED_REP_MIN,
    requireCompleteBand: Boolean = false,
): RepRangeDraftValidation {
    val minBlank = minText.isEmpty()
    val maxBlank = maxText.isEmpty()
    val minRaw = minText.toIntOrNull()
    val maxRaw = maxText.toIntOrNull()

    var minInvalid = false
    var maxInvalid = false
    var issue = RepRangeDraftIssue.None

    // Typed 0–floor-1: always hard-invalid (0 is not “blank”).
    if (!minBlank && minRaw != null && minRaw < minFloor) {
        minInvalid = true
        issue = RepRangeDraftIssue.BelowMinFloor
    }
    if (!maxBlank && maxRaw != null && maxRaw < minFloor) {
        maxInvalid = true
        if (issue == RepRangeDraftIssue.None) {
            issue = RepRangeDraftIssue.BelowMinFloor
        }
    }

    // Both present and at/above floor: max must be strictly greater than min.
    val minOk = minRaw?.takeIf { it >= minFloor }
    val maxOk = maxRaw?.takeIf { it >= minFloor }
    if (minOk != null && maxOk != null && maxOk <= minOk) {
        maxInvalid = true
        if (issue == RepRangeDraftIssue.None) {
            issue = RepRangeDraftIssue.MaxNotAboveMin
        }
    }

    // Blank (or non-numeric) only enforced on leave/collapse.
    if (requireCompleteBand) {
        if (minBlank || minRaw == null) {
            minInvalid = true
            if (issue == RepRangeDraftIssue.None) {
                issue = RepRangeDraftIssue.MissingValue
            }
        }
        if (maxBlank || maxRaw == null) {
            maxInvalid = true
            if (issue == RepRangeDraftIssue.None) {
                issue = RepRangeDraftIssue.MissingValue
            }
        }
    }

    return RepRangeDraftValidation(
        minInvalid = minInvalid,
        maxInvalid = maxInvalid,
        issue = issue,
    )
}

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
 * Icon for the key **above** the primary action (row of 7-8-9).
 *
 * Only when primary is [SetLogKeyboardPrimaryIcon.Check] **and** weight↔reps navigation is
 * available — avoids duplicating a chevron that already occupies the primary slot.
 * Returns null when the elevated slot should stay empty.
 */
fun setLogKeyboardElevatedNavIcon(
    active: SetLogKeyboardField,
    canComplete: Boolean,
    showWeightInput: Boolean,
): SetLogKeyboardPrimaryIcon? {
    val primary =
        setLogKeyboardPrimaryIcon(
            active = active,
            canComplete = canComplete,
            showWeightInput = showWeightInput,
        )
    if (primary != SetLogKeyboardPrimaryIcon.Check) return null
    // Bodyweight rows only have reps — nothing to toggle.
    if (!showWeightInput) return null
    return when (active) {
        SetLogKeyboardField.Weight -> SetLogKeyboardPrimaryIcon.NextReps
        SetLogKeyboardField.Reps -> SetLogKeyboardPrimaryIcon.PrevWeight
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

/**
 * Action for the elevated nav key (above primary), or null when that key is hidden.
 */
fun resolveSetLogKeyboardElevatedNavAction(
    active: SetLogKeyboardField,
    canComplete: Boolean,
    showWeightInput: Boolean,
): SetLogKeyboardAction? =
    when (
        setLogKeyboardElevatedNavIcon(
            active = active,
            canComplete = canComplete,
            showWeightInput = showWeightInput,
        )
    ) {
        SetLogKeyboardPrimaryIcon.NextReps -> SetLogKeyboardAction.FocusReps
        SetLogKeyboardPrimaryIcon.PrevWeight -> SetLogKeyboardAction.FocusWeight
        SetLogKeyboardPrimaryIcon.Check, null -> null
    }

