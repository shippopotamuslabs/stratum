package com.stratum.domain.program

import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.workout.SlotShapeOverride
import com.stratum.domain.workout.TemplateSetShape
import com.stratum.domain.workout.myorep.MyoRepKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("Set-shape guardrails (STR-168)")
class LiftSetGuardrailsTest {

    private fun liftSet(
        repMin: Int = 8,
        repMax: Int = 12,
        rirGoal: Int = 2,
        restSeconds: Int = DEFAULT_LIFT_SET_REST_SECONDS,
        setType: SetType = SetType.Standard,
        myoRepKind: MyoRepKind? = null,
    ): LiftSet =
        LiftSet(
            id = LiftSetId("set-1"),
            workoutLiftId = WorkoutLiftId("wl-1"),
            position = 0,
            setType = setType,
            repMin = repMin,
            repMax = repMax,
            rirGoal = rirGoal,
            isTopSet = true,
            restSeconds = restSeconds,
            myoRepKind = myoRepKind,
        )

    @Test
    @DisplayName("LiftSet rejects a negative repMin")
    fun liftSetRejectsNegativeRepMin() {
        assertThrows<IllegalArgumentException> { liftSet(repMin = -1) }
    }

    @Test
    @DisplayName("LiftSet rejects repMin above repMax")
    fun liftSetRejectsInvertedBand() {
        assertThrows<IllegalArgumentException> { liftSet(repMin = 12, repMax = 8) }
    }

    @Test
    @DisplayName("LiftSet accepts the 0-0 RIR-only back-off shape")
    fun liftSetAcceptsZeroBand() {
        val set = liftSet(repMin = 0, repMax = 0, rirGoal = 1)
        assertEquals(0, set.repMin)
        assertEquals(0, set.repMax)
    }

    @Test
    @DisplayName("LiftSet rejects a negative RIR goal")
    fun liftSetRejectsNegativeRirGoal() {
        assertThrows<IllegalArgumentException> { liftSet(rirGoal = -1) }
    }

    @Test
    @DisplayName("LiftSet rejects zero and negative rest")
    fun liftSetRejectsNonPositiveRest() {
        assertThrows<IllegalArgumentException> { liftSet(restSeconds = 0) }
        assertThrows<IllegalArgumentException> { liftSet(restSeconds = -30) }
    }

    @Test
    @DisplayName("LiftSet requires a myo kind exactly for MyoRep sets")
    fun liftSetMyoKindConsistency() {
        assertThrows<IllegalArgumentException> { liftSet(setType = SetType.MyoRep) }
        assertThrows<IllegalArgumentException> {
            liftSet(setType = SetType.Standard, myoRepKind = MyoRepKind.RepFloor)
        }
        assertThrows<IllegalArgumentException> {
            liftSet(setType = SetType.DropSet, myoRepKind = MyoRepKind.RepMatching)
        }
        val myo = liftSet(setType = SetType.MyoRep, myoRepKind = MyoRepKind.RepFloor)
        assertEquals(MyoRepKind.RepFloor, myo.myoRepKind)
    }

    @Test
    @DisplayName("WorkingSetRepSpec carries only coherent bands")
    fun workingSetRepSpecBand() {
        assertThrows<IllegalArgumentException> {
            ProgressionRepRangePolicy.WorkingSetRepSpec(repMin = 9, repMax = 5, isTopSet = true)
        }
        assertThrows<IllegalArgumentException> {
            ProgressionRepRangePolicy.WorkingSetRepSpec(repMin = -1, repMax = 5, isTopSet = true)
        }
        val backoff = ProgressionRepRangePolicy.WorkingSetRepSpec(repMin = 0, repMax = 0, isTopSet = false)
        assertEquals(0, backoff.repMax)
    }

    @Test
    @DisplayName("TemplateSetShape follows the shared shape rule")
    fun templateSetShapeRules() {
        assertThrows<IllegalArgumentException> {
            TemplateSetShape(
                setType = SetType.Standard,
                myoRepKind = null,
                repMin = 12,
                repMax = 8,
                rirGoal = 2,
                isTopSet = true,
            )
        }
        assertThrows<IllegalArgumentException> {
            TemplateSetShape(
                setType = SetType.MyoRep,
                myoRepKind = null,
                repMin = 8,
                repMax = 12,
                rirGoal = 2,
                isTopSet = true,
            )
        }
        assertThrows<IllegalArgumentException> {
            TemplateSetShape(
                setType = SetType.Standard,
                myoRepKind = null,
                repMin = 8,
                repMax = 12,
                rirGoal = -1,
                isTopSet = true,
            )
        }
    }

    @Test
    @DisplayName("SlotShapeOverride follows the shared shape rule")
    fun slotShapeOverrideRules() {
        assertThrows<IllegalArgumentException> {
            SlotShapeOverride(
                setType = SetType.Standard,
                isTopSet = true,
                repMin = -2,
                repMax = 8,
                rirGoal = 2,
            )
        }
        assertThrows<IllegalArgumentException> {
            SlotShapeOverride(
                setType = SetType.Standard,
                myoRepKind = MyoRepKind.RepFloor,
                isTopSet = true,
                repMin = 8,
                repMax = 12,
                rirGoal = 2,
            )
        }
        val ok =
            SlotShapeOverride(
                setType = SetType.MyoRep,
                myoRepKind = MyoRepKind.RepMatching,
                isTopSet = false,
                repMin = 0,
                repMax = 0,
                rirGoal = 0,
            )
        assertEquals(MyoRepKind.RepMatching, ok.myoRepKind)
    }
}
