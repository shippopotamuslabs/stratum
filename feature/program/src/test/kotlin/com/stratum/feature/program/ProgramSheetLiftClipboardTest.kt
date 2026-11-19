package com.stratum.feature.program

import com.stratum.domain.program.ProgressionScheme
import com.stratum.feature.program.ui.EditLiftState
import com.stratum.feature.program.ui.EditSetKind
import com.stratum.feature.program.ui.EditSetState
import com.stratum.feature.program.ui.EditWorkoutUiState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ProgramSheetLiftClipboardTest {

    @Test
    @DisplayName("Given two workouts when a lift moves then it lands at the end with the same id and scheme")
    fun moveKeepsIdentityAndSchemeAndAppends() {
        val moving = schemeLift("move", position = 1, groupId = null)
        val days =
            listOf(
                day("from", listOf(schemeLift("keep", 0), moving, schemeLift("tail", 2))),
                day("to", listOf(schemeLift("there", 0))),
            )

        val moved =
            moveProgramSheetLift(
                days = days,
                fromDayId = "from",
                toDayId = "to",
                workoutLiftId = "move",
                targetPosition = 1,
            )

        assertEquals(listOf("keep", "tail"), ids(moved, "from"))
        assertEquals(listOf("there", "move"), ids(moved, "to"))
        val landed = lift(moved, "to", "move")
        assertEquals("move", landed.workoutLiftId)
        assertEquals(moving.sets, landed.sets)
        assertEquals(moving.progressionScheme, landed.progressionScheme)
        assertEquals(moving.pinnedNote, landed.pinnedNote)
        assertEquals(moving.equipmentId, landed.equipmentId)
        assertEquals(moving.equipmentName, landed.equipmentName)
        assertNull(landed.supersetGroupId)
        assertEquals(false, landed.useRestTimerBetweenLifts)
    }

    @Test
    @DisplayName("Given a superset pair when one lift moves then both lose the group")
    fun moveOutOfPairClearsBothGroups() {
        val days =
            listOf(
                day(
                    "from",
                    listOf(
                        schemeLift("a", 0, GROUP),
                        schemeLift("b", 1, GROUP),
                    ),
                ),
                day("to", listOf(schemeLift("there", 0))),
            )

        val moved =
            moveProgramSheetLift(
                days = days,
                fromDayId = "from",
                toDayId = "to",
                workoutLiftId = "b",
                targetPosition = 1,
            )

        assertNull(lift(moved, "from", "a").supersetGroupId)
        assertEquals(false, lift(moved, "from", "a").useRestTimerBetweenLifts)
        assertNull(lift(moved, "to", "b").supersetGroupId)
        assertEquals(listOf("a"), ids(moved, "from"))
    }

    @Test
    @DisplayName("Given a superset of three when one lift moves then the pair keeps the group")
    fun moveOutOfTripleKeepsThePair() {
        val days =
            listOf(
                day(
                    "from",
                    listOf(
                        schemeLift("a", 0, GROUP),
                        schemeLift("b", 1, GROUP),
                        schemeLift("c", 2, GROUP),
                    ),
                ),
                day("to", emptyList()),
            )

        val moved =
            moveProgramSheetLift(
                days = days,
                fromDayId = "from",
                toDayId = "to",
                workoutLiftId = "b",
                targetPosition = 0,
            )

        assertEquals(GROUP, lift(moved, "from", "a").supersetGroupId)
        assertEquals(GROUP, lift(moved, "from", "c").supersetGroupId)
        assertEquals(listOf("a", "c"), ids(moved, "from"))
        assertNull(lift(moved, "to", "b").supersetGroupId)
        assertEquals(movingSets(moved), lift(days, "from", "b").sets.map { set -> set.setId })
    }

    @Test
    @DisplayName("Given a lift already off the source when cut pastes then the source is not removed again")
    fun detachedPasteInsertsWithoutRemovingAgain() {
        val held = schemeLift("cut", 1, GROUP)
        val days =
            listOf(
                day("from", listOf(schemeLift("stay", 0))),
                day("to", listOf(schemeLift("there", 0))),
            )

        val pasted =
            moveProgramSheetLift(
                days = days,
                fromDayId = "from",
                toDayId = "to",
                workoutLiftId = "cut",
                targetPosition = 1,
                detachedLift = held,
            )

        assertSame(days.first { day -> day.id == "from" }, pasted.first { day -> day.id == "from" })
        assertEquals(listOf("there", "cut"), ids(pasted, "to"))
        assertEquals(held.sets, lift(pasted, "to", "cut").sets)
        assertNull(lift(pasted, "to", "cut").supersetGroupId)
    }

    @Test
    @DisplayName("Given a grouped lift when copied then the source stays and the copy is a new standalone lift")
    fun copyKeepsSourceAndMintsIdentity() {
        val days =
            listOf(
                day(
                    "from",
                    listOf(
                        schemeLift("a", 0, GROUP),
                        schemeLift("b", 1, GROUP),
                    ),
                ),
                day("to", listOf(schemeLift("there", 0))),
            )
        var setIndex = 0

        val copied =
            copyProgramSheetLift(
                days = days,
                fromDayId = "from",
                toDayId = "to",
                workoutLiftId = "a",
                targetPosition = 1,
                newWorkoutLiftId = { "a-copy" },
                newSetId = { "set-copy-${setIndex++}" },
            )

        assertSame(days.first { day -> day.id == "from" }, copied.first { day -> day.id == "from" })
        assertEquals(GROUP, lift(copied, "from", "a").supersetGroupId)
        val duplicate = lift(copied, "to", "a-copy")
        assertEquals(listOf("there", "a-copy"), ids(copied, "to"))
        assertNull(duplicate.supersetGroupId)
        assertEquals(false, duplicate.useRestTimerBetweenLifts)
        assertEquals(listOf("set-copy-0", "set-copy-1"), duplicate.sets.map { set -> set.setId })
        assertEquals(
            lift(days, "from", "a").sets.map { set -> set.copy(setId = "") },
            duplicate.sets.map { set -> set.copy(setId = "") },
        )
        assertEquals(ProgressionScheme.Linear, duplicate.progressionScheme)
        assertEquals("pin-a", duplicate.pinnedNote)
        assertEquals("eq-a", duplicate.equipmentId)
    }

    @Test
    @DisplayName("Given the same workout when copied in place then the duplicate appends after the original")
    fun copyInPlaceAppendsAfterOriginal() {
        val days = listOf(day("day", listOf(schemeLift("a", 0, GROUP), schemeLift("b", 1))))

        val copied =
            copyProgramSheetLift(
                days = days,
                fromDayId = "day",
                toDayId = "day",
                workoutLiftId = "a",
                targetPosition = 2,
                newWorkoutLiftId = { "a-copy" },
                newSetId = { "fresh" },
            )

        assertEquals(listOf("a", "b", "a-copy"), ids(copied, "day"))
        assertEquals(GROUP, lift(copied, "day", "a").supersetGroupId)
        assertNull(lift(copied, "day", "a-copy").supersetGroupId)
        assertEquals(listOf("fresh", "fresh"), lift(copied, "day", "a-copy").sets.map { set -> set.setId })
    }

    @Test
    @DisplayName("Given a move when the prior lists are restored then both days get their lift order and ids back")
    fun restorePutsBothDaysBack() {
        val days =
            listOf(
                day(
                    "from",
                    listOf(
                        schemeLift("a", 0, GROUP),
                        schemeLift("b", 1, GROUP),
                        schemeLift("c", 2),
                    ),
                ),
                day("to", listOf(schemeLift("there", 0))),
            )
        val beforeFrom = days.first { day -> day.id == "from" }.editState!!.lifts
        val beforeTo = days.first { day -> day.id == "to" }.editState!!.lifts
        val moved =
            moveProgramSheetLift(
                days = days,
                fromDayId = "from",
                toDayId = "to",
                workoutLiftId = "b",
                targetPosition = beforeTo.size,
            )

        val restored =
            restoreProgramSheetLiftOrder(
                days = moved,
                liftsByDayId = mapOf("from" to beforeFrom, "to" to beforeTo),
            )

        assertEquals(listOf("a", "b", "c"), ids(restored, "from"))
        assertEquals(listOf("there"), ids(restored, "to"))
        assertEquals(beforeFrom.map { lift -> lift.supersetGroupId }, groups(restored, "from"))
        assertEquals(beforeTo.map { lift -> lift.supersetGroupId }, groups(restored, "to"))
        assertEquals(setIds(beforeFrom), setIds(lifts(restored, "from")))
        assertEquals(setIds(beforeTo), setIds(lifts(restored, "to")))
    }

    @Test
    @DisplayName("Given a missing id or a rest day when move or copy runs then the day list is unchanged")
    fun missingIdsAndRestDaysAreNoOps() {
        val days =
            listOf(
                day("from", listOf(schemeLift("a", 0))),
                day("rest", emptyList(), rest = true),
            )

        assertSame(
            days,
            moveProgramSheetLift(days, "from", "rest", "a", 0),
        )
        assertSame(
            days,
            moveProgramSheetLift(days, "from", "to", "missing", 0),
        )
        assertSame(
            days,
            copyProgramSheetLift(days, "from", "rest", "a", 0, newWorkoutLiftId = { "nope" }),
        )
        assertSame(days, restoreProgramSheetLiftOrder(days, emptyMap()))
    }

    private fun movingSets(days: List<CreateProgramDayUi>): List<String> =
        lift(days, "to", "b").sets.map { set -> set.setId }

    private fun ids(days: List<CreateProgramDayUi>, dayId: String): List<String> =
        lifts(days, dayId).map { lift -> lift.workoutLiftId }

    private fun groups(days: List<CreateProgramDayUi>, dayId: String): List<String?> =
        lifts(days, dayId).map { lift -> lift.supersetGroupId }

    private fun lifts(days: List<CreateProgramDayUi>, dayId: String): List<EditLiftState> =
        days.first { day -> day.id == dayId }.editState!!.lifts

    private fun lift(days: List<CreateProgramDayUi>, dayId: String, workoutLiftId: String): EditLiftState =
        lifts(days, dayId).first { lift -> lift.workoutLiftId == workoutLiftId }

    private fun setIds(lifts: List<EditLiftState>): List<String> =
        lifts.flatMap { lift -> lift.sets.map { set -> set.setId } }

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

    private fun schemeLift(id: String, position: Int, groupId: String? = null): EditLiftState =
        EditLiftState(
            workoutLiftId = id,
            liftId = "catalog-$id",
            name = "Name $id",
            position = position,
            sets =
                listOf(
                    EditSetState(
                        setId = "set-$id-1",
                        setNumber = 1,
                        repMin = 5,
                        repMax = 8,
                        restSeconds = 90,
                        rirGoal = 1,
                        isTopSet = true,
                        setKind = EditSetKind.MyoRepMatch,
                    ),
                    EditSetState(
                        setId = "set-$id-2",
                        setNumber = 2,
                        repMin = 8,
                        repMax = 12,
                        restSeconds = 75,
                        rirGoal = 3,
                        isTopSet = false,
                        setKind = EditSetKind.Standard,
                    ),
                ),
            supersetGroupId = groupId,
            useRestTimerBetweenLifts = groupId != null,
            isCompound = true,
            progressionScheme = ProgressionScheme.Linear,
            pinnedNote = "pin-$id",
            equipmentId = "eq-$id",
            equipmentName = "Bar $id",
        )

    private companion object {
        const val GROUP = "group-1"
    }
}
