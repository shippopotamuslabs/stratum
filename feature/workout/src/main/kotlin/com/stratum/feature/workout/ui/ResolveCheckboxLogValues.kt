package com.stratum.feature.workout.ui

/**
 * Resolves weight and reps to persist when the set checkbox is checked and the weight/reps
 * fields may still be empty (placeholders only).
 *
 * Empty fields fall back to the visible suggestion:
 * - Weight → [recommendedWeightAmount] (mirrors the weight placeholder)
 * - Reps → [recommendedReps] when a concrete suggestion exists (> 0), otherwise [repMin]
 *   (bottom of the prescribed range when only a range placeholder is shown)
 *
 * Bodyweight rows force weight to 0 regardless of entry.
 */
internal fun resolveCheckboxLogValues(
    enteredWeight: String,
    enteredReps: String,
    showWeightInput: Boolean,
    recommendedWeightAmount: Double,
    recommendedReps: Int,
    repMin: Int,
): Pair<Double, Int> {
    val finalWeight = if (showWeightInput) {
        if (enteredWeight.isEmpty()) {
            recommendedWeightAmount
        } else {
            enteredWeight.toDoubleOrNull() ?: 0.0
        }
    } else {
        0.0
    }
    val finalReps = if (enteredReps.isEmpty()) {
        // Prefer the concrete suggestion shown as placeholder (live projection, history
        // baseline, or fatigue). Fall back to repMin when recommendedReps is 0 — e.g. a
        // range-only placeholder like "4-6" with no history, or RIR-only backoff cold start.
        if (recommendedReps > 0) recommendedReps else repMin
    } else {
        enteredReps.toIntOrNull() ?: 0
    }
    return finalWeight to finalReps
}
