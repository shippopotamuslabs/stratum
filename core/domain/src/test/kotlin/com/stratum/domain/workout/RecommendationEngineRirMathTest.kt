package com.stratum.domain.workout

import com.stratum.domain.workout.fatigue.PopulationFatigueModel
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.roundToIncrement
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.lift.LoadType
import com.stratum.domain.training.EstimatedOneRm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * Pre-computed recommendation scenarios (RIR-aware math). Expected values use
 * [PopulationFatigueModel] set indexing (performed = 1, last of N remaining = N+1),
 * Brzycki/Epley e1RM, and reduce targeting last-set ≥ [BACKOFF_REP_FLOOR] at slot RIR.
 *
 * Covers:
 *  - On-track top set → null (snapshot stays put)
 *  - Lighter/heavier re-anchor
 *  - Hold vs Reduce via FatigueModel last-set capacity ≥ floor 5
 *  - Reduce aims at floor 5 (not top-set repMin)
 *  - Too-hard / too-easy triggers
 *  - Increment-tier rounding
 *  - Parameterized oracle: any Reduce/Hold load clears last-set floor
 */
@DisplayName("Recommendation engine: RIR-aware math (pre-computed)")
class RecommendationEngineRirMathTest {

    private val slotId = SetSlotId.Template(LiftSetId("ls-1"))
    private val workoutLiftId = WorkoutLiftId("wl-1")
    private val liftId = LiftId("L-1")
    private val engine = RecommendationEngine(PopulationFatigueModel())

    private fun lb(amount: Double) = Weight(amount, Weight.WeightUnit.Lb)

