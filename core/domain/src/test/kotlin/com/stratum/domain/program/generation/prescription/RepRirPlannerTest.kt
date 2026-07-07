package com.stratum.domain.program.generation.prescription

import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.reference.RP_REP_RIR_BANDS
import com.stratum.domain.program.generation.reference.RpRepProfile
import com.stratum.domain.program.generation.split.MuscleLengthBiasPreference
import com.stratum.domain.program.generation.split.SiblingVariant
import com.stratum.domain.program.generation.split.SlotRole
import com.stratum.domain.program.generation.split.WorkoutSlotTemplate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Pins the Stratum-tuned rep/RIR bands and the [repRirForSlot] lookup. The bands guarantee a
 * hypertrophic floor (repMin ≥ 8) on top sets and tier the rep-range spread by compound/load
 * class as an increment-relative-intensity proxy. See [RP_REP_RIR_BANDS] KDoc for rationale.
 */
class RepRirPlannerTest {

    @Test
    @DisplayName("every band has a hypertrophic floor of at least 8 reps")
    fun everyBandFloorAtLeastEight() {
        for (profile in RpRepProfile.entries) {
            val band = RP_REP_RIR_BANDS.getValue(profile)
            assertTrue(
                band.repMin >= 8,
                "Expected repMin ≥ 8 for $profile but was ${band.repMin}",
            )
        }
    }

    @Test
    @DisplayName("rep-range spread increases along the compound→isolation gradient")
    fun spreadIncreasesAlongCompoundGradient() {
        val axialSpread = spread(RpRepProfile.LowerFreeWeightCompound)
        val nonAxialSpreadLower = spread(RpRepProfile.LowerMachineOrUpperPress)
        val nonAxialSpreadUpper = spread(RpRepProfile.UpperMachineOrPull)
        val isolationSpread = spread(RpRepProfile.Isolation)

        assertEquals(4, axialSpread, "AxialCompound (LowerFreeWeightCompound) spread")
        assertEquals(6, nonAxialSpreadLower, "NonAxialCompound (LowerMachineOrUpperPress) spread")
        assertEquals(6, nonAxialSpreadUpper, "NonAxialCompound (UpperMachineOrPull) spread")
        assertEquals(10, isolationSpread, "Isolation spread")

        assertTrue(axialSpread < nonAxialSpreadLower, "Axial spread should be narrower than NonAxial")
        assertTrue(nonAxialSpreadLower < isolationSpread, "NonAxial spread should be narrower than Isolation")
    }

    @Test
    @DisplayName("repRirForSlot returns the tuned band values for each profile")
    fun repRirForSlotReturnsTunedBands() {
        assertEquals(
            RepRirPlan(repMin = 8, repMax = 12, topSetRir = 4, backOffRir = 3),
            repRirForSlot(slot(RpRepProfile.LowerFreeWeightCompound), barbellSquatLift()),
        )
        assertEquals(
            RepRirPlan(repMin = 8, repMax = 14, topSetRir = 3, backOffRir = 2),
            repRirForSlot(slot(RpRepProfile.LowerMachineOrUpperPress), legPressLift()),
        )
        assertEquals(
            RepRirPlan(repMin = 8, repMax = 14, topSetRir = 2, backOffRir = 1),
            repRirForSlot(slot(RpRepProfile.UpperMachineOrPull), latPulldownLift()),
        )
        assertEquals(
            RepRirPlan(repMin = 8, repMax = 18, topSetRir = 1, backOffRir = 0),
            repRirForSlot(slot(RpRepProfile.Isolation), dumbbellCurlLift()),
        )
    }

    @Test
    @DisplayName("backoff RIR is the rounded midpoint of the backOffRir band")
    fun backOffRirIsMidpoint() {
        for (profile in RpRepProfile.entries) {
            val band = RP_REP_RIR_BANDS.getValue(profile)
            val expected = kotlin.math.round((band.backOffRirMin + band.backOffRirMax) / 2.0).toInt()
            val plan = repRirForSlot(slot(profile), liftForProfile(profile))
            assertEquals(expected, plan.backOffRir, "Backoff RIR midpoint for $profile")
        }
    }

