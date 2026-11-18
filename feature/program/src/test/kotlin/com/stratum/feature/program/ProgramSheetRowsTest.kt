package com.stratum.feature.program

import com.stratum.feature.program.ui.EditLiftState
import com.stratum.feature.program.ui.EditSetState
import com.stratum.feature.program.ui.EditWorkoutUiState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ProgramSheetRowsTest {
    @Test
    @DisplayName("two workouts with a rest day between them keep order, anchors, and the visible day")
    fun twoWorkoutsAndRest() {
        val rows = programSheetRows(daysWithRestBetweenWorkouts())

        assertEquals(
            listOf(
                ProgramSheetRow.WorkoutHeader(DAY_A),
                ProgramSheetRow.ColumnHeader(DAY_A),
                ProgramSheetRow.Lift(DAY_A, LIFT_A1),
                ProgramSheetRow.Lift(DAY_A, LIFT_A2),
                ProgramSheetRow.Rest(DAY_REST),
                ProgramSheetRow.WorkoutHeader(DAY_B),
                ProgramSheetRow.ColumnHeader(DAY_B),
                ProgramSheetRow.Lift(DAY_B, LIFT_B),
            ),
            rows,
        )
        assertEquals(0, programSheetAnchorIndex(rows, DAY_A))
        assertEquals(4, programSheetAnchorIndex(rows, DAY_REST))
        assertEquals(5, programSheetAnchorIndex(rows, DAY_B))
        assertNull(programSheetAnchorIndex(rows, "missing"))
        assertEquals(DAY_A, programSheetActiveDayId(rows, 3))
        assertEquals(DAY_REST, programSheetActiveDayId(rows, 4))
        assertEquals(DAY_B, programSheetActiveDayId(rows, 7))
        assertNull(programSheetActiveDayId(rows, rows.size))
        assertNull(programSheetActiveDayId(rows, -1))
    }

    @Test
    @DisplayName("a fully visible sheet keeps the latched later day")
    fun followKeepsLatchedDayWhenEveryRowIsVisible() {
        val rows = programSheetRows(daysWithRestBetweenWorkouts())

        assertEquals(DAY_B, programSheetFollowDayId(rows, rows.indices.toList(), DAY_B))
    }

    @Test
    @DisplayName("a viewport that only shows an earlier day follows that day")
    fun followMovesToTheEarlierDayWhenTheLatchHasLeft() {
        val rows = programSheetRows(daysWithRestBetweenWorkouts())

        assertEquals(DAY_A, programSheetFollowDayId(rows, listOf(0, 1, 2, 3), DAY_B))
    }

    @Test
    @DisplayName("adjacent lifts skip a rest day and the index is the lift row")
    fun adjacentLiftSkipsRestDay() {
        val rows = programSheetRows(daysWithRestBetweenWorkouts())

        assertEquals(
            ProgramSheetLiftTarget(DAY_B, LIFT_B),
            programSheetAdjacentLift(rows, DAY_A, LIFT_A2, ProgramSheetLiftStep.Next),
        )
        assertEquals(
            ProgramSheetLiftTarget(DAY_A, LIFT_A2),
            programSheetAdjacentLift(rows, DAY_B, LIFT_B, ProgramSheetLiftStep.Previous),
        )
        assertEquals(
            ProgramSheetLiftTarget(DAY_A, LIFT_A2),
            programSheetAdjacentLift(rows, DAY_A, LIFT_A1, ProgramSheetLiftStep.Next),
        )
        assertNull(programSheetAdjacentLift(rows, DAY_A, LIFT_A1, ProgramSheetLiftStep.Previous))
        assertNull(programSheetAdjacentLift(rows, DAY_B, LIFT_B, ProgramSheetLiftStep.Next))
        assertNull(programSheetAdjacentLift(rows, DAY_A, "missing", ProgramSheetLiftStep.Next))
        assertEquals(2, programSheetLiftIndex(rows, DAY_A, LIFT_A1))
        assertEquals(3, programSheetLiftIndex(rows, DAY_A, LIFT_A2))
        assertEquals(7, programSheetLiftIndex(rows, DAY_B, LIFT_B))
        assertNull(programSheetLiftIndex(rows, DAY_A, "missing"))
    }

    @Test
    @DisplayName("an empty workout is a header and column header, and a rest day is one row")
    fun emptyWorkoutAndRest() {
        val days =
            listOf(
                workout(id = "empty", name = "Empty", position = 0, lifts = emptyList()),
                CreateProgramDayUi(
                    id = "rest",
                    name = "Rest",
                    position = 1,
                    isRest = true,
                    editState =
                        EditWorkoutUiState.Loaded(
                            workoutId = "rest",
                            workoutName = "Rest",
                            lifts = emptyList(),
                        ),
                ),
            )

        assertEquals(
            listOf(
                ProgramSheetRow.WorkoutHeader("empty"),
                ProgramSheetRow.ColumnHeader("empty"),
                ProgramSheetRow.Rest("rest"),
            ),
            programSheetRows(days),
        )
        assertEquals(2, programSheetAnchorIndex(programSheetRows(days), "rest"))
    }

    private fun daysWithRestBetweenWorkouts(): List<CreateProgramDayUi> =
        listOf(
            workout(
                id = DAY_A,
                name = "Push",
                position = 0,
                lifts = listOf(lift(LIFT_A1), lift(LIFT_A2)),
            ),
            CreateProgramDayUi(
                id = DAY_REST,
                name = "Rest",
                position = 1,
                isRest = true,
                editState = null,
            ),
            workout(
                id = DAY_B,
                name = "Pull",
                position = 2,
                lifts = listOf(lift(LIFT_B)),
            ),
        )

    private fun workout(
        id: String,
        name: String,
        position: Int,
        lifts: List<EditLiftState>,
    ): CreateProgramDayUi =
        CreateProgramDayUi(
            id = id,
            name = name,
            position = position,
            isRest = false,
            editState =
                EditWorkoutUiState.Loaded(
                    workoutId = id,
                    workoutName = name,
                    lifts = lifts,
                ),
        )

    private fun lift(workoutLiftId: String): EditLiftState =
        EditLiftState(
            workoutLiftId = workoutLiftId,
            liftId = workoutLiftId,
            name = workoutLiftId,
            position = 0,
            sets =
                listOf(
                    EditSetState(
                        setId = "$workoutLiftId-set",
                        setNumber = 1,
                        repMin = 5,
                        repMax = 8,
                        restSeconds = 120,
                        rirGoal = 2,
                    ),
                ),
        )

    private companion object {
        const val DAY_A = "day-a"
        const val DAY_B = "day-b"
        const val DAY_REST = "day-rest"
        const val LIFT_A1 = "wl-a1"
        const val LIFT_A2 = "wl-a2"
        const val LIFT_B = "wl-b"
    }
}
