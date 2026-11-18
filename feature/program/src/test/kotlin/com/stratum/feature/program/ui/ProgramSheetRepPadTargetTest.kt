package com.stratum.feature.program.ui

import com.stratum.core.ui.component.workoutlog.RepRangeKeyboardField
import com.stratum.domain.program.ProgressionScheme
import com.stratum.feature.program.CreateProgramDayUi
import com.stratum.feature.program.ProgramSheetLiftStep
import com.stratum.feature.program.ProgramSheetLiftTarget
import com.stratum.feature.program.ProgramSheetRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("programSheetRepPadTarget")
class ProgramSheetRepPadTargetTest {
    @Test
    @DisplayName("a linear band at the preferred index is chosen")
    fun linearPreferred() {
        val sets = listOf(set("first", 8, 12), set("second", 5, 8))

        val chosen =
            programSheetRepPadTarget(
                sets = sets,
                scheme = ProgressionScheme.Linear,
                preferredIndex = 1,
                step = ProgramSheetLiftStep.Next,
            )

        assertEquals("second", chosen?.setId)
    }

    @Test
    @DisplayName("a double top set is chosen")
    fun doubleTop() {
        val sets = listOf(set("top", 8, 12, isTop = true), set("back", 0, 0))

        val chosen =
            programSheetRepPadTarget(
                sets = sets,
                scheme = ProgressionScheme.Double,
                preferredIndex = 0,
                step = ProgramSheetLiftStep.Next,
            )

        assertEquals("top", chosen?.setId)
    }

    @Test
    @DisplayName("a double backoff is skipped in favor of the top set")
    fun doubleBackoffYieldsToTop() {
        val sets = listOf(set("top", 8, 12, isTop = true), set("back", 0, 0))

        val chosen =
            programSheetRepPadTarget(
                sets = sets,
                scheme = ProgressionScheme.Double,
                preferredIndex = 1,
                step = ProgramSheetLiftStep.Next,
            )

        assertEquals("top", chosen?.setId)
    }

    @Test
    @DisplayName("a lift whose every set is 0/0 has no rep pad target")
    fun allZeroReturnsNull() {
        val sets = listOf(set("a", 0, 0), set("b", 0, 0, isTop = true))

        val chosen =
            programSheetRepPadTarget(
                sets = sets,
                scheme = ProgressionScheme.Linear,
                preferredIndex = 0,
                step = ProgramSheetLiftStep.Next,
            )

        assertNull(chosen)
    }

    @Test
    @DisplayName("a drop set is not chosen")
    fun dropSetIsNotChosen() {
        val drop = set("drop", 8, 12, kind = EditSetKind.DropSet)
        val kept = set("kept", 8, 12)
        val withNeighbor =
            programSheetRepPadTarget(
                sets = listOf(drop, kept),
                scheme = ProgressionScheme.Linear,
                preferredIndex = 0,
                step = ProgramSheetLiftStep.Next,
            )
        val onlyDrop =
            programSheetRepPadTarget(
                sets = listOf(drop),
                scheme = ProgressionScheme.Linear,
                preferredIndex = 0,
                step = ProgramSheetLiftStep.Previous,
            )

        assertEquals("kept", withNeighbor?.setId)
        assertNull(onlyDrop)
    }

    @Test
    @DisplayName("a match backoff is not chosen")
    fun matchBackoffIsNotChosen() {
        val activation = set("act", 8, 12, isTop = true, kind = EditSetKind.MyoRepMatch)
        val backoff = set("back", 8, 12, kind = EditSetKind.MyoRepMatch)
        val chosen =
            programSheetRepPadTarget(
                sets = listOf(activation, backoff),
                scheme = ProgressionScheme.Linear,
                preferredIndex = 1,
                step = ProgramSheetLiftStep.Next,
            )
        val onlyInvalidActivation =
            programSheetRepPadTarget(
                sets = listOf(activation.copy(repMin = 0, repMax = 0), backoff),
                scheme = ProgressionScheme.Linear,
                preferredIndex = 1,
                step = ProgramSheetLiftStep.Previous,
            )

        assertEquals("act", chosen?.setId)
        assertNull(onlyInvalidActivation)
    }

