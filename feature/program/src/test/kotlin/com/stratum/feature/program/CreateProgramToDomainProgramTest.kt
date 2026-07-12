package com.stratum.feature.program

import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.Program
import com.stratum.domain.program.Workout
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.feature.program.ui.EditLiftState
import com.stratum.feature.program.ui.EditSetState
import com.stratum.feature.program.ui.EditWorkoutUiState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Day tabs map onto a Mon–Sun calendar by list order, including rest slots as gaps.
 * Tab order is the sole binding source of truth on save (stored day.weekday is ignored).
 */
class CreateProgramToDomainProgramTest {
    @Test
    @DisplayName("Workout Rest Workout maps to Monday and Wednesday (rest slot is calendar gap)")
    fun scratchDraftRestSlotsCreateCalendarGaps() {
        val state =
            CreateProgramUiState(
                programId = "prog-scratch",
                programName = "My Scratch",
                days =
                    listOf(
                        dayUi(
                            id = "w-a",
                            name = "Workout 1",
                            position = 0,
                            isRest = false,
                            weekday = null,
                            lifts = listOf(editLift("wl-1", "bench-press", "Bench Press")),
                        ),
                        dayUi(
                            id = "rest-1",
                            name = "Rest",
                            position = 1,
                            isRest = true,
                            weekday = null,
                            lifts = emptyList(),
                        ),
                        dayUi(
                            id = "w-b",
                            name = "Workout 2",
                            position = 2,
                            isRest = false,
                            weekday = null,
                            lifts = listOf(editLift("wl-2", "squat", "Squat")),
                        ),
                    ),
            )

        val program = state.toDomainProgram(meta = null)

        assertEquals(2, program.workouts.size)
        assertEquals(setOf(Weekday.Monday, Weekday.Wednesday), program.trainingWeekdays)
        assertEquals(Weekday.Monday, program.workouts[0].weekday)
        assertEquals(Weekday.Wednesday, program.workouts[1].weekday)
        assertEquals(0, program.workouts[0].position)
        assertEquals(1, program.workouts[1].position)
        assertEquals("Workout 1", program.workouts[0].name)
        assertEquals("Workout 2", program.workouts[1].name)
        assertEquals("My Scratch", program.name)
    }

    @Test
    @DisplayName("leading rest slot shifts first workout off Monday")
    fun leadingRestMapsFirstWorkoutToTuesday() {
        val state =
            CreateProgramUiState(
                programId = "prog-scratch-2",
                programName = "Tue Start",
                days =
                    listOf(
                        dayUi(
                            id = "rest-0",
                            name = "Rest",
                            position = 0,
                            isRest = true,
                            weekday = null,
                            lifts = emptyList(),
                        ),
                        dayUi(
                            id = "w-a",
                            name = "Workout 1",
                            position = 1,
                            isRest = false,
                            weekday = null,
                            lifts = listOf(editLift("wl-1", "bench-press", "Bench Press")),
                        ),
                    ),
            )

        val program = state.toDomainProgram(meta = null)

        assertEquals(1, program.workouts.size)
        assertEquals(setOf(Weekday.Tuesday), program.trainingWeekdays)
        assertEquals(Weekday.Tuesday, program.workouts.single().weekday)
    }

    @Test
    @DisplayName("tab index always wins over stale day.weekday (reorder / sticky labels)")
    fun tabIndexOverridesStoredWeekday() {
        // Simulate post-load sticky weekdays that no longer match tab order after reorder.
        val state =
            CreateProgramUiState(
                programId = "prog-reordered",
                programName = "Reordered",
                days =
                    listOf(
                        dayUi(
                            id = "w-legs",
                            name = "Legs",
                            position = 0,
                            isRest = false,
                            weekday = Weekday.Friday, // stale after drag to front
                            lifts = listOf(editLift("wl-3", "squat", "Squat")),
                        ),
                        dayUi(
                            id = "rest-1",
                            name = "Rest",
                            position = 1,
                            isRest = true,
                            weekday = Weekday.Tuesday,
                            lifts = emptyList(),
                        ),
                        dayUi(
                            id = "w-push",
                            name = "Push",
                            position = 2,
                            isRest = false,
                            weekday = Weekday.Monday, // stale
                            lifts = listOf(editLift("wl-1", "bench-press", "Bench Press")),
                        ),
                    ),
            )

        val program = state.toDomainProgram(meta = null)

        assertEquals(setOf(Weekday.Monday, Weekday.Wednesday), program.trainingWeekdays)
        assertEquals(Weekday.Monday, program.workouts[0].weekday)
        assertEquals("Legs", program.workouts[0].name)
        assertEquals(Weekday.Wednesday, program.workouts[1].weekday)
        assertEquals("Push", program.workouts[1].name)
    }

