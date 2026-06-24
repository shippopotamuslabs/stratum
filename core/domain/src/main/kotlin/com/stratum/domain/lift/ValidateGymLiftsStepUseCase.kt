package com.stratum.domain.lift

import javax.inject.Inject

/**
 * Validates the Gym & Lifts step of onboarding (OS-4). Pure (no I/O).
 *
 * The only hard rule is that the user must leave at least [MIN_ENABLED] lift enabled — a
 * generated program needs at least one movement to build from. Everything else (preset
 * choice is optional, manual toggling is free-form) is unconstrained, so a user can ship a
 * minimal catalog or curate heavily.
 */
class ValidateGymLiftsStepUseCase @Inject constructor() {
    operator fun invoke(lifts: List<Lift>): GymLiftsValidationResult {
        val errors = buildSet {
            if (lifts.count { it.enabled } < MIN_ENABLED) {
                add(GymLiftsValidationError.NoLiftsEnabled)
            }
        }
        return if (errors.isEmpty()) {
            GymLiftsValidationResult.Valid
        } else {
            GymLiftsValidationResult.Invalid(errors)
        }
    }

    companion object {
        /** Minimum enabled lifts required to advance from the Gym & Lifts step. */
        const val MIN_ENABLED = 1
    }
}