    @Test
    @DisplayName("barbell-named HipHinge lift in a LowerMachineOrUpperPress slot upgrades to LowerFreeWeightCompound")
    fun barbellHipHingeUpgradesToLowerFreeWeight() {
        val plan = repRirForSlot(
            slot = slot(RpRepProfile.LowerMachineOrUpperPress, pattern = MovementPattern.HipHinge),
            lift = barbellDeadliftLift(),
        )
        assertEquals(8, plan.repMin)
        assertEquals(12, plan.repMax, "Barbell HipHinge should get the AxialCompound (8–12) band")
        assertEquals(4, plan.topSetRir)
    }

    @Test
    @DisplayName("Smith-machine-named HipHinge lift does not upgrade to LowerFreeWeightCompound")
    fun smithMachineHipHingeDoesNotUpgrade() {
        val plan = repRirForSlot(
            slot = slot(RpRepProfile.LowerMachineOrUpperPress, pattern = MovementPattern.HipHinge),
            lift = smithMachineDeadliftLift(),
        )
        assertEquals(8, plan.repMin)
        assertEquals(14, plan.repMax, "Smith-machine HipHinge stays in the slot's LowerMachineOrUpperPress band")
        assertEquals(3, plan.topSetRir)
    }

    @Test
    @DisplayName("Isolation-tagged slot stays Isolation regardless of movement pattern")
    fun isolationSlotStaysIsolation() {
        val plan = repRirForSlot(
            slot = slot(RpRepProfile.Isolation, pattern = MovementPattern.KneeExtension),
            lift = legExtensionLift(),
        )
        assertEquals(8, plan.repMin)
        assertEquals(18, plan.repMax)
        assertEquals(1, plan.topSetRir)
    }

    @Test
    @DisplayName("repRangeFromFatigue: null slope falls back to the static band")
    fun repRangeFromFatigueNullSlopeFallsBack() {
        val band = RP_REP_RIR_BANDS.getValue(RpRepProfile.LowerFreeWeightCompound)
        assertEquals(8 to 12, repRangeFromFatigue(null, 4, band))
    }

    @Test
    @DisplayName("repRangeFromFatigue: setCount <= 1 falls back to the static band even with a slope")
    fun repRangeFromFatigueSingleSetFallsBack() {
        val band = RP_REP_RIR_BANDS.getValue(RpRepProfile.Isolation)
        assertEquals(8 to 18, repRangeFromFatigue(-3.0, 1, band))
    }

    @Test
    @DisplayName("repRangeFromFatigue: steep slope (-3.0, 4 sets) yields spread 9 (8–17)")
    fun repRangeFromFatigueSteepSlope() {
        val band = RP_REP_RIR_BANDS.getValue(RpRepProfile.Isolation)
        assertEquals(8 to 17, repRangeFromFatigue(-3.0, 4, band))
    }

    @Test
    @DisplayName("repRangeFromFatigue: shallow slope (-1.0, 4 sets) yields spread 3 (8–11)")
    fun repRangeFromFatigueShallowSlope() {
        val band = RP_REP_RIR_BANDS.getValue(RpRepProfile.LowerFreeWeightCompound)
        assertEquals(8 to 11, repRangeFromFatigue(-1.0, 4, band))
    }

    @Test
    @DisplayName("repRangeFromFatigue: population slope (-2.0, 4 sets) yields spread 6 (8–14)")
    fun repRangeFromFatiguePopulationSlope() {
        val band = RP_REP_RIR_BANDS.getValue(RpRepProfile.LowerMachineOrUpperPress)
        assertEquals(8 to 14, repRangeFromFatigue(-2.0, 4, band))
    }

