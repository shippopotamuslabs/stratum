package com.stratum.feature.program

import com.stratum.application.program.ComputeWorkoutMuscleVolumeUseCase
import com.stratum.application.program.EstimateWorkoutDurationMinutesUseCase
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
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
    private val computeWorkoutMuscleVolume = ComputeWorkoutMuscleVolumeUseCase()
    private val estimateWorkoutDurationMinutes = EstimateWorkoutDurationMinutesUseCase()

    @Test
    @DisplayName("adding a set on day A updates only that day and folds both workouts into program volume")
    fun addSetOnDayA_updatesThatDayAndFoldsProgramVolume() {
        val days = fixtureDays()
        val updated =
            updateProgramDay(days, DAY_A) { loaded ->
                EditWorkoutLiftMutations.addSet(loaded, LIFT_A)
            }

        val dayA = updated.single { day -> day.id == DAY_A }
        val dayB = updated.single { day -> day.id == DAY_B }
        val rest = updated.single { day -> day.id == DAY_REST }
        val dayAEdit = requireNotNull(dayA.editState)
        val dayBEdit = requireNotNull(dayB.editState)

        assertEquals(SETS_A + 1, dayAEdit.lifts.sumOf { lift -> lift.sets.size })
        assertEquals(days.single { day -> day.id == DAY_B }, dayB)
        assertNull(rest.editState)

        val sheet =
            deriveProgramSheet(
                days = updated,
                liftCatalog = CATALOG,
                computeWorkoutMuscleVolume = computeWorkoutMuscleVolume,
                estimateWorkoutDurationMinutes = estimateWorkoutDurationMinutes,
            )
        assertEquals(listOf(DAY_A, DAY_B), sheet.workouts.map { workout -> workout.dayId })

        val dayAMetrics = sheet.workouts.single { workout -> workout.dayId == DAY_A }
        val derivedA =
            dayAEdit.withDerivedMetrics(
                liftCatalog = CATALOG,
                program = null,
                computeWorkoutMuscleVolume = computeWorkoutMuscleVolume,
                estimateWorkoutDurationMinutes = estimateWorkoutDurationMinutes,
            )
        assertEquals(SETS_A + 1, dayAMetrics.setTotal)
        assertEquals(derivedA.estimatedDurationMinutes, dayAMetrics.estimatedDurationMinutes)
        assertEquals(derivedA.upperVolumeByMuscle, dayAMetrics.upperVolumeByMuscle)
        assertEquals(derivedA.lowerVolumeByMuscle, dayAMetrics.lowerVolumeByMuscle)

        val combined =
            loaded(
                workoutId = "combined",
                name = "Combined",
                lifts = dayAEdit.lifts + dayBEdit.lifts,
            ).withDerivedMetrics(
                liftCatalog = CATALOG,
                program = null,
                computeWorkoutMuscleVolume = computeWorkoutMuscleVolume,
                estimateWorkoutDurationMinutes = estimateWorkoutDurationMinutes,
            )
        assertEquals(combined.upperVolumeByMuscle, sheet.programUpperVolumeByMuscle)
        assertEquals(combined.lowerVolumeByMuscle, sheet.programLowerVolumeByMuscle)
    }

    @Test
    @DisplayName("adding a set on day B composes with day A and keeps each day's own metrics")
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
        assertEquals(dayAEdit, requireNotNull(afterA.single { day -> day.id == DAY_A }.editState))
        assertNull(updated.single { day -> day.id == DAY_REST }.editState)

        val sheet =
            deriveProgramSheet(
                days = updated,
                liftCatalog = CATALOG,
                computeWorkoutMuscleVolume = computeWorkoutMuscleVolume,
                estimateWorkoutDurationMinutes = estimateWorkoutDurationMinutes,
            )
        val dayAMetrics = sheet.workouts.single { workout -> workout.dayId == DAY_A }
        val dayBMetrics = sheet.workouts.single { workout -> workout.dayId == DAY_B }
        assertEquals(SETS_A + 1, dayAMetrics.setTotal)
        assertEquals(SETS_B + 1, dayBMetrics.setTotal)

        val derivedA =
            dayAEdit.withDerivedMetrics(
                liftCatalog = CATALOG,
                program = null,
                computeWorkoutMuscleVolume = computeWorkoutMuscleVolume,
                estimateWorkoutDurationMinutes = estimateWorkoutDurationMinutes,
            )
        assertEquals(derivedA.estimatedDurationMinutes, dayAMetrics.estimatedDurationMinutes)
        assertEquals(derivedA.upperVolumeByMuscle, dayAMetrics.upperVolumeByMuscle)
        assertEquals(derivedA.lowerVolumeByMuscle, dayAMetrics.lowerVolumeByMuscle)

        val combined =
            loaded(
                workoutId = "combined",
                name = "Combined",
                lifts = dayAEdit.lifts + dayBEdit.lifts,
            ).withDerivedMetrics(
                liftCatalog = CATALOG,
                program = null,
                computeWorkoutMuscleVolume = computeWorkoutMuscleVolume,
                estimateWorkoutDurationMinutes = estimateWorkoutDurationMinutes,
            )
        assertEquals(combined.upperVolumeByMuscle, sheet.programUpperVolumeByMuscle)
        assertEquals(combined.lowerVolumeByMuscle, sheet.programLowerVolumeByMuscle)
    }

    @Test
    @DisplayName("edits on two days both land in toDomainProgram and the rest slot stays rest")
    fun editsOnTwoDays_landInDomainProgram() {
        val updated =
            updateProgramDay(
                updateProgramDay(fixtureDays(), DAY_A) { loaded ->
                    EditWorkoutLiftMutations.addSet(loaded, LIFT_A)
                },
                DAY_B,
            ) { loaded ->
                EditWorkoutLiftMutations.addSet(loaded, LIFT_B)
            }
        val state =
            CreateProgramUiState(
                step = CreateProgramStep.Editor,
                programId = "prog-sheet",
                programName = "Sheet",
                days = updated,
                selectedDayId = DAY_A,
            )

        val program = state.toDomainProgram(meta = null)

        assertEquals(listOf(DAY_A, DAY_B), program.workouts.map { workout -> workout.id.value })
        assertEquals(SETS_A + 1, program.workouts[0].workoutLifts.sumOf { lift -> lift.sets.size })
        assertEquals(SETS_B + 1, program.workouts[1].workoutLifts.sumOf { lift -> lift.sets.size })
        assertEquals(setOf(1), program.restSlotIndices)
        assertEquals(3, program.cycleLength)
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
                        lifts = listOf(editLift(workoutLiftId = LIFT_A, liftId = BENCH_ID, setCount = SETS_A)),
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
                        lifts = listOf(editLift(workoutLiftId = LIFT_B, liftId = SQUAT_ID, setCount = SETS_B)),
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
        liftId: String,
        setCount: Int,
    ): EditLiftState =
        EditLiftState(
            workoutLiftId = workoutLiftId,
            liftId = liftId,
            name = liftId,
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
        const val BENCH_ID = "bench-press"
        const val SQUAT_ID = "squat"
        const val SETS_A = 2
        const val SETS_B = 3

        val CATALOG: List<Lift> =
            listOf(
                catalogLift(
                    id = BENCH_ID,
                    name = "Bench Press",
                    primary = MuscleGroup.Chest,
                    secondary = MuscleGroup.Tricep,
                ),
                catalogLift(
                    id = SQUAT_ID,
                    name = "Squat",
                    primary = MuscleGroup.Quad,
                    secondary = MuscleGroup.Glute,
                ),
            )

        private fun catalogLift(
            id: String,
            name: String,
            primary: MuscleGroup,
            secondary: MuscleGroup,
        ): Lift =
            Lift(
                id = LiftId(id),
                name = name,
                movementPatterns = emptySet(),
                primaryMuscles = setOf(primary),
                secondaryMuscles = setOf(secondary),
                incrementTiers = emptyList(),
                presetTags = emptySet(),
                enabled = true,
            )
    }
}
