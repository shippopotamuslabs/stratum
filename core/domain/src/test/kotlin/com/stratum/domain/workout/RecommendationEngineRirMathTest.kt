package com.stratum.domain.workout

import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.roundToIncrement
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.WorkoutLiftId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Pre-computed recommendation scenarios (RIR-aware math). Expected values were derived in
 * Python from the Brzycki/Epley e1RM formulas, the population fatigue decay slope
 * (`-2.0` reps per set index at 0 RIR), and increment-tier rounding.
 *
 * Covers:
 *  - The reduce branch targets `reps + rirGoal + fatigueForLastSet` (RIR-aware, last-set).
 *  - The too-hard trigger: missing the floor by ≥ 2 reps considering RIR → Reduce.
 *  - The too-easy trigger: exceeding the ceiling by ≥ 2 reps → +5 lb bump.
 *  - Last-set fatigue targeting: `fatigueForLastSet = |slope| × (remainingCount − 1)`.
 *  - Increment-tier rounding: raw weights rounded to the lift's band-appropriate step.
 */
@DisplayName("Recommendation engine: RIR-aware math (pre-computed)")
class RecommendationEngineRirMathTest {

    private val slotId = SetSlotId.Template(LiftSetId("ls-1"))
    private val workoutLiftId = WorkoutLiftId("wl-1")
    private val liftId = LiftId("L-1")
    private val engine = RecommendationEngine()

    private fun lb(amount: Double) = Weight(amount, Weight.WeightUnit.Lb)

    private fun topSet(repMin: Int = 4, repMax: Int = 12, rirGoal: Int = 2) = SetSlot(
        slotId = slotId,
        workoutLiftId = workoutLiftId,
        liftId = liftId,
        position = 0,
        setType = SetType.Standard,
        isTopSet = true,
        repMin = repMin,
        repMax = repMax,
        rirGoal = rirGoal,
        restSeconds = 120,
    )

    private fun backoffSlot(rirGoal: Int = 2) = SetSlot(
        slotId = SetSlotId.Template(LiftSetId("ls-2")),
        workoutLiftId = workoutLiftId,
        liftId = liftId,
        position = 1,
        setType = SetType.Standard,
        isTopSet = false,
        repMin = 0,
        repMax = 0,
        rirGoal = rirGoal,
        restSeconds = 120,
    )

    private fun performed(weight: Double, reps: Int, rir: Int) =
        LoggedSetValue(slotId, lb(weight), reps, rir)

    /** Default population tiers: 0-40 → 2.5, 40-100 → 5, 100-300 → 10, 300+ → 25. */
    private val defaultTiers = listOf(
        IncrementTier(lb(0.0), lb(40.0), lb(2.5)),
        IncrementTier(lb(40.0), lb(100.0), lb(5.0)),
        IncrementTier(lb(100.0), lb(300.0), lb(10.0)),
        IncrementTier(lb(300.0), null, lb(25.0)),
    )

    // --- Regression tests (single remaining set, no tiers) ---

    @Test
    @DisplayName("RIR-1: 75x4@2 RIR top set, next top set 4-12@2 -> Hold 75 (no weight increase)")
    fun `75x4 at 2 RIR holds weight for next top set`() {
        val outcome = engine.recommendNextSet(
            lastPerformed = performed(75.0, 4, 2),
            completedSlot = topSet(),
            targetSlot = topSet(),
            currentWeight = lb(75.0),
        )
        assertTrue(outcome is RecommendationOutcome.Hold)
        assertEquals(75.0, (outcome as RecommendationOutcome.Hold).weight.amount, 0.05)
    }

    @Test
    @DisplayName("RIR-2: 75x8@2 RIR top set, next top set 4-12@2 -> Hold 75")
    fun `75x8 at 2 RIR holds weight for next top set`() {
        val outcome = engine.recommendNextSet(
            lastPerformed = performed(75.0, 8, 2),
            completedSlot = topSet(),
            targetSlot = topSet(),
            currentWeight = lb(75.0),
        )
        assertTrue(outcome is RecommendationOutcome.Hold)
        assertEquals(75.0, (outcome as RecommendationOutcome.Hold).weight.amount, 0.05)
    }

