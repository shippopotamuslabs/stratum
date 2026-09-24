package com.stratum.domain.workout.progression

import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.isOnLattice
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.roundToIncrement
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.EstimatedOneRm
import com.stratum.domain.workout.SetSlot
import com.stratum.domain.workout.SetSlotId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Exact weight/reps asserts for [ensureRepFloorPreferWeightDrop] — empty tiers (passthrough)
 * and simple tier rounding, driven by [EstimatedOneRm] inverses matching engine math.
 */
@DisplayName("ensureRepFloorPreferWeightDrop exact floor drops")
class EnsureRepFloorPreferWeightDropTest {

    private val slotId = SetSlotId.Template(LiftSetId("ls-1"))
    private val workoutLiftId = WorkoutLiftId("wl-1")
    private val liftId = LiftId("L-1")

    private fun lb(amount: Double) = Weight(amount, Weight.WeightUnit.Lb)

    private fun topSet(repMin: Int = 5, repMax: Int = 8, rirGoal: Int = 2) = SetSlot(
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

    @Test
    @DisplayName("empty tiers: capacity below floor drops weight via weightForReps passthrough")
    fun emptyTiersDropsToExactWeightForReps() {
        // 135×6@0 RIR → capacity 6; at rirGoal 2 = 4 < floor 5 → drop load for 5@2.
        val target = lb(135.0)
        val capacity = 6
        val slot = topSet(repMin = 4, repMax = 6, rirGoal = 2)
        val e1rm = EstimatedOneRm.calculate(target.amount, capacity)
        val expectedAmount = EstimatedOneRm.weightForReps(e1rm, minRecommendedReps(slot) + slot.rirGoal)

        val (weight, reps) = ensureRepFloorPreferWeightDrop(
            targetWeight = target,
            capacityRepsAt0Rir = capacity,
            slot = slot,
            e1rm = e1rm,
            incrementTiers = emptyList(),
            loadType = LoadType.Weighted,
        )

        assertEquals(expectedAmount, weight.amount)
        assertEquals(Weight.WeightUnit.Lb, weight.unit)
        assertEquals(5, reps)
        assertEquals(true, weight.amount < target.amount)
    }

    @Test
    @DisplayName("empty tiers: capacity at/above floor holds weight and clamps reps")
    fun emptyTiersHoldsWhenAboveFloor() {
        // 135×8@2 → capacity 10; at rirGoal 2 = 8 ≥ floor 5 → hold, clamp to repMax.
        val target = lb(135.0)
        val capacity = 10
        val slot = topSet(repMin = 4, repMax = 6, rirGoal = 2)
        val e1rm = EstimatedOneRm.calculate(target.amount, capacity)

        val (weight, reps) = ensureRepFloorPreferWeightDrop(
            targetWeight = target,
            capacityRepsAt0Rir = capacity,
            slot = slot,
            e1rm = e1rm,
            incrementTiers = emptyList(),
            loadType = LoadType.Weighted,
        )

        assertEquals(target, weight)
        assertEquals(6, reps) // 10 - 2 = 8 coerced into [5, 6]
    }

    @Test
    @DisplayName("simple tiers: floor drop is tier-rounded and never raised")
    fun simpleTiersRoundsDroppedWeight() {
        val target = lb(200.0)
        val capacity = 5 // 5@0 → 3@2 below floor 5
        val slot = topSet(repMin = 5, repMax = 8, rirGoal = 2)
        val e1rm = EstimatedOneRm.calculate(target.amount, capacity)
        val tiers = listOf(
            IncrementTier(lb(0.0), lb(300.0), lb(5.0)),
        )
        val rawReduced = EstimatedOneRm.weightForReps(e1rm, minRecommendedReps(slot) + slot.rirGoal)
        val expected = tiers.roundToIncrement(lb(rawReduced), LoadType.Weighted)

        val (weight, reps) = ensureRepFloorPreferWeightDrop(
            targetWeight = target,
            capacityRepsAt0Rir = capacity,
            slot = slot,
            e1rm = e1rm,
            incrementTiers = tiers,
            loadType = LoadType.Weighted,
        )

        assertEquals(expected, weight)
        assertEquals(5, reps)
        assertEquals(true, weight.amount < target.amount)
    }

    @Test
    @DisplayName("micro: floor drop snaps to base plus adder and agrees with membership")
    fun microSnapsToAdderPoint() {
        val target = lb(200.0)
        val capacity = 5 // 5@0 → 3@2 below floor 5
        val slot = topSet(repMin = 5, repMax = 8, rirGoal = 2)
        val e1rm = EstimatedOneRm.calculate(target.amount, capacity)
        val tiers = listOf(
            IncrementTier(lb(0.0), null, lb(15.0)),
        )
        val micro = listOf(lb(5.0), lb(10.0))
        val rawReduced = EstimatedOneRm.weightForReps(e1rm, minRecommendedReps(slot) + slot.rirGoal)
        val expected = tiers.roundToIncrement(lb(rawReduced), LoadType.Weighted, micro)

        val (weight, reps) = ensureRepFloorPreferWeightDrop(
            targetWeight = target,
            capacityRepsAt0Rir = capacity,
            slot = slot,
            e1rm = e1rm,
            incrementTiers = tiers,
            loadType = LoadType.Weighted,
            micro = micro,
        )

        assertEquals(expected, weight)
        assertEquals(5, reps)
        assertEquals(true, weight.amount < target.amount)
        assertEquals(true, tiers.isOnLattice(weight, micro = micro))
    }
}
