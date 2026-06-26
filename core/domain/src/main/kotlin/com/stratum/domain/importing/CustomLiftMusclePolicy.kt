package com.stratum.domain.importing

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup

/**
 * Deterministic secondary-muscle defaults from [MovementPattern]. Used when persisting import
 * custom lifts and when crediting weekly volume so MEV/MRV stay accurate even if tags were
 * omitted or stale (pulls credit biceps, compound pushes credit triceps).
 */
object CustomLiftMusclePolicy {
    fun withPatternSecondaryDefaults(metadata: InferredCustomLiftMetadata): InferredCustomLiftMetadata =
        metadata.copy(
            secondaryMuscles =
                effectiveSecondaryMuscles(
                    movementPattern = metadata.movementPattern,
                    primaryMuscles = metadata.primaryMuscles,
                    secondaryMuscles = metadata.secondaryMuscles,
                ),
        )

    fun effectiveSecondaryMuscles(
        movementPattern: MovementPattern,
        primaryMuscles: Set<MuscleGroup>,
        secondaryMuscles: Set<MuscleGroup>,
    ): Set<MuscleGroup> {
        val merged = secondaryMuscles.toMutableSet()
        when (movementPattern) {
            MovementPattern.HorizontalPull,
            MovementPattern.VerticalPull,
            -> if (MuscleGroup.Bicep !in primaryMuscles) {
                merged += MuscleGroup.Bicep
            }
            MovementPattern.HorizontalPush,
            MovementPattern.VerticalPush,
            MovementPattern.InclinePush,
            -> if (MuscleGroup.Tricep !in primaryMuscles) {
                merged += MuscleGroup.Tricep
            }
            else -> Unit
        }
        return merged - primaryMuscles
    }
}
