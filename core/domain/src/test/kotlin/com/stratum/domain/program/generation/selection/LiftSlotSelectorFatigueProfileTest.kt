package com.stratum.domain.program.generation.selection

import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.reference.RpRepProfile
import com.stratum.domain.program.generation.slots.SlotRole
import com.stratum.domain.program.generation.slots.WorkoutSlotTemplate
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * FPR-S1-018 / FPR-S1-019 / FPR-S4-006 — fatigue allow-list eligibility and ranking.
 */
class LiftSlotSelectorFatigueProfileTest {

    @Test
    @DisplayName("FPR-S1-018 accessory-style allow-list admits Low and Medium candidates")
    fun fprS1018_allowListAdmitsLowAndMedium() {
        val context =
            baseContext(
                listOf(
                    lowCandidate(),
                    mediumCandidate(),
                    highCandidate(),
                ),
            )
        val slot = accessoryStyleSlot()
        val candidates = candidatesForSlot(slot, context)
        assertTrue(candidates.any { it.id == "lift-low" }, "Low candidate must be eligible")
        assertTrue(candidates.any { it.id == "lift-medium" }, "Medium candidate must be eligible")
        assertFalse(candidates.any { it.id == "lift-high" }, "High candidate must not be eligible")
    }

    @Test
    @DisplayName("FPR-S1-019 Low ranks before Medium when allow-list present")
    fun fprS1019_lowRanksAboveMedium() {
        val context = baseContext(listOf(lowCandidate(), mediumCandidate()))
        val slot = accessoryStyleSlot()
        val lowScore =
            scoreLiftForSlot(lowCandidate(), slot, context, siblingIndex = 0, setCount = 3)
        val mediumScore =
            scoreLiftForSlot(mediumCandidate(), slot, context, siblingIndex = 0, setCount = 3)
        assertTrue(
            lowScore > mediumScore,
            "Low ($lowScore) must rank before Medium ($mediumScore)",
        )
    }

    @Test
    @DisplayName("FPR-S4-006 Low preferred over Medium for accessory allow-list ranking")
    fun fprS4006_lowPreferredOnAccessoryAllowList() {
        val context = baseContext(listOf(lowCandidate(), mediumCandidate()))
        val slot = accessoryStyleSlot()
        val lowScore =
            scoreLiftForSlot(lowCandidate(), slot, context, siblingIndex = 0, setCount = 3)
        val mediumScore =
            scoreLiftForSlot(mediumCandidate(), slot, context, siblingIndex = 0, setCount = 3)
        assertTrue(lowScore > mediumScore)
    }

    private fun accessoryStyleSlot() =
        WorkoutSlotTemplate(
            slotId = "quad-iso",
            role = SlotRole.Accessory,
            pattern = MovementPattern.KneeExtension,
            primaryMuscle = MuscleGroup.Quad,
            repProfile = RpRepProfile.Isolation,
            // Low + Medium stand-in: Isolation + NonAxialCompound
            allowedFatigueProfiles =
                setOf(FatigueProfile.Low, FatigueProfile.Medium),
        )

    private fun baseContext(lifts: List<LiftContext>): ProgramGenerationContext {
        val liftsById = lifts.associateBy { it.id }
        return ProgramGenerationContext(
            trainingAge = "Advanced",
            lastTrained = null,
            sessionLengthMinutes = 75,
            trainingDaysPerWeek = 5,
            specialization = mapOf(MuscleGroup.Quad to SpecializationLevel.Specialize),
            liftsById = liftsById,
            muscleVolumeSnapshotsByMuscle = emptyMap(),
            volumeCorrelationsByMuscle = emptyMap(),
            enabledLiftIds = liftsById.keys,
            movementPatternByLiftId = liftsById.mapValues { (_, lift) -> lift.movementPattern },
            liftEffectivenessByLiftId = emptyMap(),
        )
    }

    private fun lowCandidate() =
        LiftContext(
            id = "lift-low",
            name = "Machine Leg Extension",
            movementPattern = MovementPattern.KneeExtension,
            movementPatterns = setOf(MovementPattern.KneeExtension),
            primaryMuscles = setOf(MuscleGroup.Quad),
            secondaryMuscles = emptySet(),
            proven = true,
            fatigueProfile = FatigueProfile.Low,
        )

    private fun mediumCandidate() =
        LiftContext(
            id = "lift-medium",
            name = "Sled Leg Press",
            movementPattern = MovementPattern.SquatPattern,
            movementPatterns = setOf(MovementPattern.SquatPattern),
            primaryMuscles = setOf(MuscleGroup.Quad),
            secondaryMuscles = setOf(MuscleGroup.Calf, MuscleGroup.Glute),
            proven = true,
            fatigueProfile = FatigueProfile.Medium,
        )

    private fun highCandidate() =
        LiftContext(
            id = "lift-high",
            name = "Barbell Back Squat",
            movementPattern = MovementPattern.SquatPattern,
            movementPatterns = setOf(MovementPattern.SquatPattern),
            primaryMuscles = setOf(MuscleGroup.Quad),
            secondaryMuscles = setOf(MuscleGroup.Calf, MuscleGroup.Glute),
            proven = true,
            fatigueProfile = FatigueProfile.High,
        )
}
