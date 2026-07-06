package com.stratum.domain.workout

import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.roundToIncrement
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.workout.progression.DoubleProgressionEngine
import com.stratum.domain.workout.progression.LinearProgressionConfig
import com.stratum.domain.workout.progression.LinearProgressionEngine
import com.stratum.domain.workout.progression.SlotProgressionRequest
import com.stratum.domain.workout.progression.StallStrictness
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * LoadType-aware progression behavior. Covers the three loading modalities
 * (Weighted / Bodyweight / Assisted) across the double + linear engines, the
 * LoadType-aware [roundToIncrement] overload, and the live [RecommendationEngine].
 *
 * - Bodyweight: engines short-circuit to 0 weight; RecommendationEngine returns null.
 * - Assisted: weights are negative; progression adds the increment toward zero and
 *   caps at 0 (graduation to bodyweight). Tier rounding uses abs(amount) and preserves sign.
 * - Weighted: unchanged (covered by the Slice 2/4 suites; asserted here only for parity).
 */
@DisplayName("LoadType: bodyweight + assisted progression")
class LoadTypeProgressionTest {

    private val workoutLiftId = WorkoutLiftId("wl-1")
    private val liftId = LiftId("L-1")
    private val slotId = SetSlotId.Template(LiftSetId("ls-1"))

    private fun lb(amount: Double) = Weight(amount, Weight.WeightUnit.Lb)

