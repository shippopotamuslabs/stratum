package com.stratum.feature.program

import com.stratum.application.program.ComputeWorkoutMuscleVolumeUseCase
import com.stratum.application.program.EstimateWorkoutDurationMinutesUseCase
import com.stratum.core.ui.component.StratumMuscleVolumeCardState
import com.stratum.core.ui.component.toLowerVolumeCards
import com.stratum.core.ui.component.toUpperVolumeCards
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
    @DisplayName("strip muscles are the program cards, including the secondary credit")
    fun stripMusclesMatchProgramCards() {
        val days = fixtureDays()
        val sheet = derive(days)
        val strip = programSheetVolumeStrip(sheet, days, DAY_A)
        val cards = sheet.programCards().filter { card -> card.setCount != 0.0 }

        assertEquals(cards.map { card -> card.displayName }, strip.programMuscles.map { muscle -> muscle.muscleName })
        strip.programMuscles.forEach { muscle ->
            val card = cards.single { it.displayName == muscle.muscleName }
            assertEquals(card.setCount, muscle.sets)
        }

        val primary = cards.single { card -> card.muscle == MuscleGroup.Chest }
        val secondary = cards.single { card -> card.muscle == MuscleGroup.Tricep }
        assertNotEquals(primary.setCount, secondary.setCount)
        assertEquals(primary.setCount, strip.programMuscles.single { it.muscleName == primary.displayName }.sets)
        assertEquals(secondary.setCount, strip.programMuscles.single { it.muscleName == secondary.displayName }.sets)
        assertEquals(DAY_A, strip.workoutDayId)
        assertEquals("Push", strip.workoutName)
        assertEquals(sheet.workouts.single { it.dayId == DAY_A }.setTotal, strip.workoutSetTotal)
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
            after.programCards().first { card ->
                before.programCards().single { it.displayName == card.displayName }.setCount != card.setCount
            }.displayName
        val stripDelta =
            stripAfter.programMuscles.single { it.muscleName == changedName }.sets -
                stripBefore.programMuscles.single { it.muscleName == changedName }.sets
        val programDelta =
            after.programCards().single { it.displayName == changedName }.setCount -
                before.programCards().single { it.displayName == changedName }.setCount
        assertEquals(programDelta, stripDelta)

        val row = pivotAfter.rows.indexOfFirst { it.muscleName == changedName }
        val columnA = pivotAfter.columns.indexOfFirst { it.dayId == DAY_A }
        val cellDelta =
            pivotAfter.rows[row].cells[columnA] -
                pivotBefore.rows[row].cells[columnA]
        val workoutDelta =
            afterWorkout.cards().single { it.displayName == changedName }.setCount -
                beforeWorkout.cards().single { it.displayName == changedName }.setCount
        assertEquals(workoutDelta, cellDelta)
    }

    @Test
    @DisplayName("rest is not a pivot column and a missing day keeps the program line")
    fun restAndMissingDay() {
        val days = fixtureDays()
        val sheet = derive(days)
        val pivot = programSheetVolumePivot(sheet, days)
        val present = programSheetVolumeStrip(sheet, days, DAY_A)

        assertTrue(pivot.columns.none { column -> column.dayId == DAY_REST })
        assertEquals(listOf(DAY_A, DAY_B), pivot.columns.map { column -> column.dayId })

        val missing = programSheetVolumeStrip(sheet, days, "missing-day")
        assertNull(missing.workoutDayId)
        assertEquals("", missing.workoutName)
        assertNull(missing.workoutSetTotal)
        assertEquals(present.programMuscles, missing.programMuscles)

        val rest = programSheetVolumeStrip(sheet, days, DAY_REST)
        assertNull(rest.workoutSetTotal)
        assertEquals("", rest.workoutName)
        assertEquals(present.programMuscles, rest.programMuscles)
    }

    @Test
    @DisplayName("a column total sums that column's cells and is not the workout set total")
    fun columnTotalSumsCells() {
        val days = fixtureDays()
        val sheet = derive(days)
        val pivot = programSheetVolumePivot(sheet, days)

        assertEquals(pivot.rows.sumOf { row -> row.programTotal }, pivot.cornerTotal)
        assertTrue(
            pivot.columns.indices.any { index ->
                val setTotal = sheet.workouts.single { it.dayId == pivot.columns[index].dayId }.setTotal.toDouble()
                pivot.columns[index].total != setTotal
            },
        )
        pivot.columns.forEachIndexed { index, column ->
            val cellSum = pivot.rows.sumOf { row -> row.cells[index] }
            assertEquals(cellSum, column.total)
            val workout = sheet.workouts.single { it.dayId == column.dayId }
            assertNotEquals(workout.setTotal.toDouble(), column.total)
            pivot.rows.forEach { row ->
                val card = workout.cards().firstOrNull { it.muscle == row.muscle }
                assertEquals(card?.setCount ?: 0.0, row.cells[index])
            }
        }
        assertEquals(
            sheet.programCards().filter { it.setCount != 0.0 }.map { it.setCount },
            pivot.rows.map { row -> row.programTotal },
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

        assertEquals(listOf(DAY_A), pivot.columns.map { column -> column.dayId })
        assertEquals(listOf(2.0), pivot.rows.single().cells)
        assertEquals(listOf(2.0), pivot.columns.map { column -> column.total })
        assertEquals(listOf(9.0), pivot.rows.map { row -> row.programTotal })
        assertEquals(9.0, pivot.cornerTotal)
        assertNotEquals(pivot.columns.single().total, pivot.rows.single().programTotal)
        assertNotEquals(4.0, pivot.rows.single().cells.single())
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
        assertEquals(programChest.setCount, strip.programMuscles.single { it.muscleName == "Chest" }.sets)
        assertEquals(programQuad.setCount, strip.programMuscles.single { it.muscleName == "Quads" }.sets)
    }

    @Test
    @DisplayName("program cards come from the first day that holds them, not the first edit day")
    fun programCardsSkipEmptyFirstDay() {
        val programChest = card(MuscleGroup.Chest, "Chest", sets = 9.0)
        val days =
            listOf(
                day(
                    id = DAY_A,
                    name = "Push",
                    isRest = false,
                    lifts = listOf(editLift(workoutLiftId = LIFT_A, liftId = BENCH_ID, setCount = SETS_A)),
                ),
                day(DAY_B, "Legs", isRest = false).let { legs ->
                    val edit = requireNotNull(legs.editState)
                    legs.copy(editState = edit.copy(programUpperVolumeByMuscle = listOf(programChest)))
                },
            )

        val metrics = programSheetMetricsFromEditorDays(days)
        val strip = programSheetVolumeStrip(metrics, days, DAY_A)

        assertEquals(listOf(programChest), metrics.programUpperVolumeByMuscle)
        assertEquals(listOf(9.0), strip.programMuscles.map { muscle -> muscle.sets })
    }

    @Test
    @DisplayName("pivot cells key on the muscle, so two cards sharing a display name keep their own values")
    fun pivotCellsKeyOnMuscle() {
        val metrics =
            ProgramSheetMetrics(
                workouts =
                    listOf(
                        ProgramSheetWorkoutMetrics(
                            dayId = DAY_A,
                            setTotal = 3,
                            estimatedDurationMinutes = 10,
                            upperVolumeByMuscle =
                                listOf(
                                    card(MuscleGroup.AnteriorDeltoid, "Delts", sets = 2.0),
                                    card(MuscleGroup.PosteriorDeltoid, "Delts", sets = 1.0),
                                ),
                            lowerVolumeByMuscle = emptyList(),
                        ),
                    ),
                programUpperVolumeByMuscle =
                    listOf(
                        card(MuscleGroup.AnteriorDeltoid, "Delts", sets = 5.0),
                        card(MuscleGroup.PosteriorDeltoid, "Delts", sets = 3.0),
                    ),
                programLowerVolumeByMuscle = emptyList(),
            )
        val days = listOf(day(DAY_A, "Push", isRest = false))

        val pivot = programSheetVolumePivot(metrics, days)

        assertEquals(listOf(2.0, 1.0), pivot.rows.map { row -> row.cells.single() })
        assertEquals(listOf(5.0, 3.0), pivot.rows.map { row -> row.programTotal })
    }

    @Test
    @DisplayName("muscle labels use one decimal in Locale.US")
    fun muscleLabelUsesUsDecimal() {
        assertEquals("Chest 13.0", programSheetVolumeMuscleLabel("Chest", 13.0))
        val fractional = programSheetVolumeMuscleLabel("Back", 2.5)
        assertEquals("Back 2.5", fractional)
        assertFalse(fractional.contains(','))
    }

    private fun derive(days: List<CreateProgramDayUi>): ProgramSheetMetrics =
        programSheetMetricsFromEditorDays(days.withStoredCards())

    /**
     * Stores volume cards on the edit states the way the editor does, so the
     * sheet metrics read the stored-cards path instead of a derivation.
     */
    private fun List<CreateProgramDayUi>.withStoredCards(): List<CreateProgramDayUi> {
        val programSummaries =
            computeWorkoutMuscleVolume(
                flatMap { day -> day.editState?.lifts?.toVolumeInputs().orEmpty() },
                CATALOG,
            )
        return map { day ->
            val edit = day.editState ?: return@map day
            val derived =
                edit.withDerivedMetrics(
                    CATALOG,
                    null,
                    computeWorkoutMuscleVolume,
                    estimateWorkoutDurationMinutes,
                )
            day.copy(
                editState =
                    derived.copy(
                        programUpperVolumeByMuscle = programSummaries.toUpperVolumeCards(),
                        programLowerVolumeByMuscle = programSummaries.toLowerVolumeCards(),
                    ),
            )
        }
    }

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