    @Test
    @DisplayName("repRangeFromFatigue: floor is always 8 even with an extreme slope")
    fun repRangeFromFatigueFloorEnforced() {
        val band = RP_REP_RIR_BANDS.getValue(RpRepProfile.LowerFreeWeightCompound)
        val (repMin, _) = repRangeFromFatigue(-5.0, 4, band)
        assertEquals(8, repMin, "Floor must be the hypertrophic floor (8) regardless of slope")
    }

    @Test
    @DisplayName("repRangeFromFatigue: ceiling is capped at REP_RANGE_CEILING_CAP")
    fun repRangeFromFatigueCeilingCapped() {
        val band = RP_REP_RIR_BANDS.getValue(RpRepProfile.Isolation)
        val (_, repMax) = repRangeFromFatigue(-10.0, 10, band)
        assertEquals(REP_RANGE_CEILING_CAP, repMax, "Extreme slope must be capped")
    }

    @Test
    @DisplayName("repRangeFromFatigue: setCount = 0 falls back to the static band (guard)")
    fun repRangeFromFatigueZeroSetCountFallsBack() {
        val band = RP_REP_RIR_BANDS.getValue(RpRepProfile.LowerFreeWeightCompound)
        assertEquals(8 to 12, repRangeFromFatigue(-3.0, 0, band))
    }

    @Test
    @DisplayName("repRangeFromFatigue: positive slope is treated as magnitude via abs (defensive)")
    fun repRangeFromFatiguePositiveSlopeTreatedAsMagnitude() {
        // A positive slope is biomechanically invalid (reps shouldn't increase across sets),
        // but the function uses abs() so a garbage input still yields a valid range pinned at
        // the floor. This pins the defensive behavior.
        val band = RP_REP_RIR_BANDS.getValue(RpRepProfile.Isolation)
        assertEquals(8 to 11, repRangeFromFatigue(1.0, 4, band))
    }

    @Test
    @DisplayName("repRangeFromFatigue: spread exactly at the cap boundary is not clamped")
    fun repRangeFromFatigueCapBoundaryNotClamped() {
        // -2.0 slope × (12 - 1) = 22 → 8 + 22 = 30 = REP_RANGE_CEILING_CAP (exact, not capped).
        val band = RP_REP_RIR_BANDS.getValue(RpRepProfile.Isolation)
        assertEquals(8 to REP_RANGE_CEILING_CAP, repRangeFromFatigue(-2.0, 12, band))
    }

    @Test
    @DisplayName("repRirForSlot: default setCount=0 with a slope falls back to the static band")
    fun repRirForSlotDefaultSetCountFallsBack() {
        // The default setCount=0 means no set-count signal → fallback, even if a slope is passed.
        assertEquals(
            RepRirPlan(repMin = 8, repMax = 18, topSetRir = 1, backOffRir = 0),
            repRirForSlot(
                slot = slot(RpRepProfile.Isolation),
                lift = dumbbellCurlLift(),
                fatigueSlope = -3.0,
                setCount = 0,
            ),
        )
    }

    @Test
    @DisplayName("repRirForSlot: personalized slope overrides the static band spread, RIR stays from band")
    fun repRirForSlotPersonalizedSlopeOverridesSpread() {
        // Isolation band (8–18, spread 10), but a shallow -1.0 slope over 4 sets → spread 3 → 8–11.
        // topSetRir (1) and backOffRir (0) come from the band, unchanged by the slope.
        assertEquals(
            RepRirPlan(repMin = 8, repMax = 11, topSetRir = 1, backOffRir = 0),
            repRirForSlot(
                slot = slot(RpRepProfile.Isolation),
                lift = dumbbellCurlLift(),
                fatigueSlope = -1.0,
                setCount = 4,
            ),
        )
    }

    private fun spread(profile: RpRepProfile): Int {
        val band = RP_REP_RIR_BANDS.getValue(profile)
        return band.repMax - band.repMin
    }

