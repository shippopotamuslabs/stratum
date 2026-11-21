package com.stratum.feature.program

import com.stratum.domain.program.ProgressionScheme
import com.stratum.feature.program.ui.EditLiftState
import com.stratum.feature.program.ui.EditSetKind
import com.stratum.feature.program.ui.EditSetState
import com.stratum.feature.program.ui.EditWorkoutUiState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
    @DisplayName("Given a move when its undo is applied then both days get their lift order and ids back")
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

        val undo =
            sheetPasteUndo(
                clip =
                    ProgramSheetClipboard(
                        kind = ProgramSheetClipboardKind.Cut,
                        fromDayId = "from",
                        lift = beforeFrom[1],
                        originalIndex = 1,
                        sourceLiftsBefore = beforeFrom,
                    ),
                toDayId = "to",
                pastedWorkoutLiftId = "b",
            )
        val restored = applySheetUndo(days = moved, undo = undo)

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
        assertSame(days, applySheetUndo(days, ProgramSheetUndo(emptyMap())))
    }

    @Test
    @DisplayName("Given a clipboard snapshot when copied then the insert matches it even if the live row differs or is gone")
    fun copyPastesTheSnapshotNotTheLiveRow() {
        val live =
            schemeLift("a", 0).copy(
                sets = schemeLift("a", 0).sets.map { set -> set.copy(repMin = 1, repMax = 2) },
            )
        val snapshot =
            schemeLift("a", 0, GROUP).copy(
                sets = schemeLift("a", 0).sets.map { set -> set.copy(repMin = 9, repMax = 11) },
            )
        val days =
            listOf(
                day("from", listOf(live, schemeLift("b", 1))),
                day("to", listOf(schemeLift("there", 0))),
            )

        val copied =
            copyProgramSheetLift(
                days = days,
                fromDayId = "from",
                toDayId = "to",
                workoutLiftId = "a",
                targetPosition = 1,
                sourceLift = snapshot,
                newWorkoutLiftId = { "a-copy" },
                newSetId = { "snap-set" },
            )

        val inserted = lift(copied, "to", "a-copy")
        assertEquals(listOf(9, 9), inserted.sets.map { set -> set.repMin })
        assertEquals(listOf(11, 11), inserted.sets.map { set -> set.repMax })
        assertEquals(listOf("snap-set", "snap-set"), inserted.sets.map { set -> set.setId })
        assertNull(inserted.supersetGroupId)
        assertEquals(false, inserted.useRestTimerBetweenLifts)
        assertEquals(1, lift(copied, "from", "a").sets.first().repMin)

        val gone =
            listOf(
                day("from", listOf(schemeLift("b", 0))),
                day("to", listOf(schemeLift("there", 0))),
            )
        val pasted =
            copyProgramSheetLift(
                days = gone,
                fromDayId = "from",
                toDayId = "to",
                workoutLiftId = "a",
                targetPosition = 1,
                sourceLift = snapshot,
                newWorkoutLiftId = { "a-copy" },
                newSetId = { "snap-set" },
            )

        assertEquals(listOf("b"), ids(pasted, "from"))
        assertEquals(listOf(9, 9), lift(pasted, "to", "a-copy").sets.map { set -> set.repMin })
        assertEquals(listOf(11, 11), lift(pasted, "to", "a-copy").sets.map { set -> set.repMax })
        assertNull(lift(pasted, "to", "a-copy").supersetGroupId)
    }

    @Test
    @DisplayName("Given lifts added and edited after a cut when cancel returns it then they stay and the partner group comes back")
    fun cancelCutInsertsIntoTheCurrentList() {
        val cut = schemeLift("a", 0, GROUP).copy(useRestTimerBetweenLifts = true)
        val partner = schemeLift("b", 1, GROUP).copy(useRestTimerBetweenLifts = false)
        val before = listOf(cut, partner)
        val afterCut = removeLiftAndCleanupSupersets(before, "a")
        val edited =
            afterCut.map { lift ->
                if (lift.workoutLiftId != "b") {
                    lift
                } else {
                    lift.copy(sets = lift.sets.map { set -> set.copy(repMin = 4) })
                }
            } + schemeLift("added", afterCut.size)
        val days = listOf(day("from", edited), day("to", listOf(schemeLift("there", 0))))

        val returned =
            returnCutLiftToCurrentDay(
                days = days,
                fromDayId = "from",
                lift = cut,
                originalIndex = 0,
                sourceLiftsBefore = before,
            )

        assertEquals(listOf("a", "b", "added"), ids(returned, "from"))
        assertEquals(4, lift(returned, "from", "b").sets.first().repMin)
        assertEquals(GROUP, lift(returned, "from", "a").supersetGroupId)
        assertEquals(true, lift(returned, "from", "a").useRestTimerBetweenLifts)
        assertEquals(GROUP, lift(returned, "from", "b").supersetGroupId)
        assertEquals(false, lift(returned, "from", "b").useRestTimerBetweenLifts)
        assertNull(lift(returned, "from", "added").supersetGroupId)
        assertSame(days.first { day -> day.id == "to" }, returned.first { day -> day.id == "to" })
    }

    @Test
    @DisplayName("Given a partner already in another group when cancel returns the cut then that partner is left alone")
    fun cancelCutDoesNotOverwriteADifferentGroup() {
        val cut = schemeLift("a", 0, GROUP)
        val partner = schemeLift("b", 1, GROUP).copy(useRestTimerBetweenLifts = false)
        val regrouped = partner.copy(supersetGroupId = "other", useRestTimerBetweenLifts = true)
        val days = listOf(day("from", listOf(regrouped)))

        val returned =
            returnCutLiftToCurrentDay(
                days = days,
                fromDayId = "from",
                lift = cut,
                originalIndex = 0,
                sourceLiftsBefore = listOf(cut, partner),
            )

        val restoredPartner = lift(returned, "from", "b")
        assertEquals("other", restoredPartner.supersetGroupId)
        assertEquals(true, restoredPartner.useRestTimerBetweenLifts)
        assertEquals(GROUP, lift(returned, "from", "a").supersetGroupId)
    }

    @Test
    @DisplayName("Given a partner that kept the group when cancel returns the cut then the prior rest flag is restored")
    fun cancelCutRestoresThePriorRestFlag() {
        val cut = schemeLift("a", 0, GROUP)
        val partner = schemeLift("b", 1, GROUP).copy(useRestTimerBetweenLifts = false)
        val days = listOf(day("from", listOf(partner.copy(useRestTimerBetweenLifts = true))))

        val returned =
            returnCutLiftToCurrentDay(
                days = days,
                fromDayId = "from",
                lift = cut,
                originalIndex = 0,
                sourceLiftsBefore = listOf(cut, partner),
            )

        val restoredPartner = lift(returned, "from", "b")
        assertEquals(GROUP, restoredPartner.supersetGroupId)
        assertEquals(false, restoredPartner.useRestTimerBetweenLifts)
    }

    @Test
    @DisplayName("Given a coerced index or a missing source day when cancel returns a cut then the lift lands or the list is unchanged")
    fun cancelCutCoercesIndexAndNoOpsWhenTheDayIsGone() {
        val cut = schemeLift("a", 0)
        val days = listOf(day("from", listOf(schemeLift("b", 0), schemeLift("c", 1))))

        val appended =
            returnCutLiftToCurrentDay(
                days = days,
                fromDayId = "from",
                lift = cut,
                originalIndex = 9,
                sourceLiftsBefore = listOf(cut),
            )
        val prepended =
            returnCutLiftToCurrentDay(
                days = days,
                fromDayId = "from",
                lift = cut,
                originalIndex = -3,
                sourceLiftsBefore = listOf(cut),
            )
        val gone = listOf(day("other", listOf(schemeLift("z", 0))))

        assertEquals(listOf("b", "c", "a"), ids(appended, "from"))
        assertEquals(listOf("a", "b", "c"), ids(prepended, "from"))
        assertSame(
            gone,
            returnCutLiftToCurrentDay(
                days = gone,
                fromDayId = "from",
                lift = cut,
                originalIndex = 0,
                sourceLiftsBefore = listOf(cut),
            ),
        )
    }

    @Test
    @DisplayName("Given a delete undo taken while a cut lift is off the day when the cut returns and the delete is undone then the order is the original one")
    fun deleteUndoAfterAReturnedCutRestoresTheOriginalOrder() {
        val cut = schemeLift("a", 0, GROUP)
        val partner = schemeLift("b", 1, GROUP).copy(useRestTimerBetweenLifts = false)
        val kept = schemeLift("c", 2)
        // The delete was recorded while "a" was cut away: the day held the
        // detached partner and "c", and the partner sat at index 0.
        val detachedPartner = partner.copy(supersetGroupId = null, useRestTimerBetweenLifts = false)
        val deleteUndo =
            sheetDeleteUndo(
                dayId = "from",
                lift = detachedPartner,
                originalIndex = 0,
                liftsBefore = listOf(detachedPartner, kept),
            )
        val rebased =
            rebaseSheetUndoForReturnedCut(
                undo = deleteUndo,
                fromDayId = "from",
                lift = cut,
                originalIndex = 0,
                sourceLiftsBefore = listOf(cut, partner, kept),
            )
        // Cancel put the cut back: the live day is the cut and "c".
        val live = listOf(day("from", listOf(cut, kept)), day("other", listOf(schemeLift("z", 0))))

        val restored = applySheetUndo(live, rebased!!)

        assertEquals(listOf("a", "b", "c"), ids(restored, "from"))
        assertEquals(GROUP, lift(restored, "from", "b").supersetGroupId)
        assertEquals(false, lift(restored, "from", "b").useRestTimerBetweenLifts)
        assertEquals(listOf("z"), ids(restored, "other"))
    }

    @Test
    @DisplayName("Given a delete undo whose before-list already has the cut lift when the cut returns then the undo is left alone")
    fun rebaseLeavesAValidDeleteUndoAlone() {
        val cut = schemeLift("a", 0)
        val before = listOf(cut, schemeLift("b", 1))
        val undo =
            sheetDeleteUndo(dayId = "from", lift = before[1], originalIndex = 1, liftsBefore = before)

        assertSame(
            undo,
            rebaseSheetUndoForReturnedCut(
                undo = undo,
                fromDayId = "from",
                lift = cut,
                originalIndex = 0,
                sourceLiftsBefore = before,
            ),
        )
        assertSame(
            undo,
            rebaseSheetUndoForReturnedCut(
                undo = undo,
                fromDayId = "missing",
                lift = cut,
                originalIndex = 0,
                sourceLiftsBefore = before,
            ),
        )
        assertNull(
            rebaseSheetUndoForReturnedCut(
                undo = null,
                fromDayId = "from",
                lift = cut,
                originalIndex = 0,
                sourceLiftsBefore = before,
            ),
        )
    }

    @Test
    @DisplayName("Given a copy paste when undo is built then only the destination day gets a step")
    fun copyUndoStepsOnlyTheDestination() {
        val clip =
            ProgramSheetClipboard(
                kind = ProgramSheetClipboardKind.Copy,
                fromDayId = "from",
                lift = schemeLift("a", 0),
                originalIndex = 0,
                sourceLiftsBefore = listOf(schemeLift("a", 0), schemeLift("b", 1)),
            )

        val undo = sheetPasteUndo(clip = clip, toDayId = "to", pastedWorkoutLiftId = "a-copy")

        assertEquals(setOf("to"), undo.stepsByDayId.keys)
        assertEquals(
            listOf(SheetLiftUndoStep.RemoveLift("a-copy")),
            undo.stepsByDayId.getValue("to"),
        )
    }

    @Test
    @DisplayName("Given a completed cut paste when undo is applied then the lift leaves the target and returns to the source")
    fun cutUndoRemovesFromTargetAndReinsertsIntoSource() {
        val source = listOf(schemeLift("a", 0), schemeLift("b", 1))
        val clip =
            ProgramSheetClipboard(
                kind = ProgramSheetClipboardKind.Cut,
                fromDayId = "from",
                lift = schemeLift("a", 0),
                originalIndex = 0,
                sourceLiftsBefore = source,
            )
        val pasted =
            listOf(
                day("from", listOf(schemeLift("b", 0))),
                day("to", listOf(schemeLift("there", 0), schemeLift("a", 1))),
            )

        val crossDay =
            applySheetUndo(
                pasted,
                sheetPasteUndo(clip = clip, toDayId = "to", pastedWorkoutLiftId = "a"),
            )
        val sameDayPasted =
            listOf(day("from", listOf(schemeLift("b", 0), schemeLift("a", 1))))
        val sameDay =
            applySheetUndo(
                sameDayPasted,
                sheetPasteUndo(clip = clip, toDayId = "from", pastedWorkoutLiftId = "a"),
            )

        assertEquals(listOf("a", "b"), ids(crossDay, "from"))
        assertEquals(listOf("there"), ids(crossDay, "to"))
        assertEquals(listOf("a", "b"), ids(sameDay, "from"))
    }

    @Test
    @DisplayName("Given a set added after a cut when the paste is undone then that set stays and the cut lift returns")
    fun pasteUndoKeepsEditsMadeAfterTheCut() {
        val cut = schemeLift("L1", 0)
        val partner = schemeLift("L2", 1)
        val before = listOf(day("A", listOf(cut, partner)), day("B", listOf(schemeLift("there", 0))))
        val afterCut =
            before.map { day ->
                val edit = day.editState
                if (day.id != "A" || edit == null) {
                    day
                } else {
                    day.copy(
                        editState =
                            edit.copy(
                                lifts = removeLiftAndCleanupSupersets(edit.lifts, cut.workoutLiftId),
                            ),
                    )
                }
            }
        val withExtraSet =
            afterCut.map { day ->
                val edit = day.editState
                if (day.id != "A" || edit == null) {
                    day
                } else {
                    day.copy(editState = EditWorkoutLiftMutations.addSet(edit, partner.workoutLiftId))
                }
            }
        val liveSource = lifts(withExtraSet, "A")
        val target = lifts(withExtraSet, "B")
        val clip =
            ProgramSheetClipboard(
                kind = ProgramSheetClipboardKind.Cut,
                fromDayId = "A",
                lift = cut,
                originalIndex = 0,
                sourceLiftsBefore = listOf(cut, partner),
            )
        val undo = sheetPasteUndo(clip = clip, toDayId = "B", pastedWorkoutLiftId = cut.workoutLiftId)
        val pasted =
            moveProgramSheetLift(
                days = withExtraSet,
                fromDayId = "A",
                toDayId = "B",
                workoutLiftId = cut.workoutLiftId,
                targetPosition = target.size,
                detachedLift = cut,
            )
        // An edit on the target day after the paste: a set added to the lift that was already there.
        val editedAfterPaste =
            pasted.map { day ->
                val edit = day.editState
                if (day.id != "B" || edit == null) {
                    day
                } else {
                    day.copy(editState = EditWorkoutLiftMutations.addSet(edit, "there"))
                }
            }
        val restored = applySheetUndo(editedAfterPaste, undo)

        assertEquals(listOf("L1", "L2"), ids(restored, "A"))
        assertEquals(liveSource.first { lift -> lift.workoutLiftId == "L2" }.sets, lift(restored, "A", "L2").sets)
        assertEquals(partner.sets.size + 1, lift(restored, "A", "L2").sets.size)
        assertEquals(listOf("there"), ids(restored, "B"))
        assertEquals(
            schemeLift("there", 0).sets.size + 1,
            lift(restored, "B", "there").sets.size,
        )
    }

    @Test
    @DisplayName("Given a lift deleted and another lift edited when the delete is undone then the edit stays and the lift returns at its index")
    fun deleteUndoKeepsEditsMadeAfterTheDelete() {
        val deleted = schemeLift("b", 1)
        val before = listOf(schemeLift("a", 0), deleted, schemeLift("c", 2))
        val afterDelete =
            listOf(
                day(
                    "A",
                    listOf(schemeLift("a", 0), schemeLift("c", 1)),
                ),
            )
        val edited =
            afterDelete.map { day ->
                val edit = day.editState ?: return@map day
                day.copy(editState = EditWorkoutLiftMutations.addSet(edit, "c"))
            }
        val undo = sheetDeleteUndo(dayId = "A", lift = deleted, originalIndex = 1, liftsBefore = before)

        val restored = applySheetUndo(edited, undo)

        assertEquals(listOf("a", "b", "c"), ids(restored, "A"))
        assertEquals(schemeLift("c", 2).sets.size + 1, lift(restored, "A", "c").sets.size)
    }

    @Test
    @DisplayName("Given a save in flight when sheet undo is requested then the undo is kept and the days stay")
    fun undoDuringSaveIsNotConsumed() {
        val days = listOf(day("A", listOf(schemeLift("L1", 0))))
        val undo =
            sheetDeleteUndo(
                dayId = "A",
                lift = schemeLift("earlier", 0),
                originalIndex = 0,
                liftsBefore = listOf(schemeLift("earlier", 0), schemeLift("L1", 1)),
            )

        val refused = consumeSheetUndo(isSaving = true, undo = undo, days = days)
        val applied = consumeSheetUndo(isSaving = false, undo = undo, days = days)

        assertEquals(false, refused.consumed)
        assertNull(refused.days)
        assertEquals(true, applied.consumed)
        assertEquals(
            applySheetUndo(days, undo).map { day -> day.editState?.lifts },
            applied.days?.map { day -> day.editState?.lifts },
        )
        assertEquals(listOf("earlier", "L1"), applied.days?.let { ids(it, "A") })
    }

    @Test
    @DisplayName("Given the same undo message published twice then each publish gets a new epoch")
    fun sameUndoMessagePublishesTwoEpochs() {
        val message = "Moved Bench Press"
        val first = publishSheetUndoMessage(currentEpoch = 0, message = message)
        val second = publishSheetUndoMessage(currentEpoch = first.epoch, message = message)

        assertEquals(message, first.message)
        assertEquals(message, second.message)
        assertNotEquals(first.epoch, second.epoch)
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
