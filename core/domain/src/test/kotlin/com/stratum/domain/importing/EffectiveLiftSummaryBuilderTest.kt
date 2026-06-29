package com.stratum.domain.importing

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.EvidenceLevel
import com.stratum.domain.response.LiftEffectivenessObservation
import com.stratum.domain.response.identity.EpochId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class EffectiveLiftSummaryBuilderTest {
    @Test
    @DisplayName("Given rank-one lifts when building summary then sorts by effectiveness score")
    fun sortsByEffectivenessScore() {
        val highlights =
            EffectiveLiftSummaryBuilder.build(
                observations =
                    listOf(
                        observation(
                            pattern = MovementPattern.HorizontalPress,
                            liftId = LiftId("bench-press"),
                            score = 0.4,
                            epoch = "e1",
                        ),
                        observation(
                            pattern = MovementPattern.VerticalPull,
                            liftId = LiftId("pull-up"),
                            score = 1.2,
                            epoch = "e1",
                        ),
                    ),
                liftsById =
                    mapOf(
                        LiftId("bench-press") to lift("bench-press", MuscleGroup.Chest),
                        LiftId("pull-up") to lift("pull-up", MuscleGroup.Back),
                    ),
            )

        assertEquals(LiftId("pull-up"), highlights.first().liftId)
        assertEquals(MuscleGroup.Back, highlights.first().muscle)
        assertEquals(UserEffectivenessTier.VeryEffective, highlights.first().effectivenessTier)
        assertEquals(UserEffectivenessTier.SomewhatEffective, highlights.last().effectivenessTier)
    }

    @Test
    @DisplayName("Given competing lifts for same muscle when building summary then keeps highest score")
    fun picksBestLiftPerMuscle() {
        val highlights =
            EffectiveLiftSummaryBuilder.build(
                observations =
                    listOf(
                        observation(
                            pattern = MovementPattern.SquatPattern,
                            liftId = LiftId("leg-press"),
                            score = 1.4,
                            epoch = "e1",
                        ),
                        observation(
                            pattern = MovementPattern.KneeExtension,
                            liftId = LiftId("leg-extension"),
                            score = 0.6,
                            epoch = "e1",
                        ),
                    ),
                liftsById =
                    mapOf(
                        LiftId("leg-press") to lift("leg-press", MuscleGroup.Quad),
                        LiftId("leg-extension") to lift("leg-extension", MuscleGroup.Quad),
                    ),
            )

        assertEquals(1, highlights.size)
        assertEquals(LiftId("leg-press"), highlights.single().liftId)
        assertEquals(MuscleGroup.Quad, highlights.single().muscle)
    }

    private fun observation(
        pattern: MovementPattern,
        liftId: LiftId,
        score: Double,
        epoch: String,
    ): LiftEffectivenessObservation =
        LiftEffectivenessObservation(
            pattern = pattern,
            liftId = liftId,
            epochId = EpochId(epoch),
            effectivenessScore = score,
            peerLiftCount = 2,
            rank = 1,
            evidence = EvidenceLevel.Hypothesis,
            confidenceScore = 1.0,
        )

    private fun lift(
        slug: String,
        primaryMuscle: MuscleGroup = MuscleGroup.Chest,
    ): com.stratum.domain.lift.Lift =
        com.stratum.domain.lift.Lift(
            id = LiftId(slug),
            name = slug,
            movementPattern = MovementPattern.HorizontalPress,
            primaryMuscles = setOf(primaryMuscle),
            secondaryMuscles = emptySet(),
            allowWeightChangeBetweenSets = false,
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
        )
}