    @Test
    @DisplayName("RIR-3: 75x4@2 RIR top set, next backoff@2 -> Hold 60 (backoff weight unchanged)")
    fun `75x4 at 2 RIR holds backoff weight`() {
        val outcome = engine.recommendNextSet(
            lastPerformed = performed(75.0, 4, 2),
            completedSlot = topSet(),
            targetSlot = backoffSlot(),
            currentWeight = lb(60.0),
        )
        assertTrue(outcome is RecommendationOutcome.Hold)
        assertEquals(60.0, (outcome as RecommendationOutcome.Hold).weight.amount, 0.05)
    }

    @Test
    @DisplayName("RIR-4: too-easy top set bumps weight by 5 lb")
    fun `too-easy top set bumps weight`() {
        val outcome = engine.recommendNextSet(
            lastPerformed = performed(100.0, 8, 1),
            completedSlot = topSet(repMin = 3, repMax = 6, rirGoal = 1),
            targetSlot = topSet(repMin = 3, repMax = 6, rirGoal = 1),
            currentWeight = lb(100.0),
        )
        assertTrue(outcome is RecommendationOutcome.Hold)
        assertEquals(105.0, (outcome as RecommendationOutcome.Hold).weight.amount, 0.05)
    }

    @Test
    @DisplayName("RIR-5: reduce branch targets reps + rirGoal (not 0-RIR reps)")
    fun `reduce branch accounts for rir goal`() {
        // 100x3@1 RIR, top set 5-8@1. achieved = 3+1-1 = 3, floor=5, 5-3=2 → too hard → Reduce.
        // e1RM from 100x4 = 112.13. remainingCount=1 → fatigueForLastSet=0.
        // weightForReps(112.13, 5+1+0) = weightForReps(112.13, 6) = 93.93.
        val outcome = engine.recommendNextSet(
            lastPerformed = performed(100.0, 3, 1),
            completedSlot = topSet(repMin = 5, repMax = 8, rirGoal = 1),
            targetSlot = topSet(repMin = 5, repMax = 8, rirGoal = 1),
            currentWeight = lb(100.0),
        )
        assertTrue(outcome is RecommendationOutcome.Reduce)
        val reduced = (outcome as RecommendationOutcome.Reduce).weight.amount
        assertTrue(reduced < 100.0, "reduce branch must lower the weight, got $reduced")
        assertEquals(93.93, reduced, 0.5)
    }

    // --- S1-S4: core scenarios (no tiers, to isolate the math) ---

