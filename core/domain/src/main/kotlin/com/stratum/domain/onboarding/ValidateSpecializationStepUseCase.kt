package com.stratum.domain.onboarding

import javax.inject.Inject

/**
 * Validates the Specialization step of onboarding. Pure (no I/O).
 *
 * The step is **optional**: an empty preference (skip) is always valid. The only hard rule
 * is the specialization cap — at most [MAX_SPECIALIZE] muscles may be pushed to
 * [SpecializationLevel.Specialize] in a single mesocycle. This mirrors the research
 * consensus (Renaissance Periodization volume landmarks; Alpha Progression; Born Fitness):
 * specialization blocks target 1–3 muscles while the rest sit at maintenance.
 *
 * Hard push/pull balance is **not** enforced here — that is a volume-distribution concern
 * handled at program generation (OS-6), where per-muscle weekly-set targets live.
 */
class ValidateSpecializationStepUseCase @Inject constructor() {
    operator fun invoke(preference: SpecializationPreference): SpecializationValidationResult {
        val errors = buildSet {
            if (preference.specializedMuscles.size > MAX_SPECIALIZE) {
                add(SpecializationValidationError.TooManySpecialized)
            }
        }
        return if (errors.isEmpty()) {
            SpecializationValidationResult.Valid
        } else {
            SpecializationValidationResult.Invalid(errors)
        }
    }

    companion object {
        /** Maximum number of muscles a user may specialize in one mesocycle. */
        const val MAX_SPECIALIZE = 3
    }
}
