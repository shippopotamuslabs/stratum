package com.stratum.domain.program.generation.selection

import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.reference.RpRepProfile
import com.stratum.domain.program.generation.split.SlotRole
import com.stratum.domain.program.generation.split.WorkoutSlotTemplate
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Isolated unit tests for the `allowedKinematicProfiles` candidate-matching branch in
 * [candidatesForSlot] — the core of the KinematicProfile refactor that lets NonAxialCompound
 * lifts (leg press, hack squat, split squats) be candidates for the `quad-iso`/`ham-iso` isolation
 * slots, replacing the old KneeExtension-pattern-overloading hack.
 */
class LiftSlotSelectorKinematicProfileTest {

    @Test
    @DisplayName("quad-iso slot admits NonAxialCompound leg press via kinematic-profile fallback")
    fun quadIsoAdmitsNonAxialCompoundLegPress() {
        val context = contextWithLegPressAndLegExtension()
        val slot = quadIsoSlot()
        val candidates = candidatesForSlot(slot, context)
        assertTrue(candidates.any { it.id == "sled-leg-press" }, "leg press should be a quad-iso candidate")
        assertTrue(candidates.any { it.id == "machine-leg-extension" }, "leg extension should be a quad-iso candidate")
    }

    @Test
    @DisplayName("quad-iso slot excludes AxialCompound barbell squat even with kinematic-profile fallback")
    fun quadIsoExcludesAxialCompoundBackSquat() {
        val context = contextWithBackSquatLegPressAndLegExtension()
        val slot = quadIsoSlot()
        val candidates = candidatesForSlot(slot, context)
        assertFalse(candidates.any { it.id == "barbell-back-squat" }, "barbell back squat (AxialCompound) should not be a quad-iso candidate")
        assertTrue(candidates.any { it.id == "sled-leg-press" }, "leg press (NonAxialCompound) should be a quad-iso candidate")
    }

    @Test
    @DisplayName("quad-iso slot excludes lifts whose primary muscle is not Quad")
    fun quadIsoExcludesNonQuadLifts() {
        val context = contextWithLegPressAndHamstringCurl()
        val slot = quadIsoSlot()
        val candidates = candidatesForSlot(slot, context)
        assertFalse(candidates.any { it.id == "machine-lying-leg-curl" }, "hamstring curl (Hamstring primary) should not be a quad-iso candidate")
        assertTrue(candidates.any { it.id == "sled-leg-press" }, "leg press (Quad primary) should be a quad-iso candidate")
    }

    @Test
    @DisplayName("squat anchor slot (no allowedKinematicProfiles) does NOT use kinematic fallback")
    fun squatAnchorDoesNotUseKinematicFallback() {
        // The squat anchor has no allowedKinematicProfiles, so a KneeExtension/Isolation lift
        // (leg extension) must NOT be a candidate even though it's Quad-primary.
        val context = contextWithLegPressAndLegExtension()
        val slot = WorkoutSlotTemplate(
            slotId = "squat",
            role = SlotRole.Anchor,
            pattern = MovementPattern.SquatPattern,
            primaryMuscle = MuscleGroup.Quad,
            repProfile = RpRepProfile.LowerFreeWeightCompound,
        )
        val candidates = candidatesForSlot(slot, context)
        assertFalse(candidates.any { it.id == "machine-leg-extension" }, "leg extension (KneeExtension) should not be a squat anchor candidate")
        assertTrue(candidates.any { it.id == "sled-leg-press" }, "leg press (SquatPattern) should be a squat anchor candidate via pattern match")
    }

    @Test
    @DisplayName("ham-iso slot admits NonAxialCompound good morning via kinematic-profile fallback")
    fun hamIsoAdmitsNonAxialCompoundGoodMorning() {
        val context = contextWithGoodMorningAndLegCurl()
        val slot = WorkoutSlotTemplate(
            slotId = "ham-iso",
            role = SlotRole.Isolation,
            pattern = MovementPattern.KneeFlexion,
            primaryMuscle = MuscleGroup.Hamstring,
            repProfile = RpRepProfile.Isolation,
            allowedKinematicProfiles = setOf(KinematicProfile.Isolation, KinematicProfile.NonAxialCompound),
        )
        val candidates = candidatesForSlot(slot, context)
        assertTrue(candidates.any { it.id == "good-morning" }, "good morning (NonAxialCompound, Hamstring primary) should be a ham-iso candidate")
        assertTrue(candidates.any { it.id == "machine-lying-leg-curl" }, "leg curl (Isolation) should be a ham-iso candidate")
    }

