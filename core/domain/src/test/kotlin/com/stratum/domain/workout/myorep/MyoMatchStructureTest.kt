package com.stratum.domain.workout.myorep

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.workout.OverlaySetInstanceId
import com.stratum.domain.workout.SetSlot
import com.stratum.domain.workout.SetSlotId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("MyoMatchStructure")
class MyoMatchStructureTest {

    private val workoutLiftId = WorkoutLiftId("wl-1")
    private val liftId = LiftId("L-1")

    private fun mm(id: String, position: Int) = SetSlot(
        slotId = SetSlotId.Template(LiftSetId(id)),
        workoutLiftId = workoutLiftId,
        liftId = liftId,
        position = position,
        setType = SetType.MyoRep,
        isTopSet = position == 0,
        repMin = if (position == 0) 8 else 0,
        repMax = if (position == 0) 12 else 0,
        rirGoal = 1,
        restSeconds = 120,
        myoRepKind = MyoRepKind.RepMatching,
    )

    private fun mini(parentKey: String, position: Int) = SetSlot(
        slotId = SetSlotId.Overlay(OverlaySetInstanceId("mini-$parentKey-1-aaaaaaaa")),
        workoutLiftId = workoutLiftId,
        liftId = liftId,
        position = position,
        setType = SetType.MyoRep,
        isTopSet = false,
        repMin = 0,
        repMax = 0,
        rirGoal = 0,
        restSeconds = 15,
        myoRepKind = MyoRepKind.RepMatching,
    )

    private fun standard(id: String, position: Int) = SetSlot(
        slotId = SetSlotId.Template(LiftSetId(id)),
        workoutLiftId = workoutLiftId,
        liftId = liftId,
        position = position,
        setType = SetType.Standard,
        isTopSet = false,
        repMin = 0,
        repMax = 0,
        rirGoal = 2,
        restSeconds = 120,
    )

    @Test
    fun `activation is first MM and following are backoffs`() {
        val slots = listOf(mm("act", 0), mm("bo1", 1), mm("bo2", 2), standard("s", 3))
        assertTrue(MyoMatchStructure.isMyoMatchActivation(slots, 0))
        assertTrue(MyoMatchStructure.isMyoMatchBackoff(slots, 1))
        assertTrue(MyoMatchStructure.isMyoMatchBackoff(slots, 2))
        assertFalse(MyoMatchStructure.isMyoMatchActivation(slots, 1))
        assertEquals(2, MyoMatchStructure.matchBackoffCount(slots, 0))
    }

    @Test
    fun `minis do not break activation resolution`() {
        val slots = listOf(
            mm("act", 0),
            mm("bo1", 1),
            mini("ls-bo1", 2),
            mm("bo2", 3),
        )
        assertEquals(
            slots[0].slotId,
            MyoMatchStructure.resolveMatchActivation(slots, 2)?.slotId,
        )
        assertEquals(
            slots[1].slotId,
            MyoMatchStructure.resolveClusterAnchor(slots, slots[2])?.slotId,
        )
        assertNull(MyoMatchStructure.resolveClusterAnchor(slots, slots[0]))
        assertEquals(slots[1].slotId, MyoMatchStructure.resolveClusterAnchor(slots, slots[1])?.slotId)
    }

    @Test
    fun `match backoffs skip minis when counting`() {
        val slots = listOf(
            mm("act", 0),
            mm("bo1", 1),
            mini("ls-bo1", 2),
            mm("bo2", 3),
            standard("s", 4),
        )
        assertEquals(2, MyoMatchStructure.matchBackoffCount(slots, 0))
        assertEquals(
            listOf(slots[1].slotId, slots[3].slotId),
            MyoMatchStructure.matchBackoffsAfter(slots, 0).map { it.slotId },
        )
    }
}
