package com.stratum.domain.workout

import com.stratum.core.testing.workout.FakeFatigueModel
import com.stratum.domain.importing.FatigueDecay
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Direct unit tests for [RecomputeRecommendationsUseCase], porting the RIR-math scenarios
 * previously asserted only through the [WorkoutViewModel] facade. Expected values were
 * derived in Python from the Brzycki/Epley e1RM formulas, the population fatigue decay
 * slope (`-2.0` reps per set index at 0 RIR), and increment-tier rounding.
 *
 * Uses [FakeFatigueModel] (UNIT_TESTING.md §6) — no MockK (Detekt enforced).
 */
@DisplayName("RecomputeRecommendationsUseCase: live recommendation pipeline (D4)")
class RecomputeRecommendationsUseCaseTest {

    private val liftId = LiftId("L-1")
    private val workoutLiftId = WorkoutLiftId("wl-1")
    private val liftSetId = LiftSetId("ls-1")
    private val backoffSetId = LiftSetId("ls-2")
    private val workoutId = WorkoutId("w-1")

    private val useCase = RecomputeRecommendationsUseCase(
        recommendationEngine = RecommendationEngine(),
        fatigueModel = FakeFatigueModel(),
    )

    private fun lb(amount: Double) = Weight(amount, Weight.WeightUnit.Lb)

    /** Default population tiers: 0-40 → 2.5, 40-100 → 5, 100-300 → 10, 300+ → 25. */
    private val defaultTiers = listOf(
        IncrementTier(lb(0.0), lb(40.0), lb(2.5)),
        IncrementTier(lb(40.0), lb(100.0), lb(5.0)),
        IncrementTier(lb(100.0), lb(300.0), lb(10.0)),
        IncrementTier(lb(300.0), null, lb(25.0)),
    )

    private fun catalogLift(tiers: List<IncrementTier> = emptyList()): Lift = Lift(
        id = liftId,
        name = "Squat",
        movementPatterns = setOf(MovementPattern.SquatPattern),
        primaryMuscles = setOf(MuscleGroup.Quad),
        secondaryMuscles = emptySet(),
        incrementTiers = tiers,
        presetTags = emptySet(),
        enabled = true,
    )

    /**
     * Builds a rendered workout with one lift, two top-set slots at the same rep range,
     * the first slot logged and the second upcoming. The snapshot baseline weight for the
     * upcoming slot is [upcomingWeight] (0.0 means "blank" — engine falls back to the
     * performed weight).
     */
    private fun twoTopSets(
        performedWeight: Double,
        performedReps: Int,
        performedRir: Int,
        repMin: Int,
        repMax: Int,
        rirGoal: Int,
        upcomingWeight: Double = performedWeight,
    ): RenderedWorkout {
        val performed = LoggedSetValue(
            slotId = SetSlotId.Template(liftSetId),
            weight = lb(performedWeight),
            reps = performedReps,
            rir = performedRir,
        )
        val firstSet = RenderedSet(
            slotId = SetSlotId.Template(liftSetId),
            workoutLiftId = workoutLiftId,
            liftId = liftId,
            position = 0,
            setType = SetType.Standard,
            isTopSet = true,
            repMin = repMin,
            repMax = repMax,
            rirGoal = rirGoal,
            restSeconds = 120,
            recommendedWeight = lb(performedWeight),
            recommendedReps = repMin,
            loggedValue = performed,
        )
        val secondSet = RenderedSet(
            slotId = SetSlotId.Template(backoffSetId),
            workoutLiftId = workoutLiftId,
            liftId = liftId,
            position = 1,
            setType = SetType.Standard,
            isTopSet = true,
            repMin = repMin,
            repMax = repMax,
            rirGoal = rirGoal,
            restSeconds = 120,
            recommendedWeight = lb(upcomingWeight),
            recommendedReps = repMin,
            loggedValue = null,
        )
        return RenderedWorkout(
            workoutId = workoutId,
            workoutName = "Workout 1",
            lifts = listOf(
                RenderedWorkoutLift(
                    workoutLiftId = workoutLiftId,
                    liftId = liftId,
                    liftName = "Squat",
                    progressionScheme = ProgressionScheme.Double,
                    sets = listOf(firstSet, secondSet),
                ),
            ),
        )
    }

