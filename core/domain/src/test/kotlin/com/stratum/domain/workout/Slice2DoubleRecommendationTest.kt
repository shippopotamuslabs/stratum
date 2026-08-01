package com.stratum.domain.workout

import com.stratum.domain.workout.fatigue.PopulationFatigueModel
import com.stratum.domain.training.EstimatedOneRm
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
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
import com.stratum.domain.workout.progression.ProgressionBaseline
import com.stratum.domain.workout.progression.SlotProgressionRequest
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Slice 2 — Double progression + live recommendation. 14 scenarios (S2-*).
 */
@DisplayName("Slice 2: Double progression + live recommendation")
class Slice2DoubleRecommendationTest {

    private val slotId = SetSlotId.Template(LiftSetId("ls-1"))
    private val workoutLiftId = WorkoutLiftId("wl-1")
    private val liftId = LiftId("L-1")

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

    private fun backoffSlot() = SetSlot(
        slotId = SetSlotId.Template(LiftSetId("ls-2")),
        workoutLiftId = workoutLiftId,
        liftId = liftId,
        position = 1,
        setType = SetType.Standard,
        isTopSet = false,
        repMin = 0,
        repMax = 0,
        rirGoal = 2,
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
                exerciseNameRaw = "Squat",
                liftId = liftId,
                setIndex = 0,
                weight = lb(weight),
                reps = reps,
                rir = rir,
                completedAt = Instant.parse("2026-06-28T10:30:00Z"),
                liftContext = LoggedLiftContext(
                    liftName = "Squat",
                    movementPatternsBitmask = 1,
                    primaryMusclesBitmask = 1,
                    secondaryMusclesBitmask = 0,
                    fatigueProfile = FatigueProfile.Low,
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

    // --- Inverse e1RM (3 scenarios) ---

    @Test
    @DisplayName("S2-weight-for-reps-round-trip")
    fun `S2-weight-for-reps-round-trip`() {
        assertEquals(135.0, EstimatedOneRm.weightForReps(156.113, 5), 0.5)
        assertEquals(122.33, EstimatedOneRm.weightForReps(156.113, 8), 0.5)
        assertEquals(100.0, EstimatedOneRm.weightForReps(127.614, 8), 0.5)
    }

    @Test
    @DisplayName("S2-reps-for-weight-round-trip")
    fun `S2-reps-for-weight-round-trip`() {
        assertEquals(5, EstimatedOneRm.repsForWeight(156.113, 135.0))
        assertEquals(5, EstimatedOneRm.repsForWeight(114.852, 100.0))
    }

    @Test
    @DisplayName("S2-mutation-defense-weight-for-reps-not-identity")
    fun `S2-mutation-defense-weight-for-reps-not-identity`() {
        val result = EstimatedOneRm.weightForReps(156.113, 8)
        assertEquals(122.33, result, 0.5)
        assertTrue(result < 156.113, "weightForReps must NOT return the e1RM unmodified")
    }

    // --- Double progression engine (3 scenarios) ---

    @Test
    @DisplayName("S2-double-increment-when-top-of-range-at-rir")
    fun `S2-double-increment-when-top-of-range-at-rir`() {
        val slot = topSet(repMin = 4, repMax = 6, rirGoal = 2)
        val history = listOf(historyEntry(reps = 6, rir = 2, weight = 135.0))
        val recommendation = DoubleProgressionEngine().recommend(request(slot, history))
        assertTrue(
            recommendation.recommendedWeight.amount > 135.0,
            "must increment when failure capacity meets top-of-range at RIR goal",
        )
    }

    @Test
    @DisplayName("S2-double-increment-when-capacity-meets-top-with-excess-rir")
    fun `S2-double-increment-when-capacity-meets-top-with-excess-rir`() {
        // User case: repMax 12, rirGoal 2 → need capacity 14. Logged 11 @ RIR 5 → 16 ≥ 14.
        val slot = topSet(repMin = 8, repMax = 12, rirGoal = 2)
        val history = listOf(historyEntry(reps = 11, rir = 5, weight = 135.0))
        val recommendation = DoubleProgressionEngine().recommend(request(slot, history))
        assertEquals(lb(140.0), recommendation.recommendedWeight)
        // After increment, reps reset to minRecommendedReps = max(repMin, BACKOFF_REP_FLOOR) = 8.
        assertEquals(8, recommendation.baselineExpectedReps)
    }

    @Test
    @DisplayName("S2-double-hold-otherwise")
    fun `S2-double-hold-otherwise`() {
        val slot = topSet(repMin = 4, repMax = 6, rirGoal = 2)
        val history = listOf(historyEntry(reps = 5, rir = 1, weight = 135.0))
        val recommendation = DoubleProgressionEngine().recommend(request(slot, history))
        // Capacity 5 + 1 = 6 < goal 6 + 2 = 8 → hold load. At rirGoal → 4 < floor 5 → drop.
        assertTrue(
            recommendation.recommendedWeight.amount < 135.0,
            "must drop weight when capacity would prescribe sub-floor reps",
        )
        assertEquals(5, recommendation.baselineExpectedReps)
    }

    @Test
    @DisplayName("S2-double-hold-reps-scale-to-rir-goal-when-weight-held")
    fun `S2-double-hold-reps-scale-to-rir-goal-when-weight-held`() {
        // Slot RIR goal 1, range 8–12. Need capacity 13. User did 120 × 7 @ RIR 5 (12 < 13).
        // Weight held; reps scale to 12 − 1 = 11 instead of 7.
        val slot = topSet(repMin = 8, repMax = 12, rirGoal = 1)
        val history = listOf(historyEntry(reps = 7, rir = 5, weight = 120.0))
        val recommendation = DoubleProgressionEngine().recommend(request(slot, history))
        assertEquals(lb(120.0), recommendation.recommendedWeight, "weight held (capacity short of top)")
        assertEquals(11, recommendation.baselineExpectedReps)
    }

    @Test
    @DisplayName("S2-double-increment-when-failure-capacity-meets-top-goal")
    fun `S2-double-increment-when-failure-capacity-meets-top-goal`() {
        // reps=5 < repMax=6 but capacity 5 + 5 = 10 ≥ goal 6 + 2 = 8 → advance load.
        val slot = topSet(repMin = 4, repMax = 6, rirGoal = 2)
        val history = listOf(historyEntry(reps = 5, rir = 5, weight = 135.0))
        val recommendation = DoubleProgressionEngine().recommend(request(slot, history))
        assertEquals(lb(140.0), recommendation.recommendedWeight)
        assertEquals(5, recommendation.baselineExpectedReps)
    }

    @Test
    @DisplayName("S2-double-hold-falls-back-to-previous-reps-when-rir-absent")
    fun `S2-double-hold-falls-back-to-previous-reps-when-rir-absent`() {
        val slot = topSet(repMin = 4, repMax = 6, rirGoal = 2)
        val history = listOf(historyEntry(reps = 5, rir = null, weight = 135.0))
        val recommendation = DoubleProgressionEngine().recommend(request(slot, history))
        assertEquals(lb(135.0), recommendation.recommendedWeight)
        // No RIR signal → no capacity gate; naive previous-reps repeat (coerced into range).
        assertEquals(5, recommendation.baselineExpectedReps)
    }

    @Test
    @DisplayName("S2-double-increment-when-extra-reps-offset-low-rir")
    fun `S2-double-increment-when-extra-reps-offset-low-rir`() {
        // 7 @ RIR 1 → capacity 8 ≥ goal 6 + 2 = 8 (extra rep offsets RIR shortfall).
        val slot = topSet(repMin = 4, repMax = 6, rirGoal = 2)
        val history = listOf(historyEntry(reps = 7, rir = 1, weight = 135.0))
        val recommendation = DoubleProgressionEngine().recommend(request(slot, history))
        assertEquals(lb(140.0), recommendation.recommendedWeight)
        assertEquals(5, recommendation.baselineExpectedReps)
    }

    @Test
    @DisplayName("S2-double-backoff-inherits-top-set-weight")
    fun `S2-double-backoff-inherits-top-set-weight`() {
        val slot = backoffSlot()
        val history = listOf(historyEntry(reps = 6, rir = 2, weight = 135.0))
        val recommendation = DoubleProgressionEngine().recommend(request(slot, history))
        assertEquals(lb(135.0), recommendation.recommendedWeight)
    }

    @Test
    @DisplayName("S2-double-backoff-reps-scale-with-rir-when-weight-held")
    fun `S2-double-backoff-reps-scale-with-rir-when-weight-held`() {
        // Backoff slot rirGoal=2. User did 135 × 8 @ RIR 3 (failure capacity 11).
        // Weight is always held for backoffs → reps scale to 11 − 2 = 9.
        val slot = backoffSlot().copy(rirGoal = 2)
        val history = listOf(historyEntry(reps = 8, rir = 3, weight = 135.0))
        val recommendation = DoubleProgressionEngine().recommend(request(slot, history))
        assertEquals(lb(135.0), recommendation.recommendedWeight)
        assertEquals(9, recommendation.baselineExpectedReps)
    }

    @Test
    @DisplayName("S2-double-backoff-reps-floored-at-backoff-floor")
    fun `S2-double-backoff-reps-floored-at-backoff-floor`() {
        // rirGoal=2. User did 135 × 5 @ RIR 1 (failure capacity 6), target 6 − 2 = 4.
        // Global floor is 5 — drop weight so 5@2 is achievable rather than holding 135@4.
        val slot = backoffSlot().copy(rirGoal = 2)
        val history = listOf(historyEntry(reps = 5, rir = 1, weight = 135.0))
        val recommendation = DoubleProgressionEngine().recommend(request(slot, history))
        assertTrue(
            recommendation.recommendedWeight.amount < 135.0,
            "must drop weight when capacity is below BACKOFF_REP_FLOOR",
        )
        assertEquals(5, recommendation.baselineExpectedReps)
    }

    // --- Recommendation engine (6 scenarios) ---

    @Test
    @DisplayName("S2-rec-top-set-hold")
    fun `S2-rec-top-set-hold`() {
        // Enough headroom that FatigueModel last set (index 2 when rem=1) still ≥ floor 5 @ RIR 2.
        val lastPerformed = LoggedSetValue(slotId, lb(135.0), 10, 2)
        val target = topSet(repMin = 4, repMax = 12, rirGoal = 2)
        val outcome = RecommendationEngine(PopulationFatigueModel()).recommendNextSet(lastPerformed, null, target, lb(135.0))
        assertEquals(RecommendationOutcome.Hold(lb(135.0)), outcome)
    }

    @Test
    @DisplayName("S2-rec-top-set-reduce")
    fun `S2-rec-top-set-reduce`() {
        val lastPerformed = LoggedSetValue(slotId, lb(135.0), 4, 0)
        val target = topSet(repMin = 6, repMax = 8, rirGoal = 2)
        val outcome = RecommendationEngine(PopulationFatigueModel()).recommendNextSet(lastPerformed, null, target, lb(135.0))
        assertNotNull(outcome)
        assertTrue(outcome is RecommendationOutcome.Reduce, "must Reduce when last-set capacity < BACKOFF_REP_FLOOR")
        val reduce = outcome as RecommendationOutcome.Reduce
        // rem=1 → lastSetIndex=2; capacity-oracle continuous best ≈116.6.
        assertEquals(116.6, reduce.weight.amount, 1.5)
    }

    @Test
    @DisplayName("S2-rec-backoff-hold")
    fun `S2-rec-backoff-hold`() {
        val backoffId = SetSlotId.Template(LiftSetId("ls-2"))
        val lastPerformed = LoggedSetValue(backoffId, lb(100.0), 12, 2)
        val target = backoffSlot()
        val outcome = RecommendationEngine(PopulationFatigueModel()).recommendNextSet(lastPerformed, null, target, lb(100.0))
        assertEquals(RecommendationOutcome.Hold(lb(100.0)), outcome)
    }

    @Test
    @DisplayName("S2-rec-backoff-reduce-to-floor")
    fun `S2-rec-backoff-reduce-to-floor`() {
        val backoffId = SetSlotId.Template(LiftSetId("ls-2"))
        val lastPerformed = LoggedSetValue(backoffId, lb(100.0), 5, 0)
        val target = backoffSlot()
        val outcome = RecommendationEngine(PopulationFatigueModel()).recommendNextSet(lastPerformed, null, target, lb(100.0))
        assertTrue(outcome is RecommendationOutcome.Reduce, "must Reduce when projected < BACKOFF_REP_FLOOR")
        val reduce = outcome as RecommendationOutcome.Reduce
        // rem=1 → lastSetIndex=2; capacity-oracle continuous best ≈89.1.
        assertEquals(89.1, reduce.weight.amount, 1.5)
    }

    @Test
    @DisplayName("S2-rec-null-when-no-performed-set")
    fun `S2-rec-null-when-no-performed-set`() {
        // The overlay-only engine returns null when no performed set exists for the lift
        // (the upcoming set is the first set of the session — no e1RM can be derived).
        // lastPerformed is nullable on the engine signature; null is passed directly.
        val target = topSet()
        val outcome = RecommendationEngine(PopulationFatigueModel()).recommendNextSet(lastPerformed = null, null, target, lb(135.0))
        assertNull(outcome)
    }

    @Test
    @DisplayName("S2-mutation-defense-always-hold")
    fun `S2-mutation-defense-always-hold`() {
        val lastPerformed = LoggedSetValue(slotId, lb(135.0), 4, 0)
        val target = topSet(repMin = 6, repMax = 8, rirGoal = 2)
        val outcome = RecommendationEngine(PopulationFatigueModel()).recommendNextSet(lastPerformed, null, target, lb(135.0))
        assertTrue(outcome is RecommendationOutcome.Reduce, "engine must NOT always return Hold")
    }

    // --- Merge priority (2 scenarios) ---

    @Test
    @DisplayName("S2-merge-logged-supersedes-recommendation")
    fun `S2-merge-logged-supersedes-recommendation`() {
        val structure = WorkoutStructureProjection(
            workoutId = com.stratum.domain.program.identity.WorkoutId("w-1"),
            workoutName = "Workout 1",
            lifts = listOf(
                WorkoutLiftProjection(
                    workoutLiftId = workoutLiftId,
                    liftId = liftId,
                    liftName = "Squat",
                    progressionScheme = com.stratum.domain.program.ProgressionScheme.Double,
                    slots = listOf(topSet()),
                ),
            ),
        )
        val snapshot = ProgressionSnapshot(
            workoutId = structure.workoutId,
            bySlot = mapOf(slotId to SlotRecommendation(slotId, lb(140.0), 5)),
        )
        val overlay = WorkoutOverlay(
            loggedSets = mapOf(slotId to LoggedSetValue(slotId, lb(130.0), 6, 2)),
            recommendations = mapOf(slotId to LiveSetRecommendation(lb(135.0), 5)),
        )
        val rendered = MergeRenderedWorkoutUseCase()(structure, snapshot, overlay)
        val set = rendered.lifts.first().sets.first()
        assertEquals(lb(130.0), set.loggedValue?.weight)
        // snapshot baseline preserved for the recommendedWeight display field
        assertEquals(lb(140.0), set.recommendedWeight)
    }

    @Test
    @DisplayName("S2-merge-recommendation-supersedes-snapshot-upcoming")
    fun `S2-merge-recommendation-supersedes-snapshot-upcoming`() {
        val ls2 = SetSlotId.Template(LiftSetId("ls-2"))
        val structure = WorkoutStructureProjection(
            workoutId = com.stratum.domain.program.identity.WorkoutId("w-1"),
            workoutName = "Workout 1",
            lifts = listOf(
                WorkoutLiftProjection(
                    workoutLiftId = workoutLiftId,
                    liftId = liftId,
                    liftName = "Squat",
                    progressionScheme = com.stratum.domain.program.ProgressionScheme.Double,
                    slots = listOf(backoffSlot().copy(slotId = ls2)),
                ),
            ),
        )
        val snapshot = ProgressionSnapshot(
            workoutId = structure.workoutId,
            bySlot = mapOf(ls2 to SlotRecommendation(ls2, lb(135.0), 5)),
        )
        val overlay = WorkoutOverlay(recommendations = mapOf(ls2 to LiveSetRecommendation(lb(120.0), 5)))
        val rendered = MergeRenderedWorkoutUseCase()(structure, snapshot, overlay)
        val set = rendered.lifts.first().sets.first()
        assertNull(set.loggedValue)
        assertEquals(lb(120.0), set.recommendedWeight)
    }

    @Test
    @DisplayName("S2-double-straight-sets-and-fatigue-decay")
    fun `S2-double-straight-sets-and-fatigue-decay`() {
        val engine = DoubleProgressionEngine()
        val topSlot = topSet(repMin = 4, repMax = 6, rirGoal = 2)
        val boSlot = backoffSlot().copy(rirGoal = 2)

        // Previous session had varying weights:
        // Set 0 (top set): 120 x 7 @ 2 RIR (met top of range repMax=6 at rirGoal=2, so it progresses by +5 lb to 125)
        // Set 1 (backoff): 110 x 8 @ 2 RIR
        val topHistory = listOf(historyEntry(reps = 7, rir = 2, weight = 120.0))
        val boHistory = listOf(historyEntry(reps = 8, rir = 2, weight = 110.0))

        // Recommend for top set first:
        val topRec = engine.recommend(request(topSlot, topHistory))
        // Verify top set progressed to 125 lb; rep reset uses global floor max(repMin, 5) = 5.
        assertEquals(lb(125.0), topRec.recommendedWeight)
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

        // Verify backoff set inherits 125 lb (straight sets) instead of 110 lb
        assertEquals(lb(125.0), boRec.recommendedWeight)

        // Verify reps projected using top set's recommended X weight (125), Y reps (4) at Z RIR (2):
        // priorSetE1rm = calculate(125.0, 4 + 2) = calculate(125.0, 6) = 152.76
        // targetWeight = 125.0
        // repsForWeight(152.76, 125.0) -> 6 reps at 0 RIR.
        // projected capacity reps for next set (index 2 relative to index 1) = 6 + (-2.0 * 1) + 0 = 4.
        // minus boSlot.rirGoal (2) = 2.
        // coerced to at least BACKOFF_REP_FLOOR (5) = 5 reps.
        assertEquals(5, boRec.baselineExpectedReps)
    }

    @Test
    @DisplayName("S2-hamstring-curl-debug")
    fun `S2-hamstring-curl-debug`() {
        val engine = DoubleProgressionEngine()
        val slot1 = topSet(repMin = 8, repMax = 30, rirGoal = 2)
        val slot2 = backoffSlot().copy(slotId = SetSlotId.Template(LiftSetId("ls-2")), repMin = 8, repMax = 30, rirGoal = 1, position = 1)
        val slot3 = backoffSlot().copy(slotId = SetSlotId.Template(LiftSetId("ls-3")), repMin = 8, repMax = 30, rirGoal = 1, position = 2)

        val hist1 = listOf(historyEntry(reps = 10, rir = 1, weight = 120.0))
        val hist2 = listOf(historyEntry(reps = 7, rir = 0, weight = 120.0))
        val hist3 = listOf(historyEntry(reps = 8, rir = null, weight = 115.0))

        val rec1 = engine.recommend(request(slot1, hist1))
        assertTrue(rec1.isProjected)

        val rec2 = engine.recommend(request(slot2, hist2, currentTopSetWeight = rec1.recommendedWeight))
        assertTrue(rec2.isProjected)

        // Sequential prior (slot2), one fatigue step — not re-anchor to top with position-based index.
        val rec3Normal = engine.recommend(
            request(
                slot = slot3,
                history = hist3,
                currentTopSetWeight = rec1.recommendedWeight,
                priorSlotRecommendation = rec2,
                priorSlot = slot2,
                perLiftSlope = -2.0,
                perLiftIntercept = 0.0,
            ),
        )
        // prior rec2 at 120: failure capacity from rec2 reps + rirGoal 1; one step (index 2) at slope -2.
        assertTrue(rec3Normal.baselineExpectedReps >= 5)
        assertTrue(
            rec2.baselineExpectedReps >= rec3Normal.baselineExpectedReps,
            "sequential prior must not produce positively-sloped reps",
        )
        assertTrue(rec3Normal.isProjected)

        // Positive slope clamped to 0: one step from prior still cannot increase capacity.
        val rec3Positive = engine.recommend(
            request(
                slot = slot3,
                history = hist3,
                currentTopSetWeight = rec1.recommendedWeight,
                priorSlotRecommendation = rec2,
                priorSlot = slot2,
                perLiftSlope = 25.0,
                perLiftIntercept = 0.0,
            ),
        )
        assertTrue(rec3Positive.baselineExpectedReps >= 5)
        assertTrue(rec2.baselineExpectedReps >= rec3Positive.baselineExpectedReps)
        assertTrue(rec3Positive.isProjected)

        // ProjectFromPrior with sequential prior → one step, still projected + ≥ floor
        val slot4 = backoffSlot().copy(slotId = SetSlotId.Template(LiftSetId("ls-4")), repMin = 8, repMax = 30, rirGoal = 1, position = 3)
        val rec4 = engine.recommend(
            request(
                slot = slot4,
                history = emptyList(),
                currentTopSetWeight = rec1.recommendedWeight,
                priorSlotRecommendation = rec3Normal,
                priorSlot = slot3,
                perLiftSlope = -2.0,
                perLiftIntercept = 0.0,
                baseline = ProgressionBaseline.ProjectFromPrior,
            ),
        )
        assertTrue(rec4.baselineExpectedReps >= 5)
        assertTrue(rec3Normal.baselineExpectedReps >= rec4.baselineExpectedReps)
        assertTrue(rec4.isProjected)
    }

    // --- Gate matrix + cold start ---

    @Test
    @DisplayName("S2-double-gate-top-met-rir-missed-holds")
    fun `S2-double-gate-top-met-rir-missed-holds`() {
        // Hit repMax but undershot RIR — classic "ground out the last rep" log. Must NOT increment.
        // Capacity 6@0 → 4@2 RIR is below the global floor of 5, so weight drops via
        // EstimatedOneRm.weightForReps to hit floor reps exactly.
        val slot = topSet(repMin = 4, repMax = 6, rirGoal = 2)
        val history = listOf(historyEntry(reps = 6, rir = 0, weight = 135.0))
        val capacity = 6 // 6 + 0 RIR
        val e1rm = EstimatedOneRm.calculate(135.0, capacity)
        val expectedWeight = EstimatedOneRm.weightForReps(e1rm, 5 + slot.rirGoal)
        val recommendation = DoubleProgressionEngine().recommend(request(slot, history))
        assertEquals(expectedWeight, recommendation.recommendedWeight.amount)
        assertEquals(5, recommendation.baselineExpectedReps)
        assertTrue(recommendation.recommendedWeight.amount < 135.0)
    }

    @Test
    @DisplayName("S2-double-gate-capacity-exactly-meets-top-goal-increments")
    fun `S2-double-gate-capacity-exactly-meets-top-goal-increments`() {
        // 5 @ RIR 3 → capacity 8 ≥ goal 6 + 2 = 8 → load advances (not hold).
        val slot = topSet(repMin = 4, repMax = 6, rirGoal = 2)
        val history = listOf(historyEntry(reps = 5, rir = 3, weight = 135.0))
        val recommendation = DoubleProgressionEngine().recommend(request(slot, history))
        assertEquals(lb(140.0), recommendation.recommendedWeight)
        assertEquals(5, recommendation.baselineExpectedReps)
    }

    @Test
    @DisplayName("S2-double-gate-capacity-one-short-holds-and-scales-reps")
    fun `S2-double-gate-capacity-one-short-holds-and-scales-reps`() {
        // 5 @ RIR 2 → capacity 7 < goal 8 → hold; scale to 7 − 2 = 5.
        val slot = topSet(repMin = 4, repMax = 6, rirGoal = 2)
        val history = listOf(historyEntry(reps = 5, rir = 2, weight = 135.0))
        val recommendation = DoubleProgressionEngine().recommend(request(slot, history))
        assertEquals(lb(135.0), recommendation.recommendedWeight)
        assertEquals(5, recommendation.baselineExpectedReps)
    }

    @Test
    @DisplayName("S2-double-gate-exact-top-and-rir-increments-and-resets-reps")
    fun `S2-double-gate-exact-top-and-rir-increments-and-resets-reps`() {
        val slot = topSet(repMin = 4, repMax = 6, rirGoal = 2)
        val history = listOf(historyEntry(reps = 6, rir = 2, weight = 135.0))
        val recommendation = DoubleProgressionEngine().recommend(request(slot, history))
        assertEquals(lb(140.0), recommendation.recommendedWeight)
        // After increment, reps reset to the global floor max(repMin, BACKOFF_REP_FLOOR) = 5.
        assertEquals(5, recommendation.baselineExpectedReps)
    }

    @Test
    @DisplayName("S2-double-cold-start-uses-fallback-one-rm")
    fun `S2-double-cold-start-uses-fallback-one-rm`() {
        val slot = topSet(repMin = 4, repMax = 6, rirGoal = 2)
        val recommendation = DoubleProgressionEngine().recommend(
            request(slot, emptyList(), fallbackOneRm = lb(100.0), baseline = null),
        )
        assertEquals(lb(100.0), recommendation.recommendedWeight)
        assertEquals(5, recommendation.baselineExpectedReps)
    }

    @Test
    @DisplayName("S2-double-sandbagged-rir-inflates-rep-target-when-weight-held")
    fun `S2-double-sandbagged-rir-inflates-rep-target-when-weight-held`() {
        // User logs high RIR (overestimates remaining capacity). Weight held → next session
        // targets inflated failure capacity. Documents self-report trust, not a guard.
        val slot = topSet(repMin = 8, repMax = 12, rirGoal = 2)
        val history = listOf(historyEntry(reps = 8, rir = 5, weight = 100.0))
        val recommendation = DoubleProgressionEngine().recommend(request(slot, history))
        assertEquals(lb(100.0), recommendation.recommendedWeight)
        // 8+5-2 = 11
        assertEquals(11, recommendation.baselineExpectedReps)
    }

    // --- Reps going up across sets (capacity-ordered recent block) ---

    @Test
    @DisplayName("S2-reps-rising-across-sets-normalized-to-monotonic")
    fun `S2-reps-rising-across-sets-normalized-to-monotonic`() {
        // The exact edge case: 240×12 RIR 1, 240×13 RIR 1, 240×15 RIR 0 (same weight,
        // reps rising across sets — earlier-set RIR was misreported). Capacity-descending
        // baseline feeds the engine 15/13/12, so recommendations come out monotonic.
        val engine = DoubleProgressionEngine()
        val slot0 = topSet(repMin = 8, repMax = 16, rirGoal = 2).copy(position = 0)
        val slot1 = backoffSlot().copy(slotId = SetSlotId.Template(LiftSetId("ls-2")), repMin = 8, repMax = 16, rirGoal = 1, position = 1)
        val slot2 = backoffSlot().copy(slotId = SetSlotId.Template(LiftSetId("ls-3")), repMin = 8, repMax = 16, rirGoal = 1, position = 2)

        // Positional history (UI display): slot 0 = 12@1, slot 1 = 13@1, slot 2 = 15@0
        val hist0 = listOf(historyEntry(reps = 12, rir = 1, weight = 240.0))
        val hist1 = listOf(historyEntry(reps = 13, rir = 1, weight = 240.0))
        val hist2 = listOf(historyEntry(reps = 15, rir = 0, weight = 240.0))

        // Capacity-descending baselines: slot 0 ← 15@0 (cap 15), slot 1 ← 13@1 (cap 14), slot 2 ← 12@1 (cap 13)
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

        // Recommendations must be monotonically non-increasing across slots.
        assertTrue(rec0.baselineExpectedReps >= rec1.baselineExpectedReps, "rec0 (${rec0.baselineExpectedReps}) >= rec1 (${rec1.baselineExpectedReps})")
        assertTrue(rec1.baselineExpectedReps >= rec2.baselineExpectedReps, "rec1 (${rec1.baselineExpectedReps}) >= rec2 (${rec2.baselineExpectedReps})")
        // UI previous* stays honest to positional actuals
        assertEquals(12, rec0.previousReps)
        assertEquals(1, rec0.previousRir)
        assertEquals(13, rec1.previousReps)
        assertEquals(1, rec1.previousRir)
        assertEquals(15, rec2.previousReps)
        assertEquals(0, rec2.previousRir)
    }

    // --- Sequential prior (engine-level; orchestrator mirrors this wiring) ---

    @Test
    @DisplayName("S2-sequential-prior-project-from-prior-one-step-no-positive-slope")
    fun `S2-sequential-prior-project-from-prior-one-step-no-positive-slope`() {
        val engine = DoubleProgressionEngine()
        val slot0 = topSet(repMin = 8, repMax = 16, rirGoal = 2).copy(position = 0)
        val slot1 = backoffSlot().copy(
            slotId = SetSlotId.Template(LiftSetId("ls-2")),
            repMin = 8,
            repMax = 16,
            rirGoal = 1,
            position = 1,
        )
        val slot2 = backoffSlot().copy(
            slotId = SetSlotId.Template(LiftSetId("ls-3")),
            repMin = 8,
            repMax = 16,
            rirGoal = 1,
            position = 2,
        )
        val hist0 = listOf(historyEntry(reps = 15, rir = 1, weight = 15.0))
        // Slot 1/2 have no recent baseline → ProjectFromPrior with sequential priors
        val rec0 = engine.recommend(request(slot0, hist0))
        val rec1 = engine.recommend(
            request(
                slot = slot1,
                history = emptyList(),
                currentTopSetWeight = rec0.recommendedWeight,
                priorSlotRecommendation = rec0,
                priorSlot = slot0,
                perLiftSlope = -2.0,
                perLiftIntercept = 0.0,
                baseline = ProgressionBaseline.ProjectFromPrior,
            ),
        )
        val rec2 = engine.recommend(
            request(
                slot = slot2,
                history = emptyList(),
                currentTopSetWeight = rec0.recommendedWeight,
                priorSlotRecommendation = rec1,
                priorSlot = slot1,
                perLiftSlope = -2.0,
                perLiftIntercept = 0.0,
                baseline = ProgressionBaseline.ProjectFromPrior,
            ),
        )
        assertTrue(rec0.baselineExpectedReps >= rec1.baselineExpectedReps)
        assertTrue(rec1.baselineExpectedReps >= rec2.baselineExpectedReps)
        assertTrue(rec1.isProjected)
        assertTrue(rec2.isProjected)
        assertTrue(rec1.baselineExpectedReps >= 5)
        assertTrue(rec2.baselineExpectedReps >= 5)
    }

    @Test
    @DisplayName("S2-reanchor-to-top-prior-on-deep-slot-is-not-required-sequential-wins")
    fun `S2-sequential-prior-beats-top-reanchor-for-deep-slots`() {
        // After a hard mid-block set (6 reps), projecting set 3 from set 2 must not bounce
        // above set 2's rec the way re-anchoring to top (15 reps) would.
        val engine = DoubleProgressionEngine()
        val slot0 = topSet(repMin = 8, repMax = 16, rirGoal = 2).copy(position = 0)
        val slot1 = backoffSlot().copy(
            slotId = SetSlotId.Template(LiftSetId("ls-2")),
            repMin = 8,
            repMax = 16,
            rirGoal = 1,
            position = 1,
        )
        val slot2 = backoffSlot().copy(
            slotId = SetSlotId.Template(LiftSetId("ls-3")),
            repMin = 8,
            repMax = 16,
            rirGoal = 1,
            position = 2,
        )
        val hist0 = listOf(historyEntry(reps = 15, rir = 1, weight = 15.0))
        val hist1 = listOf(historyEntry(reps = 6, rir = 1, weight = 15.0))
        val rec0 = engine.recommend(request(slot0, hist0))
        val rec1 = engine.recommend(
            request(
                slot = slot1,
                history = hist1,
                currentTopSetWeight = rec0.recommendedWeight,
                priorSlotRecommendation = rec0,
                priorSlot = slot0,
            ),
        )
        val fromSequential = engine.recommend(
            request(
                slot = slot2,
                history = emptyList(),
                currentTopSetWeight = rec0.recommendedWeight,
                priorSlotRecommendation = rec1,
                priorSlot = slot1,
                perLiftSlope = -2.0,
                perLiftIntercept = 0.0,
                baseline = ProgressionBaseline.ProjectFromPrior,
            ),
        )
        val fromTopReanchor = engine.recommend(
            request(
                slot = slot2,
                history = emptyList(),
                currentTopSetWeight = rec0.recommendedWeight,
                priorSlotRecommendation = rec0,
                priorSlot = slot0,
                perLiftSlope = -2.0,
                perLiftIntercept = 0.0,
                baseline = ProgressionBaseline.ProjectFromPrior,
            ),
        )
        assertTrue(
            fromSequential.baselineExpectedReps <= rec1.baselineExpectedReps,
            "sequential must not rise above prior slot",
        )
        // Document that top-reanchor can be higher — sequential is the correct product path
        assertTrue(
            fromSequential.baselineExpectedReps <= fromTopReanchor.baselineExpectedReps,
            "sequential (${fromSequential.baselineExpectedReps}) should be ≤ top-reanchor (${fromTopReanchor.baselineExpectedReps})",
        )
    }
}