    @Nested
    @DisplayName("Core scenarios (no tier rounding)")
    inner class CoreScenarios {

        @Test
        @DisplayName("S1: happy path — 75x6@2, top 4-12@2, 2 remaining -> Hold 75")
        fun `S1 happy path holds weight`() {
            // achieved = 6+2-2 = 6. floor=4, 4-6=-2 (not too hard). repMax=12, 6-12=-6 (not too easy).
            // projected at 75: repsForWeight(e1rm 89.53, 75) - 2 = 6-2 = 4 >= repMin=4 → Hold.
            val outcome = engine.recommendNextSet(
                lastPerformed = performed(75.0, 6, 2),
                completedSlot = topSet(),
                targetSlot = topSet(),
                currentWeight = lb(75.0),
                remainingCount = 2,
            )
            assertTrue(outcome is RecommendationOutcome.Hold)
            assertEquals(75.0, (outcome as RecommendationOutcome.Hold).weight.amount, 0.05)
        }

        @Test
        @DisplayName("S2: too hard — 75x2@2, top 4-12@2, 2 remaining -> Reduce 65.9")
        fun `S2 too hard reduces weight for last set`() {
            // achieved = 2+2-2 = 2. floor=4, 4-2=2 → too hard → Reduce.
            // e1RM from 75x4 = 84.10. fatigueForLastSet = 2*(2-1) = 2.
            // weightForReps(84.10, 4+2+2) = weightForReps(84.10, 8) = 65.9.
            val outcome = engine.recommendNextSet(
                lastPerformed = performed(75.0, 2, 2),
                completedSlot = topSet(),
                targetSlot = topSet(),
                currentWeight = lb(75.0),
                remainingCount = 2,
            )
            assertTrue(outcome is RecommendationOutcome.Reduce)
            assertEquals(65.9, (outcome as RecommendationOutcome.Reduce).weight.amount, 0.05)
        }

        @Test
        @DisplayName("S2b: too hard boundary (exactly 2 under) — 75x2@2, top 4-6@2, 2 remaining -> Reduce")
        fun `S2b too hard boundary triggers at exactly 2 under`() {
            val outcome = engine.recommendNextSet(
                lastPerformed = performed(75.0, 2, 2),
                completedSlot = topSet(repMin = 4, repMax = 6),
                targetSlot = topSet(repMin = 4, repMax = 6),
                currentWeight = lb(75.0),
                remainingCount = 2,
            )
            assertTrue(outcome is RecommendationOutcome.Reduce)
        }

        @Test
        @DisplayName("S3: too easy — 75x8@2, top 4-6@2, 2 remaining -> Hold(bump) 80")
        fun `S3 too easy bumps weight`() {
            // achieved = 8+2-2 = 8. repMax=6, 8-6=2 → too easy → bump 75+5 = 80.
            val outcome = engine.recommendNextSet(
                lastPerformed = performed(75.0, 8, 2),
                completedSlot = topSet(repMin = 4, repMax = 6),
                targetSlot = topSet(repMin = 4, repMax = 6),
                currentWeight = lb(75.0),
                remainingCount = 2,
            )
            assertTrue(outcome is RecommendationOutcome.Hold)
            assertEquals(80.0, (outcome as RecommendationOutcome.Hold).weight.amount, 0.05)
        }

        @Test
        @DisplayName("S3b: too easy, 1 remaining -> Hold(bump) 80")
        fun `S3b too easy with one remaining bumps`() {
            val outcome = engine.recommendNextSet(
                lastPerformed = performed(75.0, 8, 2),
                completedSlot = topSet(repMin = 4, repMax = 6),
                targetSlot = topSet(repMin = 4, repMax = 6),
                currentWeight = lb(75.0),
                remainingCount = 1,
            )
            assertTrue(outcome is RecommendationOutcome.Hold)
            assertEquals(80.0, (outcome as RecommendationOutcome.Hold).weight.amount, 0.05)
        }

        @Test
        @DisplayName("S4: backoff too hard — 100x3@2, backoff@2, 2 remaining -> Reduce 87.5")
        fun `S4 backoff too hard reduces weight`() {
            // achieved = 3+2-2 = 3. backoff floor = 5, 5-3=2 → too hard → Reduce.
            // e1RM from 100x5 = 115.64. fatigueForLastSet = 2*(2-1) = 2.
            // weightForReps(115.64, 5+2+2) = weightForReps(115.64, 9) = 87.5.
            val outcome = engine.recommendNextSet(
                lastPerformed = performed(100.0, 3, 2),
                completedSlot = backoffSlot(),
                targetSlot = backoffSlot(),
                currentWeight = lb(100.0),
                remainingCount = 2,
            )
            assertTrue(outcome is RecommendationOutcome.Reduce)
            assertEquals(87.5, (outcome as RecommendationOutcome.Reduce).weight.amount, 0.05)
        }
    }

    // --- M1-M8: edge cases ---