    @Test
    @DisplayName("RIR-1: 75x4@2 RIR top set (4-12 range) -> next top set Hold 75, reps clamp to 4")
    fun `75x4 at 2 RIR holds weight and clamps reps to repMin`() {
        // e1RM from 75x6 = 89.53. At 75 lb, repsForWeight = 6 @ 0 RIR → 4 @ 2 RIR.
        // targetReps (top, repMin=4) = 4. projectedReps (4) >= 4 → Hold 75.
        // Fatigue projection: raw set2 = 6 - 2 = 4 @ 0 RIR → 4 - 2 = 2 @ 2 RIR.
        // Clamped to repMin=4 → 4 reps.
        val rendered = twoTopSets(
            performedWeight = 75.0,
            performedReps = 4,
            performedRir = 2,
            repMin = 4,
            repMax = 12,
            rirGoal = 2,
        )
        val recommendations = useCase(rendered, mapOf(liftId to catalogLift()))
        val rec = recommendations[SetSlotId.Template(backoffSetId)]
        assertTrue(rec != null, "an upcoming recommendation must be produced")
        assertEquals(75.0, rec!!.weight.amount, 0.01)
        assertEquals(4, rec.reps)
    }

    @Test
    @DisplayName("RIR-2: 75x8@2 RIR top set (4-12 range) -> next top set Hold 75, 7 reps")
    fun `75x8 at 2 RIR holds weight and projects 7 reps`() {
        // e1RM from 75x10 = 102.81. At 75 lb, repsForWeight = 11 @ 0 RIR → 9 @ 2 RIR.
        // targetReps (top, repMin=4) = 4. 9 >= 4 → Hold 75.
        // Fatigue projection: raw set2 = 11 - 2 = 9 @ 0 RIR → 9 - 2 = 7 @ 2 RIR.
        // Clamped to 4..12 → 7 reps.
        val rendered = twoTopSets(
            performedWeight = 75.0,
            performedReps = 8,
            performedRir = 2,
            repMin = 4,
            repMax = 12,
            rirGoal = 2,
        )
        val recommendations = useCase(rendered, mapOf(liftId to catalogLift()))
        val rec = recommendations[SetSlotId.Template(backoffSetId)]
        assertTrue(rec != null)
        assertEquals(75.0, rec!!.weight.amount, 0.01)
        assertEquals(7, rec.reps)
    }

    @Test
    @DisplayName("Reduce weight rounds to lift's increment tier")
    fun `reduce weight rounds to increment tier`() {
        // 100x3@1 RIR top set (5-8 range, rirGoal=1).
        // e1RM from 100x4 = 112.13. At 100 lb, repsForWeight = 4 @ 0 RIR → 3 @ 1 RIR.
        // targetReps (top, repMin=5) = 5. 3 < 5 → Reduce.
        // weightForReps(112.13, 5+1=6) = 93.93 → rounded to 95 in the 40-100 band (5 lb).
        val rendered = twoTopSets(
            performedWeight = 100.0,
            performedReps = 3,
            performedRir = 1,
            repMin = 5,
            repMax = 8,
            rirGoal = 1,
        )
        val recommendations = useCase(rendered, mapOf(liftId to catalogLift(tiers = defaultTiers)))
        val rec = recommendations[SetSlotId.Template(backoffSetId)]
        assertTrue(rec != null)
        assertEquals(95.0, rec!!.weight.amount, 0.01)
    }

    @Test
    @DisplayName("Too-hard reduce: missed floor by 2 reduces weight for last set")
    fun `too hard reduces weight targeting last remaining set`() {
        // 75x2@2 on a 4-12 top set. 2 sets total; after logging set 1, 1 remains.
        // achieved=2, floor=4, 4-2=2 → too hard. e1RM from 75x4 = 84.10.
        // remainingCount=1 → fatigueForLastSet=0.
        // weightForReps(84.10, 4+2+0=6) = 70.5. No tiers → 70.5.
        val rendered = twoTopSets(
            performedWeight = 75.0,
            performedReps = 2,
            performedRir = 2,
            repMin = 4,
            repMax = 12,
            rirGoal = 2,
        )
        val recommendations = useCase(rendered, mapOf(liftId to catalogLift()))
        val rec = recommendations[SetSlotId.Template(backoffSetId)]
        assertTrue(rec != null)
        assertTrue(rec!!.weight.amount < 75.0, "must reduce below 75")
        assertEquals(70.5, rec.weight.amount, 0.1)
    }

