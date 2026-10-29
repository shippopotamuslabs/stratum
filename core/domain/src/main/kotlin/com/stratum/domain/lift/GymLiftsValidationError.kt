package com.stratum.domain.lift



/**
 * Typed reasons the Gym & Lifts step (OS-4) fails validation. Not display strings —
 * presentation layers map them to user-facing copy (ARCHITECTURE.md §4.3).
 */
enum class GymLiftsValidationError {
    /** Fewer than [ValidateGymLiftsStepUseCase.MIN_ENABLED] lifts enabled. */
    NoLiftsEnabled,
}

/** Outcome of validating the Gym & Lifts step. */
sealed interface GymLiftsValidationResult {
    data object Valid : GymLiftsValidationResult
    data class Invalid(val errors: Set<GymLiftsValidationError>) : GymLiftsValidationResult
}
