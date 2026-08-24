package com.stratum.core.ui.component.workoutlog

import com.stratum.core.ui.component.platecalculator.PlateInventoryUi
import com.stratum.core.ui.component.platecalculator.defaultPlateInventory
import com.stratum.domain.lift.PlateCalculator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("SetLogKeyboardHostState")
class SetLogKeyboardHostStateTest {
    private lateinit var host: SetLogKeyboardHostState
    private val weightCommits = mutableListOf<String>()
    private val repsCommits = mutableListOf<String>()

    @BeforeEach
    fun setUp() {
        host = SetLogKeyboardHostState()
        weightCommits.clear()
        repsCommits.clear()
    }

    private fun open(
        setKey: String = "set-1",
        field: SetLogKeyboardField = SetLogKeyboardField.Weight,
        weight: String = "135",
        reps: String = "8",
        rir: Int = 2,
        showWeight: Boolean = true,
    ) {
        host.open(
            setKey = setKey,
            field = field,
            weightText = weight,
            repsText = reps,
            rir = rir,
            showWeightInput = showWeight,
            hasWeightRecommendation = true,
            hasRepsRecommendation = true,
            onWeightChange = { weightCommits += it },
            onRepsChange = { repsCommits += it },
            onRirChange = {},
            onComplete = null,
        )
    }

    @Nested
    @DisplayName("open")
    inner class Open {
        @Test
        @DisplayName("new set seeds drafts and places carets at end")
        fun newSetSeedsAtEnd() {
            open(weight = "135", reps = "10")
            assertEquals("135", host.weightText)
            assertEquals("10", host.repsText)
            assertEquals(3, host.weightCursor)
            assertEquals(2, host.repsCursor)
            assertEquals(SetLogKeyboardField.Weight, host.session?.field)
        }

        @Test
        @DisplayName("same set open does not reseed drafts from props")
        fun sameSetKeepsHostDrafts() {
            open(weight = "135", reps = "8")
            host.applyPadEditAndCommit(
                insertNumericKey(
                    buffer = host.activeBuffer()!!,
                    key = '7',
                    allowDecimal = true,
                ),
            )
            assertEquals("1357", host.weightText)
            assertEquals(listOf("1357"), weightCommits)

            // Stale parent still shows "135" — open must not wipe host draft.
            open(weight = "135", reps = "8", field = SetLogKeyboardField.Reps)
            assertEquals("1357", host.weightText)
            assertEquals("8", host.repsText)
            assertEquals(SetLogKeyboardField.Reps, host.session?.field)
        }

        @Test
        @DisplayName("different set reseeds drafts and carets")
        fun differentSetReseeds() {
            open(setKey = "a", weight = "100", reps = "5")
            host.applyPadEditAndCommit(
                insertNumericKey(host.activeBuffer()!!, '0', allowDecimal = true),
            )
            open(setKey = "b", weight = "200", reps = "6")
            assertEquals("200", host.weightText)
            assertEquals("6", host.repsText)
            assertEquals(3, host.weightCursor)
            assertEquals(1, host.repsCursor)
        }
    }

    @Nested
    @DisplayName("setField and cursor")
    inner class FieldAndCursor {
        @Test
        @DisplayName("setField switches field without touching drafts")
        fun setFieldPreservesDrafts() {
            open(field = SetLogKeyboardField.Weight, weight = "100")
            host.setCursor(SetLogKeyboardField.Weight, 1)
            host.setField(SetLogKeyboardField.Reps)
            assertEquals("100", host.weightText)
            assertEquals(1, host.weightCursor)
            assertEquals(SetLogKeyboardField.Reps, host.session?.field)
        }

        @Test
        @DisplayName("mid-string insert commits text and advances cursor")
        fun midStringInsert() {
            open(weight = "135")
            host.setCursor(SetLogKeyboardField.Weight, 1)
            host.applyPadEditAndCommit(
                insertNumericKey(host.activeBuffer()!!, '2', allowDecimal = true),
            )
            assertEquals("1235", host.weightText)
            assertEquals(2, host.weightCursor)
            assertEquals(listOf("1235"), weightCommits)
        }

        @Test
        @DisplayName("backspace at cursor zero is a no-op")
        fun backspaceAtStart() {
            open(weight = "12")
            host.setCursor(SetLogKeyboardField.Weight, 0)
            host.applyPadEditAndCommit(backspaceNumericBuffer(host.activeBuffer()!!))
            assertEquals("12", host.weightText)
            assertEquals(0, host.weightCursor)
            assertTrue(weightCommits.isEmpty())
        }
    }

