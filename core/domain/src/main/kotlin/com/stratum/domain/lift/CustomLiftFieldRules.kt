package com.stratum.domain.lift

import com.stratum.domain.onboarding.MuscleGroup

/**
 * The custom-lift field rules shared by the add and update write paths (STR-169).
 *
 * [AddCustomLiftUseCase], [UpdateCustomLiftDetailsUseCase], and
 * [UpdateLiftMusclesUseCase] each re-encoded parts of these rules; they now
 * consume this single implementation:
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

    /**
     * Validates the fields the add/update paths share. A null [primaryMuscles]
     * or [defaultRestSeconds] means that path does not write that field, so its
     * rule is skipped rather than defaulted.
     */
    fun validate(
        movementPatterns: Set<MovementPattern>,
        primaryMuscles: Set<MuscleGroup>? = null,
        defaultRestSeconds: Int? = null,
    ): Result<Unit> {
        if (movementPatterns.isEmpty()) {
            return Result.failure(IllegalArgumentException(MOVEMENT_PATTERNS_REQUIRED_MESSAGE))
        }
        if (primaryMuscles != null && primaryMuscles.isEmpty()) {
            return Result.failure(IllegalArgumentException(PRIMARY_MUSCLES_REQUIRED_MESSAGE))
        }
        if (defaultRestSeconds != null &&
            (defaultRestSeconds < SetLiftDefaultRestUseCase.MIN_REST_SECONDS ||
                defaultRestSeconds > SetLiftDefaultRestUseCase.MAX_REST_SECONDS)
        ) {
            return Result.failure(
                IllegalArgumentException(
                    "Rest must be between ${SetLiftDefaultRestUseCase.MIN_REST_SECONDS} and " +
                        "${SetLiftDefaultRestUseCase.MAX_REST_SECONDS} seconds",
                ),
            )
        }
        return Result.success(Unit)
    }

    /** Secondary muscles with every primary removed — a muscle is credited once, as primary. */
    fun distinctSecondaryMuscles(
        primaryMuscles: Set<MuscleGroup>,
        secondaryMuscles: Set<MuscleGroup>,
    ): Set<MuscleGroup> = secondaryMuscles - primaryMuscles
}
