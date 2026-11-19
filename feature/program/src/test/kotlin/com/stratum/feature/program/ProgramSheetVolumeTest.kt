package com.stratum.feature.program

import com.stratum.application.program.ComputeWorkoutMuscleVolumeUseCase
import com.stratum.application.program.EstimateWorkoutDurationMinutesUseCase
import com.stratum.core.ui.component.StratumMuscleVolumeCardState
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ProgramSheetVolumeTest {
    private val computeWorkoutMuscleVolume = ComputeWorkoutMuscleVolumeUseCase()
    private val estimateWorkoutDurationMinutes = EstimateWorkoutDurationMinutesUseCase()

    @Test
    @DisplayName("strip muscles are the visible workout, and the program total stays labeled")
    fun stripMusclesFollowVisibleWorkout() {
        val days = fixtureDays()
        val sheet = derive(days)
        val strip = programSheetVolumeStrip(sheet, days, DAY_A)
        val workout = sheet.workouts.single { it.dayId == DAY_A }
        val cards = workout.cards().filter { card -> card.setCount != 0.0 }

        assertEquals(cards.map { card -> card.displayName }, strip.workoutMuscles.map { muscle -> muscle.muscleName })
        strip.workoutMuscles.forEach { muscle ->
            val card = cards.single { it.displayName == muscle.muscleName }
            assertEquals(card.setCount, muscle.sets)
        }

        val primary = cards.single { card -> card.muscle == MuscleGroup.Chest }
        val secondary = cards.single { card -> card.muscle == MuscleGroup.Tricep }
        assertNotEquals(primary.setCount, secondary.setCount)
        assertEquals(primary.setCount, strip.workoutMuscles.single { it.muscleName == primary.displayName }.sets)
        assertEquals(secondary.setCount, strip.workoutMuscles.single { it.muscleName == secondary.displayName }.sets)
        assertEquals(DAY_A, strip.workoutDayId)
        assertEquals("Push", strip.workoutName)
        assertEquals(workout.setTotal, strip.workoutSetTotal)
        assertEquals(sheet.workouts.sumOf { it.setTotal }, strip.programSetTotal)

        val legs = programSheetVolumeStrip(sheet, days, DAY_B)
        assertNotEquals(strip.workoutMuscles, legs.workoutMuscles)
        assertEquals(strip.programSetTotal, legs.programSetTotal)
        assertEquals(
            sheet.workouts.single { it.dayId == DAY_B }.cards().filter { it.setCount != 0.0 }.map { it.displayName },
            legs.workoutMuscles.map { it.muscleName },
        )
    }

    @Test
    @DisplayName("adding a set moves the strip and the pivot cell by the metric deltas")
    fun addSetDeltasMatchMetrics() {
        val days = fixtureDays()
        val before = derive(days)
        val stripBefore = programSheetVolumeStrip(before, days, DAY_A)
        val pivotBefore = programSheetVolumePivot(before, days)

        val updated =
            updateProgramDay(days, DAY_A) { loaded ->
                EditWorkoutLiftMutations.addSet(loaded, LIFT_A)
            }
        val after = derive(updated)
        val stripAfter = programSheetVolumeStrip(after, updated, DAY_A)
        val pivotAfter = programSheetVolumePivot(after, updated)

        val beforeWorkout = before.workouts.single { it.dayId == DAY_A }
        val afterWorkout = after.workouts.single { it.dayId == DAY_A }
        assertEquals(beforeWorkout.setTotal, stripBefore.workoutSetTotal)
        assertEquals(afterWorkout.setTotal, stripAfter.workoutSetTotal)
        assertEquals(
            afterWorkout.setTotal - beforeWorkout.setTotal,
            requireNotNull(stripAfter.workoutSetTotal) - requireNotNull(stripBefore.workoutSetTotal),
        )

        val changedName =
            afterWorkout.cards().first { card ->
                beforeWorkout.cards().single { it.displayName == card.displayName }.setCount != card.setCount
            }.displayName
        val stripDelta =
            stripAfter.workoutMuscles.single { it.muscleName == changedName }.sets -
                stripBefore.workoutMuscles.single { it.muscleName == changedName }.sets
        val workoutMuscleDelta =
            afterWorkout.cards().single { it.displayName == changedName }.setCount -
                beforeWorkout.cards().single { it.displayName == changedName }.setCount
        assertEquals(workoutMuscleDelta, stripDelta)
        assertEquals(after.workouts.sumOf { it.setTotal }, stripAfter.programSetTotal)
        assertEquals(
            after.workouts.sumOf { it.setTotal } - before.workouts.sumOf { it.setTotal },
            stripAfter.programSetTotal - stripBefore.programSetTotal,
        )

        val row = pivotAfter.muscleNames.indexOf(changedName)
        val cellDelta =
            pivotAfter.workoutColumns.single { it.dayId == DAY_A }.cells[row] -
                pivotBefore.workoutColumns.single { it.dayId == DAY_A }.cells[row]
        val workoutDelta =
            afterWorkout.cards().single { it.displayName == changedName }.setCount -
                beforeWorkout.cards().single { it.displayName == changedName }.setCount
        assertEquals(workoutDelta, cellDelta)
    }

    @Test
    @DisplayName("rest is not a pivot column and a missing day keeps the program total")
    fun restAndMissingDay() {
        val days = fixtureDays()
        val sheet = derive(days)
        val pivot = programSheetVolumePivot(sheet, days)
        val present = programSheetVolumeStrip(sheet, days, DAY_A)

        assertTrue(pivot.workoutColumns.none { column -> column.dayId == DAY_REST })
        assertEquals(listOf(DAY_A, DAY_B), pivot.workoutColumns.map { column -> column.dayId })

        val missing = programSheetVolumeStrip(sheet, days, "missing-day")
        assertNull(missing.workoutDayId)
        assertEquals("", missing.workoutName)
        assertNull(missing.workoutSetTotal)
        assertEquals(emptyList<ProgramSheetMuscleSets>(), missing.workoutMuscles)
        assertEquals(present.programSetTotal, missing.programSetTotal)

        val rest = programSheetVolumeStrip(sheet, days, DAY_REST)
        assertNull(rest.workoutSetTotal)
        assertEquals("", rest.workoutName)
        assertEquals(emptyList<ProgramSheetMuscleSets>(), rest.workoutMuscles)
        assertEquals(present.programSetTotal, rest.programSetTotal)
    }

    @Test
    @DisplayName("a column total sums that column's cells and is not the workout set total")
    fun columnTotalSumsCells() {
        val days = fixtureDays()
        val sheet = derive(days)
        val pivot = programSheetVolumePivot(sheet, days)

        assertEquals(pivot.programTotals.sum(), pivot.cornerTotal)
        assertTrue(
            pivot.workoutColumns.indices.any { index ->
                val setTotal = sheet.workouts.single { it.dayId == pivot.workoutColumns[index].dayId }.setTotal.toDouble()
                pivot.columnTotals[index] != setTotal
            },
        )
        pivot.workoutColumns.forEachIndexed { index, column ->
            val cellSum = column.cells.sum()
            assertEquals(cellSum, pivot.columnTotals[index])
            val workout = sheet.workouts.single { it.dayId == column.dayId }
            assertNotEquals(workout.setTotal.toDouble(), pivot.columnTotals[index])
            column.cells.forEachIndexed { cellIndex, cell ->
                val name = pivot.muscleNames[cellIndex]
                val card = workout.cards().firstOrNull { it.displayName == name }
                assertEquals(card?.setCount ?: 0.0, cell)
            }
        }
        assertEquals(
            sheet.programCards().filter { it.setCount != 0.0 }.map { it.setCount },
            pivot.programTotals,
        )
    }

    @Test
    @DisplayName("the totals column is the program card, not the cell sum or the set total")
    fun totalsColumnIsProgramCard() {
        val programChest = card(MuscleGroup.Chest, "Chest", sets = 9.0)
        val workoutChest = card(MuscleGroup.Chest, "Chest", sets = 2.0)
        val metrics =
            ProgramSheetMetrics(
                workouts =
                    listOf(
                        ProgramSheetWorkoutMetrics(
                            dayId = DAY_A,
                            setTotal = 4,
                            estimatedDurationMinutes = 10,
                            upperVolumeByMuscle = listOf(workoutChest),
                            lowerVolumeByMuscle = emptyList(),
                        ),
                    ),
                programUpperVolumeByMuscle = listOf(programChest),
                programLowerVolumeByMuscle = emptyList(),
            )
        val days =
            listOf(
                day(DAY_A, "Push", isRest = false),
                day(DAY_REST, "Rest", isRest = true),
            )

        val pivot = programSheetVolumePivot(metrics, days)

        assertEquals(listOf(DAY_A), pivot.workoutColumns.map { column -> column.dayId })
        assertEquals(listOf(2.0), pivot.workoutColumns.single().cells)
        assertEquals(listOf(2.0), pivot.columnTotals)
        assertEquals(listOf(9.0), pivot.programTotals)
        assertEquals(9.0, pivot.cornerTotal)
        assertNotEquals(pivot.columnTotals.single(), pivot.programTotals.single())
        assertNotEquals(4.0, pivot.workoutColumns.single().cells.single())
    }

    @Test
    @DisplayName("editor days expose stored cards without recomputing volume")
    fun editorDaysAdapterReadsStoredCards() {
        val programChest = card(MuscleGroup.Chest, "Chest", sets = 9.0)
        val programQuad = card(MuscleGroup.Quad, "Quads", sets = 4.0)
        val pushChest = card(MuscleGroup.Chest, "Chest", sets = 2.0)
        val legsQuad = card(MuscleGroup.Quad, "Quads", sets = 4.0)
        val days =
            listOf(
                day(DAY_REST, "Rest", isRest = true),
                day(
                    id = DAY_A,
                    name = "Push",
                    isRest = false,
                    lifts = listOf(editLift(workoutLiftId = LIFT_A, liftId = BENCH_ID, setCount = SETS_A)),
                ).let { push ->
                    val edit = requireNotNull(push.editState)
                    push.copy(
                        editState =
                            edit.copy(
                                upperVolumeByMuscle = listOf(pushChest),
                                programUpperVolumeByMuscle = listOf(programChest),
                                programLowerVolumeByMuscle = listOf(programQuad),
                            ),
                    )
                },
                day(DAY_B, "Legs", isRest = false).let { legs ->
                    val edit = requireNotNull(legs.editState)
                    legs.copy(
                        editState =
                            edit.copy(
                                lowerVolumeByMuscle = listOf(legsQuad),
                                programUpperVolumeByMuscle = listOf(card(MuscleGroup.Back, "Back", sets = 1.0)),
                            ),
                    )
                },
            )

        val metrics = programSheetMetricsFromEditorDays(days)
        val strip = programSheetVolumeStrip(metrics, days, DAY_A)

        assertEquals(listOf(DAY_A, DAY_B), metrics.workouts.map { workout -> workout.dayId })
        assertEquals(listOf(programChest), metrics.programUpperVolumeByMuscle)
        assertEquals(listOf(programQuad), metrics.programLowerVolumeByMuscle)
        assertEquals(listOf(pushChest), metrics.workouts.single { it.dayId == DAY_A }.upperVolumeByMuscle)
        assertEquals(SETS_A, metrics.workouts.single { it.dayId == DAY_A }.setTotal)
        assertEquals(pushChest.setCount, strip.workoutMuscles.single { it.muscleName == "Chest" }.sets)
        assertEquals(false, strip.workoutMuscles.any { muscle -> muscle.muscleName == "Quads" })
        assertEquals(SETS_A, strip.programSetTotal)
    }

    @Test
    @DisplayName("muscle labels use one decimal in Locale.US")
    fun muscleLabelUsesUsDecimal() {
        assertEquals("Chest 13.0", programSheetVolumeMuscleLabel("Chest", 13.0))
        val fractional = programSheetVolumeMuscleLabel("Back", 2.5)
        assertEquals("Back 2.5", fractional)
        assertFalse(fractional.contains(','))
        assertEquals(
            "Chest 4.0 - Back 4.0 - Triceps 4.0",
            programSheetVolumeMuscleLine(
                listOf(
                    ProgramSheetMuscleSets("Chest", 4.0),
                    ProgramSheetMuscleSets("Back", 4.0),
                    ProgramSheetMuscleSets("Triceps", 4.0),
                ),
            ),
        )
    }

    private fun derive(days: List<CreateProgramDayUi>): ProgramSheetMetrics =
        deriveProgramSheet(
            days = days,
            liftCatalog = CATALOG,
            computeWorkoutMuscleVolume = computeWorkoutMuscleVolume,
            estimateWorkoutDurationMinutes = estimateWorkoutDurationMinutes,
        )

    private fun fixtureDays(): List<CreateProgramDayUi> =
        listOf(
            day(
                id = DAY_A,
                name = "Push",
                isRest = false,
                lifts = listOf(editLift(workoutLiftId = LIFT_A, liftId = BENCH_ID, setCount = SETS_A)),
            ),
            day(id = DAY_REST, name = "Rest", isRest = true),
            day(
                id = DAY_B,
                name = "Legs",
                isRest = false,
                lifts = listOf(editLift(workoutLiftId = LIFT_B, liftId = SQUAT_ID, setCount = SETS_B)),
            ),
        )

    private fun day(
        id: String,
        name: String,
        isRest: Boolean,
        lifts: List<EditLiftState> = emptyList(),
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

    private fun card(
        muscle: MuscleGroup,
        name: String,
        sets: Double,
    ): StratumMuscleVolumeCardState =
        StratumMuscleVolumeCardState(
            muscle = muscle,
            displayName = name,
            exerciseCount = 1,
            setCount = sets,
        )

    private fun ProgramSheetMetrics.programCards(): List<StratumMuscleVolumeCardState> =
        programUpperVolumeByMuscle + programLowerVolumeByMuscle

    private fun ProgramSheetWorkoutMetrics.cards(): List<StratumMuscleVolumeCardState> =
        upperVolumeByMuscle + lowerVolumeByMuscle

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
