package com.stratum.domain.program.generation.prescription

import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.reference.RP_REP_RIR_BANDS
import com.stratum.domain.program.generation.reference.RpRepProfile
import com.stratum.domain.program.generation.slots.SiblingVariant
import com.stratum.domain.program.generation.slots.SlotRole
import com.stratum.domain.program.generation.slots.WorkoutSlotTemplate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Pins the Stratum-tuned rep/RIR bands and the [repRirForSlot] lookup. The bands guarantee a
 * hypertrophic floor (repMin ≥ 8) on top sets and split the rep range into two independent
 * drivers: the **floor** rises with within-session fatigue decay (so the last set stays
 * ≥ [LAST_SET_FLOOR]), and the **width** comes from the increment:typical-load ratio (so a
 * small increment on a light lift carries a wider band than a large increment on a heavy
 * compound). See [RP_REP_RIR_BANDS] KDoc for the increment/load principle rationale.
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
    @DisplayName("repRirForSlot returns the tuned band values when no fatigue/increment signal is present")
    fun repRirForSlotReturnsTunedBands() {
        // No increment tiers, no e1RM, no setCount → pure static-band fallback.
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

    // --- repRangeFromFatigue: floor from fatigue, width from increment ---

    @Test
    @DisplayName("repRangeFromFatigue: null slope + setCount<=1 falls back to the static band")
    fun repRangeFromFatigueNullSlopeFallsBack() {
        val band = RP_REP_RIR_BANDS.getValue(RpRepProfile.LowerFreeWeightCompound)
        assertEquals(8 to 12, repRangeFromFatigue(null, 4, 4, band))
    }

    @Test
    @DisplayName("repRangeFromFatigue: steep slope (-3.0, 4 sets) raises the floor; width from increment")
    fun repRangeFromFatigueSteepSlopeRaisesFloor() {
        // fatigueDrop = 3 * 3 = 9 → repMin = max(8, 5+9) = 14; width 4 → 14-18.
        val band = RP_REP_RIR_BANDS.getValue(RpRepProfile.Isolation)
        assertEquals(14 to 18, repRangeFromFatigue(-3.0, 4, 4, band))
    }

    @Test
    @DisplayName("repRangeFromFatigue: shallow slope (-1.0, 4 sets) keeps the hypertrophic floor")
    fun repRangeFromFatigueShallowSlopeKeepsFloor() {
        // fatigueDrop = 1 * 3 = 3 → repMin = max(8, 5+3) = 8; width 4 → 8-12.
        val band = RP_REP_RIR_BANDS.getValue(RpRepProfile.LowerFreeWeightCompound)
        assertEquals(8 to 12, repRangeFromFatigue(-1.0, 4, 4, band))
    }

    @Test
    @DisplayName("repRangeFromFatigue: population slope (-2.0, 4 sets) raises floor to 11")
    fun repRangeFromFatiguePopulationSlope() {
        // fatigueDrop = 2 * 3 = 6 → repMin = max(8, 5+6) = 11; width 4 → 11-15.
        val band = RP_REP_RIR_BANDS.getValue(RpRepProfile.LowerMachineOrUpperPress)
        assertEquals(11 to 15, repRangeFromFatigue(-2.0, 4, 4, band))
    }

    @Test
    @DisplayName("repRangeFromFatigue: floor is always ≥ 8 even with an extreme slope")
    fun repRangeFromFatigueFloorEnforced() {
        // slope -5, 4 sets → fatigueDrop = 15 → repMin = max(8, 5+15) = 20.
        val band = RP_REP_RIR_BANDS.getValue(RpRepProfile.LowerFreeWeightCompound)
        val (repMin, _) = repRangeFromFatigue(-5.0, 4, 4, band)
        assertEquals(20, repMin, "Floor = 5 + |slope|×(setCount-1) = 5+15 = 20")
    }

    @Test
    @DisplayName("repRangeFromFatigue: ceiling is capped at REP_RANGE_CEILING_CAP")
    fun repRangeFromFatigueCeilingCapped() {
        // Extreme slope → high floor; width pushes ceiling past the cap.
        val band = RP_REP_RIR_BANDS.getValue(RpRepProfile.Isolation)
        val (_, repMax) = repRangeFromFatigue(-10.0, 10, 10, band)
        assertEquals(REP_RANGE_CEILING_CAP, repMax, "Extreme slope must be capped")
    }

    @Test
    @DisplayName("repRangeFromFatigue: setCount <= 1 keeps the hypertrophic floor regardless of slope")
    fun repRangeFromFatigueSingleSetKeepsFloor() {
        val band = RP_REP_RIR_BANDS.getValue(RpRepProfile.Isolation)
        assertEquals(8 to 18, repRangeFromFatigue(-3.0, 1, 10, band))
    }

    @Test
    @DisplayName("repRangeFromFatigue: setCount = 0 falls back to the static band (guard)")
    fun repRangeFromFatigueZeroSetCountFallsBack() {
        val band = RP_REP_RIR_BANDS.getValue(RpRepProfile.LowerFreeWeightCompound)
        assertEquals(8 to 12, repRangeFromFatigue(-3.0, 0, 4, band))
    }

    @Test
    @DisplayName("repRangeFromFatigue: positive slope is treated as magnitude via abs (defensive)")
    fun repRangeFromFatiguePositiveSlopeTreatedAsMagnitude() {
        val band = RP_REP_RIR_BANDS.getValue(RpRepProfile.Isolation)
        assertEquals(14 to 18, repRangeFromFatigue(3.0, 4, 4, band))
    }

    @Test
    @DisplayName("repRangeFromFatigue: null slope + setCount>1 keeps floor at 8, width from increment")
    fun repRangeFromFatigueNullSlopeWithSetCountKeepsHypertrophicFloor() {
        val band = RP_REP_RIR_BANDS.getValue(RpRepProfile.LowerFreeWeightCompound)
        assertEquals(8 to 12, repRangeFromFatigue(null, 4, 4, band))
    }

    // --- repWidthFromIncrement: width from the increment:typical-load ratio ---

    @Test
    @DisplayName("repWidthFromIncrement: null e1RM falls back to the static band spread")
    fun repWidthFromIncrementNullE1rmFallsBack() {
        val band = RP_REP_RIR_BANDS.getValue(RpRepProfile.Isolation)
        assertEquals(10, repWidthFromIncrement(null, heavyBarbellTiers(), band))
    }

    @Test
    @DisplayName("repWidthFromIncrement: empty tiers fall back to the static band spread")
    fun repWidthFromIncrementEmptyTiersFallsBack() {
        val band = RP_REP_RIR_BANDS.getValue(RpRepProfile.LowerFreeWeightCompound)
        assertEquals(4, repWidthFromIncrement(Weight(250.0, Weight.WeightUnit.Lb), emptyList(), band))
    }

    @Test
    @DisplayName("repWidthFromIncrement: heavy barbell (5 lb on 250 lb e1RM) yields the minimum width")
    fun repWidthFromIncrementHeavyBarbellNarrow() {
        val band = RP_REP_RIR_BANDS.getValue(RpRepProfile.LowerFreeWeightCompound)
        // 5 lb / ~182 lb typical → ~0.75 reps/increment → 2 × 0.75 = 1.5 → round 2 → clamp to 4.
        assertEquals(4, repWidthFromIncrement(Weight(250.0, Weight.WeightUnit.Lb), heavyBarbellTiers(), band))
    }

    @Test
    @DisplayName("repWidthFromIncrement: light curl (2.5 lb on 35 lb e1RM) yields a wider band")
    fun repWidthFromIncrementLightCurlWide() {
        val band = RP_REP_RIR_BANDS.getValue(RpRepProfile.Isolation)
        // 2.5 lb / ~25.5 lb typical → ~2.65 reps/increment → 2 × 2.65 = 5.3 → round 5.
        assertEquals(5, repWidthFromIncrement(Weight(35.0, Weight.WeightUnit.Lb), lightDumbbellTiers(), band))
    }

    @Test
    @DisplayName("repWidthFromIncrement: machine chest press (5 lb on 140 lb e1RM) yields the minimum width")
    fun repWidthFromIncrementMachinePressMinimum() {
        val band = RP_REP_RIR_BANDS.getValue(RpRepProfile.LowerMachineOrUpperPress)
        // 5 lb / ~102 lb typical → ~1.33 reps/increment → 2 × 1.33 = 2.66 → round 3 → clamp to 4.
        assertEquals(4, repWidthFromIncrement(Weight(140.0, Weight.WeightUnit.Lb), plateLoadedTiers(), band))
    }

    // --- repRirForSlot end-to-end with fatigue + increment ---

    @Test
    @DisplayName("repRirForSlot: steep fatigue + small increment raises floor, keeps modest width (14-18)")
    fun repRirForSlotSteepFatigueSmallIncrementRaisesFloor() {
        // Machine chest press: slope -3, 4 sets, 5 lb on 140 lb e1RM.
        val lift = machineChestPressLift(fatigueSlope = -3.0)
        assertEquals(
            RepRirPlan(repMin = 14, repMax = 18, topSetRir = 3, backOffRir = 2),
            repRirForSlot(slot(RpRepProfile.LowerMachineOrUpperPress), lift, setCount = 4),
        )
    }

    @Test
    @DisplayName("repRirForSlot: shallow fatigue + heavy barbell keeps the hypertrophic floor (8-12)")
    fun repRirForSlotShallowFatigueHeavyBarbellKeepsFloor() {
        val lift = heavyBarbellBenchLift(fatigueSlope = -1.0)
        assertEquals(
            RepRirPlan(repMin = 8, repMax = 12, topSetRir = 4, backOffRir = 3),
            repRirForSlot(slot(RpRepProfile.LowerFreeWeightCompound), lift, setCount = 4),
        )
    }

    @Test
    @DisplayName("repRirForSlot: default setCount=0 keeps the hypertrophic floor; width from increment")
    fun repRirForSlotDefaultSetCountKeepsFloor() {
        // setCount=0 → no fatigue drop → floor 8; width 4 from the machine press increment ratio.
        val lift = machineChestPressLift(fatigueSlope = -3.0)
        assertEquals(
            RepRirPlan(repMin = 8, repMax = 12, topSetRir = 3, backOffRir = 2),
            repRirForSlot(slot(RpRepProfile.LowerMachineOrUpperPress), lift),
        )
    }

    @Test
    @DisplayName("repRirForSlot: no increment signal + steep fatigue raises floor, static width")
    fun repRirForSlotNoIncrementSignalRaisesFloorWithStaticWidth() {
        // No tiers/e1RM → width falls back to the band spread (6 for LowerMachineOrUpperPress).
        // Slope -3, 4 sets → fatigueDrop 9 → repMin 14; width 6 → 14-20.
        val lift = LiftContext(
            id = "machine-press-no-signal",
            name = "Machine Chest Press",
            movementPattern = MovementPattern.HorizontalPress,
            movementPatterns = setOf(MovementPattern.HorizontalPress),
            primaryMuscles = setOf(MuscleGroup.Chest),
            secondaryMuscles = setOf(MuscleGroup.Tricep),
            kinematicProfile = KinematicProfile.NonAxialCompound,
            fatigueDecaySlope = -3.0,
        )
        assertEquals(
            RepRirPlan(repMin = 14, repMax = 20, topSetRir = 3, backOffRir = 2),
            repRirForSlot(slot(RpRepProfile.LowerMachineOrUpperPress), lift, setCount = 4),
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

    // --- Increment tier fixtures ---

    private fun heavyBarbellTiers(): List<IncrementTier> = listOf(
        IncrementTier(Weight(0.0, Weight.WeightUnit.Lb), Weight(100.0, Weight.WeightUnit.Lb), Weight(5.0, Weight.WeightUnit.Lb)),
        IncrementTier(Weight(100.0, Weight.WeightUnit.Lb), null, Weight(5.0, Weight.WeightUnit.Lb)),
    )

    private fun lightDumbbellTiers(): List<IncrementTier> = listOf(
        IncrementTier(Weight(0.0, Weight.WeightUnit.Lb), Weight(50.0, Weight.WeightUnit.Lb), Weight(2.5, Weight.WeightUnit.Lb)),
        IncrementTier(Weight(50.0, Weight.WeightUnit.Lb), null, Weight(5.0, Weight.WeightUnit.Lb)),
    )

    private fun plateLoadedTiers(): List<IncrementTier> = listOf(
        IncrementTier(Weight(0.0, Weight.WeightUnit.Lb), Weight(50.0, Weight.WeightUnit.Lb), Weight(5.0, Weight.WeightUnit.Lb)),
        IncrementTier(Weight(50.0, Weight.WeightUnit.Lb), null, Weight(5.0, Weight.WeightUnit.Lb)),
    )

    // --- Lift factories (no increment/e1RM signal by default → static-band fallback) ---

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

    // --- Lift factories with increment + e1RM signal (for end-to-end rep-range tests) ---

    private fun machineChestPressLift(fatigueSlope: Double? = null): LiftContext = LiftContext(
        id = "machine-chest-press",
        name = "Seated Neutral Grip Plate-Loaded Machine Chest Press",
        movementPattern = MovementPattern.HorizontalPress,
        movementPatterns = setOf(MovementPattern.HorizontalPress),
        primaryMuscles = setOf(MuscleGroup.Chest),
        secondaryMuscles = setOf(MuscleGroup.Tricep),
        kinematicProfile = KinematicProfile.NonAxialCompound,
        fatigueDecaySlope = fatigueSlope,
        incrementTiers = plateLoadedTiers(),
        estimatedOneRm = Weight(140.0, Weight.WeightUnit.Lb),
    )

    private fun heavyBarbellBenchLift(fatigueSlope: Double? = null): LiftContext = LiftContext(
        id = "barbell-bench-press",
        name = "Barbell Bench Press",
        movementPattern = MovementPattern.HorizontalPress,
        movementPatterns = setOf(MovementPattern.HorizontalPress),
        primaryMuscles = setOf(MuscleGroup.Chest),
        secondaryMuscles = setOf(MuscleGroup.Tricep),
        kinematicProfile = KinematicProfile.AxialCompound,
        fatigueDecaySlope = fatigueSlope,
        incrementTiers = heavyBarbellTiers(),
        estimatedOneRm = Weight(250.0, Weight.WeightUnit.Lb),
    )
}