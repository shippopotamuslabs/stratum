package com.stratum.domain.workout

import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.isOnLattice
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.workout.dropset.DropSetConstraintResult
import com.stratum.domain.workout.dropset.DropSetConstraintValidator
import com.stratum.domain.workout.dropset.DropSetConstraintViolation
import com.stratum.domain.workout.dropset.EstimateDropSetWeightUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Slice 6 — Drop sets. Fixed 20% reduction + must-follow constraint.
 */
@DisplayName("Slice 6: Drop sets")
class Slice6DropSetTest {

    private val workoutLiftId = WorkoutLiftId("wl-1")
    private val liftId = LiftId("L-1")

    private fun lb(amount: Double) = Weight(amount, Weight.WeightUnit.Lb)

    private fun standardSlot(id: SetSlotId, position: Int) = SetSlot(
        slotId = id,
        workoutLiftId = workoutLiftId,
        liftId = liftId,
        position = position,
        setType = SetType.Standard,
        isTopSet = true,
        repMin = 4,
        repMax = 6,
        rirGoal = 2,
        restSeconds = 120,
    )

    private fun dropSlot(id: SetSlotId, position: Int) = SetSlot(
        slotId = id,
        workoutLiftId = workoutLiftId,
        liftId = liftId,
        position = position,
        setType = SetType.DropSet,
        isTopSet = false,
        repMin = 0,
        repMax = 0,
        rirGoal = 0,
        restSeconds = 0,
    )

    private fun projection(slots: List<SetSlot>) = WorkoutLiftProjection(
        workoutLiftId = workoutLiftId,
        liftId = liftId,
        liftName = "Curl",
        progressionScheme = com.stratum.domain.program.ProgressionScheme.Double,
        slots = slots,
    )

    @Test
    @DisplayName("S6-drop-set-follows-another-set-valid")
    fun `S6-drop-set-follows-another-set-valid`() {
        val ls1 = SetSlotId.Template(LiftSetId("ls-1"))
        val ls2 = SetSlotId.Template(LiftSetId("ls-2"))
        val lift = projection(listOf(standardSlot(ls1, 0), dropSlot(ls2, 1)))
        val result = DropSetConstraintValidator().invoke(lift)
        assertEquals(DropSetConstraintResult.Valid, result)
    }

    @Test
    @DisplayName("S6-drop-set-first-set-invalid")
    fun `S6-drop-set-first-set-invalid`() {
        val ls1 = SetSlotId.Template(LiftSetId("ls-1"))
        val lift = projection(listOf(dropSlot(ls1, 0)))
        val result = DropSetConstraintValidator().invoke(lift)
        assertTrue(result is DropSetConstraintResult.Invalid, "drop set as first set must be invalid")
        val reasons = (result as DropSetConstraintResult.Invalid).reasons
        assertTrue(DropSetConstraintViolation.DropSetNotFollowingAnotherSet in reasons)
    }

    @Test
    @DisplayName("S6-drop-set-20-percent-reduction")
    fun `S6-drop-set-20-percent-reduction`() {
        val useCase = EstimateDropSetWeightUseCase()
        val result = useCase.invoke(precedingWeight = lb(100.0))
        assertEquals(lb(80.0), result)
    }

    @Test
    @DisplayName("S6-drop-set-floor-repair-when-capacity-low")
    fun `S6-drop-set-floor-repair-when-capacity-low`() {
        val useCase = EstimateDropSetWeightUseCase()
        // Capacity fails at 80 lb and above; succeeds at lower loads → further reduce.
        val result = useCase.invoke(
            precedingWeight = lb(100.0),
            capacityAtRir = { amountLb -> if (amountLb <= 60.0) 6 else 3 },
        )
        assertTrue(result.amount <= 60.0 + 1e-6, "must reduce below 80 when capacity fails floor")
        assertTrue(result.amount > 0.0)
    }

    @Test
    @DisplayName("S6-drop-set-reduce-with-micro-agrees-with-adder-lattice")
    fun `S6-drop-set-reduce-with-micro-agrees-with-adder-lattice`() {
        val tiers = listOf(IncrementTier(lb(0.0), null, lb(15.0)))
        val micro = listOf(lb(5.0), lb(10.0))
        val useCase = EstimateDropSetWeightUseCase()
        val result = useCase.invoke(
            precedingWeight = lb(100.0),
            incrementTiers = tiers,
            capacityAtRir = { amountLb -> if (amountLb <= 60.0) 6 else 3 },
            micro = micro,
        )
        assertTrue(result.amount <= 80.0 + 1e-6, "must reduce below 80 when capacity fails floor")
        assertTrue(result.amount > 0.0)
        assertTrue(
            tiers.isOnLattice(result, micro = micro),
            "drop must agree with adder lattice, was ${result.amount}",
        )
    }
}
