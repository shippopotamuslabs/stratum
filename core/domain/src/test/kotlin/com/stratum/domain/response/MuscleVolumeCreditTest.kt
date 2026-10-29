package com.stratum.domain.response

import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import com.stratum.domain.training.LoggedLiftContext

class MuscleVolumeCreditTest {
    @Test
    @DisplayName("Given primary and secondary muscles when accumulating then secondary credits at half rate")
    fun secondaryCreditsAtHalfRate() {
        val weekStart = Instant.parse("2025-01-06T00:00:00Z")
        val credits = mutableMapOf<MuscleGroup, MutableMap<Instant, Double>>()
        val lift =
            Lift(
                id = LiftId("bench"),
                name = "Bench Press",
                movementPatterns = setOf(MovementPattern.HorizontalPress),
                primaryMuscles = setOf(MuscleGroup.Chest),
                secondaryMuscles = setOf(MuscleGroup.Tricep, MuscleGroup.Chest),
                incrementTiers = emptyList(),
                presetTags = emptySet(),
                enabled = true,
            )

        MuscleVolumeCredit.accumulateSetCredits(lift, weekStart, credits)

        assertEquals(1.0, requireNotNull(credits[MuscleGroup.Chest]?.getValue(weekStart)), 0.001)
        assertEquals(
            MuscleVolumeCredit.SECONDARY_SET_CREDIT,
            requireNotNull(credits[MuscleGroup.Tricep]?.getValue(weekStart)),
            0.001,
        )
    }

    @Test
    @DisplayName("Given pull lift missing bicep tags when accumulating then bicep is credited at half rate")
    fun pullPatternSelfHealsBicepSecondaryCredit() {
        val weekStart = Instant.parse("2025-01-06T00:00:00Z")
        val credits = mutableMapOf<MuscleGroup, MutableMap<Instant, Double>>()
        val lift =
            Lift(
                id = LiftId("row"),
                name = "Cable Row",
                movementPatterns = setOf(MovementPattern.HorizontalPull),
                primaryMuscles = setOf(MuscleGroup.Back),
                secondaryMuscles = emptySet(),
                incrementTiers = emptyList(),
                presetTags = emptySet(),
                enabled = true,
            )

        MuscleVolumeCredit.accumulateSetCredits(lift, weekStart, credits)

        assertEquals(1.0, requireNotNull(credits[MuscleGroup.Back]?.getValue(weekStart)), 0.001)
        assertEquals(
            MuscleVolumeCredit.SECONDARY_SET_CREDIT,
            requireNotNull(credits[MuscleGroup.Bicep]?.getValue(weekStart)),
            0.001,
        )
    }

    @Test
    @DisplayName("Given push lift missing tricep tags when accumulating then tricep is credited at half rate")
    fun pushPatternSelfHealsTricepSecondaryCredit() {
        val weekStart = Instant.parse("2025-01-06T00:00:00Z")
        val credits = mutableMapOf<MuscleGroup, MutableMap<Instant, Double>>()
        val lift =
            Lift(
                id = LiftId("press"),
                name = "Machine Chest Press",
                movementPatterns = setOf(MovementPattern.HorizontalPress),
                primaryMuscles = setOf(MuscleGroup.Chest),
                secondaryMuscles = emptySet(),
                incrementTiers = emptyList(),
                presetTags = emptySet(),
                enabled = true,
                isCustom = true,
            )

        MuscleVolumeCredit.accumulateSetCredits(lift, weekStart, credits)

        assertEquals(1.0, requireNotNull(credits[MuscleGroup.Chest]?.getValue(weekStart)), 0.001)
        assertEquals(
            MuscleVolumeCredit.SECONDARY_SET_CREDIT,
            requireNotNull(credits[MuscleGroup.Tricep]?.getValue(weekStart)),
            0.001,
        )
    }

    @Test
    @DisplayName("Given stub row custom lift when accumulating then name guess credits bicep at half rate")
    fun stubRowSelfHealsBicepSecondaryCredit() {
        val weekStart = Instant.parse("2025-01-06T00:00:00Z")
        val credits = mutableMapOf<MuscleGroup, MutableMap<Instant, Double>>()
        val lift =
            Lift(
                id = LiftId("row"),
                name = "Iso-Lateral Row",
                movementPatterns = setOf(MovementPattern.SquatPattern),
                primaryMuscles = emptySet(),
                secondaryMuscles = emptySet(),
                incrementTiers = emptyList(),
                presetTags = emptySet(),
                enabled = true,
                isCustom = true,
            )

        MuscleVolumeCredit.accumulateSetCredits(lift, weekStart, credits)

        assertEquals(
            MuscleVolumeCredit.SECONDARY_SET_CREDIT,
            requireNotNull(credits[MuscleGroup.Bicep]?.getValue(weekStart)),
            0.001,
        )
    }

    @Test
    @DisplayName("WL-006 MuscleVolumeCredit credits muscles from LoggedLiftContext bitmasks")
    fun wl006_snapshotVolumeCredit() {
        val weekStart = Instant.parse("2025-01-06T00:00:00Z")
        val credits = mutableMapOf<MuscleGroup, MutableMap<Instant, Double>>()
        val liftContext = com.stratum.core.testing.training.WorkoutLoggingFixtures.importLiftContext()

        MuscleVolumeCredit.accumulateSetCredits(liftContext, weekStart, credits)

        assertEquals(1.0, requireNotNull(credits[MuscleGroup.Chest]?.getValue(weekStart)), 0.001)
        assertEquals(0.5, requireNotNull(credits[MuscleGroup.Tricep]?.getValue(weekStart)), 0.001)
    }
}
