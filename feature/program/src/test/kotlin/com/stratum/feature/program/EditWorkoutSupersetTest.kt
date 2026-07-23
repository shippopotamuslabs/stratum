package com.stratum.feature.program

import com.stratum.feature.program.ui.EditLiftState
import com.stratum.feature.program.ui.EditSetState
import com.stratum.feature.program.ui.EditWorkoutUiState
import com.stratum.core.ui.component.SupersetLinkDirection
import com.stratum.core.ui.component.isSupersetPairMember
import com.stratum.core.ui.component.isLastInSupersetChain
import com.stratum.core.ui.component.shouldKeepSupersetSheetOpenAfterLink
import com.stratum.core.ui.component.supersetLinkSheetOptions
import com.stratum.core.ui.component.linkSupersetWithAdjacent
import com.stratum.core.ui.component.unlinkLiftFromSuperset
import com.stratum.core.ui.component.cleanupSupersetGroups
import com.stratum.feature.program.R
import com.stratum.core.ui.R as CoreUiR
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class EditWorkoutSupersetTest {
    private val groupA = "group-a"
    private val groupB = "group-b"

    @Test
    @DisplayName("Given adjacent lifts when linked with previous then both share a group id")
    fun linkWithPreviousCreatesPair() {
        val lifts =
            listOf(
                lift("wl-1", position = 0),
                lift("wl-2", position = 1),
            )

        val updated =
            linkSupersetWithAdjacent(
                lifts = lifts,
                workoutLiftId = "wl-2",
                direction = SupersetLinkDirection.Previous,
                nextGroupId = { groupA },
            )

        assertEquals(groupA, updated[0].supersetGroupId)
        assertEquals(groupA, updated[1].supersetGroupId)
        assertFalse(updated[0].useRestTimerBetweenLifts)
    }

    @Test
    @DisplayName("Given grouped neighbor when linked with next then current joins existing group")
    fun linkWithNextJoinsGiantSet() {
        val lifts =
            listOf(
                lift("wl-1", position = 0, groupId = groupB),
                lift("wl-2", position = 1, groupId = groupB),
                lift("wl-3", position = 2),
            )

        val updated =
            linkSupersetWithAdjacent(
                lifts = lifts,
                workoutLiftId = "wl-2",
                direction = SupersetLinkDirection.Next,
                nextGroupId = { "unused" },
            )

        assertEquals(groupB, updated[2].supersetGroupId)
        assertEquals(3, updated.count { lift -> lift.supersetGroupId == groupB })
    }

    @Test
    @DisplayName("Given a lone grouped lift when cleaned up then group id is cleared")
    fun cleanupRemovesSingletonGroup() {
        val lifts =
            listOf(
                lift("wl-1", position = 0, groupId = "group-solo"),
                lift("wl-2", position = 1),
            )

        val cleaned = cleanupSupersetGroups(lifts)

        assertNull(cleaned[0].supersetGroupId)
    }

    @Test
    @DisplayName("Given a superset pair when one lift is removed then survivor loses group id")
    fun removeLiftClearsOrphanSupersetGroup() {
        val lifts =
            listOf(
                lift("wl-1", position = 0, groupId = groupA),
                lift("wl-2", position = 1, groupId = groupA),
                lift("wl-3", position = 2),
            )

        val remaining = removeLiftAndCleanupSupersets(lifts, "wl-2")

        assertEquals(2, remaining.size)
        assertNull(remaining.single { it.workoutLiftId == "wl-1" }.supersetGroupId)
        assertEquals(0, remaining.single { it.workoutLiftId == "wl-1" }.position)
        assertEquals(1, remaining.single { it.workoutLiftId == "wl-3" }.position)
    }

    @Test
    @DisplayName("Given giant set of three when one lift is removed then remaining two keep group")
    fun removeLiftFromGiantSetKeepsRemainingPair() {
        val lifts =
            listOf(
                lift("wl-1", position = 0, groupId = groupB),
                lift("wl-2", position = 1, groupId = groupB),
                lift("wl-3", position = 2, groupId = groupB),
            )

        val remaining = removeLiftAndCleanupSupersets(lifts, "wl-2")

        assertEquals(2, remaining.size)
        assertEquals(groupB, remaining[0].supersetGroupId)
        assertEquals(groupB, remaining[1].supersetGroupId)
        assertEquals(listOf("wl-1", "wl-3"), remaining.map { it.workoutLiftId })
    }

    @Test
    @DisplayName("Given unknown workoutLiftId when removeLift then list is unchanged")
    fun removeLiftUnknownIdIsNoOp() {
        val lifts =
            listOf(
                lift("wl-1", position = 0, groupId = groupA),
                lift("wl-2", position = 1, groupId = groupA),
            )

        val remaining = removeLiftAndCleanupSupersets(lifts, "missing")

        assertEquals(lifts, remaining)
    }

    @Test
    @DisplayName("EditWorkoutLiftMutations.removeLift marks dirty and clears orphan")
    fun mutationsRemoveLiftClearsOrphanAndMarksDirty() {
        val loaded =
            EditWorkoutUiState.Loaded(
                workoutId = "w-1",
                workoutName = "Day",
                lifts =
                    listOf(
                        lift("wl-1", position = 0, groupId = groupA),
                        lift("wl-2", position = 1, groupId = groupA),
                    ),
                isDirty = false,
            )

        val next = EditWorkoutLiftMutations.removeLift(loaded, "wl-2")

        assertTrue(next.isDirty)
        assertEquals(1, next.lifts.size)
        assertNull(next.lifts.single().supersetGroupId)
    }

    @Test
    @DisplayName("EditWorkoutLiftMutations.removeLift with unknown id does not mark dirty")
    fun mutationsRemoveLiftUnknownIdSkipsDirty() {
        val loaded =
            EditWorkoutUiState.Loaded(
                workoutId = "w-1",
                workoutName = "Day",
                lifts = listOf(lift("wl-1", position = 0)),
                isDirty = false,
            )

        val next = EditWorkoutLiftMutations.removeLift(loaded, "missing")

        assertFalse(next.isDirty)
        assertEquals(loaded.lifts, next.lifts)
    }

    @Test
    @DisplayName("Given paired lift when unlinked then both lifts lose their group id")
    fun unlinkFromPairClearsBoth() {
        val lifts =
            listOf(
                lift("wl-1", position = 0, groupId = groupA),
                lift("wl-2", position = 1, groupId = groupA),
            )

        val updated = unlinkLiftFromSuperset(lifts = lifts, workoutLiftId = "wl-2")

        assertNull(updated[0].supersetGroupId)
        assertNull(updated[1].supersetGroupId)
    }

    @Test
    @DisplayName("Given giant set when middle lift unlinked then remaining members keep their group")
    fun unlinkFromGiantSetKeepsRemainingGroup() {
        val lifts =
            listOf(
                lift("wl-1", position = 0, groupId = groupB),
                lift("wl-2", position = 1, groupId = groupB),
                lift("wl-3", position = 2, groupId = groupB),
            )

        val updated = unlinkLiftFromSuperset(lifts = lifts, workoutLiftId = "wl-2")

        assertEquals(groupB, updated[0].supersetGroupId)
        assertNull(updated[1].supersetGroupId)
        assertEquals(groupB, updated[2].supersetGroupId)
    }

    @Test
    @DisplayName("Given reorder when terminal lift changes then last-in-chain updates")
    fun reorderChangesTerminalLift() {
        val lifts =
            listOf(
                lift("wl-1", position = 0, groupId = groupA),
                lift("wl-2", position = 1, groupId = groupA),
            )
        val reordered =
            listOf(
                lifts[1].copy(position = 0),
                lifts[0].copy(position = 1),
            )

        assertFalse(isLastInSupersetChain(reordered[0], reordered))
        assertTrue(isLastInSupersetChain(reordered[1], reordered))
    }

    @Test
    @DisplayName("Given grouped lift when sheet options built then remove option is first")
    fun sheetOptionsIncludeRemoveWhenGrouped() {
        val lifts =
            listOf(
                lift("wl-1", position = 0, groupId = groupA),
                lift("wl-2", position = 1, groupId = groupA),
            )

        val options = supersetLinkSheetOptions(lifts = lifts, workoutLiftId = "wl-2")

        assertEquals(SupersetLinkDirection.Remove, options.first().direction)
        assertEquals(R.string.edit_workout_remove_from_superset, options.first().labelRes)
        assertEquals(CoreUiR.drawable.ic_join, options.first().trailingIconRes)
    }

    @Test
    @DisplayName("Given paired lifts when sheet reopened then link to grouped neighbor is hidden")
    fun sheetOptionsHideAlreadyLinkedNeighbor() {
        val lifts =
            listOf(
                lift("wl-1", position = 0, groupId = groupA),
                lift("wl-2", position = 1, groupId = groupA),
            )

        val optionsFromFirst = supersetLinkSheetOptions(lifts = lifts, workoutLiftId = "wl-1")
        val optionsFromSecond = supersetLinkSheetOptions(lifts = lifts, workoutLiftId = "wl-2")

        assertEquals(1, optionsFromFirst.size)
        assertEquals(SupersetLinkDirection.Remove, optionsFromFirst.single().direction)
        assertEquals(1, optionsFromSecond.size)
        assertEquals(SupersetLinkDirection.Remove, optionsFromSecond.single().direction)
    }

    @Test
    @DisplayName("Given middle lift when sheet options built then previous and next labels reflect neighbor groups")
    fun sheetOptionsUseGiantSetLabels() {
        val lifts =
            listOf(
                lift("wl-1", position = 0, groupId = groupA),
                lift("wl-2", position = 1),
                lift("wl-3", position = 2, groupId = groupB),
            )

        val options = supersetLinkSheetOptions(lifts = lifts, workoutLiftId = "wl-2")

        assertEquals(2, options.size)
        assertEquals(R.string.edit_workout_giant_set_with_previous, options[0].labelRes)
        assertEquals(CoreUiR.drawable.ic_join_left, options[0].trailingIconRes)
        assertEquals(R.string.edit_workout_giant_set_with_next, options[1].labelRes)
        assertEquals(CoreUiR.drawable.ic_join_right, options[1].trailingIconRes)
    }

    @Test
    @DisplayName("Given paired lifts when chip visibility checked then both show the chip")
    fun chipVisibleForPairMembers() {
        val lifts =
            listOf(
                lift("wl-1", position = 0, groupId = groupA),
                lift("wl-2", position = 1, groupId = groupA),
            )

        assertTrue(lifts[0].showsSupersetChip(lifts))
        assertTrue(lifts[1].showsSupersetChip(lifts))
        assertTrue(isSupersetPairMember(lifts[0], lifts))
    }

    @Test
    @DisplayName("Given ungrouped lift when chip visibility checked then chip is hidden")
    fun chipHiddenWhenUngrouped() {
        val lifts = listOf(lift("wl-1", position = 0))

        assertFalse(lifts[0].showsSupersetChip(lifts))
    }

    @Test
    @DisplayName("Given link with next when lift is not terminal then sheet stays open")
    fun keepSheetOpenAfterLinkWithNext() {
        val lifts =
            listOf(
                lift("wl-1", position = 0),
                lift("wl-2", position = 1),
            )

        assertTrue(
            shouldKeepSupersetSheetOpenAfterLink(
                lifts = lifts,
                workoutLiftId = "wl-1",
                direction = SupersetLinkDirection.Next,
            ),
        )
    }

    @Test
    @DisplayName("Given link with previous when lift becomes terminal then sheet closes")
    fun closeSheetAfterLinkWithPrevious() {
        val lifts =
            listOf(
                lift("wl-1", position = 0),
                lift("wl-2", position = 1),
            )

        assertFalse(
            shouldKeepSupersetSheetOpenAfterLink(
                lifts = lifts,
                workoutLiftId = "wl-2",
                direction = SupersetLinkDirection.Previous,
            ),
        )
    }

    @Test
    @DisplayName("Given remove from superset then sheet closes")
    fun closeSheetAfterRemove() {
        val lifts =
            listOf(
                lift("wl-1", position = 0, groupId = groupA),
                lift("wl-2", position = 1, groupId = groupA),
            )

        assertFalse(
            shouldKeepSupersetSheetOpenAfterLink(
                lifts = lifts,
                workoutLiftId = "wl-2",
                direction = SupersetLinkDirection.Remove,
            ),
        )
    }

    private fun lift(
        workoutLiftId: String,
        position: Int,
        groupId: String? = null,
    ): EditLiftState =
        EditLiftState(
            workoutLiftId = workoutLiftId,
            liftId = "lift-$workoutLiftId",
            name = "Lift $workoutLiftId",
            position = position,
            sets =
                listOf(
                    EditSetState(
                        setId = "set-$workoutLiftId",
                        setNumber = 1,
                        repMin = 8,
                        repMax = 12,
                        restSeconds = 120,
                        rirGoal = 2,
                    ),
                ),
            supersetGroupId = groupId,
        )

    private fun EditLiftState.showsSupersetChip(allLifts: List<EditLiftState>): Boolean =
        isSupersetPairMember(this, allLifts)
}