    private fun slot(
        profile: RpRepProfile,
        pattern: MovementPattern = MovementPattern.HorizontalPress,
    ): WorkoutSlotTemplate =
        WorkoutSlotTemplate(
            slotId = "test-slot",
            role = SlotRole.Anchor,
            pattern = pattern,
            primaryMuscle = MuscleGroup.Chest,
            repProfile = profile,
            lengthBiasPreference = null,
            siblingVariant = SiblingVariant.A,
            isCrossDomainTouch = false,
        )

    private fun liftForProfile(profile: RpRepProfile): LiftContext = when (profile) {
        RpRepProfile.LowerFreeWeightCompound -> barbellSquatLift()
        RpRepProfile.LowerMachineOrUpperPress -> legPressLift()
        RpRepProfile.UpperMachineOrPull -> latPulldownLift()
        RpRepProfile.Isolation -> dumbbellCurlLift()
    }

    private fun barbellSquatLift(): LiftContext = LiftContext(
        id = "barbell-back-squat",
        name = "Barbell Back Squat",
        movementPattern = MovementPattern.SquatPattern,
        movementPatterns = setOf(MovementPattern.SquatPattern),
        primaryMuscles = setOf(MuscleGroup.Quad),
        secondaryMuscles = setOf(MuscleGroup.Glute, MuscleGroup.Hamstring),
        kinematicProfile = KinematicProfile.AxialCompound,
    )

    private fun barbellDeadliftLift(): LiftContext = LiftContext(
        id = "barbell-deadlift",
        name = "Barbell Deadlift",
        movementPattern = MovementPattern.HipHinge,
        movementPatterns = setOf(MovementPattern.HipHinge),
        primaryMuscles = setOf(MuscleGroup.Hamstring),
        secondaryMuscles = setOf(MuscleGroup.Glute, MuscleGroup.LowerBack),
        kinematicProfile = KinematicProfile.AxialCompound,
    )

    private fun smithMachineDeadliftLift(): LiftContext = LiftContext(
        id = "smith-machine-deadlift",
        name = "Smith Machine Deadlift",
        movementPattern = MovementPattern.HipHinge,
        movementPatterns = setOf(MovementPattern.HipHinge),
        primaryMuscles = setOf(MuscleGroup.Hamstring),
        secondaryMuscles = setOf(MuscleGroup.Glute),
        kinematicProfile = KinematicProfile.AxialCompound,
    )

    private fun legPressLift(): LiftContext = LiftContext(
        id = "leg-press",
        name = "Leg Press",
        movementPattern = MovementPattern.SquatPattern,
        movementPatterns = setOf(MovementPattern.SquatPattern),
        primaryMuscles = setOf(MuscleGroup.Quad),
        secondaryMuscles = setOf(MuscleGroup.Glute),
        kinematicProfile = KinematicProfile.NonAxialCompound,
    )

    private fun latPulldownLift(): LiftContext = LiftContext(
        id = "lat-pulldown",
        name = "Lat Pulldown",
        movementPattern = MovementPattern.VerticalPull,
        movementPatterns = setOf(MovementPattern.VerticalPull),
        primaryMuscles = setOf(MuscleGroup.Back),
        secondaryMuscles = setOf(MuscleGroup.Bicep),
        kinematicProfile = KinematicProfile.NonAxialCompound,
    )

    private fun dumbbellCurlLift(): LiftContext = LiftContext(
        id = "dumbbell-curl",
        name = "Dumbbell Curl",
        movementPattern = MovementPattern.ElbowFlexion,
        movementPatterns = setOf(MovementPattern.ElbowFlexion),
        primaryMuscles = setOf(MuscleGroup.Bicep),
        secondaryMuscles = emptySet(),
        kinematicProfile = KinematicProfile.Isolation,
    )

    private fun legExtensionLift(): LiftContext = LiftContext(
        id = "leg-extension",
        name = "Leg Extension",
        movementPattern = MovementPattern.KneeExtension,
        movementPatterns = setOf(MovementPattern.KneeExtension),
        primaryMuscles = setOf(MuscleGroup.Quad),
        secondaryMuscles = emptySet(),
        kinematicProfile = KinematicProfile.Isolation,
    )
}