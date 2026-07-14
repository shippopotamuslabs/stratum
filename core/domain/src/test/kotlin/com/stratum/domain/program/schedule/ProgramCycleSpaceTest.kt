package com.stratum.domain.program.schedule

import com.stratum.domain.program.Program
import com.stratum.domain.program.Workout
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ProgramCycleSpaceTest {
    @Test
    @DisplayName("Program.cycleSlots uses restSlotIndices and cycleLength")
    fun programCycleSlots() {
        val program =
            Program(
                id = ProgramId("p-1"),
                name = "PPL",
                createdAt = Instant.parse("2026-06-16T12:00:00Z"),
                cycleLength = 8,
                workouts = (0 until 6).map { index -> workout("w-$index", index) },
                restSlotIndices = setOf(3, 7),
                cycleStartEpochDay = 20_000L,
            )
        val slots = program.cycleSlots()
        assertEquals(8, slots.size)
        assertInstanceOf(CycleSlot.Rest::class.java, slots[3])
        assertEquals("w-3", (slots[4] as CycleSlot.Training).workout.id.value)
    }

    private fun workout(id: String, position: Int): Workout =
        Workout(
            id = WorkoutId(id),
            programId = ProgramId("p-1"),
            name = id,
            weekday = null,
            position = position,
            workoutLifts = emptyList(),
        )
}
