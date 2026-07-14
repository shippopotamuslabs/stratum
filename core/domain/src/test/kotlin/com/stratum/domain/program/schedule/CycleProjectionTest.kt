package com.stratum.domain.program.schedule

import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.Workout
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import java.time.LocalDate
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class CycleProjectionTest {
    /** Monday 2025-06-16 as epoch day (slot 0 of cycle 1 in worked example style). */
    private val mondayEpochDay = LocalDate.of(2025, 6, 16).toEpochDay()

    @Test
    @DisplayName("projectedDate advances one calendar day per slot")
    fun projectedDate_advancesPerSlot() {
        assertEquals(LocalDate.of(2025, 6, 16), CycleProjection.projectedDate(mondayEpochDay, 0))
        assertEquals(LocalDate.of(2025, 6, 17), CycleProjection.projectedDate(mondayEpochDay, 1))
        assertEquals(LocalDate.of(2025, 6, 23), CycleProjection.projectedDate(mondayEpochDay, 7))
    }

    @Test
    @DisplayName("projectedWeekday drifts across an 8-day cycle into the next week")
    fun projectedWeekday_eightDayDrift() {
        assertEquals(Weekday.Monday, CycleProjection.projectedWeekday(mondayEpochDay, 0))
        assertEquals(Weekday.Sunday, CycleProjection.projectedWeekday(mondayEpochDay, 6))
        assertEquals(Weekday.Monday, CycleProjection.projectedWeekday(mondayEpochDay, 7))
        // Cycle 2 start = monday + 8 → Tuesday
        val cycle2Start = CycleProjection.nextCycleStartEpochDay(mondayEpochDay, cycleLength = 8)
        assertEquals(Weekday.Tuesday, CycleProjection.projectedWeekday(cycle2Start, 0))
    }

    @Test
    @DisplayName("formatSlotLabel matches Monday - 6/17 style without zero-padding")
    fun formatSlotLabel_ownerExample() {
        val label =
            CycleProjection.formatSlotLabel(
                cycleStartEpochDay = mondayEpochDay,
                slotIndex = 1,
                locale = Locale.US,
            )
        assertEquals("Tuesday - 6/17", label)
    }

    @Test
    @DisplayName("projectCycleWindow labels all slots for PPLPPL + 2 rests")
    fun projectCycleWindow_eightSlots() {
        val workouts = (0 until 6).map { index -> workout("w-$index", index) }
        val slots = buildCycleSlots(8, setOf(3, 7), workouts)
        val window = CycleProjection.projectCycleWindow(slots, mondayEpochDay, Locale.US)

        assertEquals(8, window.size)
        assertEquals("Monday - 6/16", window[0].label)
        assertEquals(Weekday.Thursday, window[3].weekday)
        assertTrue(window[3].slot is CycleSlot.Rest)
        assertEquals("Monday - 6/23", window[7].label)
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.jupiter.api.Assertions.assertTrue(condition)
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
