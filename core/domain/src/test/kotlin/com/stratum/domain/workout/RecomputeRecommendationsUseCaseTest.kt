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
     * performed weight). [completedSetPrescribedWeight] is the logged slot's baseline
     * prescription (merge keeps baseline when a set is logged).
     */
    private fun twoTopSets(
        performedWeight: Double,
        performedReps: Int,
        performedRir: Int,
        repMin: Int,
        repMax: Int,
        rirGoal: Int,
        upcomingWeight: Double = performedWeight,
        completedSetPrescribedWeight: Double = performedWeight,
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
            recommendedWeight = lb(completedSetPrescribedWeight),
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
    @DisplayName("RIR-1: 75x4@2 RIR top set (4-12 range) -> Reduce so next set hits global floor of 5")
    fun `75x4 at 2 RIR reduces weight so next set hits rep floor of 5`() {
        // e1RM from 75x6 = 89.53. At 75 lb, repsForWeight = 6 @ 0 RIR → 4 @ 2 RIR.
        // Global floor: targetReps = max(repMin=4, BACKOFF_REP_FLOOR=5) = 5.
        // projectedReps (4) < 5 → Reduce weight targeting 5@2 RIR.
        // Live rep projection is floored at BACKOFF_REP_FLOOR (never surfaces 4).
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
        assertTrue(rec!!.weight.amount < 75.0, "must drop weight when capacity is below the rep floor")
        assertTrue(rec.reps >= 5, "recommended reps must never go below BACKOFF_REP_FLOOR")
    }

    @Test
    @DisplayName("Backoff live rep projection never goes below BACKOFF_REP_FLOOR")
    fun `backoff live projection floors at 5 even when capacity is lower`() {
        // Top set logged poorly; backoff has no rep range (repMin=repMax=0). Fatigue can
        // project 3–4 raw at-RIR reps — the display pipeline must still floor at 5.
        val performed = LoggedSetValue(
            slotId = SetSlotId.Template(liftSetId),
            weight = lb(100.0),
            reps = 5,
            rir = 0,
        )
        val firstSet = RenderedSet(
            slotId = SetSlotId.Template(liftSetId),
            workoutLiftId = workoutLiftId,
            liftId = liftId,
            position = 0,
            setType = SetType.Standard,
            isTopSet = true,
            repMin = 8,
            repMax = 12,
            rirGoal = 2,
            restSeconds = 120,
            recommendedWeight = lb(100.0),
            recommendedReps = 8,
            loggedValue = performed,
        )
        val backoff = RenderedSet(
            slotId = SetSlotId.Template(backoffSetId),
            workoutLiftId = workoutLiftId,
            liftId = liftId,
            position = 1,
            setType = SetType.Standard,
            isTopSet = false,
            repMin = 0,
            repMax = 0,
            rirGoal = 2,
            restSeconds = 120,
            recommendedWeight = lb(100.0),
            recommendedReps = 0,
            loggedValue = null,
        )
        val rendered = RenderedWorkout(
            workoutId = workoutId,
            workoutName = "Workout 1",
            lifts = listOf(
                RenderedWorkoutLift(
                    workoutLiftId = workoutLiftId,
                    liftId = liftId,
                    liftName = "Leg Extension",
                    progressionScheme = ProgressionScheme.Double,
                    sets = listOf(firstSet, backoff),
                ),
            ),
        )
        val recommendations = useCase(rendered, mapOf(liftId to catalogLift()))
        val rec = recommendations[SetSlotId.Template(backoffSetId)]
        assertTrue(rec != null, "backoff must receive a live recommendation")
        assertTrue(rec!!.reps >= 5, "backoff recommended reps must be >= BACKOFF_REP_FLOOR, was ${rec.reps}")
    }

    @Test
    @DisplayName("RIR-2: 75x8@2 RIR top set (4-12 range) -> next top set Hold 75, 7 reps")
    fun `75x8 at 2 RIR holds weight and projects 7 reps`() {
        // e1RM from 75x10 = 102.81. At 75 lb, repsForWeight = 10 @ 0 RIR → 8 @ 2 RIR.
        // targetReps (top, repMin=4) = 4. 8 >= 4 → Hold 75.
        // Fatigue projection: raw set2 = 10 - 2 = 8 @ 0 RIR → 8 - 2 = 6 @ 2 RIR.
        // Clamped to 4..12 → 6 reps.
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
        assertEquals(6, rec.reps)
    }

    @Test
    @DisplayName("Re-anchor: logged heavier than snapshot prescription reanchors upcoming weight")
    fun `heavier than prescription reanchors upcoming recommendation weight`() {
        // Performed 400 on a set prescribed 200; upcoming snapshot still 200.
        // Must re-anchor to 400 rather than Hold(200) with inflated projected reps.
        val rendered = twoTopSets(
            performedWeight = 400.0,
            performedReps = 5,
            performedRir = 2,
            repMin = 4,
            repMax = 8,
            rirGoal = 2,
            upcomingWeight = 200.0,
            completedSetPrescribedWeight = 200.0,
        )
        val recommendations = useCase(rendered, mapOf(liftId to catalogLift()))
        val rec = recommendations[SetSlotId.Template(backoffSetId)]
        assertTrue(rec != null, "upcoming set must receive a live recommendation")
        assertEquals(400.0, rec!!.weight.amount, 0.01)
        // At the re-anchored load, projected reps stay in the prescribed band (not 30+).
        assertTrue(rec.reps in 4..8, "reps must stay in range at re-anchored load, was ${rec.reps}")
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
        // achieved=2, targetFloor=max(4,5)=5, 5-2=3 → too hard. e1RM from 75x4 = 84.10.
        // remainingCount=1 → fatigueForLastSet=0.
        // weightForReps(84.10, 5+2+0=7) ≈ 68.0. No tiers → unrounded.
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
        assertTrue(rec.reps >= 5, "recommended reps must respect global floor")
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
        // Fatigue steps per slot: set2 index=2 → 6 reps; set3 index=3 would be 4 raw but is
        // floored at BACKOFF_REP_FLOOR (5).
        assertEquals(6, recommendations[SetSlotId.Template(LiftSetId("ls-2"))]!!.reps)
        assertEquals(5, recommendations[SetSlotId.Template(LiftSetId("ls-3"))]!!.reps)
    }

    @Test
    @DisplayName("Multi-set cascade: remaining slots get stepped fatigue, not a flat next-set copy")
    fun `multi set cascade steps fatigue for each upcoming slot`() {
        // Screenshot repro: top set 165×14@2 logged; three backoff slots @ RIR 1, same load.
        // Population slope −2.0 capacity reps / set index, baseline capacity = 14+2 = 16:
        //   set2 index=2 → raw 14 @0 → 13 @1 RIR
        //   set3 index=3 → raw 12 @0 → 11 @1 RIR
        //   set4 index=4 → raw 10 @0 →  9 @1 RIR
        // (Not a flat 13/13/13 — each slot is one more decay step from the performed anchor.)
        val performed = LoggedSetValue(
            slotId = SetSlotId.Template(liftSetId),
            weight = lb(165.0),
            reps = 14,
            rir = 2,
        )
        fun backoff(setId: String, position: Int) = RenderedSet(
            slotId = SetSlotId.Template(LiftSetId(setId)),
            workoutLiftId = workoutLiftId,
            liftId = liftId,
            position = position,
            setType = SetType.Standard,
            isTopSet = false,
            repMin = 8,
            repMax = 20,
            rirGoal = 1,
            restSeconds = 90,
            recommendedWeight = lb(165.0),
            recommendedReps = 8,
            loggedValue = null,
        )
        val sets = listOf(
            RenderedSet(
                slotId = SetSlotId.Template(liftSetId),
                workoutLiftId = workoutLiftId,
                liftId = liftId,
                position = 0,
                setType = SetType.Standard,
                isTopSet = true,
                repMin = 8,
                repMax = 20,
                rirGoal = 2,
                restSeconds = 120,
                recommendedWeight = lb(165.0),
                recommendedReps = 8,
                loggedValue = performed,
            ),
            backoff("ls-2", 1),
            backoff("ls-3", 2),
            backoff("ls-4", 3),
        )
        val rendered = RenderedWorkout(
            workoutId = workoutId,
            workoutName = "Workout 1",
            lifts = listOf(
                RenderedWorkoutLift(
                    workoutLiftId = workoutLiftId,
                    liftId = liftId,
                    liftName = "Machine Leg Extension",
                    progressionScheme = ProgressionScheme.Double,
                    sets = sets,
                ),
            ),
        )
        val recommendations = useCase(rendered, mapOf(liftId to catalogLift()))
        assertEquals(3, recommendations.size)
        val r2 = recommendations[SetSlotId.Template(LiftSetId("ls-2"))]!!
        val r3 = recommendations[SetSlotId.Template(LiftSetId("ls-3"))]!!
        val r4 = recommendations[SetSlotId.Template(LiftSetId("ls-4"))]!!
        assertEquals(165.0, r2.weight.amount, 0.01)
        assertEquals(165.0, r3.weight.amount, 0.01)
        assertEquals(165.0, r4.weight.amount, 0.01)
        assertEquals(13, r2.reps)
        assertEquals(11, r3.reps)
        assertEquals(9, r4.reps)
        assertTrue(r2.reps > r3.reps && r3.reps > r4.reps, "reps must be strictly decreasing across remaining sets")
    }

    @Test
    @DisplayName("Cascade re-anchors on the most recent performed set, not the first")
    fun `cascade reanchors on most recent performed set`() {
        // Set 1 and set 2 logged; set 3/4 upcoming. Anchor must be set 2 (most recent).
        // Set 2 is deliberately off the population schedule (10@1, not 13@1) so anchoring
        // on set 1 vs set 2 diverges — a firstOrNull regression would still project from
        // set 1 (capacity 16) → 11/9, while last/max position anchor on set 2 (capacity 11)
        // → raw set3 index2 = 9 → 8@1; set4 index3 = 7 → 6@1.
        val set1 = LoggedSetValue(
            slotId = SetSlotId.Template(liftSetId),
            weight = lb(165.0),
            reps = 14,
            rir = 2,
        )
        val set2 = LoggedSetValue(
            slotId = SetSlotId.Template(LiftSetId("ls-2")),
            weight = lb(165.0),
            reps = 10,
            rir = 1,
        )
        fun set(
            setId: String,
            position: Int,
            logged: LoggedSetValue?,
            rirGoal: Int,
            isTop: Boolean,
        ) = RenderedSet(
            slotId = SetSlotId.Template(LiftSetId(setId)),
            workoutLiftId = workoutLiftId,
            liftId = liftId,
            position = position,
            setType = SetType.Standard,
            isTopSet = isTop,
            repMin = 1,
            repMax = 30,
            rirGoal = rirGoal,
            restSeconds = 90,
            recommendedWeight = lb(165.0),
            recommendedReps = 8,
            loggedValue = logged,
        )
        val rendered = RenderedWorkout(
            workoutId = workoutId,
            workoutName = "Workout 1",
            lifts = listOf(
                RenderedWorkoutLift(
                    workoutLiftId = workoutLiftId,
                    liftId = liftId,
                    liftName = "Machine Leg Extension",
                    progressionScheme = ProgressionScheme.Double,
                    sets = listOf(
                        set("ls-1", 0, set1, rirGoal = 2, isTop = true),
                        set("ls-2", 1, set2, rirGoal = 1, isTop = false),
                        set("ls-3", 2, null, rirGoal = 1, isTop = false),
                        set("ls-4", 3, null, rirGoal = 1, isTop = false),
                    ),
                ),
            ),
        )
        val recommendations = useCase(rendered, mapOf(liftId to catalogLift()))
        assertEquals(2, recommendations.size)
        assertEquals(8, recommendations[SetSlotId.Template(LiftSetId("ls-3"))]!!.reps)
        assertEquals(6, recommendations[SetSlotId.Template(LiftSetId("ls-4"))]!!.reps)
    }

    @Test
    @DisplayName("Gapped positions after delete use ordinal distance, not raw position delta")
    fun `gapped positions use ordinal distance not raw position delta`() {
        // Positions 0 (logged), 2, 3 — middle slot deleted without renumbering.
        // Ordinal steps from pos0: set@2 is 1 step (index 2 → 13), set@3 is 2 steps (index 3 → 11).
        // Raw position delta would wrongly use index 3/4 → 11/9.
        val performed = LoggedSetValue(
            slotId = SetSlotId.Template(liftSetId),
            weight = lb(165.0),
            reps = 14,
            rir = 2,
        )
        fun set(setId: String, position: Int, logged: LoggedSetValue?) = RenderedSet(
            slotId = SetSlotId.Template(LiftSetId(setId)),
            workoutLiftId = workoutLiftId,
            liftId = liftId,
            position = position,
            setType = SetType.Standard,
            isTopSet = position == 0,
            repMin = 1,
            repMax = 30,
            rirGoal = if (position == 0) 2 else 1,
            restSeconds = 90,
            recommendedWeight = lb(165.0),
            recommendedReps = 8,
            loggedValue = logged,
        )
        val rendered = RenderedWorkout(
            workoutId = workoutId,
            workoutName = "Workout 1",
            lifts = listOf(
                RenderedWorkoutLift(
                    workoutLiftId = workoutLiftId,
                    liftId = liftId,
                    liftName = "Machine Leg Extension",
                    progressionScheme = ProgressionScheme.Double,
                    sets = listOf(
                        set("ls-1", 0, performed),
                        set("ls-3", 2, null),
                        set("ls-4", 3, null),
                    ),
                ),
            ),
        )
        val recommendations = useCase(rendered, mapOf(liftId to catalogLift()))
        assertEquals(2, recommendations.size)
        assertEquals(13, recommendations[SetSlotId.Template(LiftSetId("ls-3"))]!!.reps)
        assertEquals(11, recommendations[SetSlotId.Template(LiftSetId("ls-4"))]!!.reps)
    }

    @Test
    @DisplayName("per-lift slope steeper than population yields fewer projected reps on set 2")
    fun `per lift slope steeper than population reduces projected reps`() {
        // Same setup as `75x8 at 2 RIR holds weight and projects 7 reps` (population slope -2.0
        // → raw set2 = 8 @ 0 RIR → 6 @ 2 RIR). With a steeper -3.0 slope, raw set2 = 7 @ 0 RIR
        // → 5 @ 2 RIR. FatigueDecay passed via the new fatigueByLiftId param.
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
        assertEquals(5, rec!!.reps, "Steeper slope (-3.0) should project fewer reps than population (-2.0) → 6")
    }

    @Test
    @DisplayName("per-lift intercept shifts projected reps by a flat offset")
    fun `per lift intercept shifts projected reps`() {
        // Baseline (population slope -2.0, intercept 0.0): 75x8@2 → raw set2 = 8 @ 0 RIR → 6 @ 2 RIR.
        // With intercept -1.0 (one fewer rep across the board): raw set2 = 7 @ 0 RIR → 5 @ 2 RIR.
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
        assertEquals(5, rec!!.reps, "Intercept -1.0 should shift projected reps down by 1 vs population (6 → 5)")
    }

    @Test
    @DisplayName("shallower per-lift slope yields more projected reps than population")
    fun `shallower slope yields more projected reps`() {
        // Population slope -2.0 → 6 reps (see `75x8 at 2 RIR holds weight and projects 7 reps`).
        // Shallower -1.0 slope: raw set2 = 9 @ 0 RIR → 7 @ 2 RIR.
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
        assertEquals(7, rec!!.reps, "Shallower slope (-1.0) should project more reps than population (-2.0) → 6")
    }

    @Test
    @DisplayName("lift absent from fatigueByLiftId falls back to population slope")
    fun `lift absent from fatigue map falls back to population`() {
        // Same setup as the 6-rep population case, but fatigueByLiftId is empty (lift not in map).
        // Must match the population-slope result (6 reps), not error or produce a different value.
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
        assertEquals(6, rec!!.reps, "Absent lift must fall back to population slope (-2.0) → 6 reps")
    }

    @Test
    @DisplayName("null RIR on performed set is treated as 0 (no free capacity)")
    fun `null rir treated as zero capacity`() {
        // 75×8@null → effective RIR 0 → e1RM from 75×8. At 2 RIR goal, projected set2 is lower
        // than 75×8@2. Population: capacity 8, set2 raw 6 @0 → 4 @2 RIR.
        val performed = LoggedSetValue(
            slotId = SetSlotId.Template(liftSetId),
            weight = lb(75.0),
            reps = 8,
            rir = null,
        )
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
            recommendedWeight = lb(75.0),
            recommendedReps = 4,
            loggedValue = performed,
        )
        val secondSet = firstSet.copy(
            slotId = SetSlotId.Template(backoffSetId),
            position = 1,
            loggedValue = null,
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
        val rec = useCase(rendered, mapOf(liftId to catalogLift()))[SetSlotId.Template(backoffSetId)]
        assertTrue(rec != null)
        // Null RIR is treated as 0 capacity-wise, but recommended reps still respect the
        // global floor of BACKOFF_REP_FLOOR (5).
        assertTrue(rec!!.reps >= 5)
    }

    @Test
    @DisplayName("zero-weight performed set produces no live recommendation")
    fun `zero weight performed produces no recommendation`() {
        val rendered = twoTopSets(
            performedWeight = 0.0,
            performedReps = 10,
            performedRir = 2,
            repMin = 8,
            repMax = 12,
            rirGoal = 2,
        )
        val recommendations = useCase(rendered, mapOf(liftId to catalogLift()))
        assertTrue(recommendations.isEmpty())
    }

    @Test
    @DisplayName("multi-lift: logging lift A does not produce recs for lift B")
    fun `multi lift isolation`() {
        val liftB = LiftId("L-2")
        val wlB = WorkoutLiftId("wl-2")
        val setB1 = LiftSetId("ls-b1")
        val setB2 = LiftSetId("ls-b2")
        val performedA = LoggedSetValue(
            slotId = SetSlotId.Template(liftSetId),
            weight = lb(75.0),
            reps = 8,
            rir = 2,
        )
        fun set(
            wl: WorkoutLiftId,
            lid: LiftId,
            setId: LiftSetId,
            position: Int,
            logged: LoggedSetValue?,
        ) = RenderedSet(
            slotId = SetSlotId.Template(setId),
            workoutLiftId = wl,
            liftId = lid,
            position = position,
            setType = SetType.Standard,
            isTopSet = position == 0,
            repMin = 4,
            repMax = 12,
            rirGoal = 2,
            restSeconds = 120,
            recommendedWeight = lb(75.0),
            recommendedReps = 4,
            loggedValue = logged,
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
                    sets = listOf(
                        set(workoutLiftId, liftId, liftSetId, 0, performedA),
                        set(workoutLiftId, liftId, backoffSetId, 1, null),
                    ),
                ),
                RenderedWorkoutLift(
                    workoutLiftId = wlB,
                    liftId = liftB,
                    liftName = "Bench",
                    progressionScheme = ProgressionScheme.Double,
                    sets = listOf(
                        set(wlB, liftB, setB1, 0, null),
                        set(wlB, liftB, setB2, 1, null),
                    ),
                ),
            ),
        )
        val recommendations = useCase(
            rendered,
            mapOf(
                liftId to catalogLift(),
                liftB to catalogLift().copy(id = liftB, name = "Bench"),
            ),
        )
        assertTrue(recommendations.containsKey(SetSlotId.Template(backoffSetId)))
        assertNull(recommendations[SetSlotId.Template(setB1)])
        assertNull(recommendations[SetSlotId.Template(setB2)])
    }

    @Test
    @DisplayName("reduce with multiple remaining sets targets the last set (extra fatigue)")
    fun `reduce with multiple remaining targets last set`() {
        // 100×3@1 on 5–8@1 top sets: too hard / projected short → reduce.
        // remainingCount=2 → fatigueForLastSet = 2 → heavier reduction than single remaining.
        val performed = LoggedSetValue(
            slotId = SetSlotId.Template(liftSetId),
            weight = lb(100.0),
            reps = 3,
            rir = 1,
        )
        fun top(setId: String, position: Int, logged: LoggedSetValue?) = RenderedSet(
            slotId = SetSlotId.Template(LiftSetId(setId)),
            workoutLiftId = workoutLiftId,
            liftId = liftId,
            position = position,
            setType = SetType.Standard,
            isTopSet = true,
            repMin = 5,
            repMax = 8,
            rirGoal = 1,
            restSeconds = 120,
            recommendedWeight = lb(100.0),
            recommendedReps = 5,
            loggedValue = logged,
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
                    sets = listOf(
                        top("ls-1", 0, performed),
                        top("ls-2", 1, null),
                        top("ls-3", 2, null),
                    ),
                ),
            ),
        )
        val recommendations = useCase(rendered, mapOf(liftId to catalogLift(tiers = defaultTiers)))
        assertEquals(2, recommendations.size)
        // Both remaining share one weight (last-set targeting). With remainingCount=2:
        // e1RM from 100×4, weightForReps(..., 5+1+2=8) rounds in 40–100 band to a value < 95
        // (single-remaining case was 95).
        val w2 = recommendations[SetSlotId.Template(LiftSetId("ls-2"))]!!.weight.amount
        val w3 = recommendations[SetSlotId.Template(LiftSetId("ls-3"))]!!.weight.amount
        assertEquals(w2, w3, 0.01, "shared weight across remaining sets")
        assertTrue(w2 < 95.0, "multi-remaining reduce must be lighter than single-remaining 95; got $w2")
    }

    @Test
    @DisplayName("reversed mid-session difficulty re-anchors only on last performed set")
    fun `reversed mid session difficulty reanchors on last set only`() {
        // Set 1 weak (6@0), set 2 stronger (10@2) — RIR misreport or sandbag on set 1.
        // Upcoming set 3 must use set 2's capacity (12), not set 1's (6).
        val set1 = LoggedSetValue(SetSlotId.Template(liftSetId), lb(100.0), 6, 0)
        val set2 = LoggedSetValue(SetSlotId.Template(LiftSetId("ls-2")), lb(100.0), 10, 2)
        fun s(id: String, pos: Int, logged: LoggedSetValue?) = RenderedSet(
            slotId = SetSlotId.Template(LiftSetId(id)),
            workoutLiftId = workoutLiftId,
            liftId = liftId,
            position = pos,
            setType = SetType.Standard,
            isTopSet = pos == 0,
            repMin = 1,
            repMax = 20,
            rirGoal = 2,
            restSeconds = 90,
            recommendedWeight = lb(100.0),
            recommendedReps = 5,
            loggedValue = logged,
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
                    sets = listOf(
                        s("ls-1", 0, set1),
                        s("ls-2", 1, set2),
                        s("ls-3", 2, null),
                    ),
                ),
            ),
        )
        val rec = useCase(rendered, mapOf(liftId to catalogLift()))[SetSlotId.Template(LiftSetId("ls-3"))]
        assertTrue(rec != null)
        // Anchor capacity 12; set index 2 → raw 10 @0 → 8 @2 RIR.
        assertEquals(8, rec!!.reps)
        // If wrongly anchored on set1 capacity 6 → set index would still be from set2 position
        // but capacity 6 → much lower. 8 proves last-set anchor.
        assertTrue(rec.reps >= 6)
    }
}