    @Test
    @DisplayName("the step direction wins over a nearer set the other way")
    fun stepDirectionBeforeTheOtherWay() {
        val sets =
            listOf(
                set("back", 8, 12),
                set("preferred", 0, 0),
                set("gap", 0, 0),
                set("next", 6, 10),
            )

        val forward =
            programSheetRepPadTarget(
                sets = sets,
                scheme = ProgressionScheme.Linear,
                preferredIndex = 1,
                step = ProgramSheetLiftStep.Next,
            )
        val backward =
            programSheetRepPadTarget(
                sets = sets,
                scheme = ProgressionScheme.Linear,
                preferredIndex = 1,
                step = ProgramSheetLiftStep.Previous,
            )

        assertEquals("next", forward?.setId)
        assertEquals("back", backward?.setId)
    }

    private fun set(
        id: String,
        min: Int,
        max: Int,
        isTop: Boolean = false,
        kind: EditSetKind = EditSetKind.Standard,
    ): EditSetState =
        EditSetState(
            setId = id,
            setNumber = 1,
            repMin = min,
            repMax = max,
            restSeconds = 90,
            rirGoal = 2,
            isTopSet = isTop,
            setKind = kind,
        )
}

@DisplayName("movedProgramSheetLift rep pad")
class MovedProgramSheetLiftRepPadTest {
    @Test
    @DisplayName("a double backoff retargets the pad onto the top set and stays open")
    fun doubleBackoffRetargetsTopSet() {
        val host = openRepHost()
        assertTrue(host.tryCommitPendingAndStay())
        val writes = mutableListOf<Triple<String, Int, Int>>()

        val move =
            movedProgramSheetLift(
                expanded = ProgramSheetLiftTarget(DAY_A, LIFT_A),
                rows = rows(),
                days = doubleBackoffDays(),
                step = ProgramSheetLiftStep.Next,
                anchorSetId = SOURCE_BACKOFF,
                host = host,
                onSelectDay = {},
                onSetRepBand = { setId, repMin, repMax -> writes += Triple(setId, repMin, repMax) },
                onSetRestSeconds = { _, _ -> },
                onSetRestSecondsForAllSetsOnLift = { _, _ -> },
                rirSetId = SOURCE_BACKOFF,
            )

        assertEquals(ProgramSheetLiftTarget(DAY_B, LIFT_B), move?.target)
        assertEquals(DEST_BACKOFF, move?.rirSetId)
        assertEquals(DEST_TOP, host.openSetId())
        assertEquals("8", host.minText)
        assertEquals("12", host.maxText)
        assertTrue(host.isOpen)
        assertFalse(host.overlay.skipNextCollapseScrollRestore)
        assertTrue(writes.isEmpty())
    }

    @Test
    @DisplayName("a lift of only 0/0 closes the pad, arms skip restore, and does not draft 0")
    fun allZeroClosesWithoutDraftingZero() {
        val host = openRepHost()
        assertTrue(host.tryCommitPendingAndStay())
        val writes = mutableListOf<Triple<String, Int, Int>>()

        val move =
            movedProgramSheetLift(
                expanded = ProgramSheetLiftTarget(DAY_A, LIFT_A),
                rows = rows(),
                days = allZeroDays(),
                step = ProgramSheetLiftStep.Next,
                anchorSetId = SOURCE_TOP,
                host = host,
                onSelectDay = {},
                onSetRepBand = { setId, repMin, repMax -> writes += Triple(setId, repMin, repMax) },
                onSetRestSeconds = { _, _ -> },
                onSetRestSecondsForAllSetsOnLift = { _, _ -> },
                rirSetId = null,
            )

        assertEquals(ProgramSheetLiftTarget(DAY_B, LIFT_B), move?.target)
        assertNull(move?.rirSetId)
        assertFalse(host.isOpen)
        assertNull(host.openSetId())
        assertEquals("8", host.minText)
        assertTrue(host.overlay.skipNextCollapseScrollRestore)
        assertTrue(writes.none { (_, repMin, repMax) -> repMin == 0 || repMax == 0 })
    }