    @Test
    @DisplayName("Too-easy bump: exceeded ceiling by 2 bumps weight +5 lb")
    fun `too easy bumps weight by 5 lb`() {
        // 135x9@1 on a 4-6 top set. Achieved @ 2 RIR = 9 + 1 - 2 = 8; repMax=6; 8-6=2 → too easy.
        // Bump = 135 + 5 = 140 (no tiers → unrounded).
        val rendered = twoTopSets(
            performedWeight = 135.0,
            performedReps = 9,
            performedRir = 1,
            repMin = 4,
            repMax = 6,
            rirGoal = 2,
        )
        val recommendations = useCase(rendered, mapOf(liftId to catalogLift()))
        val rec = recommendations[SetSlotId.Template(backoffSetId)]
        assertTrue(rec != null)
        assertEquals(140.0, rec!!.weight.amount, 0.01)
    }

    @Test
    @DisplayName("No performed set -> no recommendation (first set of the session)")
    fun `no performed set produces no recommendation`() {
        // No logged sets → no e1RM derivable → engine returns null → no entry.
        val firstSet = RenderedSet(
            slotId = SetSlotId.Template(liftSetId),
            workoutLiftId = workoutLiftId,
            liftId = liftId,
            position = 0,
            setType = SetType.Standard,
            isTopSet = true,
            repMin = 4,
            repMax = 12,
            rirGoal = 2,
            restSeconds = 120,
            recommendedWeight = lb(135.0),
            recommendedReps = 4,
            loggedValue = null,
        )
        val secondSet = firstSet.copy(
            slotId = SetSlotId.Template(backoffSetId),
            position = 1,
        )
        val rendered = RenderedWorkout(
            workoutId = workoutId,
            workoutName = "Workout 1",
            lifts = listOf(
                RenderedWorkoutLift(
                    workoutLiftId = workoutLiftId,
                    liftId = liftId,
                    liftName = "Squat",
                    progressionScheme = ProgressionScheme.Double,
                    sets = listOf(firstSet, secondSet),
                ),
            ),
        )
        val recommendations = useCase(rendered, mapOf(liftId to catalogLift()))
        assertNull(recommendations[SetSlotId.Template(backoffSetId)])
        assertTrue(recommendations.isEmpty(), "no recommendations when nothing is logged yet")
    }

    @Test
    @DisplayName("Recommendation is produced for every upcoming set, keyed by slot id")
    fun `recommendations keyed by upcoming slot id`() {
        // Two upcoming sets after one logged set — both should get recommendations.
        val performed = LoggedSetValue(
            slotId = SetSlotId.Template(liftSetId),
            weight = lb(75.0),
            reps = 8,
            rir = 2,
        )
        val sets = listOf(
            RenderedSet(
                slotId = SetSlotId.Template(liftSetId),
                workoutLiftId = workoutLiftId,
                liftId = liftId,
                position = 0,
                setType = SetType.Standard,
                isTopSet = true,
                repMin = 4,
                repMax = 12,
                rirGoal = 2,
                restSeconds = 120,
                recommendedWeight = lb(75.0),
                recommendedReps = 4,
                loggedValue = performed,
            ),
            RenderedSet(
                slotId = SetSlotId.Template(LiftSetId("ls-2")),
                workoutLiftId = workoutLiftId,
                liftId = liftId,
                position = 1,
                setType = SetType.Standard,
                isTopSet = true,
                repMin = 4,
                repMax = 12,
                rirGoal = 2,
                restSeconds = 120,
                recommendedWeight = lb(75.0),
                recommendedReps = 4,
                loggedValue = null,
            ),
            RenderedSet(
                slotId = SetSlotId.Template(LiftSetId("ls-3")),
                workoutLiftId = workoutLiftId,
                liftId = liftId,
                position = 2,
                setType = SetType.Standard,
                isTopSet = true,
                repMin = 4,
                repMax = 12,
                rirGoal = 2,
                restSeconds = 120,
                recommendedWeight = lb(75.0),
                recommendedReps = 4,
                loggedValue = null,
            ),
        )
        val rendered = RenderedWorkout(
            workoutId = workoutId,
            workoutName = "Workout 1",
            lifts = listOf(
                RenderedWorkoutLift(
                    workoutLiftId = workoutLiftId,
                    liftId = liftId,
                    liftName = "Squat",
                    progressionScheme = ProgressionScheme.Double,
                    sets = sets,
                ),
            ),
        )
        val recommendations = useCase(rendered, mapOf(liftId to catalogLift()))
        assertEquals(2, recommendations.size)
        assertTrue(recommendations.containsKey(SetSlotId.Template(LiftSetId("ls-2"))))
        assertTrue(recommendations.containsKey(SetSlotId.Template(LiftSetId("ls-3"))))
    }

