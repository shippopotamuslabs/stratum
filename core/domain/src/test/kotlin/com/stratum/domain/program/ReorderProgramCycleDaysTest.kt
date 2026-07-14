package com.stratum.domain.program

import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.schedule.CycleSlot
import com.stratum.domain.program.schedule.resolvedCycleSlots
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ReorderProgramCycleDaysTest {

    @Test
    @DisplayName("toReorderDayItems includes train and rest slots with stable ids")
    fun toReorderDayItems_includesRest() {
        val program = sampleProgram()
        val items = program.toReorderDayItems(restDayName = "Rest")
        assertEquals(4, items.size)
        assertEquals("w-a", items[0].dayId)
        assertEquals("A", items[0].name)
        assertEquals("rest-1-prog-1", items[1].dayId)
        assertEquals("Rest", items[1].name)
        assertEquals("w-b", items[2].dayId)
        assertEquals("rest-3-prog-1", items[3].dayId)
    }

    @Test
    @DisplayName("reorderCycleDays moves train slots and recomputes restSlotIndices")
    fun reorderCycleDays_movesTrainAndRest() {
        val program = sampleProgram()
        val items = program.toReorderDayItems()
        // Swap first train with rest: Rest, A, B, Rest
        val ordered =
            listOf(
                items[1].dayId,
                items[0].dayId,
                items[2].dayId,
                items[3].dayId,
            )
        val reordered = program.reorderCycleDays(ordered)
        assertEquals(setOf(0, 3), reordered.restSlotIndices)
        assertEquals(listOf("w-a", "w-b"), reordered.workouts.map { it.id.value })
        assertEquals(0, reordered.workouts[0].position)
        assertEquals(1, reordered.workouts[1].position)

        val slots = reordered.resolvedCycleSlots()
        assertTrue(slots[0] is CycleSlot.Rest)
        assertEquals("w-a", (slots[1] as CycleSlot.Training).workout.id.value)
        assertEquals("w-b", (slots[2] as CycleSlot.Training).workout.id.value)
        assertTrue(slots[3] is CycleSlot.Rest)
    }

    @Test
    @DisplayName("reorderCycleDays reverses training order among slots")
    fun reorderCycleDays_reverseTrainings() {
        val program = sampleProgram()
        val items = program.toReorderDayItems()
        // B, Rest, A, Rest
        val ordered =
            listOf(
                items[2].dayId,
                items[1].dayId,
                items[0].dayId,
                items[3].dayId,
            )
        val reordered = program.reorderCycleDays(ordered)
        assertEquals(listOf("w-b", "w-a"), reordered.workouts.map { it.id.value })
        assertEquals(setOf(1, 3), reordered.restSlotIndices)
    }

    @Test
    @DisplayName("reorderCycleDays no-ops on mismatched id set")
    fun reorderCycleDays_mismatchNoOp() {
        val program = sampleProgram()
        val result = program.reorderCycleDays(listOf("missing"))
        assertEquals(program, result)
    }

    @Test
    @DisplayName("slotIndexRemapAfterReorder maps old slot indices to new positions")
    fun slotIndexRemap_mapsOldToNew() {
        val program = sampleProgram()
        val items = program.toReorderDayItems()
        // Rest@1, A, B, Rest@3  → new indices 0,1,2,3 for old 1,0,2,3
        val ordered =
            listOf(
                items[1].dayId,
                items[0].dayId,
                items[2].dayId,
                items[3].dayId,
            )
        val remap = program.slotIndexRemapAfterReorder(ordered)
        assertEquals(
            mapOf(1 to 0, 0 to 1, 2 to 2, 3 to 3),
            remap,
        )
    }

    @Test
    @DisplayName("slotIndexRemapAfterReorder empty on mismatched ids")
    fun slotIndexRemap_mismatchEmpty() {
        assertTrue(sampleProgram().slotIndexRemapAfterReorder(listOf("x")).isEmpty())
    }

    private fun sampleProgram(): Program =
        Program(
            id = ProgramId("prog-1"),
            name = "Sample",
            createdAt = Instant.EPOCH,
            cycleLength = 4,
            workouts =
                listOf(
                    workout("w-a", 0, "A"),
                    workout("w-b", 1, "B"),
                ),
            restSlotIndices = setOf(1, 3),
            cyclePosition = 0,
            isActive = true,
        )

    private fun workout(id: String, position: Int, name: String): Workout =
        Workout(
            id = WorkoutId(id),
            programId = ProgramId("prog-1"),
            name = name,
            weekday = null,
            position = position,
            workoutLifts = emptyList(),
        )
}
