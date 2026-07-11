package com.stratum.domain.workout

import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.LastTrainedBucket
import com.stratum.domain.onboarding.TrainingAge
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.generation.reference.schemeForTrainingAge
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.EstimatedOneRm
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.workout.progression.LinearProgressionConfig
import com.stratum.domain.workout.progression.LinearProgressionEngine
import com.stratum.domain.workout.progression.LinearProgressionState
import com.stratum.domain.workout.progression.ProgressionBaseline
import com.stratum.domain.workout.progression.SlotProgressionRequest
import com.stratum.domain.workout.progression.SchemeTransitionReason
import com.stratum.domain.workout.progression.StallStrictness
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Slice 4 — Linear progression (Greyskull-style hybrid). 16 scenarios (S4-*).
 */
@DisplayName("Slice 4: Linear progression (Greyskull-style hybrid)")
class Slice4LinearProgressionTest {

    private val workoutLiftId = WorkoutLiftId("wl-1")
    private val liftId = LiftId("L-1")
    private val slotId = SetSlotId.Template(LiftSetId("ls-1"))

    private fun lb(amount: Double) = Weight(amount, Weight.WeightUnit.Lb)

    private fun linearSlot(repMin: Int = 5, repMax: Int = 8, rirGoal: Int = 2) = SetSlot(
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

    private fun linearBackoffSlot(rirGoal: Int = 2) = SetSlot(
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

    private fun config(strictness: StallStrictness = StallStrictness.RirBased) = LinearProgressionConfig(
        lowerBodyIncrement = lb(5.0),
        upperBodyIncrement = lb(2.5),
        minimumIncrement = lb(2.5),
        perLiftIncrementOverride = emptyMap(),
        stallThresholdSessions = 2,
        stallStrictness = strictness,
    )

    private fun historyEntry(
        reps: Int,
        rir: Int?,
        weight: Double,
        sessionId: String,
        startedAt: String = "2026-06-28T10:00:00Z",
    ) = SlotHistoryEntry(
        session = TrainingSession(
            id = TrainingSessionId(sessionId),
            programId = com.stratum.domain.program.identity.ProgramId("p-1"),
            workoutId = com.stratum.domain.program.identity.WorkoutId("w-1"),
            status = TrainingSessionStatus.COMPLETED,
            kind = TrainingSessionKind.PROGRAM_WORKOUT,
            origin = TrainingSessionOrigin.IMPORT,
            cycleNumberSnapshot = 1,
            weekdaySnapshot = null,
            startedAt = Instant.parse(startedAt),
            endedAt = Instant.parse(startedAt).plusSeconds(3600),
            sourceApp = null,
        ),
        loggedSet = LoggedSet(
            id = LoggedSetId("set-$sessionId"),
            sessionId = TrainingSessionId(sessionId),
            exerciseNameRaw = "Squat",
            liftId = liftId,
            setIndex = 0,
            weight = lb(weight),
            reps = reps,
            rir = rir,
            completedAt = Instant.parse(startedAt).plusSeconds(1800),
            liftContext = LoggedLiftContext(
                liftName = "Squat",
                movementPatternsBitmask = 1,
                primaryMusclesBitmask = 1,
                secondaryMusclesBitmask = 0,
                kinematicProfile = KinematicProfile.Isolation,
                muscleLengthBias = MuscleLengthBias.MidRange,
            ),
        ),
    )

    /**
     * Builds a [SlotProgressionRequest] mirroring what the snapshot use case now produces:
     * the math baseline is [ProgressionBaseline.FromRecent] pointing at the first history
     * entry (the most-recent positional actual), so the engine reads it for math while
     * `history` supplies `previousWeight`/`previousReps` for UI display.
     */
    private fun request(
        slot: SetSlot,
        history: List<SlotHistoryEntry>,
        fallbackOneRm: Weight? = null,
        loadType: com.stratum.domain.lift.LoadType = com.stratum.domain.lift.LoadType.Weighted,
        currentTopSetWeight: Weight? = null,
        priorSlotRecommendation: SlotRecommendation? = null,
        priorSlot: SetSlot? = null,
        perLiftSlope: Double? = null,
        perLiftIntercept: Double? = null,
        baseline: ProgressionBaseline? = history.firstOrNull()?.let { ProgressionBaseline.FromRecent(it) },
    ): SlotProgressionRequest = SlotProgressionRequest(
        slot = slot,
        history = history,
        fallbackOneRm = fallbackOneRm,
        loadType = loadType,
        currentTopSetWeight = currentTopSetWeight,
        priorSlotRecommendation = priorSlotRecommendation,
        priorSlot = priorSlot,
        perLiftSlope = perLiftSlope,
        perLiftIntercept = perLiftIntercept,
        progressionBaseline = baseline,
    )

    // --- Enum collapse (3 scenarios) ---

    @Test
    @DisplayName("S4-last-trained-bucket-3-values")
    fun `S4-last-trained-bucket-3-values`() {
        val entries = LastTrainedBucket.entries
        assertEquals(
            listOf(
                LastTrainedBucket.WithinLastThreeMonths,
                LastTrainedBucket.MoreThanThreeMonthsAgo,
                LastTrainedBucket.Never,
            ),
            entries,
        )
        assertEquals(3, entries.size)
    }

    @Test
    @DisplayName("S4-enum-converters-round-trip")
    fun `S4-enum-converters-round-trip`() {
        // fromLastTrainedBucket returns the enum name; toLastTrainedBucket decodes via valueOf.
        assertEquals("WithinLastThreeMonths", LastTrainedBucket.WithinLastThreeMonths.name)
        assertEquals(
            LastTrainedBucket.MoreThanThreeMonthsAgo,
            LastTrainedBucket.valueOf("MoreThanThreeMonthsAgo"),
        )
        // Stale old name decodes to null (valueOf throws → caught → null).
        val stale = runCatching { LastTrainedBucket.valueOf("WithinLastMonth") }.getOrNull()
        assertEquals(null, stale)
    }

    @Test
    @DisplayName("S4-old-5-values-no-longer-referenced")
    fun `S4-old-5-values-no-longer-referenced`() {
        // No production or test source references the old 5 names except in migration docs.
        // This scenario's assertion is structural: the enum no longer declares them.
        val names = LastTrainedBucket.entries.map { it.name }.toSet()
        assertFalse("WithinLastMonth" in names)
        assertFalse("OneToSixMonthsAgo" in names)
        assertFalse("SixToTwelveMonthsAgo" in names)
        assertFalse("MoreThanAYearAgo" in names)
    }

    // --- schemeForTrainingAge (5 scenarios) ---

    @Test
    @DisplayName("S4-scheme-beginner-linear")
    fun `S4-scheme-beginner-linear`() {
        assertEquals(
            ProgressionScheme.Linear,
            schemeForTrainingAge(
                trainingAge = TrainingAge.Beginner,
                lastTrained = LastTrainedBucket.WithinLastThreeMonths,
                isCompound = true,
            ),
        )
    }

    @Test
    @DisplayName("S4-scheme-non-beginner-within-three-months-double")
    fun `S4-scheme-non-beginner-within-three-months-double`() {
        assertEquals(
            ProgressionScheme.Double,
            schemeForTrainingAge(
                trainingAge = TrainingAge.Intermediate,
                lastTrained = LastTrainedBucket.WithinLastThreeMonths,
                isCompound = true,
            ),
        )
    }

    @Test
    @DisplayName("S4-scheme-non-beginner-more-than-three-months-linear")
    fun `S4-scheme-non-beginner-more-than-three-months-linear`() {
        assertEquals(
            ProgressionScheme.Linear,
            schemeForTrainingAge(
                trainingAge = TrainingAge.LateIntermediate,
                lastTrained = LastTrainedBucket.MoreThanThreeMonthsAgo,
                isCompound = true,
            ),
        )
    }

    @Test
    @DisplayName("S4-scheme-isolation-always-double")
    fun `S4-scheme-isolation-always-double`() {
        assertEquals(
            ProgressionScheme.Double,
            schemeForTrainingAge(
                trainingAge = TrainingAge.Beginner,
                lastTrained = LastTrainedBucket.MoreThanThreeMonthsAgo,
                isCompound = false,
            ),
        )
    }

    @Test
    @DisplayName("S4-mutation-defense-scheme-ignores-iscompound")
    fun `S4-mutation-defense-scheme-ignores-iscompound`() {
        // A lazy implementation that ignores isCompound returns Linear here and fails.
        assertEquals(
            ProgressionScheme.Double,
            schemeForTrainingAge(
                trainingAge = TrainingAge.Beginner,
                lastTrained = LastTrainedBucket.MoreThanThreeMonthsAgo,
                isCompound = false,
            ),
        )
    }

    // --- LinearProgressionEngine (8 scenarios) ---

    @Test
    @DisplayName("S4-linear-increment-when-bottom-at-rir")
    fun `S4-linear-increment-when-bottom-at-rir`() {
        val slot = linearSlot(repMin = 5, repMax = 8, rirGoal = 2)
        val history = listOf(historyEntry(reps = 5, rir = 2, weight = 200.0, sessionId = "s-n1"))
        val recommendation = LinearProgressionEngine(config()).recommend(request(slot, history))
        assertEquals(lb(205.0), recommendation.recommendedWeight)
    }

    @Test
    @DisplayName("S4-linear-hold-when-rir-not-met")
    fun `S4-linear-hold-when-rir-not-met`() {
        // Bottom met but RIR missed → no Linear increment. Capacity 5@0 → 3@2 is below the
        // global floor of 5, so weight drops via EstimatedOneRm.weightForReps (never increments).
        val slot = linearSlot(repMin = 5, repMax = 8, rirGoal = 2)
        val history = listOf(historyEntry(reps = 5, rir = 0, weight = 200.0, sessionId = "s-n1"))
        val capacity = 5 // 5 + 0 RIR
        val e1rm = EstimatedOneRm.calculate(200.0, capacity)
        val expectedWeight = EstimatedOneRm.weightForReps(e1rm, 5 + slot.rirGoal)
        val recommendation = LinearProgressionEngine(config()).recommend(request(slot, history))
        assertEquals(expectedWeight, recommendation.recommendedWeight.amount)
        assertEquals(5, recommendation.baselineExpectedReps)
        assertTrue(recommendation.recommendedWeight.amount < 200.0)
    }

    @Test
    @DisplayName("S4-linear-2-consecutive-stalls-transition-to-double")
    fun `S4-linear-2-consecutive-stalls-transition-to-double`() {
        // Newest-first history (matches ProgressionHistoryPort order). Distinct timestamps
        // so diagnose walks oldest→newest correctly.
        val history = listOf(
            historyEntry(reps = 4, rir = 2, weight = 200.0, sessionId = "s-n1", startedAt = "2026-06-28T10:00:00Z"),
            historyEntry(reps = 4, rir = 2, weight = 200.0, sessionId = "s-n2", startedAt = "2026-06-21T10:00:00Z"),
        )
        val engine = LinearProgressionEngine(config())
        val state = engine.diagnose(liftId, history)
        assertTrue(state.shouldTransitionToDouble, "2 consecutive stalls → auto Double (no reset churn)")
        assertNotNull(state.emittedEvent)
        val event = state.emittedEvent!!
        assertEquals(ProgressionScheme.Linear, event.from)
        assertEquals(ProgressionScheme.Double, event.to)
        assertEquals(SchemeTransitionReason.ConsecutiveStalls, event.reason)
        assertEquals(2, state.currentStallCount)

        // Recommend does not *increment* while Linear; transition is applied by the snapshot
        // orchestrator (scheme flip), not by a classic 10% greyskull deload. Capacity 4+2=6 →
        // 4@2 is below the global floor of 5, so weight drops via weightForReps for floor 5.
        val slot = linearSlot(repMin = 5, repMax = 8, rirGoal = 2)
        val capacity = 6 // newest entry: 4 reps + 2 RIR
        val e1rm = EstimatedOneRm.calculate(200.0, capacity)
        val expectedWeight = EstimatedOneRm.weightForReps(e1rm, 5 + slot.rirGoal)
        val recommendation = engine.recommend(request(slot, history))
        assertEquals(expectedWeight, recommendation.recommendedWeight.amount)
        assertEquals(5, recommendation.baselineExpectedReps)
        assertTrue(recommendation.recommendedWeight.amount < 200.0)
    }

    @Test
    @DisplayName("S4-linear-success-after-stalls-clears-transition")
    fun `S4-linear-success-after-stalls-clears-transition`() {
        // Two stalls then a success: streak broken before threshold "sticks" across later
        // sessions. Wait — threshold is sticky once reached in the walk. Sequence:
        // stall, stall (transition true), success (stallCount=0 but shouldTransition already true).
        // After success only: no transition if stalls never hit 2 consecutively at end.
        // stall then success → no transition.
        val history = listOf(
            historyEntry(reps = 5, rir = 2, weight = 200.0, sessionId = "s-n1", startedAt = "2026-07-05T10:00:00Z"),
            historyEntry(reps = 4, rir = 2, weight = 200.0, sessionId = "s-n2", startedAt = "2026-06-28T10:00:00Z"),
        )
        val state = LinearProgressionEngine(config()).diagnose(liftId, history)
        assertEquals(0, state.currentStallCount)
        assertFalse(state.shouldTransitionToDouble)
    }

    @Test
    @DisplayName("S4-linear-single-stall-does-not-transition")
    fun `S4-linear-single-stall-does-not-transition`() {
        val history = listOf(
            historyEntry(reps = 4, rir = 2, weight = 200.0, sessionId = "s-n1", startedAt = "2026-06-28T10:00:00Z"),
        )
        val state = LinearProgressionEngine(config()).diagnose(liftId, history)
        assertEquals(1, state.currentStallCount)
        assertFalse(state.shouldTransitionToDouble)
        assertEquals(null, state.emittedEvent)
    }

    @Test
    @DisplayName("S4-linear-missed-sessions-do-not-credit-stall")
    fun `S4-linear-missed-sessions-do-not-credit-stall`() {
        val history = listOf(
            historyEntry(reps = 5, rir = 2, weight = 200.0, sessionId = "s-n1", startedAt = "2026-07-12T10:00:00Z"),
            // gap of 2 missed sessions (no entries)
            historyEntry(reps = 4, rir = 2, weight = 200.0, sessionId = "s-n4", startedAt = "2026-06-21T10:00:00Z"),
        )
        val state = LinearProgressionEngine(config()).diagnose(liftId, history)
        assertEquals(0, state.currentStallCount, "the gap must not count as stalls")
        assertFalse(state.shouldTransitionToDouble)
    }

    @Test
    @DisplayName("S4-linear-hold-neither-credits-nor-breaks-stall-streak")
    fun `S4-linear-hold-neither-credits-nor-breaks-stall-streak`() {
        // RirBased: bottom met + RIR missed → Hold. Sequence: stall, hold, stall →
        // hold is ignored → two stalls → transition.
        val history = listOf(
            historyEntry(reps = 4, rir = 2, weight = 200.0, sessionId = "s-n1", startedAt = "2026-07-12T10:00:00Z"),
            historyEntry(reps = 6, rir = 0, weight = 200.0, sessionId = "s-n2", startedAt = "2026-07-05T10:00:00Z"),
            historyEntry(reps = 4, rir = 2, weight = 200.0, sessionId = "s-n3", startedAt = "2026-06-28T10:00:00Z"),
        )
        val state = LinearProgressionEngine(config()).diagnose(liftId, history)
        assertTrue(state.shouldTransitionToDouble, "Hold must not break the stall streak between two stalls")
        assertEquals(SchemeTransitionReason.ConsecutiveStalls, state.emittedEvent!!.reason)
    }

    @Test
    @DisplayName("S4-linear-success-clears-single-stall-streak")
    fun `S4-linear-success-clears-single-stall-streak`() {
        val history = listOf(
            historyEntry(reps = 5, rir = 2, weight = 200.0, sessionId = "s-n1", startedAt = "2026-07-05T10:00:00Z"),
            historyEntry(reps = 4, rir = 2, weight = 200.0, sessionId = "s-n2", startedAt = "2026-06-28T10:00:00Z"),
        )
        val state = LinearProgressionEngine(config()).diagnose(liftId, history)
        assertEquals(0, state.currentStallCount)
        assertFalse(state.shouldTransitionToDouble)
    }

    @Test
    @DisplayName("S4-linear-diagnose-orders-by-startedAt-not-list-order")
    fun `S4-linear-diagnose-orders-by-startedAt-not-list-order`() {
        // Deliberately scrambled list order. Two consecutive stalls by time → transition.
        val history = listOf(
            historyEntry(reps = 5, rir = 2, weight = 200.0, sessionId = "s-old", startedAt = "2026-06-14T10:00:00Z"),
            historyEntry(reps = 4, rir = 2, weight = 200.0, sessionId = "s-mid", startedAt = "2026-06-21T10:00:00Z"),
            historyEntry(reps = 4, rir = 2, weight = 200.0, sessionId = "s-new", startedAt = "2026-06-28T10:00:00Z"),
        )
        val state = LinearProgressionEngine(config()).diagnose(liftId, history)
        assertTrue(state.shouldTransitionToDouble, "must process oldest→newest regardless of input list order")
    }

    @Test
    @DisplayName("S4-linear-transition-is-sticky-once-stall-threshold-hit-even-if-later-success")
    fun `S4-linear-transition-sticky-after-threshold`() {
        // stall, stall (threshold), then success. LP already failed — still transition.
        // Prevents "one good day after plateau" from keeping the lifter on Linear forever.
        val history = listOf(
            historyEntry(reps = 5, rir = 2, weight = 200.0, sessionId = "s-n1", startedAt = "2026-07-12T10:00:00Z"),
            historyEntry(reps = 4, rir = 2, weight = 200.0, sessionId = "s-n2", startedAt = "2026-07-05T10:00:00Z"),
            historyEntry(reps = 4, rir = 2, weight = 200.0, sessionId = "s-n3", startedAt = "2026-06-28T10:00:00Z"),
        )
        val state = LinearProgressionEngine(config()).diagnose(liftId, history)
        assertTrue(state.shouldTransitionToDouble)
        assertEquals(0, state.currentStallCount, "success cleared the live streak counter")
    }

    @Test
    @DisplayName("S4-linear-gate-bottom-missed-rir-met-is-stall-not-increment")
    fun `S4-linear-gate-bottom-missed-rir-met-is-stall-not-increment`() {
        val slot = linearSlot(repMin = 5, repMax = 8, rirGoal = 2)
        val history = listOf(
            historyEntry(reps = 4, rir = 3, weight = 200.0, sessionId = "s-n1"),
        )
        val engine = LinearProgressionEngine(config())
        val rec = engine.recommend(request(slot, history))
        assertEquals(lb(200.0), rec.recommendedWeight, "must not increment when bottom missed")
        val state = engine.diagnose(liftId, history)
        assertEquals(1, state.currentStallCount)
    }

    @Test
    @DisplayName("S4-linear-gate-bottom-met-rir-missed-holds-weight")
    fun `S4-linear-gate-bottom-met-rir-missed-holds-weight`() {
        val slot = linearSlot(repMin = 5, repMax = 8, rirGoal = 2)
        val history = listOf(
            historyEntry(reps = 7, rir = 0, weight = 200.0, sessionId = "s-n1"),
        )
        val rec = LinearProgressionEngine(config()).recommend(request(slot, history))
        assertEquals(lb(200.0), rec.recommendedWeight)
    }

    @Test
    @DisplayName("S4-linear-cold-start-uses-fallback-one-rm")
    fun `S4-linear-cold-start-uses-fallback-one-rm`() {
        val slot = linearSlot(repMin = 5, repMax = 8, rirGoal = 2)
        val rec = LinearProgressionEngine(config()).recommend(
            request(slot, emptyList(), fallbackOneRm = lb(135.0), baseline = null),
        )
        assertEquals(lb(135.0), rec.recommendedWeight)
        assertEquals(5, rec.baselineExpectedReps)
    }

    @Test
    @DisplayName("S4-linear-project-from-prior-when-no-recent-baseline")
    fun `S4-linear-project-from-prior-when-no-recent-baseline`() {
        val engine = LinearProgressionEngine(config())
        val topSlot = linearSlot(repMin = 5, repMax = 8, rirGoal = 2)
        val boSlot = linearBackoffSlot(rirGoal = 2)
        val topHistory = listOf(historyEntry(reps = 5, rir = 2, weight = 200.0, sessionId = "s-n1"))
        val topRec = engine.recommend(request(topSlot, topHistory))
        val boRec = engine.recommend(
            request(
                slot = boSlot,
                history = emptyList(),
                currentTopSetWeight = topRec.recommendedWeight,
                priorSlotRecommendation = topRec,
                priorSlot = topSlot,
                perLiftSlope = -2.0,
                perLiftIntercept = 0.0,
                baseline = ProgressionBaseline.ProjectFromPrior,
            ),
        )
        assertEquals(topRec.recommendedWeight, boRec.recommendedWeight)
        assertEquals(5, boRec.baselineExpectedReps)
    }


    @Test
    @DisplayName("S4-linear-per-lift-increment-override")
    fun `S4-linear-per-lift-increment-override`() {
        val cfg = config().copy(perLiftIncrementOverride = mapOf(liftId to lb(2.5)))
        val slot = linearSlot(repMin = 5, repMax = 8, rirGoal = 2)
        val history = listOf(historyEntry(reps = 5, rir = 2, weight = 100.0, sessionId = "s-n1"))
        val recommendation = LinearProgressionEngine(cfg).recommend(request(slot, history))
        // 100.0 + 2.5 override (not the default upper-body increment)
        assertEquals(lb(102.5), recommendation.recommendedWeight)
    }

    @Test
    @DisplayName("S4-linear-stall-strictness-absolute-vs-rir")
    fun `S4-linear-stall-strictness-absolute-vs-rir`() {
        // Same ground-out set: reps=5 (bottom) at rir=0.
        val history = listOf(historyEntry(reps = 5, rir = 0, weight = 200.0, sessionId = "s-n1"))

        val rirBasedState = LinearProgressionEngine(config(StallStrictness.RirBased))
            .diagnose(liftId, history)
        assertEquals(0, rirBasedState.currentStallCount, "RirBased: RIR not met → hold, not stall")

        val absoluteState = LinearProgressionEngine(config(StallStrictness.AbsoluteFailure))
            .diagnose(liftId, history)
        // AbsoluteFailure: completed bottom of range at any RIR beyond bottom = stall.
        assertTrue(
            absoluteState.currentStallCount > rirBasedState.currentStallCount,
            "AbsoluteFailure must report a stall where RirBased reports a hold",
        )
    }

    // --- RIR-adjusted rep recommendations (weight held → reps lever) ---

    @Test
    @DisplayName("S4-linear-top-set-reps-scale-to-rir-goal-when-weight-held")
    fun `S4-linear-top-set-reps-scale-to-rir-goal-when-weight-held`() {
        // Slot range 5–8, rirGoal=2. User did 200 × 6 @ RIR 3 (bottom met at RIR → would
        // increment). To force a HOLD on Linear the bottom must NOT be met at RIR: reps=6
        // ≥ repMin=5 but rir=1 < rirGoal=2 → hold. Failure capacity 6 + 1 = 7, target
        // 7 − 2 = 5, coerced into [5, 8] → 5.
        val slot = linearSlot(repMin = 5, repMax = 8, rirGoal = 2)
        val history = listOf(historyEntry(reps = 6, rir = 1, weight = 200.0, sessionId = "s-n1"))
        val recommendation = LinearProgressionEngine(config()).recommend(request(slot, history))
        assertEquals(lb(200.0), recommendation.recommendedWeight, "weight held (bottom not met at RIR)")
        assertEquals(5, recommendation.baselineExpectedReps)
    }

    @Test
    @DisplayName("S4-linear-top-set-reps-reset-to-repMin-on-increment")
    fun `S4-linear-top-set-reps-reset-to-repMin-on-increment`() {
        // Bottom met at RIR → increment fires → reps reset to repMin (no RIR scaling).
        val slot = linearSlot(repMin = 5, repMax = 8, rirGoal = 2)
        val history = listOf(historyEntry(reps = 7, rir = 3, weight = 200.0, sessionId = "s-n1"))
        val recommendation = LinearProgressionEngine(config()).recommend(request(slot, history))
        assertEquals(lb(205.0), recommendation.recommendedWeight)
        assertEquals(5, recommendation.baselineExpectedReps)
    }

    @Test
    @DisplayName("S4-linear-top-set-reps-fall-back-to-repMin-when-rir-absent")
    fun `S4-linear-top-set-reps-fall-back-to-repMin-when-rir-absent`() {
        // No RIR signal and weight held → fall back to repMin.
        val slot = linearSlot(repMin = 5, repMax = 8, rirGoal = 2)
        val history = listOf(historyEntry(reps = 6, rir = null, weight = 200.0, sessionId = "s-n1"))
        val recommendation = LinearProgressionEngine(config()).recommend(request(slot, history))
        assertEquals(lb(200.0), recommendation.recommendedWeight)
        assertEquals(5, recommendation.baselineExpectedReps)
    }

    @Test
    @DisplayName("S4-linear-backoff-reps-scale-with-rir-when-weight-held")
    fun `S4-linear-backoff-reps-scale-with-rir-when-weight-held`() {
        // Backoff rirGoal=2. User did 200 × 8 @ RIR 3 (failure capacity 11) → target 9.
        val slot = linearBackoffSlot(rirGoal = 2)
        val history = listOf(historyEntry(reps = 8, rir = 3, weight = 200.0, sessionId = "s-n1"))
        val recommendation = LinearProgressionEngine(config()).recommend(request(slot, history))
        assertEquals(lb(200.0), recommendation.recommendedWeight, "backoff inherits working weight")
        assertEquals(9, recommendation.baselineExpectedReps)
    }

    @Test
    @DisplayName("S4-linear-backoff-reps-floored-at-backoff-floor")
    fun `S4-linear-backoff-reps-floored-at-backoff-floor`() {
        // rirGoal=2. User did 200 × 5 @ RIR 1 (failure capacity 6), target 6 − 2 = 4.
        // Prefer weight drop so recommended reps can hit BACKOFF_REP_FLOOR (5).
        val slot = linearBackoffSlot(rirGoal = 2)
        val history = listOf(historyEntry(reps = 5, rir = 1, weight = 200.0, sessionId = "s-n1"))
        val recommendation = LinearProgressionEngine(config()).recommend(request(slot, history))
        assertTrue(
            recommendation.recommendedWeight.amount < 200.0,
            "must drop weight when capacity is below BACKOFF_REP_FLOOR",
        )
        assertEquals(5, recommendation.baselineExpectedReps)
    }

    @Test
    @DisplayName("S4-linear-straight-sets-and-fatigue-decay")
    fun `S4-linear-straight-sets-and-fatigue-decay`() {
        val engine = LinearProgressionEngine(config())
        val topSlot = linearSlot(repMin = 5, repMax = 8, rirGoal = 2)
        val boSlot = linearBackoffSlot(rirGoal = 2)

        // Previous session had varying weights:
        // Set 0 (top set): 200 x 5 @ 2 RIR (met bottom of range repMin=5 at rirGoal=2, so it progresses by +5 lb to 205)
        // Set 1 (backoff): 190 x 8 @ 2 RIR
        val topHistory = listOf(historyEntry(reps = 5, rir = 2, weight = 200.0, sessionId = "s-n1"))
        val boHistory = listOf(historyEntry(reps = 8, rir = 2, weight = 190.0, sessionId = "s-n2"))

        // Recommend for top set first:
        val topRec = engine.recommend(request(topSlot, topHistory))
        // Verify top set progressed to 205 lb and recommended reps is repMin = 5
        assertEquals(lb(205.0), topRec.recommendedWeight)
        assertEquals(5, topRec.baselineExpectedReps)

        // Recommend for backoff set passing top set recommended weight and prior slot info
        val boRec = engine.recommend(
            request(
                slot = boSlot,
                history = boHistory,
                currentTopSetWeight = topRec.recommendedWeight,
                priorSlotRecommendation = topRec,
                priorSlot = topSlot,
                perLiftSlope = -2.0,
                perLiftIntercept = 0.0,
            ),
        )

        // Verify backoff set inherits 205 lb (straight sets) instead of 190 lb
        assertEquals(lb(205.0), boRec.recommendedWeight)

        // Verify reps projected using top set's recommended X weight (205), Y reps (5) at Z RIR (2):
        // priorSetE1rm = calculate(205.0, 5 + 2) = calculate(205.0, 7) = 252.87
        // targetWeight = 205.0
        // repsForWeight(252.87, 205.0) -> 7 reps at 0 RIR.
        // projected capacity reps for next set (index 2 relative to index 1) = 7 + (-2.0 * 1) + 0 = 5.
        // minus boSlot.rirGoal (2) = 3.
        // coerced to at least BACKOFF_REP_FLOOR (5) = 5 reps.
        assertEquals(5, boRec.baselineExpectedReps)
    }

    // --- Reps going up across sets (capacity-ordered recent block) ---

    @Test
    @DisplayName("S4-linear-reps-rising-across-sets-normalized-to-monotonic")
    fun `S4-linear-reps-rising-across-sets-normalized-to-monotonic`() {
        // Same edge case as S2: 240×12 RIR 1, 240×13 RIR 1, 240×15 RIR 0. Capacity-descending
        // baseline feeds the engine 15/13/12 so recommendations come out monotonic.
        val engine = LinearProgressionEngine(config())
        val slot0 = linearSlot(repMin = 8, repMax = 16, rirGoal = 2).copy(position = 0)
        val slot1 = linearBackoffSlot(rirGoal = 1).copy(
            slotId = SetSlotId.Template(LiftSetId("ls-2")),
            repMin = 8, repMax = 16, position = 1,
        )
        val slot2 = linearBackoffSlot(rirGoal = 1).copy(
            slotId = SetSlotId.Template(LiftSetId("ls-3")),
            repMin = 8, repMax = 16, position = 2,
        )

        val hist0 = listOf(historyEntry(reps = 12, rir = 1, weight = 240.0, sessionId = "s-n1"))
        val hist1 = listOf(historyEntry(reps = 13, rir = 1, weight = 240.0, sessionId = "s-n1"))
        val hist2 = listOf(historyEntry(reps = 15, rir = 0, weight = 240.0, sessionId = "s-n1"))

        val rec0 = engine.recommend(request(slot0, hist0, baseline = ProgressionBaseline.FromRecent(hist2.first())))
        val rec1 = engine.recommend(
            request(
                slot = slot1,
                history = hist1,
                currentTopSetWeight = rec0.recommendedWeight,
                priorSlotRecommendation = rec0,
                priorSlot = slot0,
                baseline = ProgressionBaseline.FromRecent(hist1.first()),
            ),
        )
        val rec2 = engine.recommend(
            request(
                slot = slot2,
                history = hist2,
                currentTopSetWeight = rec0.recommendedWeight,
                priorSlotRecommendation = rec0,
                priorSlot = slot0,
                baseline = ProgressionBaseline.FromRecent(hist0.first()),
            ),
        )

        assertTrue(rec0.baselineExpectedReps >= rec1.baselineExpectedReps, "rec0 (${rec0.baselineExpectedReps}) >= rec1 (${rec1.baselineExpectedReps})")
        assertTrue(rec1.baselineExpectedReps >= rec2.baselineExpectedReps, "rec1 (${rec1.baselineExpectedReps}) >= rec2 (${rec2.baselineExpectedReps})")
    }
}
