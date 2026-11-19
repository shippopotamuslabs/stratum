package com.stratum.feature.program

import com.stratum.application.program.ComputeWorkoutMuscleVolumeUseCase
import com.stratum.application.program.EstimateWorkoutDurationMinutesUseCase
import com.stratum.domain.program.ProgressionScheme
import com.stratum.feature.program.ui.EditLiftState
import com.stratum.feature.program.ui.EditSetKind
import com.stratum.feature.program.ui.EditSetState
import com.stratum.feature.program.ui.EditWorkoutUiState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ProgramSheetBulkEditTest {
    private val computeWorkoutMuscleVolume = ComputeWorkoutMuscleVolumeUseCase()
    private val estimateWorkoutDurationMinutes = EstimateWorkoutDurationMinutesUseCase()

    @Test
    @DisplayName("Given three lifts on two days when one set is added then only those lifts grow and one undo restores them")
    fun addSetOnTwoDaysUndoRestoresSetIds() {
        val days =
            listOf(
                day(
                    "a",
                    listOf(
                        lift("a1", sets("a1", 1)),
                        lift("a2", sets("a2", 2)),
                        lift("other", sets("other", 2)),
                    ),
                ),
                day("b", listOf(lift("b1", sets("b1", 1)))),
            )
        val selection =
            listOf(
                ProgramSheetLiftRef("a", "a1"),
                ProgramSheetLiftRef("a", "a2"),
                ProgramSheetLiftRef("b", "b1"),
            )
        val beforeTotal = sheetSetTotal(days)

        val edited = addSetToSelectedSheetLifts(days, selection)

        assertEquals(2, setsOf(edited.days, "a", "a1").size)
        assertEquals(3, setsOf(edited.days, "a", "a2").size)
        assertEquals(2, setsOf(edited.days, "b", "b1").size)
        assertEquals(setsOf(days, "a", "other"), setsOf(edited.days, "a", "other"))
        assertSame(lift(days, "a", "other"), lift(edited.days, "a", "other"))
        assertEquals(beforeTotal + 3, sheetSetTotal(edited.days))
        assertEquals(selection, edited.selection)

        val restored = restoreProgramSheetLiftOrder(edited.days, edited.undo)
        assertEquals(setIds(days, "a", "a1"), setIds(restored, "a", "a1"))
        assertEquals(setIds(days, "a", "a2"), setIds(restored, "a", "a2"))
        assertEquals(setIds(days, "b", "b1"), setIds(restored, "b", "b1"))
        assertEquals(1, setsOf(restored, "a", "a1").size)
        assertEquals(2, setsOf(restored, "a", "a2").size)
        assertEquals(1, setsOf(restored, "b", "b1").size)
    }

    @Test
    @DisplayName("Given a 1-set lift and a longer lift when one set is removed then only the last extra set goes")
    fun removeSetLeavesTheOneSetFloor() {
        val single = lift("solo", sets("solo", 1))
        val longer = lift("more", sets("more", 3))
        val days = listOf(day("a", listOf(single)), day("b", listOf(longer, lift("keep", sets("keep", 2)))))
        val selection = listOf(ProgramSheetLiftRef("a", "solo"), ProgramSheetLiftRef("b", "more"))

        val edited = removeSetFromSelectedSheetLifts(days, selection)

        assertSame(days.first(), edited.days.first())
        assertEquals(listOf("solo-1"), setIds(edited.days, "a", "solo"))
        assertEquals(listOf("more-1", "more-2"), setIds(edited.days, "b", "more"))
        assertEquals(setIds(days, "b", "keep"), setIds(edited.days, "b", "keep"))
        assertEquals(selection, edited.selection)
    }

    @Test
    @DisplayName("Given Double and Linear lifts when a rep band is set then Double backoffs stay empty and Linear takes the band")
    fun repBandKeepsDoubleBackoffAndWritesLinear() {
        val doubleLift =
            lift(
                "double",
                listOf(
                    set("double-top", 1, min = 8, max = 12, top = true),
                    set("double-back", 2, min = 0, max = 0, top = false, kind = EditSetKind.MyoRepMatch),
                ),
                scheme = ProgressionScheme.Double,
            )
        val linear = lift("linear", sets("linear", 2), scheme = ProgressionScheme.Linear)
        val other = lift("other", sets("other", 2))
        val days = listOf(day("a", listOf(doubleLift, linear, other)))
        val selection = listOf(ProgramSheetLiftRef("a", "double"), ProgramSheetLiftRef("a", "linear"))

        val edited =
            setRepBandOnSelectedSheetLifts(days, selection, repMin = 6, repMax = 10)

        val doubled = lift(edited.days, "a", "double")
        assertEquals(6, doubled.sets[0].repMin)
        assertEquals(10, doubled.sets[0].repMax)
        assertEquals(0, doubled.sets[1].repMin)
        assertEquals(0, doubled.sets[1].repMax)
        assertEquals("double-back", doubled.sets[1].setId)
        assertEquals(EditSetKind.MyoRepMatch, doubled.sets[1].setKind)
        assertEquals(listOf(6 to 10, 6 to 10), lift(edited.days, "a", "linear").sets.map { it.repMin to it.repMax })
        assertSame(other, lift(edited.days, "a", "other"))
        assertEquals(selection, edited.selection)
    }

    @Test
    @DisplayName("Given selected lifts when RIR is set then every set on those lifts changes and the others do not")
    fun rirWritesEverySetOnSelectedLiftsOnly() {
        val selected =
            lift(
                "pick",
                listOf(
                    set("pick-1", 1, rir = 1),
                    set("pick-2", 2, min = 0, max = 0, rir = 3, top = false),
                ),
            )
        val other = lift("other", listOf(set("other-1", 1, rir = 2)))
        val days = listOf(day("a", listOf(selected, other)), day("b", listOf(lift("far", sets("far", 1)))))
        val selection = listOf(ProgramSheetLiftRef("a", "pick"), ProgramSheetLiftRef("b", "far"))

        val edited = setRirOnSelectedSheetLifts(days, selection, rir = 4)

        assertEquals(listOf(4, 4), lift(edited.days, "a", "pick").sets.map { it.rirGoal })
        assertEquals(listOf(8 to 12, 0 to 0), lift(edited.days, "a", "pick").sets.map { it.repMin to it.repMax })
        assertSame(other, lift(edited.days, "a", "other"))
        assertEquals(listOf(4), lift(edited.days, "b", "far").sets.map { it.rirGoal })
        assertEquals(selection, edited.selection)
    }

    @Test
    @DisplayName("Given three lifts when they move then they append in selection order with the same ids and no superset")
    fun moveAppendsInSelectionOrderAndKeepsIdentity() {
        val days =
            listOf(
                day(
                    "from",
                    listOf(
                        lift("a", sets("a", 1), groupId = GROUP),
                        lift("partner", sets("partner", 1), groupId = GROUP),
                        lift("spare", sets("spare", 1)),
                    ),
                ),
                day("mid", listOf(lift("c", sets("c", 2)))),
                day("to", listOf(lift("tail", sets("tail", 1)))),
            )
        val selection =
            listOf(
                ProgramSheetLiftRef("from", "a"),
                ProgramSheetLiftRef("mid", "c"),
                ProgramSheetLiftRef("from", "spare"),
            )

        val edited = moveSelectedSheetLifts(days, selection, "to")

        assertEquals(listOf("partner"), ids(edited.days, "from"))
        assertEquals(emptyList<String>(), ids(edited.days, "mid"))
        assertEquals(listOf("tail", "a", "c", "spare"), ids(edited.days, "to"))
        assertEquals(null, lift(edited.days, "from", "partner").supersetGroupId)
        listOf("a", "c", "spare").forEach { id ->
            val landed = lift(edited.days, "to", id)
            assertEquals(id, landed.workoutLiftId)
            assertEquals(null, landed.supersetGroupId)
            assertEquals(setIds(days, if (id == "c") "mid" else "from", id), landed.sets.map { it.setId })
        }
        assertEquals(
            listOf(
                ProgramSheetLiftRef("to", "a"),
                ProgramSheetLiftRef("to", "c"),
                ProgramSheetLiftRef("to", "spare"),
            ),
            edited.selection,
        )
    }

    @Test
    @DisplayName("Given lifts already on the destination when they move then they append in selection order")
    fun moveOntoOwnWorkoutAppendsInSelectionOrder() {
        val days = listOf(day("day", listOf(lift("a", sets("a", 1)), lift("b", sets("b", 1)), lift("c", sets("c", 1)), lift("tail", sets("tail", 1)))))
        val selection = listOf(ProgramSheetLiftRef("day", "a"), ProgramSheetLiftRef("day", "c"))

        val edited = moveSelectedSheetLifts(days, selection, "day")

        assertEquals(listOf("b", "tail", "a", "c"), ids(edited.days, "day"))
        assertEquals(listOf("a-1"), setIds(edited.days, "day", "a"))
        assertEquals(listOf(ProgramSheetLiftRef("day", "a"), ProgramSheetLiftRef("day", "c")), edited.selection)
    }

    @Test
    @DisplayName("Given a superset pair when the selected lifts are deleted then the partner loses the group and undo restores both")
    fun deleteCleansSupersetAndUndoRestores() {
        val days =
            listOf(
                day(
                    "a",
                    listOf(
                        lift("gone", sets("gone", 2), groupId = GROUP),
                        lift("partner", sets("partner", 1), groupId = GROUP),
                        lift("keep", sets("keep", 1)),
                    ),
                ),
                day("b", listOf(lift("also", sets("also", 1)))),
            )
        val selection = listOf(ProgramSheetLiftRef("a", "gone"), ProgramSheetLiftRef("b", "also"))
        val beforeA = days.first { it.id == "a" }.editState!!.lifts
        val beforeB = days.first { it.id == "b" }.editState!!.lifts

        val edited = deleteSelectedSheetLifts(days, selection)

        assertEquals(listOf("partner", "keep"), ids(edited.days, "a"))
        assertEquals(null, lift(edited.days, "a", "partner").supersetGroupId)
        assertEquals(emptyList<String>(), ids(edited.days, "b"))
        assertEquals(emptyList<ProgramSheetLiftRef>(), edited.selection)
        assertEquals(setIds(days, "a", "keep"), setIds(edited.days, "a", "keep"))

        val restored = restoreProgramSheetLiftOrder(edited.days, edited.undo)
        assertEquals(beforeA.map { it.workoutLiftId }, ids(restored, "a"))
        assertEquals(beforeB.map { it.workoutLiftId }, ids(restored, "b"))
        assertEquals(GROUP, lift(restored, "a", "gone").supersetGroupId)
        assertEquals(GROUP, lift(restored, "a", "partner").supersetGroupId)
        assertEquals(setIds(days, "a", "gone"), setIds(restored, "a", "gone"))
    }

    @Test
    @DisplayName("Given an ordered selection when one id is toggled then it appends or drops without shuffling the rest")
    fun toggleAppendsAndRemovesWithoutReordering() {
        val first = ProgramSheetLiftRef("d1", "a")
        val second = ProgramSheetLiftRef("d1", "b")
        val third = ProgramSheetLiftRef("d2", "c")

        val selected =
            toggleProgramSheetSelection(
                toggleProgramSheetSelection(toggleProgramSheetSelection(emptyList(), first), second),
                third,
            )
        assertEquals(listOf(first, second, third), selected)
        assertEquals(listOf(first, third), toggleProgramSheetSelection(selected, second))
    }

    @Test
    @DisplayName("Given a rest day or a missing id when a bulk move runs then the day list is the same instance")
    fun restDayAndMissingIdLeaveTheList() {
        val days =
            listOf(
                day("from", listOf(lift("a", sets("a", 1)))),
                day("rest", emptyList(), rest = true),
                day("to", listOf(lift("there", sets("there", 1)))),
            )

        assertSame(days, moveSelectedSheetLifts(days, listOf(ProgramSheetLiftRef("from", "a")), "rest").days)
        assertSame(days, moveSelectedSheetLifts(days, listOf(ProgramSheetLiftRef("from", "missing")), "to").days)
        assertSame(days, addSetToSelectedSheetLifts(days, listOf(ProgramSheetLiftRef("from", "missing"))).days)
        assertSame(days, removeSetFromSelectedSheetLifts(days, listOf(ProgramSheetLiftRef("from", "a"))).days)
    }

    private fun sheetSetTotal(days: List<CreateProgramDayUi>): Int =
        deriveProgramSheet(
            days = days,
            liftCatalog = emptyList(),
            computeWorkoutMuscleVolume = computeWorkoutMuscleVolume,
            estimateWorkoutDurationMinutes = estimateWorkoutDurationMinutes,
        ).workouts.sumOf { workout -> workout.setTotal }

    private fun ids(days: List<CreateProgramDayUi>, dayId: String): List<String> =
        days.first { day -> day.id == dayId }.editState!!.lifts.map { lift -> lift.workoutLiftId }

    private fun lift(days: List<CreateProgramDayUi>, dayId: String, workoutLiftId: String): EditLiftState =
        days.first { day -> day.id == dayId }.editState!!.lifts.first { lift -> lift.workoutLiftId == workoutLiftId }

    private fun setsOf(days: List<CreateProgramDayUi>, dayId: String, workoutLiftId: String): List<EditSetState> =
        lift(days, dayId, workoutLiftId).sets

    private fun setIds(days: List<CreateProgramDayUi>, dayId: String, workoutLiftId: String): List<String> =
        setsOf(days, dayId, workoutLiftId).map { set -> set.setId }

    private fun day(
        id: String,
        lifts: List<EditLiftState>,
        rest: Boolean = false,
    ): CreateProgramDayUi =
        CreateProgramDayUi(
            id = id,
            name = id,
            position = 0,
            isRest = rest,
            editState =
                if (rest) {
                    null
                } else {
                    EditWorkoutUiState.Loaded(
                        workoutId = id,
                        workoutName = id,
                        lifts = lifts,
                    )
                },
        )

    private fun lift(
        id: String,
        sets: List<EditSetState>,
        scheme: ProgressionScheme = ProgressionScheme.Linear,
        groupId: String? = null,
    ): EditLiftState =
        EditLiftState(
            workoutLiftId = id,
            liftId = "catalog-$id",
            name = "Name $id",
            position = 0,
            sets = sets,
            supersetGroupId = groupId,
            useRestTimerBetweenLifts = groupId != null,
            isCompound = true,
            progressionScheme = scheme,
        )

    private fun sets(id: String, count: Int): List<EditSetState> =
        List(count) { index ->
            set(
                id = "$id-${index + 1}",
                number = index + 1,
                min = if (index == 0) 8 else 0,
                max = if (index == 0) 12 else 0,
                top = index == 0,
            )
        }

    private fun set(
        id: String,
        number: Int,
        min: Int = 8,
        max: Int = 12,
        rir: Int = 2,
        top: Boolean = number == 1,
        kind: EditSetKind = EditSetKind.Standard,
    ): EditSetState =
        EditSetState(
            setId = id,
            setNumber = number,
            repMin = min,
            repMax = max,
            restSeconds = 90,
            rirGoal = rir,
            isTopSet = top,
            setKind = kind,
        )

    private companion object {
        const val GROUP = "group-1"
    }
}