    companion object {
        /** Population-style tiers shared by oracle suite (same lattice as production defaults). */
        @JvmStatic
        fun populationTiers(): List<IncrementTier> {
            fun w(a: Double) = Weight(a, Weight.WeightUnit.Lb)
            return listOf(
                IncrementTier(w(0.0), w(40.0), w(2.5)),
                IncrementTier(w(40.0), w(100.0), w(5.0)),
                IncrementTier(w(100.0), w(300.0), w(10.0)),
                IncrementTier(w(300.0), null, w(25.0)),
            )
        }

        /**
         * Cartesian-ish weak-performance grid for oracle floor checks.
         * Columns: weight, reps, rir, remainingCount, targetRirGoal, perLiftSlope (null=population),
         * useTiers (true=population tiers, false=empty continuous).
         */
        @JvmStatic
        fun oracleReduceCases(): Stream<Arguments> {
            val weights = listOf(45.0, 75.0, 100.0, 135.0, 185.0, 225.0, 315.0)
            val weakReps = listOf(1, 2, 3, 4, 5)
            val rirs = listOf(0, 1, 2)
            val remaining = listOf(1, 2, 3, 4)
            val targetRirs = listOf(1, 2)
            val slopes = listOf(null, -1.0, -2.0, -3.0)
            val tierFlags = listOf(false, true)

            val args = mutableListOf<Arguments>()
            for (w in weights) {
                for (reps in weakReps) {
                    for (rir in rirs) {
                        for (rem in remaining) {
                            for (tRir in targetRirs) {
                                for (slope in slopes) {
                                    for (useTiers in tierFlags) {
                                        // Skip absurd free-capacity cases that always Hold at full load
                                        // without exercising reduce (still valid; floor check holds).
                                        args += Arguments.of(w, reps, rir, rem, tRir, slope, useTiers)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return args.stream()
        }

        /**
         * Smaller backoff grid: same weight as prescription (no re-anchor), weak sets force
         * Reduce; asserts last-set floor under backoff RIR targets.
         */
        @JvmStatic
        fun oracleBackoffCases(): Stream<Arguments> {
            val weights = listOf(60.0, 100.0, 140.0, 200.0)
            val weakReps = listOf(2, 3, 4, 5)
            val rirs = listOf(0, 1, 2)
            val remaining = listOf(1, 2, 3)
            val targetRirs = listOf(1, 2)
            val tierFlags = listOf(false, true)
            val args = mutableListOf<Arguments>()
            for (w in weights) {
                for (reps in weakReps) {
                    for (rir in rirs) {
                        for (rem in remaining) {
                            for (tRir in targetRirs) {
                                for (useTiers in tierFlags) {
                                    args += Arguments.of(w, reps, rir, rem, tRir, useTiers)
                                }
                            }
                        }
                    }
                }
            }
            return args.stream()
        }
    }

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
    @DisplayName("RIR-1: 75x2@2 top set (missed range) -> Reduce for last-set floor of 5")
    fun `75x2 at 2 RIR reduces weight for global rep floor`() {
        // achieved=2 outside 4-12 → not on-track; too-hard / capacity short → Reduce.
        val outcome = engine.recommendNextSet(
            lastPerformed = performed(75.0, 2, 2),
            completedSlot = topSet(),
            targetSlot = topSet(),
            currentWeight = lb(75.0),
            completedSetPrescribedWeight = lb(75.0),
        )
        assertTrue(outcome is RecommendationOutcome.Reduce)
        assertTrue((outcome as RecommendationOutcome.Reduce).weight.amount < 75.0)
    }

    @Test
    @DisplayName("On-track top set at goal weight in range -> null (snapshot stays put)")
    fun `on track top set returns null so snapshot recs stay put`() {
        val outcome = engine.recommendNextSet(
            lastPerformed = performed(75.0, 8, 2),
            completedSlot = topSet(),
            targetSlot = topSet(),
            currentWeight = lb(75.0),
            remainingCount = 3,
            completedSetPrescribedWeight = lb(75.0),
        )
        assertNull(outcome)
    }

    @Test
    @DisplayName("RIR-2: 75x8@2 without prescribed baseline -> Hold 75 (legacy callers)")
    fun `75x8 at 2 RIR holds weight for next top set when no baseline`() {
        // No completedSetPrescribedWeight → on-track short-circuit does not fire.
        val outcome = engine.recommendNextSet(
            lastPerformed = performed(75.0, 8, 2),
            completedSlot = topSet(),
            targetSlot = topSet(),
            currentWeight = lb(75.0),
            remainingCount = 1,
        )
        assertTrue(outcome is RecommendationOutcome.Hold)
        assertEquals(75.0, (outcome as RecommendationOutcome.Hold).weight.amount, 0.05)
    }

    @Test
    @DisplayName("RIR-3: on-track top set -> null (snapshot keeps intentional backoff)")
    fun `75x4 at 2 RIR on track leaves backoff to snapshot`() {
        // In-range at goal weight → no live overlay; merge keeps snapshot backoff load.
        val outcome = engine.recommendNextSet(
            lastPerformed = performed(75.0, 4, 2),
            completedSlot = topSet(),
            targetSlot = backoffSlot(),
            currentWeight = lb(60.0),
            completedSetPrescribedWeight = lb(75.0),
        )
        assertNull(outcome)
    }

    @Test
    @DisplayName("Re-anchor: performed heavier than completed prescription holds at performed weight")
    fun `heavier than prescription reanchors remaining sets to performed weight`() {
        // Logged 400 on a set prescribed 200 with enough headroom that last-set capacity ≥ 5.
        val outcome = engine.recommendNextSet(
            lastPerformed = performed(400.0, 12, 2),
            completedSlot = topSet(repMin = 4, repMax = 15, rirGoal = 2),
            targetSlot = topSet(repMin = 4, repMax = 15, rirGoal = 2),
            currentWeight = lb(200.0),
            remainingCount = 1,
            completedSetPrescribedWeight = lb(200.0),
        )
        assertTrue(outcome is RecommendationOutcome.Hold)
        assertEquals(400.0, (outcome as RecommendationOutcome.Hold).weight.amount, 0.05)
    }

    @Test
    @DisplayName("Re-anchor off: on-track top set does not emit live rec (snapshot keeps backoff)")
    fun `matched prescription on track leaves intentional backoff to snapshot`() {
        val outcome = engine.recommendNextSet(
            lastPerformed = performed(75.0, 6, 2),
            completedSlot = topSet(),
            targetSlot = backoffSlot(),
            currentWeight = lb(60.0),
            completedSetPrescribedWeight = lb(75.0),
        )
        assertNull(outcome)
    }

    @Test
    @DisplayName("resolveWorkingWeight still preserves intentional backoff when not on-track path")
    fun `resolveWorkingWeight keeps lighter target when performed matches baseline`() {
        assertEquals(
            60.0,
            engine.resolveWorkingWeight(lb(75.0), lb(60.0), lb(75.0)).amount,
            0.0,
        )
    }

    @Nested
    @DisplayName("resolveWorkingWeight")
    inner class ResolveWorkingWeight {

        @Test
        @DisplayName("blank target falls back to performed")
        fun blankTargetUsesPerformed() {
            assertEquals(
                400.0,
                engine.resolveWorkingWeight(lb(400.0), lb(0.0), lb(200.0)).amount,
                0.0,
            )
        }

        @Test
        @DisplayName("performed heavier than baseline and target re-anchors")
        fun reanchorsWhenHeavierThanBoth() {
            assertEquals(
                400.0,
                engine.resolveWorkingWeight(lb(400.0), lb(200.0), lb(200.0)).amount,
                0.0,
            )
        }

        @Test
        @DisplayName("matched baseline keeps intentional lighter target")
        fun keepsIntentionalBackoff() {
            assertEquals(
                60.0,
                engine.resolveWorkingWeight(lb(75.0), lb(60.0), lb(75.0)).amount,
                0.0,
            )
        }

        @Test
        @DisplayName("lighter than baseline re-anchors to performed (not heavier snapshot target)")
        fun reanchorsWhenLighterThanBaseline() {
            // Logged 120 on a set prescribed 165; next slot still says 165 (or 140).
            assertEquals(
                120.0,
                engine.resolveWorkingWeight(lb(120.0), lb(165.0), lb(165.0)).amount,
                0.0,
            )
            assertEquals(
                120.0,
                engine.resolveWorkingWeight(lb(120.0), lb(140.0), lb(165.0)).amount,
                0.0,
            )
        }

        @Test
        @DisplayName("lighter performed keeps deeper intentional backoff when target is lower")
        fun lighterPerformedKeepsDeeperBackoffTarget() {
            // Logged 120 (< baseline 165) but next slot is already prescribed at 100.
            assertEquals(
                100.0,
                engine.resolveWorkingWeight(lb(120.0), lb(100.0), lb(165.0)).amount,
                0.0,
            )
        }

        @Test
        @DisplayName("null completed prescribed keeps target (legacy callers)")
        fun nullCompletedKeepsTarget() {
            assertEquals(
                200.0,
                engine.resolveWorkingWeight(lb(400.0), lb(200.0), null).amount,
                0.0,
            )
        }
    }

    @Test
    @DisplayName("Lighter re-anchor: 120x11@1 after 165 baseline holds 120 for remaining sets")
    fun `lighter than prescription reanchors remaining sets to performed weight`() {
        // Screenshot repro: lifter dropped to 120×11@1 on a slot prescribed 165.
        // Old behavior clung to next-slot ~165, then partially reduced (~140) and clamped
        // reps to 5. Correct: hold at the load the lifter entered when last-set capacity
        // still clears BACKOFF_REP_FLOOR.
        val outcome = engine.recommendNextSet(
            lastPerformed = performed(120.0, 11, 1),
            completedSlot = backoffSlot(rirGoal = 1),
            targetSlot = backoffSlot(rirGoal = 1),
            currentWeight = lb(165.0),
            remainingCount = 2,
            completedSetPrescribedWeight = lb(165.0),
        )
        assertTrue(outcome is RecommendationOutcome.Hold)
        assertEquals(120.0, (outcome as RecommendationOutcome.Hold).weight.amount, 0.05)
    }

    @Test
    @DisplayName("Lighter re-anchor top set: 100x13@2 after 240 baseline holds 100 (not repMin 8)")
    fun `lighter top set reanchors remaining sets to performed weight`() {
        // Below repMin 8 on last set is OK if capacity still ≥ BACKOFF_REP_FLOOR.
        // 100×13@2 is outside 8–12 at goal weight 240 → not on-track; re-anchor Hold 100.
        val outcome = engine.recommendNextSet(
            lastPerformed = performed(100.0, 13, 2),
            completedSlot = topSet(repMin = 8, repMax = 12, rirGoal = 2),
            targetSlot = topSet(repMin = 8, repMax = 12, rirGoal = 2),
            currentWeight = lb(240.0),
            remainingCount = 4,
            completedSetPrescribedWeight = lb(240.0),
        )
        assertTrue(outcome is RecommendationOutcome.Hold)
        assertEquals(100.0, (outcome as RecommendationOutcome.Hold).weight.amount, 0.05)
    }

    @Test
    @DisplayName("Lighter re-anchor still reduces when last set cannot hit floor at performed load")
    fun `lighter reanchor reduces when last set below floor at performed weight`() {
        val outcome = engine.recommendNextSet(
            lastPerformed = performed(100.0, 5, 1),
            completedSlot = topSet(repMin = 5, repMax = 8, rirGoal = 1),
            targetSlot = topSet(repMin = 5, repMax = 8, rirGoal = 1),
            currentWeight = lb(165.0),
            remainingCount = 4,
            completedSetPrescribedWeight = lb(165.0),
        )
        assertTrue(outcome is RecommendationOutcome.Reduce)
        assertTrue(
            (outcome as RecommendationOutcome.Reduce).weight.amount < 100.0,
            "must drop below performed load when last set cannot hit floor, got ${outcome.weight.amount}",
        )
    }

    @Test
    @DisplayName("RIR-4: too-easy top set bumps weight by 5 lb")
    fun `too-easy top set bumps weight`() {
        // Outside repMax → not on-track; too-easy bump.
        val outcome = engine.recommendNextSet(
            lastPerformed = performed(100.0, 8, 1),
            completedSlot = topSet(repMin = 3, repMax = 6, rirGoal = 1),
            targetSlot = topSet(repMin = 3, repMax = 6, rirGoal = 1),
            currentWeight = lb(100.0),
            completedSetPrescribedWeight = lb(100.0),
        )
        assertTrue(outcome is RecommendationOutcome.Hold)
        assertEquals(105.0, (outcome as RecommendationOutcome.Hold).weight.amount, 0.05)
    }

    @Test
    @DisplayName("RIR-5: reduce uses FatigueModel oracle so last set capacity ≥ floor 5")
    fun `reduce branch accounts for rir goal and fatigue model index`() {
        // 100x3@1, rem=1 → lastSetIndex=2. Oracle binary-search ≈89.4 (not closed-form 87.87).
        val outcome = engine.recommendNextSet(
            lastPerformed = performed(100.0, 3, 1),
            completedSlot = topSet(repMin = 5, repMax = 8, rirGoal = 1),
            targetSlot = topSet(repMin = 5, repMax = 8, rirGoal = 1),
            currentWeight = lb(100.0),
            remainingCount = 1,
            completedSetPrescribedWeight = lb(100.0),
        )
        assertTrue(outcome is RecommendationOutcome.Reduce)
        val reduced = (outcome as RecommendationOutcome.Reduce).weight
        assertTrue(reduced.amount < 100.0, "reduce branch must lower the weight, got ${reduced.amount}")
        assertEquals(89.4, reduced.amount, 1.0)
        val e1rm = com.stratum.domain.training.EstimatedOneRm.calculate(100.0, 4)
        assertTrue(
            engine.capacityAtRir(e1rm, reduced, 2, rirGoal = 1, null, null) >= BACKOFF_REP_FLOOR,
            "reduced load must clear last-set floor",
        )
    }

    // --- S1-S4: core scenarios (no tiers, to isolate the math) ---

    @Nested
    @DisplayName("Core scenarios (no tier rounding)")
    inner class CoreScenarios {

        @Test
        @DisplayName("S1: happy path — 75x12@2, top 4-12@2, 2 remaining -> Hold 75")
        fun `S1 happy path holds weight`() {
            // Strong enough that FatigueModel last set (index 3) still ≥ floor 5 @ RIR 2.
            // No completedSetPrescribedWeight so on-track short-circuit does not fire.
            val outcome = engine.recommendNextSet(
                lastPerformed = performed(75.0, 12, 2),
                completedSlot = topSet(),
                targetSlot = topSet(),
                currentWeight = lb(75.0),
                remainingCount = 2,
            )
            assertTrue(outcome is RecommendationOutcome.Hold)
            assertEquals(75.0, (outcome as RecommendationOutcome.Hold).weight.amount, 0.05)
        }

        @Test
        @DisplayName("S1b: borderline multi-set — last set under floor reduces")
        fun `S1b last set under floor reduces weight`() {
            val outcome = engine.recommendNextSet(
                lastPerformed = performed(75.0, 6, 2),
                completedSlot = topSet(),
                targetSlot = topSet(),
                currentWeight = lb(75.0),
                remainingCount = 2,
            )
            assertTrue(outcome is RecommendationOutcome.Reduce)
            assertTrue((outcome as RecommendationOutcome.Reduce).weight.amount < 75.0)
        }

        @Test
        @DisplayName("S2: weak set — 75x2@2, top 4-12@2, 2 remaining -> Reduce for floor 5")
        fun `S2 too hard reduces weight for last set`() {
            // lastSetIndex=3; capacity oracle ≈60.2 continuous.
            val outcome = engine.recommendNextSet(
                lastPerformed = performed(75.0, 2, 2),
                completedSlot = topSet(),
                targetSlot = topSet(),
                currentWeight = lb(75.0),
                remainingCount = 2,
            )
            assertTrue(outcome is RecommendationOutcome.Reduce)
            val reduced = (outcome as RecommendationOutcome.Reduce).weight
            assertEquals(60.2, reduced.amount, 1.0)
            val e1rm = com.stratum.domain.training.EstimatedOneRm.calculate(75.0, 4)
            assertTrue(
                engine.capacityAtRir(e1rm, reduced, 3, 2, null, null) >= BACKOFF_REP_FLOOR,
            )
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
        @DisplayName("S3: too easy — 75x11@2, top 4-6@2, 2 remaining -> Hold(bump) 80")
        fun `S3 too easy bumps weight`() {
            // Strong enough that bumped 80 still clears last-set floor (index 3).
            val outcome = engine.recommendNextSet(
                lastPerformed = performed(75.0, 11, 2),
                completedSlot = topSet(repMin = 4, repMax = 6),
                targetSlot = topSet(repMin = 4, repMax = 6),
                currentWeight = lb(75.0),
                remainingCount = 2,
            )
            assertTrue(outcome is RecommendationOutcome.Hold)
            assertEquals(80.0, (outcome as RecommendationOutcome.Hold).weight.amount, 0.05)
        }

        @Test
        @DisplayName("S3b: too easy, 1 remaining -> Hold(bump) 80 when last set still ≥ floor")
        fun `S3b too easy with one remaining bumps`() {
            // 75×9@2: bump to 80 clears floor at lastSetIndex=2.
            val outcome = engine.recommendNextSet(
                lastPerformed = performed(75.0, 9, 2),
                completedSlot = topSet(repMin = 4, repMax = 6),
                targetSlot = topSet(repMin = 4, repMax = 6),
                currentWeight = lb(75.0),
                remainingCount = 1,
            )
            assertTrue(outcome is RecommendationOutcome.Hold)
            assertEquals(80.0, (outcome as RecommendationOutcome.Hold).weight.amount, 0.05)
        }

        @Test
        @DisplayName("S3c: too easy bump that fails last-set floor stays at current")
        fun `S3c too easy bump stays at current when floor not met`() {
            // 75×8@2 rem=1: bump 80 has last-set capacity 4 < 5; current 75 still clears → Hold 75.
            val outcome = engine.recommendNextSet(
                lastPerformed = performed(75.0, 8, 2),
                completedSlot = topSet(repMin = 4, repMax = 6),
                targetSlot = topSet(repMin = 4, repMax = 6),
                currentWeight = lb(75.0),
                remainingCount = 1,
            )
            assertTrue(outcome is RecommendationOutcome.Hold)
            assertEquals(75.0, (outcome as RecommendationOutcome.Hold).weight.amount, 0.05)
        }

        @Test
        @DisplayName("S3d: too-easy bump always from performed load (no stack if currentWeight inflated)")
        fun `S3d too easy bump from performed not inflated current`() {
            // Performed 75, too easy; if next-slot weight is already 85 (stale live), still bump 75→80.
            val outcome = engine.recommendNextSet(
                lastPerformed = performed(75.0, 11, 2),
                completedSlot = topSet(repMin = 4, repMax = 6),
                targetSlot = topSet(repMin = 4, repMax = 6),
                currentWeight = lb(85.0),
                remainingCount = 2,
                completedSetPrescribedWeight = lb(75.0),
            )
            assertTrue(outcome is RecommendationOutcome.Hold)
            assertEquals(80.0, (outcome as RecommendationOutcome.Hold).weight.amount, 0.05)
        }

        @Test
        @DisplayName("S4: backoff weak set — 100x3@2, 2 remaining -> Reduce ~82.8")
        fun `S4 backoff too hard reduces weight`() {
            val outcome = engine.recommendNextSet(
                lastPerformed = performed(100.0, 3, 2),
                completedSlot = backoffSlot(),
                targetSlot = backoffSlot(),
                currentWeight = lb(100.0),
                remainingCount = 2,
            )
            assertTrue(outcome is RecommendationOutcome.Reduce)
            assertEquals(82.8, (outcome as RecommendationOutcome.Reduce).weight.amount, 1.0)
        }
    }

    // --- M1-M8: edge cases ---

    @Nested
    @DisplayName("Edge cases")
    inner class EdgeCases {

        @Test
        @DisplayName("M1: RIR mismatch — 75x10@4 (target 2 RIR) -> Hold (not too easy)")
        fun `M1 RIR mismatch holds`() {
            // achieved = 10+4-2 = 12 (at ceiling, not ≥2 over). rem=1 so last-set capacity clears floor.
            val outcome = engine.recommendNextSet(
                lastPerformed = performed(75.0, 10, 4),
                completedSlot = topSet(),
                targetSlot = topSet(),
                currentWeight = lb(75.0),
                remainingCount = 1,
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
        @DisplayName("M4: single remaining set — lastSetIndex=2 still applies one fatigue step")
        fun `M4 single remaining set applies one fatigue step`() {
            // rem=1 → index 2. 75×10@2 has enough headroom after one step to Hold.
            val outcome = engine.recommendNextSet(
                lastPerformed = performed(75.0, 10, 2),
                completedSlot = topSet(),
                targetSlot = topSet(),
                currentWeight = lb(75.0),
                remainingCount = 1,
            )
            assertTrue(outcome is RecommendationOutcome.Hold)
            assertEquals(75.0, (outcome as RecommendationOutcome.Hold).weight.amount, 0.05)
            assertEquals(2, engine.lastRemainingSetIndex(1))
        }

        @Test
        @DisplayName("M8: exactly 2 over ceiling — 75x9@2, top 4-6@2, rem=1 -> bump Hold 80")
        fun `M8 exactly 2 over triggers too easy`() {
            // achieved=9 with rem=1 so bumped 80 still clears floor.
            val outcome = engine.recommendNextSet(
                lastPerformed = performed(75.0, 9, 2),
                completedSlot = topSet(repMin = 4, repMax = 6),
                targetSlot = topSet(repMin = 4, repMax = 6),
                currentWeight = lb(75.0),
                remainingCount = 1,
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
        @DisplayName("S2-tier: weak set + rounding — 75x2@2, 2 remaining -> Reduce 60 with floor clear")
        fun `S2 with tiers rounds to 60`() {
            val outcome = engine.recommendNextSet(
                lastPerformed = performed(75.0, 2, 2),
                completedSlot = topSet(),
                targetSlot = topSet(),
                currentWeight = lb(75.0),
                remainingCount = 2,
                incrementTiers = defaultTiers,
            )
            assertTrue(outcome is RecommendationOutcome.Reduce)
            val w = (outcome as RecommendationOutcome.Reduce).weight
            assertEquals(60.0, w.amount, 0.0)
            val e1rm = com.stratum.domain.training.EstimatedOneRm.calculate(75.0, 4)
            assertTrue(engine.capacityAtRir(e1rm, w, 3, 2, null, null) >= BACKOFF_REP_FLOOR)
        }

        @Test
        @DisplayName("S4-tier: backoff weak + rounding — 100x3@2, 2 remaining -> Reduce 80 with floor clear")
        fun `S4 with tiers rounds to 80`() {
            val outcome = engine.recommendNextSet(
                lastPerformed = performed(100.0, 3, 2),
                completedSlot = backoffSlot(),
                targetSlot = backoffSlot(),
                currentWeight = lb(100.0),
                remainingCount = 2,
                incrementTiers = defaultTiers,
            )
            assertTrue(outcome is RecommendationOutcome.Reduce)
            val w = (outcome as RecommendationOutcome.Reduce).weight
            assertEquals(80.0, w.amount, 0.0)
            val e1rm = com.stratum.domain.training.EstimatedOneRm.calculate(100.0, 5)
            assertTrue(engine.capacityAtRir(e1rm, w, 3, 2, null, null) >= BACKOFF_REP_FLOOR)
        }

        @Test
        @DisplayName("RIR-5-tier: reduce + step-down — 100x3@1, 1 remaining -> Reduce 85 (not 90)")
        fun `RIR-5 with tiers steps down until floor clears`() {
            // Continuous best ≈89.4 → nearest 90 fails capacity → step to 85.
            val outcome = engine.recommendNextSet(
                lastPerformed = performed(100.0, 3, 1),
                completedSlot = topSet(repMin = 5, repMax = 8, rirGoal = 1),
                targetSlot = topSet(repMin = 5, repMax = 8, rirGoal = 1),
                currentWeight = lb(100.0),
                remainingCount = 1,
                incrementTiers = defaultTiers,
            )
            assertTrue(outcome is RecommendationOutcome.Reduce)
            val w = (outcome as RecommendationOutcome.Reduce).weight
            assertEquals(85.0, w.amount, 0.0)
            val e1rm = com.stratum.domain.training.EstimatedOneRm.calculate(100.0, 4)
            assertTrue(engine.capacityAtRir(e1rm, w, 2, 1, null, null) >= BACKOFF_REP_FLOOR)
        }

        @Test
        @DisplayName("too-easy bump + rounding — 100x10@1, top 3-6@1 -> Hold 110 when floor clears")
        fun `too-easy bump with tiers stays at 110 when capacity allows`() {
            // 100+5=105 → 10 lb band → 110. 100×10@1 has headroom for last-set floor at 110.
            val outcome = engine.recommendNextSet(
                lastPerformed = performed(100.0, 10, 1),
                completedSlot = topSet(repMin = 3, repMax = 6, rirGoal = 1),
                targetSlot = topSet(repMin = 3, repMax = 6, rirGoal = 1),
                currentWeight = lb(100.0),
                remainingCount = 1,
                incrementTiers = defaultTiers,
            )
            assertTrue(outcome is RecommendationOutcome.Hold)
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

    @Nested
    @DisplayName("per-lift slope override")
    inner class PerLiftSlopeOverride {

        @Test
        @DisplayName("steeper per-lift slope reduces weight more than population slope (2 remaining)")
        fun `steeper slope reduces weight more`() {
            val population = engine.recommendNextSet(
                lastPerformed = performed(75.0, 2, 2),
                completedSlot = topSet(),
                targetSlot = topSet(),
                currentWeight = lb(75.0),
                remainingCount = 2,
                perLiftSlope = null,
            )
            val outcome = engine.recommendNextSet(
                lastPerformed = performed(75.0, 2, 2),
                completedSlot = topSet(),
                targetSlot = topSet(),
                currentWeight = lb(75.0),
                remainingCount = 2,
                perLiftSlope = -3.0,
            )
            assertTrue(outcome is RecommendationOutcome.Reduce)
            assertTrue(population is RecommendationOutcome.Reduce)
            val reduced = (outcome as RecommendationOutcome.Reduce).weight.amount
            val pop = (population as RecommendationOutcome.Reduce).weight.amount
            assertTrue(reduced < pop, "Steeper slope must reduce below population $pop, got $reduced")
        }

        @Test
        @DisplayName("per-lift slope of null falls back to population slope (no behavior change)")
        fun `null slope falls back to population`() {
            val outcome = engine.recommendNextSet(
                lastPerformed = performed(75.0, 2, 2),
                completedSlot = topSet(),
                targetSlot = topSet(),
                currentWeight = lb(75.0),
                remainingCount = 2,
                perLiftSlope = null,
            )
            assertTrue(outcome is RecommendationOutcome.Reduce)
            assertEquals(60.2, (outcome as RecommendationOutcome.Reduce).weight.amount, 1.0)
        }

        @Test
        @DisplayName("shallower per-lift slope reduces weight less than population (2 remaining)")
        fun `shallower slope reduces weight less`() {
            val population = engine.recommendNextSet(
                lastPerformed = performed(75.0, 2, 2),
                completedSlot = topSet(),
                targetSlot = topSet(),
                currentWeight = lb(75.0),
                remainingCount = 2,
            )
            val outcome = engine.recommendNextSet(
                lastPerformed = performed(75.0, 2, 2),
                completedSlot = topSet(),
                targetSlot = topSet(),
                currentWeight = lb(75.0),
                remainingCount = 2,
                perLiftSlope = -1.0,
            )
            assertTrue(outcome is RecommendationOutcome.Reduce)
            assertTrue(population is RecommendationOutcome.Reduce)
            val reduced = (outcome as RecommendationOutcome.Reduce).weight.amount
            val pop = (population as RecommendationOutcome.Reduce).weight.amount
            assertTrue(reduced > pop, "Shallower slope must reduce less than population $pop, got $reduced")
            assertTrue(reduced < 75.0, "Must still reduce below current weight 75.0, got $reduced")
        }

        @Test
        @DisplayName("per-lift slope still applies when remainingCount = 1 (lastSetIndex = 2)")
        fun `per lift slope applies when one remaining`() {
            // rem=1 → lastSetIndex=2 → one decay step; steeper slope reduces more.
            val populationOutcome = engine.recommendNextSet(
                lastPerformed = performed(75.0, 2, 2),
                completedSlot = topSet(),
                targetSlot = topSet(),
                currentWeight = lb(75.0),
                remainingCount = 1,
            )
            val steepOutcome = engine.recommendNextSet(
                lastPerformed = performed(75.0, 2, 2),
                completedSlot = topSet(),
                targetSlot = topSet(),
                currentWeight = lb(75.0),
                remainingCount = 1,
                perLiftSlope = -5.0,
            )
            assertTrue(populationOutcome is RecommendationOutcome.Reduce)
            assertTrue(steepOutcome is RecommendationOutcome.Reduce)
            assertTrue(
                (steepOutcome as RecommendationOutcome.Reduce).weight.amount <
                    (populationOutcome as RecommendationOutcome.Reduce).weight.amount,
                "Steeper slope must reduce more even with one remaining set",
            )
        }
    }

    /**
     * Exhaustive decision-tree coverage for live recommendations. Prefer behavioral
     * assertions (Hold/Reduce/null + floor capacity) over brittle exact pounds unless
     * the case is a known regression fixture.
     */
    @Nested
    @DisplayName("Decision-tree edge coverage")
    inner class DecisionTreeEdgeCoverage {

        @Nested
        @DisplayName("On-track top set boundaries")
        inner class OnTrackBoundaries {

            @Test
            @DisplayName("exactly repMin at goal weight is on-track → null")
            fun atRepMinOnTrack() {
                assertNull(
                    engine.recommendNextSet(
                        lastPerformed = performed(100.0, 4, 2), // achieved @ rir2 = 4
                        completedSlot = topSet(repMin = 4, repMax = 8, rirGoal = 2),
                        targetSlot = topSet(repMin = 4, repMax = 8, rirGoal = 2),
                        currentWeight = lb(100.0),
                        remainingCount = 2,
                        completedSetPrescribedWeight = lb(100.0),
                    ),
                )
            }

            @Test
            @DisplayName("exactly repMax at goal weight is on-track → null")
            fun atRepMaxOnTrack() {
                assertNull(
                    engine.recommendNextSet(
                        lastPerformed = performed(100.0, 8, 2), // achieved = 8
                        completedSlot = topSet(repMin = 4, repMax = 8, rirGoal = 2),
                        targetSlot = topSet(repMin = 4, repMax = 8, rirGoal = 2),
                        currentWeight = lb(100.0),
                        remainingCount = 2,
                        completedSetPrescribedWeight = lb(100.0),
                    ),
                )
            }

            @Test
            @DisplayName("one rep under repMin is not on-track (may still Hold if capacity OK)")
            fun oneUnderRepMinNotOnTrack() {
                val outcome = engine.recommendNextSet(
                    lastPerformed = performed(100.0, 7, 2), // achieved = 7, range 8-12
                    completedSlot = topSet(repMin = 8, repMax = 12, rirGoal = 2),
                    targetSlot = topSet(repMin = 8, repMax = 12, rirGoal = 2),
                    currentWeight = lb(100.0),
                    remainingCount = 1,
                    completedSetPrescribedWeight = lb(100.0),
                )
                // Not null — live path runs (missed band by 1, not on-track).
                assertTrue(outcome is RecommendationOutcome.Hold || outcome is RecommendationOutcome.Reduce)
            }

            @Test
            @DisplayName("weight differs from prescription is never on-track even if reps in range")
            fun wrongWeightNotOnTrack() {
                val outcome = engine.recommendNextSet(
                    lastPerformed = performed(90.0, 8, 2),
                    completedSlot = topSet(repMin = 4, repMax = 12, rirGoal = 2),
                    targetSlot = topSet(repMin = 4, repMax = 12, rirGoal = 2),
                    currentWeight = lb(100.0),
                    remainingCount = 1,
                    completedSetPrescribedWeight = lb(100.0),
                )
                assertTrue(outcome is RecommendationOutcome.Hold || outcome is RecommendationOutcome.Reduce)
            }

            @Test
            @DisplayName("isOnTrackTopSet: blank prescribed weight is false")
            fun blankPrescribedNotOnTrack() {
                assertTrue(
                    !engine.isOnTrackTopSet(
                        performed(100.0, 8, 2),
                        topSet(),
                        lb(0.0),
                    ),
                )
            }

            @Test
            @DisplayName("isOnTrackTopSet: non-top completed slot is false")
            fun backoffCompletedNotOnTrack() {
                assertTrue(
                    !engine.isOnTrackTopSet(
                        performed(100.0, 10, 2),
                        backoffSlot(),
                        lb(100.0),
                    ),
                )
            }
        }

        @Nested
        @DisplayName("Too-easy boundaries and failed bump")
        inner class TooEasyEdges {

            @Test
            @DisplayName("exactly one over ceiling does not bump")
            fun oneOverCeilingNoBump() {
                // achieved = 7, repMax = 6 → shortfall 1, not ≥2
                val outcome = engine.recommendNextSet(
                    lastPerformed = performed(75.0, 7, 2),
                    completedSlot = topSet(repMin = 4, repMax = 6, rirGoal = 2),
                    targetSlot = topSet(repMin = 4, repMax = 6, rirGoal = 2),
                    currentWeight = lb(75.0),
                    remainingCount = 1,
                    completedSetPrescribedWeight = lb(200.0), // force off-track on weight
                )
                // Off-track because weight 75 ≠ 200; not too-easy; hold or reduce at 75.
                when (outcome) {
                    is RecommendationOutcome.Hold ->
                        assertEquals(75.0, outcome.weight.amount, 0.05)
                    is RecommendationOutcome.Reduce ->
                        assertTrue(outcome.weight.amount < 75.0)
                    null -> error("expected live rec")
                }
            }

            @Test
            @DisplayName("exactly two over ceiling bumps when last set still clears floor")
            fun twoOverCeilingBumps() {
                val outcome = engine.recommendNextSet(
                    lastPerformed = performed(75.0, 9, 2), // achieved 9, max 6 → +3
                    completedSlot = topSet(repMin = 4, repMax = 6, rirGoal = 2),
                    targetSlot = topSet(repMin = 4, repMax = 6, rirGoal = 2),
                    currentWeight = lb(75.0),
                    remainingCount = 1,
                    completedSetPrescribedWeight = lb(75.0),
                )
                // Not on-track (over range); too-easy; rem=1 bump 80 clears floor.
                assertTrue(outcome is RecommendationOutcome.Hold)
                assertEquals(80.0, (outcome as RecommendationOutcome.Hold).weight.amount, 0.05)
            }

            @Test
            @DisplayName("failed bump never recommends the bumped load")
            fun failedBumpNeverRecommendsBump() {
                val outcome = engine.recommendNextSet(
                    lastPerformed = performed(75.0, 8, 2),
                    completedSlot = topSet(repMin = 4, repMax = 6, rirGoal = 2),
                    targetSlot = topSet(repMin = 4, repMax = 6, rirGoal = 2),
                    currentWeight = lb(75.0),
                    remainingCount = 1,
                    completedSetPrescribedWeight = lb(75.0),
                )
                when (outcome) {
                    is RecommendationOutcome.Hold ->
                        assertEquals(75.0, outcome.weight.amount, 0.05)
                    is RecommendationOutcome.Reduce ->
                        assertTrue(outcome.weight.amount < 75.0)
                    null -> error("expected live rec")
                }
            }
        }

        @Nested
        @DisplayName("Missed range vs deliberate light load (no forced cut)")
        inner class MissedRangeVsLightLoad {

            @Test
            @DisplayName("light load under repMin but last-set capacity ≥ 5 → Hold performed")
            fun lightLoadHoldsWhenFloorClears() {
                // Prescribed 165 for 10-15@2; lifter did 120×11@2 (under min, not on-track).
                // Capacity at 120 with rem=1 should clear floor → Hold 120, not force-reduce.
                val outcome = engine.recommendNextSet(
                    lastPerformed = performed(120.0, 11, 2),
                    completedSlot = topSet(repMin = 10, repMax = 15, rirGoal = 2),
                    targetSlot = topSet(repMin = 10, repMax = 15, rirGoal = 2),
                    currentWeight = lb(165.0),
                    remainingCount = 1,
                    completedSetPrescribedWeight = lb(165.0),
                )
                assertTrue(outcome is RecommendationOutcome.Hold)
                assertEquals(120.0, (outcome as RecommendationOutcome.Hold).weight.amount, 0.05)
            }

            @Test
            @DisplayName("light load with multi remaining under floor → Reduce below performed")
            fun lightLoadReducesWhenLastSetFailsFloor() {
                val outcome = engine.recommendNextSet(
                    lastPerformed = performed(100.0, 5, 1),
                    completedSlot = topSet(repMin = 8, repMax = 12, rirGoal = 1),
                    targetSlot = topSet(repMin = 8, repMax = 12, rirGoal = 1),
                    currentWeight = lb(165.0),
                    remainingCount = 4,
                    completedSetPrescribedWeight = lb(165.0),
                )
                assertTrue(outcome is RecommendationOutcome.Reduce)
                assertTrue((outcome as RecommendationOutcome.Reduce).weight.amount < 100.0)
            }
        }

        @Nested
        @DisplayName("Oracle reduce guarantees last-set floor")
        inner class OracleReduceGuarantees {

            @Test
            @DisplayName("tier step-down never ships a load under last-set floor")
            fun tierStepDownClearsFloor() {
                val outcome = engine.recommendNextSet(
                    lastPerformed = performed(100.0, 3, 1),
                    completedSlot = topSet(repMin = 5, repMax = 8, rirGoal = 1),
                    targetSlot = topSet(repMin = 5, repMax = 8, rirGoal = 1),
                    currentWeight = lb(100.0),
                    remainingCount = 1,
                    incrementTiers = defaultTiers,
                    completedSetPrescribedWeight = lb(100.0),
                )
                assertTrue(outcome is RecommendationOutcome.Reduce)
                val w = (outcome as RecommendationOutcome.Reduce).weight
                val e1rm = EstimatedOneRm.calculate(100.0, 4)
                assertTrue(
                    engine.capacityAtRir(e1rm, w, setIndex = 2, rirGoal = 1, null, null)
                        >= BACKOFF_REP_FLOOR,
                    "tiered reduce ${w.amount} must clear floor, capacity was under",
                )
            }

            @Test
            @DisplayName("empty tiers continuous reduce still clears floor")
            fun continuousReduceClearsFloor() {
                val outcome = engine.recommendNextSet(
                    lastPerformed = performed(100.0, 3, 1),
                    completedSlot = topSet(repMin = 5, repMax = 8, rirGoal = 1),
                    targetSlot = topSet(repMin = 5, repMax = 8, rirGoal = 1),
                    currentWeight = lb(100.0),
                    remainingCount = 1,
                    incrementTiers = emptyList(),
                    completedSetPrescribedWeight = lb(100.0),
                )
                assertTrue(outcome is RecommendationOutcome.Reduce)
                val w = (outcome as RecommendationOutcome.Reduce).weight
                val e1rm = EstimatedOneRm.calculate(100.0, 4)
                assertTrue(
                    engine.capacityAtRir(e1rm, w, setIndex = 2, rirGoal = 1, null, null)
                        >= BACKOFF_REP_FLOOR,
                )
            }

            @Test
            @DisplayName("more remaining sets → lower or equal reduce weight (extra fatigue)")
            fun moreRemainingMeansLowerOrEqualWeight() {
                val one = engine.recommendNextSet(
                    lastPerformed = performed(100.0, 3, 1),
                    completedSlot = topSet(repMin = 5, repMax = 8, rirGoal = 1),
                    targetSlot = topSet(repMin = 5, repMax = 8, rirGoal = 1),
                    currentWeight = lb(100.0),
                    remainingCount = 1,
                    completedSetPrescribedWeight = lb(100.0),
                )
                val many = engine.recommendNextSet(
                    lastPerformed = performed(100.0, 3, 1),
                    completedSlot = topSet(repMin = 5, repMax = 8, rirGoal = 1),
                    targetSlot = topSet(repMin = 5, repMax = 8, rirGoal = 1),
                    currentWeight = lb(100.0),
                    remainingCount = 4,
                    completedSetPrescribedWeight = lb(100.0),
                )
                assertTrue(one is RecommendationOutcome.Reduce)
                assertTrue(many is RecommendationOutcome.Reduce)
                assertTrue(
                    (many as RecommendationOutcome.Reduce).weight.amount <=
                        (one as RecommendationOutcome.Reduce).weight.amount + 0.01,
                )
            }
        }

        @Nested
        @DisplayName("Guards and short-circuits")
        inner class Guards {

            @Test
            @DisplayName("null lastPerformed → null")
            fun nullPerformed() {
                assertNull(
                    engine.recommendNextSet(
                        lastPerformed = null,
                        completedSlot = topSet(),
                        targetSlot = topSet(),
                        currentWeight = lb(100.0),
                    ),
                )
            }

            @Test
            @DisplayName("zero performed weight → null")
            fun zeroWeight() {
                assertNull(
                    engine.recommendNextSet(
                        lastPerformed = performed(0.0, 10, 2),
                        completedSlot = topSet(),
                        targetSlot = topSet(),
                        currentWeight = lb(100.0),
                    ),
                )
            }

            @Test
            @DisplayName("bodyweight load type → null")
            fun bodyweight() {
                assertNull(
                    engine.recommendNextSet(
                        lastPerformed = performed(0.0, 10, 2),
                        completedSlot = topSet(),
                        targetSlot = topSet(),
                        currentWeight = lb(0.0),
                        loadType = LoadType.Bodyweight,
                    ),
                )
            }

            @Test
            @DisplayName("assisted load type → null")
            fun assisted() {
                assertNull(
                    engine.recommendNextSet(
                        lastPerformed = LoggedSetValue(slotId, lb(-40.0), 8, 2),
                        completedSlot = topSet(),
                        targetSlot = topSet(),
                        currentWeight = lb(-40.0),
                        loadType = LoadType.Assisted,
                    ),
                )
            }

            @Test
            @DisplayName("null RIR treated as 0 (no free capacity)")
            fun nullRirAsZero() {
                val withRir = engine.recommendNextSet(
                    lastPerformed = performed(75.0, 8, 2),
                    completedSlot = topSet(),
                    targetSlot = topSet(),
                    currentWeight = lb(75.0),
                    remainingCount = 1,
                )
                val nullRir = engine.recommendNextSet(
                    lastPerformed = LoggedSetValue(slotId, lb(75.0), 8, null),
                    completedSlot = topSet(),
                    targetSlot = topSet(),
                    currentWeight = lb(75.0),
                    remainingCount = 1,
                )
                // Null RIR is weaker capacity → equal or lower recommended load.
                val wWith = when (withRir) {
                    is RecommendationOutcome.Hold -> withRir.weight.amount
                    is RecommendationOutcome.Reduce -> withRir.weight.amount
                    null -> 0.0
                }
                val wNull = when (nullRir) {
                    is RecommendationOutcome.Hold -> nullRir.weight.amount
                    is RecommendationOutcome.Reduce -> nullRir.weight.amount
                    null -> 0.0
                }
                assertTrue(wNull <= wWith + 0.01, "null RIR must not recommend heavier than rir=2")
            }

            @Test
            @DisplayName("lastRemainingSetIndex: rem N → index N+1")
            fun lastSetIndexFormula() {
                assertEquals(1, engine.lastRemainingSetIndex(0))
                assertEquals(2, engine.lastRemainingSetIndex(1))
                assertEquals(5, engine.lastRemainingSetIndex(4))
            }
        }

        @Nested
        @DisplayName("Working-weight re-anchor matrix")
        inner class ReanchorMatrix {

            @Test
            @DisplayName("heavier than baseline but not than target keeps target (asymmetric)")
            fun heavierThanBaselineOnlyKeepsTargetIfTargetHigher() {
                // performed 150, baseline 100, target 200 → not > max(100,200)=200 → keep 200
                assertEquals(
                    200.0,
                    engine.resolveWorkingWeight(lb(150.0), lb(200.0), lb(100.0)).amount,
                    0.0,
                )
            }

            @Test
            @DisplayName("performed equals baseline keeps target (intentional backoff path)")
            fun equalsBaselineKeepsTarget() {
                assertEquals(
                    140.0,
                    engine.resolveWorkingWeight(lb(165.0), lb(140.0), lb(165.0)).amount,
                    0.0,
                )
            }

            @Test
            @DisplayName("performed equals target lighter than baseline re-anchors to performed")
            fun equalsTargetAfterDropReanchors() {
                assertEquals(
                    120.0,
                    engine.resolveWorkingWeight(lb(120.0), lb(120.0), lb(165.0)).amount,
                    0.0,
                )
            }
        }
    }

    /**
     * Property-style suite: every recommended live load must leave the **last remaining set**
     * with FatigueModel capacity ≥ [BACKOFF_REP_FLOOR] at the target RIR. Covers continuous
     * and tiered lattices, multiple slopes, and weak performances that force Reduce.
     *
     * Factory: [oracleReduceCases] (outer companion).
     */
    @Nested
    @DisplayName("Oracle reduce property: last-set capacity ≥ BACKOFF_REP_FLOOR")
    inner class OracleReducePropertySuite {

        @ParameterizedTest(name = "w={0} reps={1} rir={2} rem={3} tRir={4} slope={5} tiers={6}")
        @MethodSource("com.stratum.domain.workout.RecommendationEngineRirMathTest#oracleReduceCases")
        fun `recommended load clears last-set floor`(
            weight: Double,
            reps: Int,
            rir: Int,
            remainingCount: Int,
            targetRirGoal: Int,
            perLiftSlope: Double?,
            useTiers: Boolean,
        ) {
            val tiers = if (useTiers) populationTiers() else emptyList()
            val completed = topSet(repMin = 5, repMax = 12, rirGoal = 2)
            val target = topSet(repMin = 5, repMax = 12, rirGoal = targetRirGoal)
            // Off-track prescription so on-track short-circuit never freezes the suite.
            val prescribed = weight + 50.0

            val outcome = engine.recommendNextSet(
                lastPerformed = performed(weight, reps, rir),
                completedSlot = completed,
                targetSlot = target,
                currentWeight = lb(prescribed),
                remainingCount = remainingCount,
                incrementTiers = tiers,
                perLiftSlope = perLiftSlope,
                completedSetPrescribedWeight = lb(prescribed),
            )

            assertTrue(
                outcome is RecommendationOutcome.Hold || outcome is RecommendationOutcome.Reduce,
                "expected Hold or Reduce for w=$weight reps=$reps, got $outcome",
            )
            val recommended = when (outcome) {
                is RecommendationOutcome.Hold -> outcome.weight
                is RecommendationOutcome.Reduce -> outcome.weight
                null -> error("unreachable")
            }
            assertTrue(recommended.amount > 0.0, "recommended weight must be positive")
            if (outcome is RecommendationOutcome.Reduce) {
                assertTrue(
                    recommended.amount <= weight + 0.01,
                    "Reduce must not exceed performed load ($weight → ${recommended.amount})",
                )
            }

            val e1rm = EstimatedOneRm.calculate(weight, reps + rir)
            val lastIdx = engine.lastRemainingSetIndex(remainingCount)
            val capacity = engine.capacityAtRir(
                e1rm = e1rm,
                weight = recommended,
                setIndex = lastIdx,
                rirGoal = targetRirGoal,
                perLiftSlope = perLiftSlope,
                perLiftIntercept = null,
            )
            assertTrue(
                capacity >= BACKOFF_REP_FLOOR,
                "last-set capacity $capacity < floor $BACKOFF_REP_FLOOR " +
                    "at ${recommended.amount} lb (w=$weight×$reps@$rir rem=$remainingCount " +
                    "tRir=$targetRirGoal slope=$perLiftSlope tiers=$useTiers)",
            )
        }

        @ParameterizedTest(name = "backoff w={0} reps={1} rir={2} rem={3} tRir={4} tiers={5}")
        @MethodSource("com.stratum.domain.workout.RecommendationEngineRirMathTest#oracleBackoffCases")
        fun `backoff target rir also clears last-set floor`(
            weight: Double,
            reps: Int,
            rir: Int,
            remainingCount: Int,
            targetRirGoal: Int,
            useTiers: Boolean,
        ) {
            val tiers = if (useTiers) populationTiers() else emptyList()
            val outcome = engine.recommendNextSet(
                lastPerformed = performed(weight, reps, rir),
                completedSlot = backoffSlot(rirGoal = 2),
                targetSlot = backoffSlot(rirGoal = targetRirGoal),
                currentWeight = lb(weight),
                remainingCount = remainingCount,
                incrementTiers = tiers,
                completedSetPrescribedWeight = lb(weight),
            )
            // Matched baseline on backoff completed is not "on-track top set" — live path runs.
            assertTrue(outcome is RecommendationOutcome.Hold || outcome is RecommendationOutcome.Reduce)
            val recommended = when (outcome) {
                is RecommendationOutcome.Hold -> outcome.weight
                is RecommendationOutcome.Reduce -> outcome.weight
                null -> error("unreachable")
            }
            val e1rm = EstimatedOneRm.calculate(weight, reps + rir)
            val capacity = engine.capacityAtRir(
                e1rm = e1rm,
                weight = recommended,
                setIndex = engine.lastRemainingSetIndex(remainingCount),
                rirGoal = targetRirGoal,
                perLiftSlope = null,
                perLiftIntercept = null,
            )
            assertTrue(
                capacity >= BACKOFF_REP_FLOOR,
                "backoff last-set capacity $capacity < floor at ${recommended.amount}",
            )
        }
    }
}