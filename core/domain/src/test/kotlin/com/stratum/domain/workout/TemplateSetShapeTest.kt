package com.stratum.domain.workout

import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.workout.myorep.MyoRepKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("TemplateSetShape.from")
class TemplateSetShapeTest {

    @Test
    @DisplayName("Maps the template subset of a set")
    fun mapsTemplateSubset() {
        val set = sampleSet()

        val shape = TemplateSetShape.from(set)

        assertEquals(
            TemplateSetShape(
                setType = SetType.MyoRep,
                myoRepKind = MyoRepKind.RepFloor,
                repMin = 8,
                repMax = 12,
                rirGoal = 1,
                isTopSet = true,
            ),
            shape,
        )
    }

    @Test
    @DisplayName("Ignores non-template fields (rest, position, identity)")
    fun ignoresNonTemplateFields() {
        val shape = TemplateSetShape.from(sampleSet())
        val rescheduled = TemplateSetShape.from(sampleSet().copy(restSeconds = 45, position = 3))

        assertEquals(shape, rescheduled)
    }

    private fun sampleSet(): LiftSet =
        LiftSet(
            id = LiftSetId("ls-1"),
            workoutLiftId = WorkoutLiftId("wl-1"),
            position = 0,
            setType = SetType.MyoRep,
            repMin = 8,
            repMax = 12,
            rirGoal = 1,
            isTopSet = true,
            restSeconds = 120,
            myoRepKind = MyoRepKind.RepFloor,
        )
}
