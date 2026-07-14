package com.stratum.domain.program.schedule

import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.Workout
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CycleSpaceTest {
    @Test
    @DisplayName("buildCycleSlots interleaves train and rest")
    fun buildCycleSlots_interleaves() {
        val workouts =
            listOf(
                workout("w-0", 0),
                workout("w-1", 1),
                workout("w-2", 2),
            )
        val slots =
            buildCycleSlots(
                cycleLength = 5,
                restSlotIndices = setOf(1, 3),
                workouts = workouts,
            )
        assertEquals(5, slots.size)
        assertEquals("w-0", (slots[0] as CycleSlot.Training).workout.id.value)
        assertInstanceOf(CycleSlot.Rest::class.java, slots[1])
        assertEquals("w-1", (slots[2] as CycleSlot.Training).workout.id.value)
        assertInstanceOf(CycleSlot.Rest::class.java, slots[3])
        assertEquals("w-2", (slots[4] as CycleSlot.Training).workout.id.value)
    }

    @Test
    @DisplayName("cycleSpaceForSevenDayWeek uses weekday complement as rests")
    fun sevenDayWeek_complement() {
        val workouts =
            listOf(
                workout("w-0", 0),
                workout("w-1", 1),
                workout("w-2", 2),
            )
        val space =
            cycleSpaceForSevenDayWeek(
                trainingWeekdays = setOf(Weekday.Monday, Weekday.Wednesday, Weekday.Friday),
                workouts = workouts,
            )
        assertEquals(7, space.cycleLength)
        assertEquals(setOf(1, 3, 5, 6), space.restSlotIndices)
        val slots = space.slots()
        assertEquals("w-0", (slots[0] as CycleSlot.Training).workout.id.value)
        assertInstanceOf(CycleSlot.Rest::class.java, slots[1])
        assertEquals("w-1", (slots[2] as CycleSlot.Training).workout.id.value)
        assertEquals("w-2", (slots[4] as CycleSlot.Training).workout.id.value)
    }

    @Test
    @DisplayName("invalid rest index fails")
    fun invalidRestFails() {
        assertThrows<IllegalArgumentException> {
            CycleSpace(
                cycleLength = 4,
                restSlotIndices = setOf(4),
                workouts = listOf(workout("w-0", 0)),
            )
        }
    }

    private fun workout(id: String, position: Int): Workout =
        Workout(
            id = WorkoutId(id),
            programId = ProgramId("p"),
            name = "W$position",
            weekday = null,
            position = position,
            workoutLifts = emptyList(),
        )
}
