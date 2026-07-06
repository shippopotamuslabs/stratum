package com.stratum.domain.workout

import com.stratum.domain.importing.EstimatedOneRm
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.KinematicProfile
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
                    kinematicProfile = KinematicProfile.Isolation,
                    muscleLengthBias = MuscleLengthBias.MidRange,
                ),
            ),
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
        val recommendation = DoubleProgressionEngine()
            .recommend(SlotProgressionRequest(slot, history, fallbackOneRm = null))
        assertTrue(
            recommendation.recommendedWeight.amount > 135.0,
            "must increment when TOP of range met at RIR",
        )
    }

    @Test
    @DisplayName("S2-double-hold-otherwise")
    fun `S2-double-hold-otherwise`() {
        val slot = topSet(repMin = 4, repMax = 6, rirGoal = 2)
        val history = listOf(historyEntry(reps = 5, rir = 1, weight = 135.0))
        val recommendation = DoubleProgressionEngine()
            .recommend(SlotProgressionRequest(slot, history, fallbackOneRm = null))
        assertEquals(lb(135.0), recommendation.recommendedWeight)
    }

    @Test
    @DisplayName("S2-double-backoff-inherits-top-set-weight")
    fun `S2-double-backoff-inherits-top-set-weight`() {
        val slot = backoffSlot()
        val history = listOf(historyEntry(reps = 6, rir = 2, weight = 135.0))
        val recommendation = DoubleProgressionEngine()
            .recommend(SlotProgressionRequest(slot, history, fallbackOneRm = null))
        assertEquals(lb(135.0), recommendation.recommendedWeight)
    }

    // --- Recommendation engine (6 scenarios) ---

    @Test
    @DisplayName("S2-rec-top-set-hold")
    fun `S2-rec-top-set-hold`() {
        val lastPerformed = LoggedSetValue(slotId, lb(135.0), 5, 2)
        val target = topSet(repMin = 4, repMax = 6, rirGoal = 2)
        val outcome = RecommendationEngine().recommendNextSet(lastPerformed, null, target, lb(135.0))
        assertEquals(RecommendationOutcome.Hold(lb(135.0)), outcome)
    }

    @Test
    @DisplayName("S2-rec-top-set-reduce")
    fun `S2-rec-top-set-reduce`() {
        val lastPerformed = LoggedSetValue(slotId, lb(135.0), 4, 0)
        val target = topSet(repMin = 6, repMax = 8, rirGoal = 2)
        val outcome = RecommendationEngine().recommendNextSet(lastPerformed, null, target, lb(135.0))
        assertNotNull(outcome)
        assertTrue(outcome is RecommendationOutcome.Reduce, "must Reduce when projected < repMin")
        val reduce = outcome as RecommendationOutcome.Reduce
        // Brzycki inverse derivation (spec-mandated formula, EstimatedOneRm constants):
        //   e1rm = calculate(135.0, 4+0) = 135.0 * 1.0278 / (1.0278 - 0.0278*4)
        //        = 138.753 / 0.9166 = 151.385
        //   weightForReps(151.385, repMin=6) = 151.385 * (1.0278 - 0.0278*6) / 1.0278
        //        = 151.385 * 0.8610 / 1.0278 = 126.81
        // (Epley inverse for reference would be 134.47 — neither formula yields 120.6,
        // which was a pre-Implementation arithmetic error in the Gherkin expected literal.)
        assertEquals(126.81, reduce.weight.amount, 0.5)
    }

    @Test
    @DisplayName("S2-rec-backoff-hold")
    fun `S2-rec-backoff-hold`() {
        val backoffId = SetSlotId.Template(LiftSetId("ls-2"))
        val lastPerformed = LoggedSetValue(backoffId, lb(100.0), 8, 2)
        val target = backoffSlot()
        val outcome = RecommendationEngine().recommendNextSet(lastPerformed, null, target, lb(100.0))
        assertEquals(RecommendationOutcome.Hold(lb(100.0)), outcome)
    }

    @Test
    @DisplayName("S2-rec-backoff-reduce-to-floor")
    fun `S2-rec-backoff-reduce-to-floor`() {
        val backoffId = SetSlotId.Template(LiftSetId("ls-2"))
        val lastPerformed = LoggedSetValue(backoffId, lb(100.0), 5, 0)
        val target = backoffSlot()
        val outcome = RecommendationEngine().recommendNextSet(lastPerformed, null, target, lb(100.0))
        assertTrue(outcome is RecommendationOutcome.Reduce, "must Reduce when projected < BACKOFF_REP_FLOOR")
        val reduce = outcome as RecommendationOutcome.Reduce
        // weightForReps(115.64, 5) ≈ 99.97
        assertEquals(99.97, reduce.weight.amount, 0.5)
    }

    @Test
    @DisplayName("S2-rec-null-when-no-performed-set")
    fun `S2-rec-null-when-no-performed-set`() {
        // The overlay-only engine returns null when no performed set exists for the lift
        // (the upcoming set is the first set of the session — no e1RM can be derived).
        // lastPerformed is nullable on the engine signature; null is passed directly.
        val target = topSet()
        val outcome = RecommendationEngine().recommendNextSet(lastPerformed = null, null, target, lb(135.0))
        assertNull(outcome)
    }

    @Test
    @DisplayName("S2-mutation-defense-always-hold")
    fun `S2-mutation-defense-always-hold`() {
        val lastPerformed = LoggedSetValue(slotId, lb(135.0), 4, 0)
        val target = topSet(repMin = 6, repMax = 8, rirGoal = 2)
        val outcome = RecommendationEngine().recommendNextSet(lastPerformed, null, target, lb(135.0))
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
            recommendations = mapOf(slotId to lb(135.0)),
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
        val overlay = WorkoutOverlay(recommendations = mapOf(ls2 to lb(120.0)))
        val rendered = MergeRenderedWorkoutUseCase()(structure, snapshot, overlay)
        val set = rendered.lifts.first().sets.first()
        assertNull(set.loggedValue)
        assertEquals(lb(120.0), set.recommendedWeight)
    }
}