    private fun topSet(repMin: Int = 4, repMax: Int = 6, rirGoal: Int = 2) = SetSlot(
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

    private fun historyEntry(reps: Int, rir: Int?, weight: Double, sessionId: String = "s-n1") =
        SlotHistoryEntry(
            session = TrainingSession(
                id = TrainingSessionId(sessionId),
                programId = com.stratum.domain.program.identity.ProgramId("p-1"),
                workoutId = com.stratum.domain.program.identity.WorkoutId("w-1"),
                status = TrainingSessionStatus.COMPLETED,
                kind = TrainingSessionKind.PROGRAM_WORKOUT,
                origin = TrainingSessionOrigin.IMPORT,
                cycleNumberSnapshot = 1,
                weekdaySnapshot = null,
                startedAt = Instant.parse("2026-06-28T10:00:00Z"),
                endedAt = Instant.parse("2026-06-28T11:00:00Z"),
                sourceApp = null,
            ),
            loggedSet = LoggedSet(
                id = LoggedSetId("set-1"),
                sessionId = TrainingSessionId(sessionId),
                exerciseNameRaw = "Pull-Up",
                liftId = liftId,
                setIndex = 0,
                weight = lb(weight),
                reps = reps,
                rir = rir,
                completedAt = Instant.parse("2026-06-28T10:30:00Z"),
                liftContext = LoggedLiftContext(
                    liftName = "Pull-Up",
                    movementPatternsBitmask = 1,
                    primaryMusclesBitmask = 1,
                    secondaryMusclesBitmask = 0,
                    kinematicProfile = KinematicProfile.Isolation,
                    muscleLengthBias = MuscleLengthBias.MidRange,
                ),
            ),
        )

    private fun tiers() = listOf(
        IncrementTier(lb(0.0), lb(40.0), lb(2.5)),
        IncrementTier(lb(40.0), lb(100.0), lb(5.0)),
        IncrementTier(lb(100.0), null, lb(10.0)),
    )

    private fun linearConfig(overrides: Map<LiftId, Weight> = emptyMap()) = LinearProgressionConfig(
        lowerBodyIncrement = lb(5.0),
        upperBodyIncrement = lb(2.5),
        minimumIncrement = lb(2.5),
        perLiftIncrementOverride = overrides,
        stallThresholdSessions = 2,
        resetFraction = 0.10,
        perLiftResetFractionOverride = emptyMap(),
        resetsBeforeTransition = 2,
        stallStrictness = StallStrictness.RirBased,
    )

    // --- DoubleProgressionEngine: Bodyweight ----------------------------------------

    @Test
    @DisplayName("LT-double-bodyweight-holds-at-zero-regardless-of-history")
    fun `LT-double-bodyweight-holds-at-zero`() {
        val slot = topSet(repMin = 4, repMax = 8, rirGoal = 2)
        // History met the top of the range at RIR — would normally increment, but
        // bodyweight short-circuits to 0 weight.
        val history = listOf(historyEntry(reps = 8, rir = 2, weight = 0.0))
        val rec = DoubleProgressionEngine()
            .recommend(SlotProgressionRequest(slot, history, fallbackOneRm = null, loadType = LoadType.Bodyweight))
        assertEquals(lb(0.0), rec.recommendedWeight)
        assertEquals(8, rec.baselineExpectedReps)
    }

    @Test
    @DisplayName("LT-double-bodyweight-baseline-reps-from-slot-min-when-no-history")
    fun `LT-double-bodyweight-no-history`() {
        val slot = topSet(repMin = 5, repMax = 10, rirGoal = 2)
        val rec = DoubleProgressionEngine()
            .recommend(SlotProgressionRequest(slot, emptyList(), fallbackOneRm = null, loadType = LoadType.Bodyweight))
        assertEquals(lb(0.0), rec.recommendedWeight)
        assertEquals(5, rec.baselineExpectedReps)
    }

    // --- DoubleProgressionEngine: Assisted ------------------------------------------

    @Test
    @DisplayName("LT-double-assisted-increments-toward-zero")
    fun `LT-double-assisted-increments-toward-zero`() {
        val slot = topSet(repMin = 4, repMax = 6, rirGoal = 2)
        // -50 lb assistance, hit the top at RIR → engine adds +5 → -45 (less assistance).
        val history = listOf(historyEntry(reps = 6, rir = 2, weight = -50.0))
        val rec = DoubleProgressionEngine()
            .recommend(SlotProgressionRequest(slot, history, fallbackOneRm = null, loadType = LoadType.Assisted))
        assertEquals(lb(-45.0), rec.recommendedWeight)
    }

    @Test
    @DisplayName("LT-double-assisted-caps-at-zero-on-graduation")
    fun `LT-double-assisted-caps-at-zero`() {
        val slot = topSet(repMin = 4, repMax = 6, rirGoal = 2)
        // -2.5 lb assistance + 5 lb increment = +2.5 (positive) → must cap at 0.
        val history = listOf(historyEntry(reps = 6, rir = 2, weight = -2.5))
        val rec = DoubleProgressionEngine()
            .recommend(SlotProgressionRequest(slot, history, fallbackOneRm = null, loadType = LoadType.Assisted))
        assertEquals(lb(0.0), rec.recommendedWeight)
    }

    @Test
    @DisplayName("LT-double-assisted-holds-when-top-not-met")
    fun `LT-double-assisted-holds`() {
        val slot = topSet(repMin = 4, repMax = 6, rirGoal = 2)
        val history = listOf(historyEntry(reps = 5, rir = 1, weight = -50.0))
        val rec = DoubleProgressionEngine()
            .recommend(SlotProgressionRequest(slot, history, fallbackOneRm = null, loadType = LoadType.Assisted))
        assertEquals(lb(-50.0), rec.recommendedWeight)
    }

    // --- LinearProgressionEngine: Bodyweight + Assisted ------------------------------

    @Test
    @DisplayName("LT-linear-bodyweight-holds-at-zero")
    fun `LT-linear-bodyweight-holds-at-zero`() {
        val slot = topSet(repMin = 5, repMax = 8, rirGoal = 2)
        // Bottom met at RIR would normally increment; bodyweight short-circuits.
        val history = listOf(historyEntry(reps = 5, rir = 2, weight = 0.0))
        val engine = LinearProgressionEngine(linearConfig())
        val rec = engine.recommend(
            SlotProgressionRequest(slot, history, fallbackOneRm = null, loadType = LoadType.Bodyweight),
        )
        assertEquals(lb(0.0), rec.recommendedWeight)
    }

    @Test
    @DisplayName("LT-linear-assisted-increments-toward-zero")
    fun `LT-linear-assisted-increments-toward-zero`() {
        val slot = topSet(repMin = 5, repMax = 8, rirGoal = 2)
        val history = listOf(historyEntry(reps = 5, rir = 2, weight = -80.0))
        val engine = LinearProgressionEngine(linearConfig(overrides = mapOf(liftId to lb(5.0))))
        val rec = engine.recommend(
            SlotProgressionRequest(slot, history, fallbackOneRm = null, loadType = LoadType.Assisted),
        )
        assertEquals(lb(-75.0), rec.recommendedWeight)
    }

    @Test
    @DisplayName("LT-linear-assisted-caps-at-zero")
    fun `LT-linear-assisted-caps-at-zero`() {
        val slot = topSet(repMin = 5, repMax = 8, rirGoal = 2)
        // -2.5 + 5 = +2.5 → cap at 0.
        val history = listOf(historyEntry(reps = 5, rir = 2, weight = -2.5))
        val engine = LinearProgressionEngine(linearConfig(overrides = mapOf(liftId to lb(5.0))))
        val rec = engine.recommend(
            SlotProgressionRequest(slot, history, fallbackOneRm = null, loadType = LoadType.Assisted),
        )
        assertEquals(lb(0.0), rec.recommendedWeight)
    }

    // --- IncrementTier.roundToIncrement(weight, loadType) ---------------------------

    @Test
    @DisplayName("LT-tier-bodyweight-passthrough")
    fun `LT-tier-bodyweight-passthrough`() {
        // Bodyweight weight is always 0; rounding is a no-op.
        assertEquals(lb(0.0), tiers().roundToIncrement(lb(0.0), LoadType.Bodyweight))
    }

    @Test
    @DisplayName("LT-tier-weighted-delegates-to-standard-overload")
    fun `LT-tier-weighted-delegates`() {
        // 93.93 → band 40-100 (inc 5) → 95.
        assertEquals(lb(95.0), tiers().roundToIncrement(lb(93.93), LoadType.Weighted))
    }

    @Test
    @DisplayName("LT-tier-assisted-uses-abs-and-preserves-sign")
    fun `LT-tier-assisted-abs-and-sign`() {
        // -47.3 → abs 47.3 → band 40-100 (inc 5) → 45 → re-negated → -45.
        assertEquals(lb(-45.0), tiers().roundToIncrement(lb(-47.3), LoadType.Assisted))
    }

    @Test
    @DisplayName("LT-tier-assisted-zero-stays-zero")
    fun `LT-tier-assisted-zero`() {
        assertEquals(lb(0.0), tiers().roundToIncrement(lb(0.0), LoadType.Assisted))
    }

    // --- RecommendationEngine: Bodyweight + Assisted --------------------------------

    @Test
    @DisplayName("LT-rec-bodyweight-returns-null")
    fun `LT-rec-bodyweight-returns-null`() {
        val lastPerformed = LoggedSetValue(slotId, lb(0.0), 8, 2)
        val target = topSet(repMin = 5, repMax = 10, rirGoal = 2)
        val outcome = RecommendationEngine().recommendNextSet(
            lastPerformed = lastPerformed,
            completedSlot = null,
            targetSlot = target,
            currentWeight = lb(0.0),
            loadType = LoadType.Bodyweight,
        )
        assertNull(outcome)
    }

    @Test
    @DisplayName("LT-rec-assisted-defers-to-snapshot-baseline-v1")
    fun `LT-rec-assisted-returns-null`() {
        // The e1RM formula assumes positive load; assisted weights are negative, so the
        // live engine defers to the snapshot baseline (which handles assisted progression
        // correctly). A future abs-based e1RM reformulation could enable this path.
        val lastPerformed = LoggedSetValue(slotId, lb(-50.0), 8, 2)
        val target = topSet(repMin = 4, repMax = 6, rirGoal = 2)
        val outcome = RecommendationEngine().recommendNextSet(
            lastPerformed = lastPerformed,
            completedSlot = null,
            targetSlot = target,
            currentWeight = lb(-50.0),
            loadType = LoadType.Assisted,
        )
        assertNull(outcome)
    }

    @Test
    @DisplayName("LT-rec-weighted-still-produces-recommendations")
    fun `LT-rec-weighted-unaffected`() {
        // Parity check: Weighted behavior is unchanged by the loadType branch.
        val lastPerformed = LoggedSetValue(slotId, lb(135.0), 5, 2)
        val target = topSet(repMin = 4, repMax = 6, rirGoal = 2)
        val outcome = RecommendationEngine().recommendNextSet(
            lastPerformed = lastPerformed,
            completedSlot = null,
            targetSlot = target,
            currentWeight = lb(135.0),
            loadType = LoadType.Weighted,
        )
        assertNotNull(outcome)
        assertTrue(outcome is RecommendationOutcome.Hold || outcome is RecommendationOutcome.Reduce)
    }
}