    @Test
    @DisplayName("meta fields preserved when editing an existing program")
    fun metaPreservedOnEdit() {
        val state =
            CreateProgramUiState(
                programId = "prog-bound",
                programName = "PPL",
                days =
                    listOf(
                        dayUi(
                            id = "w-push",
                            name = "Push",
                            position = 0,
                            isRest = false,
                            weekday = Weekday.Monday,
                            lifts = listOf(editLift("wl-1", "bench-press", "Bench Press")),
                        ),
                        dayUi(
                            id = "w-pull",
                            name = "Pull",
                            position = 1,
                            isRest = false,
                            weekday = Weekday.Wednesday,
                            lifts = listOf(editLift("wl-2", "row", "Row")),
                        ),
                        dayUi(
                            id = "w-legs",
                            name = "Legs",
                            position = 2,
                            isRest = false,
                            weekday = Weekday.Friday,
                            lifts = listOf(editLift("wl-3", "squat", "Squat")),
                        ),
                    ),
            )
        val meta =
            LoadedProgramMeta(
                createdAt = Instant.parse("2026-01-15T12:00:00Z"),
                isActive = true,
                currentCycleNumber = 2,
                cyclePosition = 1,
                cycleLength = 3,
            )

        val program = state.toDomainProgram(meta)

        // Positions 0,1,2 with no rest → Mon, Tue, Wed (tab index, not stored Fri/Wed/Mon).
        assertEquals(setOf(Weekday.Monday, Weekday.Tuesday, Weekday.Wednesday), program.trainingWeekdays)
        assertEquals(Instant.parse("2026-01-15T12:00:00Z"), program.createdAt)
        assertTrue(program.isActive)
        assertEquals(2, program.currentCycleNumber)
        assertEquals(1, program.cyclePosition)
    }

    @Test
    @DisplayName("toCreateProgramDays does not stamp fallback Monday onto unbound workouts")
    fun toCreateProgramDaysKeepsUnboundWorkoutWeekdaysNull() {
        val program =
            Program(
                id = ProgramId("p-unbound"),
                name = "Legacy",
                createdAt = Instant.parse("2026-01-15T12:00:00Z"),
                cycleLength = 2,
                trainingWeekdays = emptySet(),
                workouts =
                    listOf(
                        Workout(
                            id = WorkoutId("w-0"),
                            programId = ProgramId("p-unbound"),
                            name = "A",
                            weekday = null,
                            position = 0,
                            workoutLifts = emptyList(),
                        ),
                        Workout(
                            id = WorkoutId("w-1"),
                            programId = ProgramId("p-unbound"),
                            name = "B",
                            weekday = null,
                            position = 1,
                            workoutLifts = emptyList(),
                        ),
                    ),
            )

        val days = program.toCreateProgramDays(emptyMap(), emptyMap())

        assertEquals(2, days.size)
        assertTrue(days.none { day -> day.isRest })
        assertNull(days[0].weekday)
        assertNull(days[1].weekday)

        // Round-trip: unbound load → save assigns Mon/Tue by tab order (no Monday collapse).
        val state =
            CreateProgramUiState(
                programId = program.id.value,
                programName = program.name,
                days =
                    days.mapIndexed { index, day ->
                        day.copy(
                            editState =
                                EditWorkoutUiState.Loaded(
                                    workoutId = day.id,
                                    workoutName = day.name,
                                    weekdayLabel = "",
                                    lifts = listOf(editLift("wl-$index", "lift-$index", "Lift $index")),
                                ),
                        )
                    },
            )
        val saved = state.toDomainProgram(meta = null)
        assertEquals(setOf(Weekday.Monday, Weekday.Tuesday), saved.trainingWeekdays)
        assertEquals(Weekday.Monday, saved.workouts[0].weekday)
        assertEquals(Weekday.Tuesday, saved.workouts[1].weekday)
    }

    private fun dayUi(
        id: String,
        name: String,
        position: Int,
        isRest: Boolean,
        weekday: Weekday?,
        lifts: List<EditLiftState>,
    ): CreateProgramDayUi =
        CreateProgramDayUi(
            id = id,
            name = name,
            position = position,
            isRest = isRest,
            editState =
                if (isRest) {
                    null
                } else {
                    EditWorkoutUiState.Loaded(
                        workoutId = id,
                        workoutName = name,
                        weekdayLabel = weekday?.name.orEmpty(),
                        lifts = lifts,
                    )
                },
            weekday = weekday,
        )

    private fun editLift(workoutLiftId: String, liftId: String, name: String): EditLiftState =
        EditLiftState(
            workoutLiftId = workoutLiftId,
            liftId = liftId,
            name = name,
            position = 0,
            sets =
                listOf(
                    EditSetState(
                        setId = "$workoutLiftId-s1",
                        setNumber = 1,
                        repMin = 8,
                        repMax = 12,
                        restSeconds = 120,
                        rirGoal = 2,
                        isTopSet = true,
                    ),
                ),
            progressionScheme = ProgressionScheme.Double,
        )
}