    // --- Helpers ---

    private fun quadIsoSlot() = WorkoutSlotTemplate(
        slotId = "quad-iso",
        role = SlotRole.Isolation,
        pattern = MovementPattern.KneeExtension,
        primaryMuscle = MuscleGroup.Quad,
        repProfile = RpRepProfile.Isolation,
        allowedKinematicProfiles = setOf(KinematicProfile.Isolation, KinematicProfile.NonAxialCompound),
    )

    private fun baseContext(lifts: List<LiftContext>): ProgramGenerationContext {
        val liftsById = lifts.associateBy { it.id }
        return ProgramGenerationContext(
            trainingAge = "Advanced",
            lastTrained = null,
            sessionLengthMinutes = 75,
            trainingDaysPerWeek = 5,
            weekdays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday"),
            specialization = mapOf(MuscleGroup.Quad to SpecializationLevel.Specialize),
            liftsById = liftsById,
            muscleVolumeSnapshotsByMuscle = emptyMap(),
            volumeCorrelationsByMuscle = emptyMap(),
            enabledLiftIds = liftsById.keys,
            movementPatternByLiftId = liftsById.mapValues { (_, lift) -> lift.movementPattern },
            liftEffectivenessByLiftId = emptyMap(),
        )
    }

    private fun contextWithLegPressAndLegExtension(): ProgramGenerationContext = baseContext(
        listOf(
            legPress(),
            legExtension(),
        ),
    )

    private fun contextWithBackSquatLegPressAndLegExtension(): ProgramGenerationContext = baseContext(
        listOf(
            backSquat(),
            legPress(),
            legExtension(),
        ),
    )

    private fun contextWithLegPressAndHamstringCurl(): ProgramGenerationContext = baseContext(
        listOf(
            legPress(),
            legCurl(),
        ),
    )

    private fun contextWithGoodMorningAndLegCurl(): ProgramGenerationContext = baseContext(
        listOf(
            goodMorning(),
            legCurl(),
        ),
    )

    private fun legPress() = LiftContext(
        id = "sled-leg-press",
        name = "Sled Leg Press",
        movementPattern = MovementPattern.SquatPattern,
        movementPatterns = setOf(MovementPattern.SquatPattern),
        primaryMuscles = setOf(MuscleGroup.Quad),
        secondaryMuscles = setOf(MuscleGroup.Calf, MuscleGroup.Glute),
        proven = true,
        kinematicProfile = KinematicProfile.NonAxialCompound,
    )

    private fun backSquat() = LiftContext(
        id = "barbell-back-squat",
        name = "Barbell Back Squat",
        movementPattern = MovementPattern.SquatPattern,
        movementPatterns = setOf(MovementPattern.SquatPattern),
        primaryMuscles = setOf(MuscleGroup.Quad),
        secondaryMuscles = setOf(MuscleGroup.Calf, MuscleGroup.Glute),
        proven = true,
        kinematicProfile = KinematicProfile.AxialCompound,
    )

    private fun legExtension() = LiftContext(
        id = "machine-leg-extension",
        name = "Machine Leg Extension",
        movementPattern = MovementPattern.KneeExtension,
        movementPatterns = setOf(MovementPattern.KneeExtension),
        primaryMuscles = setOf(MuscleGroup.Quad),
        secondaryMuscles = emptySet(),
        proven = true,
        kinematicProfile = KinematicProfile.Isolation,
    )

    private fun legCurl() = LiftContext(
        id = "machine-lying-leg-curl",
        name = "Machine Lying Leg Curl",
        movementPattern = MovementPattern.KneeFlexion,
        movementPatterns = setOf(MovementPattern.KneeFlexion),
        primaryMuscles = setOf(MuscleGroup.Hamstring),
        secondaryMuscles = emptySet(),
        proven = true,
        kinematicProfile = KinematicProfile.Isolation,
    )

    private fun goodMorning() = LiftContext(
        id = "good-morning",
        name = "Good Morning",
        movementPattern = MovementPattern.HipHinge,
        movementPatterns = setOf(MovementPattern.HipHinge),
        primaryMuscles = setOf(MuscleGroup.Hamstring),
        secondaryMuscles = setOf(MuscleGroup.Glute, MuscleGroup.LowerBack),
        proven = true,
        kinematicProfile = KinematicProfile.NonAxialCompound,
    )}
