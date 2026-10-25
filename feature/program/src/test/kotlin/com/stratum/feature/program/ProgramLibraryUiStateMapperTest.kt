package com.stratum.feature.program

import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ProgramLibraryUiStateMapperTest {
    private val restDayName = "Rest"
    private val liftsById =
        mapOf(
            LiftId("bench-press") to
                Lift(
                    id = LiftId("bench-press"),
                    name = "Barbell Bench Press",
                    movementPatterns = setOf(MovementPattern.HorizontalPress),
                    primaryMuscles = setOf(MuscleGroup.Chest),
                    secondaryMuscles = setOf(MuscleGroup.Tricep),
                    incrementTiers = emptyList(),
                    presetTags = emptySet(),
                    enabled = true,
                ),
        )

    @Test
    @DisplayName("8-slot rotating program shows all cycle slots including rests")
    fun eightSlotRotatingShowsRestSlots() {
        val program =
            Program(
                id = ProgramId("p-8"),
                name = "PPL×2",
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                cycleLength = 8,

                workouts = (0 until 5).map { index -> trainWorkout("w-$index", index, programId = "p-8") },
                restSlotIndices = setOf(3, 5, 7),
            )

        val card = program.toProgramLibraryCard(liftsById, restDayName)

        assertEquals(8, card.days.size)
        assertEquals(5, card.workoutCount)
        assertEquals(
            listOf(false, false, false, true, false, true, false, true),
            card.days.map { day -> day.isRest },
        )
        assertEquals(
            listOf("Day w-0", "Day w-1", "Day w-2", "Rest", "Day w-3", "Rest", "Day w-4", "Rest"),
            card.days.map { day -> day.name },
        )
    }

    @Test
    @DisplayName("train-only classic does not Mon–Sun pad when restSlotIndices empty")
    fun trainOnlyClassicNoWeekdayPadding() {
        val program =
            Program(
                id = ProgramId("p-classic"),
                name = "UL",
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                cycleLength = 2,
                workouts =
                    listOf(
                        trainWorkout("w-u", 0, weekday = Weekday.Monday, name = "Upper", programId = "p-classic"),
                        trainWorkout("w-l", 1, weekday = Weekday.Thursday, name = "Lower", programId = "p-classic"),
                    ),
                restSlotIndices = emptySet(),
            )

        val card = program.toProgramLibraryCard(liftsById, restDayName)

        assertEquals(2, card.days.size)
        assertTrue(card.days.none { day -> day.isRest })
        assertEquals(listOf("Upper", "Lower"), card.days.map { day -> day.name })
    }

    private fun trainWorkout(
        id: String,
        position: Int,
        weekday: Weekday? = null,
        name: String = "Day $id",
        programId: String = "p",
    ): Workout =
        Workout(
            id = WorkoutId(id),
            programId = ProgramId(programId),
            name = name,
            weekday = weekday,
            position = position,
            workoutLifts =
                listOf(
                    WorkoutLift(
                        id = WorkoutLiftId("wl-$id"),
                        workoutId = WorkoutId(id),
                        liftId = LiftId("bench-press"),
                        progressionScheme = ProgressionScheme.Linear,
                        position = 0,
                        sets =
                            listOf(
                                LiftSet(
                                    id = LiftSetId("ls-$id"),
                                    workoutLiftId = WorkoutLiftId("wl-$id"),
                                    position = 0,
                                    setType = SetType.Standard,
                                    repMin = 8,
                                    repMax = 12,
                                    rirGoal = 2,
                                    isTopSet = true,
                                ),
                            ),
                    ),
                ),
        )
}
