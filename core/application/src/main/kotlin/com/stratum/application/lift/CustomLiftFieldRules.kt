package com.stratum.application.lift

import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.lift.MovementPattern

/**
 * Outcome of a custom-lift field rule (STR-243), mirroring the shape of
 * [com.stratum.domain.lift.CatalogNames.Validation]: the failure branch carries
 * the rule's user-facing message as a typed value, never a [Throwable].
 */
sealed interface CustomLiftFieldValidation {
    data object Valid : CustomLiftFieldValidation

    data class Invalid(val message: String) : CustomLiftFieldValidation
}

/**
 * The custom-lift field rules shared by the add and update write paths (STR-169).
 *
 * [AddCustomLiftUseCase], [UpdateCustomLiftDetailsUseCase], and
 * [UpdateLiftMusclesUseCase] each re-encoded parts of these rules; they now
 * consume these single per-field implementations (each path calls only the
 * rules for the fields it writes):
 *
 * - a lift needs at least one movement pattern and at least one primary muscle;
 * - a default rest override, when present, sits inside the same range
 *   [SetLiftDefaultRestUseCase] enforces for the standalone rest write;
 * - secondary muscles never duplicate a primary muscle (a muscle is credited
 *   once, as primary, when it appears in both sets).
 */
object CustomLiftFieldRules {
    const val MOVEMENT_PATTERNS_REQUIRED_MESSAGE: String = "At least one movement pattern is required"
    const val PRIMARY_MUSCLES_REQUIRED_MESSAGE: String = "At least one primary muscle is required"

    /** The movement-pattern rule: a lift needs at least one pattern. */
    fun validateMovementPatterns(movementPatterns: Set<MovementPattern>): CustomLiftFieldValidation =
        if (movementPatterns.isEmpty()) {
            CustomLiftFieldValidation.Invalid(MOVEMENT_PATTERNS_REQUIRED_MESSAGE)
        } else {
            CustomLiftFieldValidation.Valid
        }

    /** The primary-muscle rule: a lift needs at least one primary muscle. */
    fun validatePrimaryMuscles(primaryMuscles: Set<MuscleGroup>): CustomLiftFieldValidation =
        if (primaryMuscles.isEmpty()) {
            CustomLiftFieldValidation.Invalid(PRIMARY_MUSCLES_REQUIRED_MESSAGE)
        } else {
            CustomLiftFieldValidation.Valid
        }

    /** The rest rule: a present override sits inside the shared rest range; absent is fine. */
    fun validateDefaultRestSeconds(defaultRestSeconds: Int?): CustomLiftFieldValidation {
        if (defaultRestSeconds != null &&
            (defaultRestSeconds < SetLiftDefaultRestUseCase.MIN_REST_SECONDS ||
                defaultRestSeconds > SetLiftDefaultRestUseCase.MAX_REST_SECONDS)
        ) {
            return CustomLiftFieldValidation.Invalid(
                "Rest must be between ${SetLiftDefaultRestUseCase.MIN_REST_SECONDS} and " +
                    "${SetLiftDefaultRestUseCase.MAX_REST_SECONDS} seconds",
            )
        }
        return CustomLiftFieldValidation.Valid
    }

    /** Secondary muscles with every primary removed — a muscle is credited once, as primary. */
    fun distinctSecondaryMuscles(
        primaryMuscles: Set<MuscleGroup>,
        secondaryMuscles: Set<MuscleGroup>,
    ): Set<MuscleGroup> = secondaryMuscles - primaryMuscles
}