    @Nested
    @DisplayName("complete dismiss restore flag")
    inner class CompleteDismissRestore {
        @Test
        @DisplayName("overlay skipNextCollapseScrollRestore defaults false and is settable")
        fun skipRestoreFlag() {
            assertFalse(host.overlay.skipNextCollapseScrollRestore)
            host.overlay.skipNextCollapseScrollRestore = true
            assertTrue(host.overlay.skipNextCollapseScrollRestore)
        }
    }

    @Nested
    @DisplayName("syncSessionChrome")
    inner class SyncChrome {
        @Test
        @DisplayName("does not overwrite host weight or reps text")
        fun doesNotReseedText() {
            open(weight = "135", reps = "8", rir = 2)
            host.applyPadEditAndCommit(
                insertNumericKey(host.activeBuffer()!!, '0', allowDecimal = true),
            )
            host.syncSessionChrome(
                setKey = "set-1",
                rir = 1,
                hasWeightRecommendation = false,
                hasRepsRecommendation = true,
            )
            assertEquals("1350", host.weightText)
            assertEquals("8", host.repsText)
            assertEquals(1, host.rir)
            assertFalse(host.session!!.hasWeightRecommendation)
            assertTrue(host.session!!.hasRepsRecommendation)
        }

        @Test
        @DisplayName("legacy syncValues ignores weight and reps args")
        fun legacySyncValuesIgnoresText() {
            open(weight = "135")
            host.applyPadEditAndCommit(
                insertNumericKey(host.activeBuffer()!!, '5', allowDecimal = true),
            )
            host.syncValues(
                setKey = "set-1",
                weightText = "999",
                repsText = "1",
                rir = 3,
                hasWeightRecommendation = true,
                hasRepsRecommendation = true,
            )
            assertEquals("1355", host.weightText)
            assertEquals("8", host.repsText)
            assertEquals(3, host.rir)
        }
    }

    @Nested
    @DisplayName("applyWeightFromCalculator")
    inner class ApplyWeight {
        @Test
        @DisplayName("writes the weight buffer even when reps field is active")
        fun applyAlwaysWritesWeight() {
            open(field = SetLogKeyboardField.Weight, weight = "135", reps = "8")
            host.setField(SetLogKeyboardField.Reps)
            host.applyWeightFromCalculator(225.0)
            assertEquals("225", host.weightText)
            assertEquals("8", host.repsText)
            assertEquals(listOf("225"), weightCommits)
            assertTrue(repsCommits.isEmpty())
        }

        @Test
        @DisplayName("applyCalculatorToSet commits inventory and weight together")
        fun applyCalculatorCommitsInventoryAndWeight() {
            open(weight = "135")
            val without45s = PlateInventoryUi(
                barWeight = 45.0,
                enabledPlates = setOf(35.0, 25.0, 10.0, 5.0, 2.5),
                unitLabel = "lb",
            )
            host.applyCalculatorToSet(225.0, without45s)
            assertEquals("225", host.weightText)
            assertEquals(listOf("225"), weightCommits)
            assertEquals(without45s, host.plateInventory)
        }

        @Test
        @DisplayName("weight-only apply does not commit plate inventory")
        fun weightOnlyApplyLeavesInventoryUnchanged() {
            open(weight = "135")
            host.applyWeightFromCalculator(225.0)
            assertEquals(null, host.plateInventory)
            assertEquals(defaultPlateInventory("lb"), host.plateInventoryFor("lb"))
        }
    }