    @Test
    @DisplayName("per-lift slope steeper than population yields fewer projected reps on set 2")
    fun `per lift slope steeper than population reduces projected reps`() {
        // Same setup as `75x8 at 2 RIR holds weight and projects 7 reps` (population slope -2.0
        // → raw set2 = 9 @ 0 RIR → 7 @ 2 RIR). With a steeper -3.0 slope, raw set2 = 8 @ 0 RIR
        // → 6 @ 2 RIR. FatigueDecay passed via the new fatigueByLiftId param.
        val rendered = twoTopSets(
            performedWeight = 75.0,
            performedReps = 8,
            performedRir = 2,
            repMin = 4,
            repMax = 12,
            rirGoal = 2,
        )
        val fatigueByLiftId = mapOf(liftId to FatigueDecay(slope = -3.0, intercept = 0.0))
        val recommendations = useCase(rendered, mapOf(liftId to catalogLift()), fatigueByLiftId)
        val rec = recommendations[SetSlotId.Template(backoffSetId)]
        assertTrue(rec != null)
        assertEquals(6, rec!!.reps, "Steeper slope (-3.0) should project fewer reps than population (-2.0) → 7")
    }

    @Test
    @DisplayName("per-lift intercept shifts projected reps by a flat offset")
    fun `per lift intercept shifts projected reps`() {
        // Baseline (population slope -2.0, intercept 0.0): 75x8@2 → raw set2 = 9 @ 0 RIR → 7 @ 2 RIR.
        // With intercept -1.0 (one fewer rep across the board): raw set2 = 8 @ 0 RIR → 6 @ 2 RIR.
        val rendered = twoTopSets(
            performedWeight = 75.0,
            performedReps = 8,
            performedRir = 2,
            repMin = 4,
            repMax = 12,
            rirGoal = 2,
        )
        val fatigueByLiftId = mapOf(liftId to FatigueDecay(slope = -2.0, intercept = -1.0))
        val recommendations = useCase(rendered, mapOf(liftId to catalogLift()), fatigueByLiftId)
        val rec = recommendations[SetSlotId.Template(backoffSetId)]
        assertTrue(rec != null)
        assertEquals(6, rec!!.reps, "Intercept -1.0 should shift projected reps down by 1 vs population (7 → 6)")
    }

    @Test
    @DisplayName("shallower per-lift slope yields more projected reps than population")
    fun `shallower slope yields more projected reps`() {
        // Population slope -2.0 → 7 reps (see `75x8 at 2 RIR holds weight and projects 7 reps`).
        // Shallower -1.0 slope: raw set2 = 10 @ 0 RIR → 8 @ 2 RIR.
        val rendered = twoTopSets(
            performedWeight = 75.0,
            performedReps = 8,
            performedRir = 2,
            repMin = 4,
            repMax = 12,
            rirGoal = 2,
        )
        val fatigueByLiftId = mapOf(liftId to FatigueDecay(slope = -1.0, intercept = 0.0))
        val recommendations = useCase(rendered, mapOf(liftId to catalogLift()), fatigueByLiftId)
        val rec = recommendations[SetSlotId.Template(backoffSetId)]
        assertTrue(rec != null)
        assertEquals(8, rec!!.reps, "Shallower slope (-1.0) should project more reps than population (-2.0) → 7")
    }

    @Test
    @DisplayName("lift absent from fatigueByLiftId falls back to population slope")
    fun `lift absent from fatigue map falls back to population`() {
        // Same setup as the 7-rep population case, but fatigueByLiftId is empty (lift not in map).
        // Must match the population-slope result (7 reps), not error or produce a different value.
        val rendered = twoTopSets(
            performedWeight = 75.0,
            performedReps = 8,
            performedRir = 2,
            repMin = 4,
            repMax = 12,
            rirGoal = 2,
        )
        val recommendations = useCase(rendered, mapOf(liftId to catalogLift()), fatigueByLiftId = emptyMap())
        val rec = recommendations[SetSlotId.Template(backoffSetId)]
        assertTrue(rec != null)
        assertEquals(7, rec!!.reps, "Absent lift must fall back to population slope (-2.0) → 7 reps")
    }
}