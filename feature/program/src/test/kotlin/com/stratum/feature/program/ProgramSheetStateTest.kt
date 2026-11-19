package com.stratum.feature.program

import com.stratum.feature.program.ui.DEFAULT_REP_MAX
import com.stratum.feature.program.ui.DEFAULT_REP_MIN
import com.stratum.feature.program.ui.DEFAULT_REST_SECONDS
import com.stratum.feature.program.ui.DEFAULT_RIR_GOAL
import com.stratum.feature.program.ui.EditLiftState
import com.stratum.feature.program.ui.EditSetState
import com.stratum.feature.program.ui.EditWorkoutUiState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ProgramSheetStateTest {
    @Test
    @DisplayName("adding a set on day A updates only that day")
    fun addSetOnDayA_updatesOnlyThatDay() {
        val days = fixtureDays()
        val updated =
            updateProgramDay(days, DAY_A) { loaded ->
                EditWorkoutLiftMutations.addSet(loaded, LIFT_A)
            }

        val dayA = updated.single { day -> day.id == DAY_A }
        val dayB = updated.single { day -> day.id == DAY_B }
        val rest = updated.single { day -> day.id == DAY_REST }
        val dayAEdit = requireNotNull(dayA.editState)

        assertEquals(SETS_A + 1, dayAEdit.lifts.sumOf { lift -> lift.sets.size })
        assertEquals(days.single { day -> day.id == DAY_B }, dayB)
        assertNull(rest.editState)
    }

    @Test
    @DisplayName("adding a set on day B composes with day A and leaves day A untouched")
    fun addSetOnDayB_composesWithDayA() {
        val afterA =
            updateProgramDay(fixtureDays(), DAY_A) { loaded ->
                EditWorkoutLiftMutations.addSet(loaded, LIFT_A)
            }
        val updated =
            updateProgramDay(afterA, DAY_B) { loaded ->
                EditWorkoutLiftMutations.addSet(loaded, LIFT_B)
            }

        val dayAEdit = requireNotNull(updated.single { day -> day.id == DAY_A }.editState)
        val dayBEdit = requireNotNull(updated.single { day -> day.id == DAY_B }.editState)
        assertEquals(SETS_A + 1, dayAEdit.lifts.sumOf { lift -> lift.sets.size })
        assertEquals(SETS_B + 1, dayBEdit.lifts.sumOf { lift -> lift.sets.size })
        assertEquals(dayAEdit, requireNotNull(afterA.single { day -> day.id == DAY_A }.editState))
        assertNull(updated.single { day -> day.id == DAY_REST }.editState)
    }

    @Test
    @DisplayName("an unknown day id and a rest day id leave the day list unchanged")
    fun updateProgramDay_unknownAndRestIdsLeaveDaysUnchanged() {
        val days = fixtureDays()

        assertEquals(
            days,
            updateProgramDay(days, "missing-day") { loaded ->
                EditWorkoutLiftMutations.addSet(loaded, LIFT_A)
            },
        )
        assertEquals(
            days,
            updateProgramDay(days, DAY_REST) { loaded ->
                EditWorkoutLiftMutations.addSet(loaded, LIFT_A)
            },
        )
    }

    private fun fixtureDays(): List<CreateProgramDayUi> =
        listOf(
            CreateProgramDayUi(
                id = DAY_A,
                name = "Push",
                position = 0,
                isRest = false,
                editState =
                    loaded(
                        workoutId = DAY_A,
                        name = "Push",
                        lifts = listOf(editLift(workoutLiftId = LIFT_A, setCount = SETS_A)),
                    ),
            ),
            CreateProgramDayUi(
                id = DAY_REST,
                name = "Rest",
                position = 1,
                isRest = true,
                editState = null,
            ),
            CreateProgramDayUi(
                id = DAY_B,
                name = "Legs",
                position = 2,
                isRest = false,
                editState =
                    loaded(
                        workoutId = DAY_B,
                        name = "Legs",
                        lifts = listOf(editLift(workoutLiftId = LIFT_B, setCount = SETS_B)),
                    ),
            ),
        )

    private fun loaded(
        workoutId: String,
        name: String,
        lifts: List<EditLiftState>,
    ): EditWorkoutUiState.Loaded =
        EditWorkoutUiState.Loaded(
            workoutId = workoutId,
            workoutName = name,
            lifts = lifts,
        )

    private fun editLift(
        workoutLiftId: String,
        setCount: Int,
    ): EditLiftState =
        EditLiftState(
            workoutLiftId = workoutLiftId,
            liftId = workoutLiftId,
            name = workoutLiftId,
            position = 0,
            sets =
                (1..setCount).map { index ->
                    EditSetState(
                        setId = "set-$workoutLiftId-$index",
                        setNumber = index,
                        repMin = DEFAULT_REP_MIN,
                        repMax = DEFAULT_REP_MAX,
                        restSeconds = DEFAULT_REST_SECONDS,
                        rirGoal = DEFAULT_RIR_GOAL,
                    )
                },
        )

    private companion object {
        const val DAY_A = "day-a"
        const val DAY_B = "day-b"
        const val DAY_REST = "day-rest"
        const val LIFT_A = "wl-a"
        const val LIFT_B = "wl-b"
        const val SETS_A = 2
        const val SETS_B = 3
    }
}
