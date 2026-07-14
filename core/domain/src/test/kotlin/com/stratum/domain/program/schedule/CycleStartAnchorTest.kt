package com.stratum.domain.program.schedule

import com.stratum.domain.program.Program
import com.stratum.domain.program.Workout
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class CycleStartAnchorTest {
    @Test
    @DisplayName("mondayOfWeekContaining is previous-or-same Monday")
    fun mondayOfWeek() {
        // Wednesday 2026-07-15 → Monday 2026-07-13
        assertEquals(
            LocalDate.of(2026, 7, 13),
            mondayOfWeekContaining(LocalDate.of(2026, 7, 15)),
        )
        assertEquals(
            LocalDate.of(2026, 7, 13),
            mondayOfWeekContaining(LocalDate.of(2026, 7, 13)),
        )
    }

    @Test
    @DisplayName("7-day program anchors to Monday of current week")
    fun sevenDayAnchorsMonday() {
        val program = program(cycleLength = 7, trainCount = 4, restSlotIndices = setOf(2, 5, 6))
        val today = LocalDate.of(2026, 7, 15) // Wed
        assertEquals(
            LocalDate.of(2026, 7, 13).toEpochDay(),
            resolveInitialCycleStartEpochDay(program, today),
        )
    }

    @Test
    @DisplayName("non-7 cycle uses existing start when set")
    fun nonSevenKeepsExisting() {
        val program =
            program(cycleLength = 8, trainCount = 6, restSlotIndices = setOf(3, 7))
                .copy(cycleStartEpochDay = 10_000L)
        assertEquals(
            10_000L,
            resolveInitialCycleStartEpochDay(program, LocalDate.of(2026, 7, 15)),
        )
    }

    @Test
    @DisplayName("non-7 cycle without start uses today")
    fun nonSevenUsesToday() {
        val program = program(cycleLength = 8, trainCount = 6, restSlotIndices = setOf(3, 7))
        val today = LocalDate.of(2026, 7, 15)
        assertEquals(
            today.toEpochDay(),
            resolveInitialCycleStartEpochDay(program, today),
        )
    }

    private fun program(
        cycleLength: Int,
        trainCount: Int,
        restSlotIndices: Set<Int>,
    ): Program =
        Program(
            id = ProgramId("p-mtrfsa"),
            name = "Cycle",
            createdAt = Instant.parse("2026-07-14T12:00:00Z"),
            cycleLength = cycleLength,
            restSlotIndices = restSlotIndices,
            workouts =
                (0 until trainCount).map { index ->
                    Workout(
                        id = WorkoutId("w-$index"),
                        programId = ProgramId("p-mtrfsa"),
                        name = "Day $index",
                        weekday = null,
                        position = index,
                        workoutLifts = emptyList(),
                    )
                },
        )
}
