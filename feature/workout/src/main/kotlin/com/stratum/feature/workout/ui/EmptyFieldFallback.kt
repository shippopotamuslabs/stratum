package com.stratum.feature.workout.ui

/**
 * How empty weight/reps fields are filled when resolving a commit.
 *
 * - [AcceptPlaceholders]: empty → recommendation / repMin (checkbox or keyboard Complete —
 *   accepting the visible placeholder as the logged value, including amend-after-clear).
 * - [KeepPreviousLogged]: empty → previously logged values only (RIR-only auto-commit on a
 *   complete set, so clearing weight mid-edit then tapping RIR does not wipe to a rec).
 */
enum class EmptyFieldFallback {
    AcceptPlaceholders,
    KeepPreviousLogged,
}

/**
 * Resolves weight and reps to persist when committing a set log.
 *
 * Bodyweight rows force weight to 0 regardless of entry.
 */
internal fun resolveCommitLogValues(
    emptyFieldFallback: EmptyFieldFallback,
    enteredWeight: String,
    enteredReps: String,
    showWeightInput: Boolean,
    recommendedWeightAmount: Double,
    recommendedReps: Int,
    repMin: Int,
    previousLoggedWeight: Double?,
    previousLoggedReps: Int?,
): Pair<Double, Int> {
    val suggestedReps = if (recommendedReps > 0) recommendedReps else repMin

    val finalWeight =
        when {
            !showWeightInput -> 0.0
            enteredWeight.isNotEmpty() ->
                enteredWeight.toDoubleOrNull()
                    ?: when (emptyFieldFallback) {
                        EmptyFieldFallback.KeepPreviousLogged ->
                            previousLoggedWeight ?: 0.0
                        EmptyFieldFallback.AcceptPlaceholders -> 0.0
                    }
            emptyFieldFallback == EmptyFieldFallback.KeepPreviousLogged ->
                previousLoggedWeight ?: 0.0
            else -> recommendedWeightAmount
        }

    val finalReps =
        when {
            enteredReps.isNotEmpty() ->
                enteredReps.toIntOrNull()
                    ?: when (emptyFieldFallback) {
                        EmptyFieldFallback.KeepPreviousLogged ->
                            previousLoggedReps ?: 0
                        EmptyFieldFallback.AcceptPlaceholders -> 0
                    }
            emptyFieldFallback == EmptyFieldFallback.KeepPreviousLogged ->
                previousLoggedReps ?: suggestedReps
            else -> suggestedReps
        }

    return finalWeight to finalReps
}

/**
 * First-complete / explicit-complete resolution (empty → recommendations).
 */
internal fun resolveCheckboxLogValues(
    enteredWeight: String,
    enteredReps: String,
    showWeightInput: Boolean,
    recommendedWeightAmount: Double,
    recommendedReps: Int,
    repMin: Int,
): Pair<Double, Int> =
    resolveCommitLogValues(
        emptyFieldFallback = EmptyFieldFallback.AcceptPlaceholders,
        enteredWeight = enteredWeight,
        enteredReps = enteredReps,
        showWeightInput = showWeightInput,
        recommendedWeightAmount = recommendedWeightAmount,
        recommendedReps = recommendedReps,
        repMin = repMin,
        previousLoggedWeight = null,
        previousLoggedReps = null,
    )