    @Test
    @DisplayName("rest stays on the preferred backoff instead of the top set")
    fun restStaysOnPreferredBackoff() {
        val host = EditWorkoutDockHostState()
        host.openRest(
            setId = SOURCE_BACKOFF,
            restSeconds = 90,
            onApplyToSet = {},
            onApplyToAllSets = {},
        )

        movedProgramSheetLift(
            expanded = ProgramSheetLiftTarget(DAY_A, LIFT_A),
            rows = rows(),
            days = doubleBackoffDays(),
            step = ProgramSheetLiftStep.Next,
            anchorSetId = SOURCE_BACKOFF,
            host = host,
            onSelectDay = {},
            onSetRepBand = { _, _, _ -> },
            onSetRestSeconds = { _, _ -> },
            onSetRestSecondsForAllSetsOnLift = { _, _ -> },
            rirSetId = null,
        )

        val session = host.session as EditWorkoutDockSession.Rest
        assertEquals(DEST_BACKOFF, session.setId)
        assertEquals(45, host.restSeconds)
        assertTrue(host.isOpen)
        assertFalse(host.overlay.skipNextCollapseScrollRestore)
    }

    private fun openRepHost(): EditWorkoutDockHostState {
        val host = EditWorkoutDockHostState()
        host.openRepRange(
            setId = SOURCE_TOP,
            field = RepRangeKeyboardField.Min,
            minText = "8",
            maxText = "12",
            onCommitBand = { _, _ -> },
        )
        return host
    }

    private fun rows(): List<ProgramSheetRow> =
        listOf(
            ProgramSheetRow.Lift(DAY_A, LIFT_A),
            ProgramSheetRow.Lift(DAY_B, LIFT_B),
        )

    private fun doubleBackoffDays(): List<CreateProgramDayUi> =
        listOf(
            day(
                DAY_A,
                LIFT_A,
                ProgressionScheme.Linear,
                listOf(set("src-top", 8, 12, isTop = true), set(SOURCE_BACKOFF, 8, 12)),
            ),
            day(
                DAY_B,
                LIFT_B,
                ProgressionScheme.Double,
                listOf(
                    set(DEST_TOP, 8, 12, isTop = true, rest = 120),
                    set(DEST_BACKOFF, 0, 0, rest = 45),
                ),
            ),
        )

    private fun allZeroDays(): List<CreateProgramDayUi> =
        listOf(
            day(
                DAY_A,
                LIFT_A,
                ProgressionScheme.Linear,
                listOf(set(SOURCE_TOP, 8, 12, isTop = true)),
            ),
            day(
                DAY_B,
                LIFT_B,
                ProgressionScheme.Linear,
                listOf(set("zero-a", 0, 0), set("zero-b", 0, 0)),
            ),
        )

    private fun day(
        dayId: String,
        liftId: String,
        scheme: ProgressionScheme,
        sets: List<EditSetState>,
    ): CreateProgramDayUi =
        CreateProgramDayUi(
            id = dayId,
            name = dayId,
            position = 0,
            isRest = false,
            editState =
                EditWorkoutUiState.Loaded(
                    workoutId = dayId,
                    workoutName = dayId,
                    lifts =
                        listOf(
                            EditLiftState(
                                workoutLiftId = liftId,
                                liftId = liftId,
                                name = liftId,
                                position = 0,
                                sets = sets,
                                progressionScheme = scheme,
                            ),
                        ),
                ),
        )

    private fun set(
        id: String,
        min: Int,
        max: Int,
        isTop: Boolean = false,
        rest: Int = 90,
    ): EditSetState =
        EditSetState(
            setId = id,
            setNumber = 1,
            repMin = min,
            repMax = max,
            restSeconds = rest,
            rirGoal = 2,
            isTopSet = isTop,
        )

    private companion object {
        const val DAY_A = "day-a"
        const val DAY_B = "day-b"
        const val LIFT_A = "lift-a"
        const val LIFT_B = "lift-b"
        const val SOURCE_TOP = "src-top"
        const val SOURCE_BACKOFF = "src-back"
        const val DEST_TOP = "dest-top"
        const val DEST_BACKOFF = "dest-back"
    }
}