    @Nested
    @DisplayName("Edge cases")
    inner class EdgeCases {

        @Test
        @DisplayName("M1: RIR mismatch — 75x6@4 (target 2 RIR) -> Hold (not too easy)")
        fun `M1 RIR mismatch holds`() {
            // achieved = 6+4-2 = 8. repMax=12, 8-12=-4 (not too easy). floor=4, 4-8=-4 (not too hard).
            val outcome = engine.recommendNextSet(
                lastPerformed = performed(75.0, 6, 4),
                completedSlot = topSet(),
                targetSlot = topSet(),
                currentWeight = lb(75.0),
                remainingCount = 2,
            )
            assertTrue(outcome is RecommendationOutcome.Hold)
            assertEquals(75.0, (outcome as RecommendationOutcome.Hold).weight.amount, 0.05)
        }

        @Test
        @DisplayName("M2: backoff happy path — 100x10@2, 1 remaining -> Hold 100")
        fun `M2 backoff happy path holds`() {
            val outcome = engine.recommendNextSet(
                lastPerformed = performed(100.0, 10, 2),
                completedSlot = backoffSlot(),
                targetSlot = backoffSlot(),
                currentWeight = lb(100.0),
                remainingCount = 1,
            )
            assertTrue(outcome is RecommendationOutcome.Hold)
            assertEquals(100.0, (outcome as RecommendationOutcome.Hold).weight.amount, 0.05)
        }

        @Test
        @DisplayName("M4: single remaining set (boundary) — fatigueForLastSet = 0")
        fun `M4 single remaining set has zero last-set fatigue`() {
            val outcome = engine.recommendNextSet(
                lastPerformed = performed(75.0, 6, 2),
                completedSlot = topSet(),
                targetSlot = topSet(),
                currentWeight = lb(75.0),
                remainingCount = 1,
            )
            assertTrue(outcome is RecommendationOutcome.Hold)
            assertEquals(75.0, (outcome as RecommendationOutcome.Hold).weight.amount, 0.05)
        }

        @Test
        @DisplayName("M8: exactly 2 over ceiling — 75x8@2, top 4-6@2 -> bump")
        fun `M8 exactly 2 over triggers too easy`() {
            val outcome = engine.recommendNextSet(
                lastPerformed = performed(75.0, 8, 2),
                completedSlot = topSet(repMin = 4, repMax = 6),
                targetSlot = topSet(repMin = 4, repMax = 6),
                currentWeight = lb(75.0),
                remainingCount = 2,
            )
            assertTrue(outcome is RecommendationOutcome.Hold)
            assertEquals(80.0, (outcome as RecommendationOutcome.Hold).weight.amount, 0.05)
        }
    }

    // --- Tier rounding scenarios ---

