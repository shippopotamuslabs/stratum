package com.stratum.domain.onboarding



/**
 * Typed reasons the Specialization step fails validation. Not display strings —
 * presentation layers map them to user-facing copy (ARCHITECTURE.md §4.3).
 */
enum class SpecializationValidationError {
    /** More than [ValidateSpecializationStepUseCase.MAX_SPECIALIZE] muscles selected. */
    TooManySpecialized,
}

/** Outcome of validating the Specialization step. */
sealed interface SpecializationValidationResult {
    data object Valid : SpecializationValidationResult
    data class Invalid(val errors: Set<SpecializationValidationError>) : SpecializationValidationResult
}
