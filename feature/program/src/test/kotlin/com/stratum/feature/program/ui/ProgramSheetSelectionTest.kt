package com.stratum.feature.program.ui

import com.stratum.feature.program.CreateProgramDayUi
import com.stratum.feature.program.ProgramSheetLiftTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ProgramSheetSelectionTest {
    @Test
    fun nullSelectionStaysNull() {
        assertNull(reconcileSheetSelection(selected = null, days = fixtureDays()))
    }

    @Test
    fun selectionInItsOwnDayIsUnchanged() {
        val selected = ProgramSheetLiftTarget(dayId = DAY_A, workoutLiftId = LIFT_BENCH)
        assertSame(selected, reconcileSheetSelection(selected, fixtureDays()))
    }

    @Test
    fun undoOfMoveRetargetsSelectionToTheDayHoldingTheLift() {
        // The STR-320 repro at the model level: the selection still points at
        // the workout the lift was moved to, but the undo put the lift back.
        val selected = ProgramSheetLiftTarget(dayId = DAY_B, workoutLiftId = LIFT_BENCH)
        val reconciled = reconcileSheetSelection(selected, fixtureDays())
        assertEquals(ProgramSheetLiftTarget(dayId = DAY_A, workoutLiftId = LIFT_BENCH), reconciled)
    }

    @Test
    fun selectionForAGoneLiftClears() {
        val selected = ProgramSheetLiftTarget(dayId = DAY_A, workoutLiftId = "lift-gone")
        assertNull(reconcileSheetSelection(selected, fixtureDays()))
    }

    @Test
    fun selectionOnARestDayRetargetsToTheDayHoldingTheLift() {
        val selected = ProgramSheetLiftTarget(dayId = DAY_REST, workoutLiftId = LIFT_ROW)
        val reconciled = reconcileSheetSelection(selected, fixtureDays())
        assertEquals(ProgramSheetLiftTarget(dayId = DAY_B, workoutLiftId = LIFT_ROW), reconciled)
    }

    private fun fixtureDays(): List<CreateProgramDayUi> =
        listOf(
            day(id = DAY_A, name = "Push", lifts = listOf(editLift(LIFT_BENCH))),
            day(id = DAY_B, name = "Pull", lifts = listOf(editLift(LIFT_ROW))),
            day(id = DAY_REST, name = "Rest", lifts = emptyList(), isRest = true),
        )

    private fun day(
        id: String,
        name: String,
        lifts: List<EditLiftState>,
        isRest: Boolean = false,
    ): CreateProgramDayUi =
        CreateProgramDayUi(
            id = id,
            name = name,
            position = 0,
            isRest = isRest,
            editState =
                if (isRest) {
                    null
                } else {
                    EditWorkoutUiState.Loaded(
                        workoutId = id,
                        workoutName = name,
                        lifts = lifts,
                    )
                },
        )

    private fun editLift(workoutLiftId: String): EditLiftState =
        EditLiftState(
            workoutLiftId = workoutLiftId,
            liftId = workoutLiftId,
            name = workoutLiftId,
            position = 0,
            sets =
                listOf(
                    EditSetState(
                        setId = "set-$workoutLiftId-1",
                        setNumber = 1,
                        repMin = DEFAULT_REP_MIN,
                        repMax = DEFAULT_REP_MAX,
                        restSeconds = DEFAULT_REST_SECONDS,
                        rirGoal = DEFAULT_RIR_GOAL,
                    ),
                ),
        )

    private companion object {
        const val DAY_A = "day-a"
        const val DAY_B = "day-b"
        const val DAY_REST = "day-rest"
        const val LIFT_BENCH = "lift-bench"
        const val LIFT_ROW = "lift-row"
    }
}
