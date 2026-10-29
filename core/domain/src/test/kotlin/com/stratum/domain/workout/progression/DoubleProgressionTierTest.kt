package com.stratum.domain.workout.progression

import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.workout.SetSlot
import com.stratum.domain.workout.SetSlotId
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.workout.SlotHistoryEntry

class DoubleProgressionTierTest {

    private val engine = DoubleProgressionEngine()
    private val liftId = LiftId("L-1")
    private val workoutLiftId = WorkoutLiftId("wl-1")

    @Test
    @DisplayName("Given top-of-range at RIR and 10 lb tiers when recommend then weight uses tier step")
    fun usesLiftIncrementTiers() {
        val slot =
            SetSlot(
                slotId = SetSlotId.Template(LiftSetId("ls-1")),
                workoutLiftId = workoutLiftId,
                liftId = liftId,
                position = 0,
                setType = SetType.Standard,
                isTopSet = true,
                repMin = 4,
                repMax = 6,
                rirGoal = 2,
                restSeconds = 120,
            )
        // 130 + 10 lb open-band step → 140 (proves tiers beat the 5 lb default).
        val history = listOf(historyEntry(reps = 6, rir = 2, weight = 130.0))
        val tiers =
            listOf(
                IncrementTier(
                    lowerBound = Weight(0.0, Weight.WeightUnit.Lb),
                    upperBound = Weight(100.0, Weight.WeightUnit.Lb),
                    increment = Weight(5.0, Weight.WeightUnit.Lb),
                ),
                IncrementTier(
                    lowerBound = Weight(100.0, Weight.WeightUnit.Lb),
                    upperBound = null,
                    increment = Weight(10.0, Weight.WeightUnit.Lb),
                ),
            )

        val rec =
            engine.recommend(
                SlotProgressionRequest(
                    slot = slot,
                    history = history,
                    fallbackOneRm = null,
                    progressionBaseline = ProgressionBaseline.FromRecent(history.first()),
                    incrementTiers = tiers,
                ),
            )

        assertEquals(140.0, rec.recommendedWeight.amount)
    }

    @Test
    @DisplayName(
        "Given micro-plate base and 5 lb open band when top met then adds step without " +
            "zero-anchored re-round (2.5 + 5 = 7.5, not 10)",
    )
    fun microPlateBaseAddsExactStepWithoutReRound() {
        val slot =
            SetSlot(
                slotId = SetSlotId.Template(LiftSetId("ls-1")),
                workoutLiftId = workoutLiftId,
                liftId = liftId,
                position = 0,
                setType = SetType.Standard,
                isTopSet = true,
                repMin = 8,
                repMax = 12,
                rirGoal = 2,
                restSeconds = 90,
            )
        // User tiers: [0, 2.5) → 2.5 lb; [2.5, ∞) → 5 lb. At 2.5 the open band steps +5.
        val history = listOf(historyEntry(reps = 12, rir = 2, weight = 2.5))
        val tiers =
            listOf(
                IncrementTier(
                    lowerBound = Weight(0.0, Weight.WeightUnit.Lb),
                    upperBound = Weight(2.5, Weight.WeightUnit.Lb),
                    increment = Weight(2.5, Weight.WeightUnit.Lb),
                ),
                IncrementTier(
                    lowerBound = Weight(2.5, Weight.WeightUnit.Lb),
                    upperBound = null,
                    increment = Weight(5.0, Weight.WeightUnit.Lb),
                ),
            )

        val rec =
            engine.recommend(
                SlotProgressionRequest(
                    slot = slot,
                    history = history,
                    fallbackOneRm = null,
                    progressionBaseline = ProgressionBaseline.FromRecent(history.first()),
                    incrementTiers = tiers,
                ),
            )

        // Must not re-snap 7.5 onto a zero-anchored 5 lb lattice (→ 10).
        assertEquals(7.5, rec.recommendedWeight.amount)
    }

    private fun historyEntry(reps: Int, rir: Int?, weight: Double): SlotHistoryEntry {
        val sessionId = TrainingSessionId("s-1")
        return SlotHistoryEntry(
            session =
                TrainingSession(
                    id = sessionId,
                    programId = ProgramId("p-1"),
                    workoutId = WorkoutId("w-1"),
                    status = TrainingSessionStatus.COMPLETED,
                    kind = TrainingSessionKind.PROGRAM_WORKOUT,
                    origin = TrainingSessionOrigin.LIVE,
                    cycleNumberSnapshot = 1,
                    weekdaySnapshot = null,
                    startedAt = Instant.parse("2026-06-28T10:00:00Z"),
                    endedAt = Instant.parse("2026-06-28T11:00:00Z"),
                    sourceApp = null,
                ),
            loggedSet =
                LoggedSet(
                    id = LoggedSetId("set-1"),
                    sessionId = sessionId,
                    exerciseNameRaw = "Squat",
                    liftId = liftId,
                    setIndex = 0,
                    weight = Weight(weight, Weight.WeightUnit.Lb),
                    reps = reps,
                    rir = rir,
                    completedAt = Instant.parse("2026-06-28T10:30:00Z"),
                    liftContext =
                        LoggedLiftContext(
                            liftName = "Squat",
                            fatigueProfile = FatigueProfile.Medium,
                            muscleLengthBias = MuscleLengthBias.MidRange,
                        ),
                ),
        )
    }
}
