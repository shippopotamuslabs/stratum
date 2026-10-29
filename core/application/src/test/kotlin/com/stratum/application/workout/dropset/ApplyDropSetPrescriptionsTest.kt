package com.stratum.application.workout.dropset

import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.workout.SetSlot
import com.stratum.domain.workout.SetSlotId
import com.stratum.domain.workout.SlotRecommendation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("applyDropSetPrescriptions: snapshot drop rewrite")
class ApplyDropSetPrescriptionsTest {

    private val workoutLiftId = WorkoutLiftId("wl-1")
    private val liftId = LiftId("L-1")

    private fun lb(amount: Double) = Weight(amount, Weight.WeightUnit.Lb)

    private fun slot(
        id: String,
        position: Int,
        type: SetType,
        isTopSet: Boolean = false,
        rirGoal: Int = 1,
        repMin: Int = 8,
        repMax: Int = 12,
    ) = SetSlot(
        slotId = SetSlotId.Template(LiftSetId(id)),
        workoutLiftId = workoutLiftId,
        liftId = liftId,
        position = position,
        setType = type,
        isTopSet = isTopSet,
        repMin = repMin,
        repMax = repMax,
        rirGoal = rirGoal,
        restSeconds = if (type == SetType.DropSet) 0 else 120,
    )

    private fun rec(id: String, weight: Double, reps: Int) = SlotRecommendation(
        slotId = SetSlotId.Template(LiftSetId(id)),
        recommendedWeight = lb(weight),
        baselineExpectedReps = reps,
    )

    @Test
    @DisplayName("screenshot: S-S-D at shared 150 → drop becomes 120")
    fun dropTwentyPercentOffSharedStraightLoad() {
        val slots = listOf(
            slot("s1", 0, SetType.Standard, isTopSet = true),
            slot("s2", 1, SetType.Standard),
            slot("d1", 2, SetType.DropSet, rirGoal = 0, repMin = 0, repMax = 0),
            slot("s3", 3, SetType.Standard, rirGoal = 2, repMin = 6, repMax = 10),
        )
        // Engines emit every slot at shared 150 before this pass.
        val engineRecs = listOf(
            rec("s1", 150.0, 11),
            rec("s2", 150.0, 11),
            rec("d1", 150.0, 11),
            rec("s3", 150.0, 8),
        )
        val out = applyDropSetPrescriptions(slots, engineRecs)
        assertEquals(150.0, out[0].recommendedWeight.amount, 0.01)
        assertEquals(150.0, out[1].recommendedWeight.amount, 0.01)
        assertEquals(120.0, out[2].recommendedWeight.amount, 0.01, "20% off prior standard")
        assertEquals(150.0, out[3].recommendedWeight.amount, 0.01, "trailing standard unchanged")
        assertTrue(out[2].baselineExpectedReps > 0)
        // Zero-rest at lighter load should not keep the shared-set 11-rep copy.
        assertTrue(
            out[2].baselineExpectedReps <= 11,
            "drop reps should be projected, not blindly copy shared 11",
        )
    }

    @Test
    @DisplayName("drop chain: second drop is 20% off first drop prescription")
    fun dropChainRewritesSequentially() {
        val slots = listOf(
            slot("s1", 0, SetType.Standard, isTopSet = true),
            slot("d1", 1, SetType.DropSet, rirGoal = 0, repMin = 0, repMax = 0),
            slot("d2", 2, SetType.DropSet, rirGoal = 0, repMin = 0, repMax = 0),
        )
        val engineRecs = listOf(
            rec("s1", 100.0, 8),
            rec("d1", 100.0, 8),
            rec("d2", 100.0, 8),
        )
        val out = applyDropSetPrescriptions(slots, engineRecs)
        assertEquals(80.0, out[1].recommendedWeight.amount, 0.01)
        assertEquals(64.0, out[2].recommendedWeight.amount, 0.01)
    }

    @Test
    @DisplayName("no drops → identity")
    fun noDropsUnchanged() {
        val slots = listOf(
            slot("s1", 0, SetType.Standard, isTopSet = true),
            slot("s2", 1, SetType.Standard),
        )
        val engineRecs = listOf(rec("s1", 150.0, 11), rec("s2", 150.0, 10))
        val out = applyDropSetPrescriptions(slots, engineRecs)
        assertEquals(engineRecs, out)
    }
}
