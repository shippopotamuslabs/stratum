package com.stratum.domain.workout.progression

import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.EstimatedOneRm
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.workout.BACKOFF_REP_FLOOR
import com.stratum.domain.workout.SetSlot
import com.stratum.domain.workout.SetSlotId
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.workout.SlotHistoryEntry

/**
 * Pins [projectBaselineAtTargetWeight]: after shared-weight look-ahead drops load below
 * history, reproject this slot's real capacity at the new load (no multi-set fatigue).
 */
@DisplayName("projectBaselineAtTargetWeight after shared reduce")
class ProjectBaselineAtTargetWeightTest {

    private val liftId = LiftId("L-1")
    private val workoutLiftId = WorkoutLiftId("wl-1")

    private fun lb(amount: Double) = Weight(amount, Weight.WeightUnit.Lb)

    private fun slot(position: Int = 0, rirGoal: Int = 1) = SetSlot(
        slotId = SetSlotId.Template(LiftSetId("ls-$position")),
        workoutLiftId = workoutLiftId,
        liftId = liftId,
        position = position,
        setType = SetType.Standard,
        isTopSet = position == 0,
        repMin = 8,
        repMax = 16,
        rirGoal = rirGoal,
        restSeconds = 90,
    )

    private fun historyEntry(reps: Int, rir: Int?, weight: Double): SlotHistoryEntry =
        SlotHistoryEntry(
            session = TrainingSession(
                id = TrainingSessionId("s1"),
                programId = ProgramId("p-1"),
                workoutId = WorkoutId("w-1"),
                status = TrainingSessionStatus.COMPLETED,
                kind = TrainingSessionKind.PROGRAM_WORKOUT,
                origin = TrainingSessionOrigin.IMPORT,
                cycleNumberSnapshot = 1,
                weekdaySnapshot = null,
                startedAt = Instant.parse("2026-07-01T10:00:00Z"),
                endedAt = Instant.parse("2026-07-01T11:00:00Z"),
                sourceApp = null,
            ),
            loggedSet = LoggedSet(
                id = LoggedSetId("set-1"),
                sessionId = TrainingSessionId("s1"),
                exerciseNameRaw = "Curl",
                liftId = liftId,
                setIndex = 0,
                weight = lb(weight),
                reps = reps,
                rir = rir,
                completedAt = Instant.parse("2026-07-01T10:30:00Z"),
                liftContext = LoggedLiftContext(
                    liftName = "Curl",
                    movementPatternsBitmask = 1,
                    primaryMusclesBitmask = 1,
                    secondaryMusclesBitmask = 0,
                    fatigueProfile = FatigueProfile.Low,
                    muscleLengthBias = MuscleLengthBias.MidRange,
                ),
            ),
        )

    @Test
    @DisplayName("lighter target raises projected reps from same baseline e1RM")
    fun lighterTargetRaisesReps() {
        val baseline = historyEntry(reps = 10, rir = 1, weight = 15.0)
        // Backoff slot: no repMax clamp so pure e1RM projection is visible
        val backoff = slot(position = 1, rirGoal = 1).copy(isTopSet = false, repMax = 0)
        val (w, r) = projectBaselineAtTargetWeight(
            baselineEntry = baseline,
            targetWeight = lb(10.0),
            slot = backoff,
            incrementTiers = emptyList(),
            loadType = LoadType.Weighted,
        )
        assertEquals(10.0, w.amount, 0.01, "empty tiers / capacity ok → hold target weight")
        val e1rm = EstimatedOneRm.calculate(15.0, 11)
        val expectedFailure = EstimatedOneRm.repsForWeight(e1rm, 10.0)
        val expectedAtRir = (expectedFailure - 1).coerceAtLeast(BACKOFF_REP_FLOOR)
        assertEquals(expectedAtRir, r)
        assertTrue(r > 10, "10 lb should allow more than history 10@1 at 15 lb, was $r")
    }

    @Test
    @DisplayName("same weight returns history failure capacity at RIR goal")
    fun sameWeightMatchesHistoryCapacity() {
        val baseline = historyEntry(reps = 10, rir = 2, weight = 15.0)
        val (_, r) = projectBaselineAtTargetWeight(
            baselineEntry = baseline,
            targetWeight = lb(15.0),
            slot = slot(rirGoal = 2),
            incrementTiers = emptyList(),
            loadType = LoadType.Weighted,
        )
        // failure 12 − rirGoal 2 = 10
        assertEquals(10, r)
    }

    @Test
    @DisplayName("very heavy target still never recommends below BACKOFF_REP_FLOOR")
    fun heavyTargetStillFloorsReps() {
        val baseline = historyEntry(reps = 6, rir = 0, weight = 15.0)
        val (w, r) = projectBaselineAtTargetWeight(
            baselineEntry = baseline,
            targetWeight = lb(40.0),
            slot = slot(rirGoal = 1),
            incrementTiers = emptyList(),
            loadType = LoadType.Weighted,
        )
        assertTrue(r >= BACKOFF_REP_FLOOR)
        // Floor path may drop weight when capacity is hopeless at 40
        assertTrue(w.amount <= 40.0)
    }
}