    @Nested
    @DisplayName("ephemeral plate inventory")
    inner class PlateInventory {
        @Test
        @DisplayName("plateInventoryFor returns unit defaults until a sheet commit")
        fun defaultsUntilCommit() {
            val inventory = host.plateInventoryFor("lb")
            assertEquals(PlateCalculator.OLYMPIC_BAR_LB, inventory.barWeight)
            assertEquals(PlateCalculator.DEFAULT_PLATES_LB.toSet(), inventory.enabledPlates)
            assertEquals("lb", inventory.unitLabel)
        }

        @Test
        @DisplayName("closePlateSheet without applyCalculatorToSet leaves inventory untouched")
        fun closeSheetDoesNotCommitInventory() {
            open(weight = "225")
            host.openPlateSheet()
            host.closePlateSheet()
            assertEquals(null, host.plateInventory)
            assertEquals(defaultPlateInventory("lb"), host.plateInventoryFor("lb"))
        }
        fun commitSurvivesDismissAndSetSwitch() {
            val without45s = PlateInventoryUi(
                barWeight = 45.0,
                enabledPlates = setOf(35.0, 25.0, 10.0, 5.0, 2.5),
                unitLabel = "lb",
            )
            host.commitPlateInventory(without45s)
            open(setKey = "a", weight = "225")
            host.dismiss()
            open(setKey = "b", weight = "135")

            val held = host.plateInventoryFor("lb")
            assertEquals(without45s, held)
            assertEquals(without45s, host.plateInventory)
        }

        @Test
        @DisplayName("unit mismatch uses that unit's defaults without dropping the held inventory")
        fun unitMismatchDoesNotDropHeld() {
            val lbInventory = PlateInventoryUi(
                barWeight = 35.0,
                enabledPlates = setOf(25.0, 10.0, 5.0, 2.5),
                unitLabel = "lb",
            )
            host.commitPlateInventory(lbInventory)

            val kg = host.plateInventoryFor("kg")
            assertEquals(defaultPlateInventory("kg"), kg)
            assertEquals(lbInventory, host.plateInventoryFor("lb"))
            assertNotEquals(kg.enabledPlates, lbInventory.enabledPlates)
        }
    }

    @Nested
    @DisplayName("reps entry mode")
    inner class RepsEntry {
        @Test
        @DisplayName("digits in Full mode update reps, not partials")
        fun fullModeRoutesToReps() {
            val partialCommits = mutableListOf<String>()
            host.open(
                setKey = "set-1",
                field = SetLogKeyboardField.Reps,
                weightText = "135",
                repsText = "8",
                partialRepsText = "",
                rir = 2,
                showWeightInput = true,
                hasWeightRecommendation = true,
                hasRepsRecommendation = true,
                onWeightChange = { weightCommits += it },
                onRepsChange = { repsCommits += it },
                onPartialRepsChange = { partialCommits += it },
                onRirChange = {},
                onComplete = null,
            )
            host.applyPadEditAndCommit(
                insertNumericKey(host.activeBuffer()!!, '5', allowDecimal = false),
            )
            assertEquals("85", host.repsText)
            assertEquals(listOf("85"), repsCommits)
            assertEquals("", host.partialRepsText)
            assertTrue(partialCommits.isEmpty())
        }

        @Test
        @DisplayName("digits in Partial mode update partials")
        fun partialModeRoutesToPartials() {
            val partialCommits = mutableListOf<String>()
            host.open(
                setKey = "set-1",
                field = SetLogKeyboardField.Reps,
                weightText = "135",
                repsText = "8",
                rir = 2,
                showWeightInput = true,
                hasWeightRecommendation = true,
                hasRepsRecommendation = true,
                onWeightChange = { weightCommits += it },
                onRepsChange = { repsCommits += it },
                onPartialRepsChange = { partialCommits += it },
                onRirChange = {},
                onComplete = null,
            )
            host.selectRepsEntryMode(RepsEntryMode.Partial)
            host.applyPadEditAndCommit(
                insertNumericKey(host.activeBuffer()!!, '2', allowDecimal = false),
            )
            assertEquals("8", host.repsText)
            assertEquals("2", host.partialRepsText)
            assertEquals(listOf("2"), partialCommits)
            assertTrue(repsCommits.isEmpty())
        }

        @Test
        @DisplayName("new set resets mode to Full")
        fun newSetResetsMode() {
            open(setKey = "a", field = SetLogKeyboardField.Reps)
            host.selectRepsEntryMode(RepsEntryMode.Partial)
            open(setKey = "b", field = SetLogKeyboardField.Reps)
            assertEquals(RepsEntryMode.Full, host.repsEntryMode)
        }
    }
}
