package com.stratum.feature.program.ui

import com.stratum.core.ui.component.workoutlog.RepRangeKeyboardField
import com.stratum.core.ui.component.workoutlog.backspaceNumericBuffer
import com.stratum.core.ui.component.workoutlog.insertNumericKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("EditWorkoutDockHostState")
class EditWorkoutDockHostStateTest {
    private lateinit var host: EditWorkoutDockHostState
    private val commits = mutableListOf<Pair<Int, Int>>()

    @BeforeEach
    fun setUp() {
        host = EditWorkoutDockHostState()
        commits.clear()
    }

    private fun openRep(
        setId: String = "s1",
        field: RepRangeKeyboardField = RepRangeKeyboardField.Min,
        min: String = "8",
        max: String = "12",
    ) {
        host.openRepRange(
            setId = setId,
            field = field,
            minText = min,
            maxText = max,
            onCommitBand = { lo, hi -> commits += lo to hi },
        )
    }

    @Nested
    @DisplayName("openRepRange")
    inner class OpenRep {
        @Test
        @DisplayName("seeds drafts and carets at end")
        fun seedsAtEnd() {
            openRep(min = "8", max = "12")
            assertEquals("8", host.minText)
            assertEquals("12", host.maxText)
            assertEquals(1, host.minCursor)
            assertEquals(2, host.maxCursor)
        }

        @Test
        @DisplayName("same set only switches field and keeps drafts")
        fun sameSetKeepsDrafts() {
            openRep(field = RepRangeKeyboardField.Min, min = "8", max = "12")
            host.applyPadEditToDrafts(
                insertNumericKey(host.activeBuffer()!!, '5', allowDecimal = false),
            )
            assertEquals("85", host.minText)
            openRep(field = RepRangeKeyboardField.Max, min = "0", max = "0")
            assertEquals("85", host.minText)
            assertEquals("12", host.maxText)
            assertEquals(RepRangeKeyboardField.Max, (host.session as EditWorkoutDockSession.RepRange).field)
        }
    }

    @Nested
    @DisplayName("pad edits")
    inner class PadEdits {
        @Test
        @DisplayName("mid-string insert updates draft cursor without committing")
        fun midInsertNoCommit() {
            openRep(min = "85")
            host.setCursor(RepRangeKeyboardField.Min, 1)
            host.applyPadEditToDrafts(
                insertNumericKey(host.activeBuffer()!!, '0', allowDecimal = false),
            )
            assertEquals("805", host.minText)
            assertEquals(2, host.minCursor)
            assertTrue(commits.isEmpty())
        }

        @Test
        @DisplayName("backspace mid-string updates draft only")
        fun backspaceMid() {
            openRep(min = "805")
            host.setCursor(RepRangeKeyboardField.Min, 2)
            host.applyPadEditToDrafts(backspaceNumericBuffer(host.activeBuffer()!!))
            assertEquals("85", host.minText)
            assertEquals(1, host.minCursor)
            assertTrue(commits.isEmpty())
        }
    }

    @Nested
    @DisplayName("leave policy")
    inner class Leave {
        @Test
        @DisplayName("valid drafts commit on leave")
        fun commitValid() {
            openRep(min = "8", max = "12")
            assertTrue(host.tryCommitPendingOrBlock())
            assertEquals(listOf(8 to 12), commits)
            assertFalse(host.isOpen)
        }

        @Test
        @DisplayName("invalid drafts block leave and keep session")
        fun blockInvalid() {
            openRep(min = "3", max = "12")
            assertFalse(host.tryCommitPendingOrBlock())
            assertTrue(commits.isEmpty())
            assertTrue(host.isOpen)
            assertTrue(host.requireCompleteBand)
        }
    }
}