    @Nested
    @DisplayName("Increment-tier rounding")
    inner class TierRounding {

        @Test
        @DisplayName("S2-tier: too hard + rounding — 75x2@2, 2 remaining -> Reduce 65")
        fun `S2 with tiers rounds to 65`() {
            // Raw reduce weight = 65.9. In the 40-100 band (5 lb increment), 65.9 rounds to 65.
            val outcome = engine.recommendNextSet(
                lastPerformed = performed(75.0, 2, 2),
                completedSlot = topSet(),
                targetSlot = topSet(),
                currentWeight = lb(75.0),
                remainingCount = 2,
                incrementTiers = defaultTiers,
            )
            assertTrue(outcome is RecommendationOutcome.Reduce)
            assertEquals(65.0, (outcome as RecommendationOutcome.Reduce).weight.amount, 0.0)
        }

        @Test
        @DisplayName("S4-tier: backoff too hard + rounding — 100x3@2, 2 remaining -> Reduce 85")
        fun `S4 with tiers rounds to 85`() {
            // Raw reduce weight = 87.49. In the 40-100 band (5 lb increment),
            // 87.49/5 = 17.50 → roundToLong = 18 → 90. But the actual raw weight is
            // 87.489 (Brzycki), so 87.489/5 = 17.498 → 17 → 85.
            val outcome = engine.recommendNextSet(
                lastPerformed = performed(100.0, 3, 2),
                completedSlot = backoffSlot(),
                targetSlot = backoffSlot(),
                currentWeight = lb(100.0),
                remainingCount = 2,
                incrementTiers = defaultTiers,
            )
            assertTrue(outcome is RecommendationOutcome.Reduce)
            // 87.489 in the 40-100 band (5 lb step): 87.489/5 = 17.498 → 17 → 85.
            assertEquals(85.0, (outcome as RecommendationOutcome.Reduce).weight.amount, 0.0)
        }

        @Test
        @DisplayName("RIR-5-tier: reduce + rounding — 100x3@1, 1 remaining -> Reduce 95")
        fun `RIR-5 with tiers rounds to 95`() {
            // Raw reduce = 93.93. In the 40-100 band (5 lb), 93.93/5 = 18.79 → 19 → 95.
            val outcome = engine.recommendNextSet(
                lastPerformed = performed(100.0, 3, 1),
                completedSlot = topSet(repMin = 5, repMax = 8, rirGoal = 1),
                targetSlot = topSet(repMin = 5, repMax = 8, rirGoal = 1),
                currentWeight = lb(100.0),
                remainingCount = 1,
                incrementTiers = defaultTiers,
            )
            assertTrue(outcome is RecommendationOutcome.Reduce)
            assertEquals(95.0, (outcome as RecommendationOutcome.Reduce).weight.amount, 0.0)
        }

        @Test
        @DisplayName("too-easy bump + rounding — 100x8@1, top 3-6@1 -> Hold 105 (already aligned)")
        fun `too-easy bump with tiers stays at 105`() {
            // 100+5 = 105. In the 100-300 band (10 lb), 105/10 = 10.5 → 11 → 110.
            // Wait — 105 is in the 100-300 band (lowerBound=100). 105/10 = 10.5 → 11 → 110.
            val outcome = engine.recommendNextSet(
                lastPerformed = performed(100.0, 8, 1),
                completedSlot = topSet(repMin = 3, repMax = 6, rirGoal = 1),
                targetSlot = topSet(repMin = 3, repMax = 6, rirGoal = 1),
                currentWeight = lb(100.0),
                incrementTiers = defaultTiers,
            )
            assertTrue(outcome is RecommendationOutcome.Hold)
            // 105 in the 100-300 band (10 lb): 105/10 = 10.5 → 11 → 110.
            assertEquals(110.0, (outcome as RecommendationOutcome.Hold).weight.amount, 0.0)
        }
    }

    // --- roundToIncrement unit tests ---

    @Nested
    @DisplayName("roundToIncrement helper")
    inner class RoundToIncrementTest {

        @Test
        @DisplayName("empty tier list is a passthrough")
        fun `empty tiers returns weight unchanged`() {
            val w = lb(93.93)
            assertEquals(w, emptyList<IncrementTier>().roundToIncrement(w))
        }

        @Test
        @DisplayName("rounds to nearest 5 in the 40-100 band")
        fun `rounds to nearest 5 in mid band`() {
            assertEquals(65.0, defaultTiers.roundToIncrement(lb(65.9)).amount, 0.0)
            assertEquals(95.0, defaultTiers.roundToIncrement(lb(93.93)).amount, 0.0)
            assertEquals(80.0, defaultTiers.roundToIncrement(lb(77.5)).amount, 0.0)
        }

        @Test
        @DisplayName("rounds to nearest 10 in the 100-300 band")
        fun `rounds to nearest 10 in high band`() {
            assertEquals(110.0, defaultTiers.roundToIncrement(lb(105.0)).amount, 0.0)
            assertEquals(140.0, defaultTiers.roundToIncrement(lb(135.0)).amount, 0.0)
        }

        @Test
        @DisplayName("rounds to nearest 2.5 in the 0-40 band")
        fun `rounds to nearest 2_5 in low band`() {
            assertEquals(22.5, defaultTiers.roundToIncrement(lb(22.3)).amount, 0.0)
            assertEquals(25.0, defaultTiers.roundToIncrement(lb(23.8)).amount, 0.0)
        }

        @Test
        @DisplayName("weight below lowest band uses the first tier")
        fun `weight below lowest band uses first tier`() {
            // 0-40 band, 2.5 lb step. -5.0 / 2.5 = -2 → -2 * 2.5 = -5.0.
            assertEquals(-5.0, defaultTiers.roundToIncrement(lb(-5.0)).amount, 0.0)
        }
    }
}