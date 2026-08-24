package com.stratum.core.ui.component.workoutlog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SetLogKeyboardLogicTest {

    @Nested
    @DisplayName("toggleRepRangeField")
    inner class ToggleRepRange {
        @Test
        @DisplayName("min toggles to max and back")
        fun togglesBothWays() {
            assertEquals(RepRangeKeyboardField.Max, toggleRepRangeField(RepRangeKeyboardField.Min))
            assertEquals(RepRangeKeyboardField.Min, toggleRepRangeField(RepRangeKeyboardField.Max))
        }
    }

    @Nested
    @DisplayName("parseRepRangeDraft")
    inner class ParseRepDraft {
        @Test
        @DisplayName("empty and junk become 0; digits parse")
        fun parseDrafts() {
            assertEquals(0, parseRepRangeDraft(""))
            assertEquals(0, parseRepRangeDraft("abc"))
            assertEquals(8, parseRepRangeDraft("8"))
            assertEquals(12, parseRepRangeDraft("12"))
        }
    }

    @Nested
    @DisplayName("validateRepRangeDrafts")
    inner class ValidateRepDrafts {
        @Test
        @DisplayName("blank is soft-valid until leave/collapse")
        fun blankSoftValid() {
            val live = validateRepRangeDrafts("", "16", requireCompleteBand = false)
            assertTrue(live.isValid)
            assertFalse(live.minInvalid)
            val commit = validateRepRangeDrafts("", "16", requireCompleteBand = true)
            assertFalse(commit.isValid)
            assertTrue(commit.minInvalid)
            assertEquals(RepRangeDraftIssue.MissingValue, commit.issue)
        }

        @Test
        @DisplayName("typed zero is hard-invalid (below floor)")
        fun zeroHardInvalid() {
            val v = validateRepRangeDrafts("0", "16")
            assertFalse(v.isValid)
            assertTrue(v.minInvalid)
            assertEquals(RepRangeDraftIssue.BelowMinFloor, v.issue)
        }

        @Test
        @DisplayName("positive min below floor is invalid")
        fun minBelowFloor() {
            val v = validateRepRangeDrafts("3", "12")
            assertFalse(v.isValid)
            assertTrue(v.minInvalid)
            assertEquals(RepRangeDraftIssue.BelowMinFloor, v.issue)
        }

        @Test
        @DisplayName("min equals max is invalid")
        fun maxEqualsMin() {
            val v = validateRepRangeDrafts("8", "8")
            assertFalse(v.isValid)
            assertTrue(v.maxInvalid)
            assertEquals(RepRangeDraftIssue.MaxNotAboveMin, v.issue)
        }

        @Test
        @DisplayName("min greater than max is invalid")
        fun minGreaterThanMax() {
            val v = validateRepRangeDrafts("12", "8")
            assertFalse(v.isValid)
            assertTrue(v.maxInvalid)
            assertEquals(RepRangeDraftIssue.MaxNotAboveMin, v.issue)
        }

        @Test
        @DisplayName("max greater than min at floor is valid")
        fun validBand() {
            val v = validateRepRangeDrafts("5", "6")
            assertTrue(v.isValid)
        }

        @Test
        @DisplayName("both blank invalid only when requireCompleteBand")
        fun bothBlank() {
            assertTrue(validateRepRangeDrafts("", "", requireCompleteBand = false).isValid)
            val commit = validateRepRangeDrafts("", "", requireCompleteBand = true)
            assertFalse(commit.isValid)
            assertEquals(RepRangeDraftIssue.MissingValue, commit.issue)
        }
    }

    @Nested
    @DisplayName("insertNumericKey")
    inner class InsertAtCursor {
        @Test
        @DisplayName("digits insert mid-string and advance cursor")
        fun digitMid() {
            val next =
                insertNumericKey(
                    buffer = NumericEditBuffer(text = "135", cursor = 1),
                    key = '2',
                    allowDecimal = true,
                )
            assertEquals(NumericEditBuffer(text = "1235", cursor = 2), next)
        }

        @Test
        @DisplayName("digits insert at start and end")
        fun digitEnds() {
            assertEquals(
                NumericEditBuffer(text = "91", cursor = 1),
                insertNumericKey(
                    buffer = NumericEditBuffer(text = "1", cursor = 0),
                    key = '9',
                    allowDecimal = false,
                ),
            )
            assertEquals(
                NumericEditBuffer(text = "15", cursor = 2),
                insertNumericKey(
                    buffer = NumericEditBuffer(text = "1", cursor = 1),
                    key = '5',
                    allowDecimal = false,
                ),
            )
        }

        @Test
        @DisplayName("decimal inserts once when allowed")
        fun decimalOnce() {
            assertEquals(
                NumericEditBuffer(text = "1.5", cursor = 2),
                insertNumericKey(
                    buffer = NumericEditBuffer(text = "15", cursor = 1),
                    key = '.',
                    allowDecimal = true,
                ),
            )
            assertEquals(
                NumericEditBuffer(text = "1.5", cursor = 2),
                insertNumericKey(
                    buffer = NumericEditBuffer(text = "1.5", cursor = 2),
                    key = '.',
                    allowDecimal = true,
                ),
            )
        }

        @Test
        @DisplayName("decimal ignored when not allowed")
        fun decimalDisallowed() {
            assertEquals(
                NumericEditBuffer(text = "12", cursor = 1),
                insertNumericKey(
                    buffer = NumericEditBuffer(text = "12", cursor = 1),
                    key = '.',
                    allowDecimal = false,
                ),
            )
        }

        @Test
        @DisplayName("non digit non decimal ignored")
        fun junkIgnored() {
            assertEquals(
                NumericEditBuffer(text = "1", cursor = 1),
                insertNumericKey(
                    buffer = NumericEditBuffer(text = "1", cursor = 1),
                    key = 'a',
                    allowDecimal = true,
                ),
            )
        }

        @Test
        @DisplayName("out-of-range cursor is clamped before insert")
        fun clampsCursor() {
            assertEquals(
                NumericEditBuffer(text = "12", cursor = 2),
                insertNumericKey(
                    buffer = NumericEditBuffer(text = "1", cursor = 99),
                    key = '2',
                    allowDecimal = false,
                ),
            )
        }
    }

    @Nested
    @DisplayName("backspaceNumericBuffer")
    inner class BackspaceAtCursor {
        @Test
        @DisplayName("deletes char before caret mid-string")
        fun mid() {
            assertEquals(
                NumericEditBuffer(text = "15", cursor = 1),
                backspaceNumericBuffer(NumericEditBuffer(text = "135", cursor = 2)),
            )
        }

        @Test
        @DisplayName("no-op at caret index 0")
        fun atStart() {
            assertEquals(
                NumericEditBuffer(text = "135", cursor = 0),
                backspaceNumericBuffer(NumericEditBuffer(text = "135", cursor = 0)),
            )
        }

        @Test
        @DisplayName("deletes last char when caret at end")
        fun atEnd() {
            assertEquals(
                NumericEditBuffer(text = "13", cursor = 2),
                backspaceNumericBuffer(NumericEditBuffer(text = "135", cursor = 3)),
            )
        }

        @Test
        @DisplayName("empty stays empty")
        fun empty() {
            assertEquals(
                NumericEditBuffer(text = "", cursor = 0),
                backspaceNumericBuffer(NumericEditBuffer(text = "", cursor = 0)),
            )
        }
    }

    @Nested
    @DisplayName("appendNumericKey")
    inner class Append {
        @Test
        @DisplayName("digits append")
        fun digitsAppend() {
            assertEquals("12", appendNumericKey("1", '2', allowDecimal = true))
            assertEquals("5", appendNumericKey("", '5', allowDecimal = false))
        }

        @Test
        @DisplayName("decimal appends once when allowed")
        fun decimalOnce() {
            assertEquals("1.", appendNumericKey("1", '.', allowDecimal = true))
            assertEquals("1.5", appendNumericKey("1.", '5', allowDecimal = true))
            assertEquals("1.5", appendNumericKey("1.5", '.', allowDecimal = true))
        }

        @Test
        @DisplayName("decimal ignored when not allowed")
        fun decimalDisallowed() {
            assertEquals("12", appendNumericKey("12", '.', allowDecimal = false))
        }

        @Test
        @DisplayName("non digit non decimal ignored")
        fun junkIgnored() {
            assertEquals("1", appendNumericKey("1", 'a', allowDecimal = true))
        }
    }

    @Nested
    @DisplayName("backspaceNumericValue")
    inner class Backspace {
        @Test
        @DisplayName("drops last char")
        fun dropsLast() {
            assertEquals("13", backspaceNumericValue("135"))
            assertEquals("", backspaceNumericValue("1"))
        }

        @Test
        @DisplayName("empty stays empty")
        fun empty() {
            assertEquals("", backspaceNumericValue(""))
        }
    }

    @Nested
    @DisplayName("canCompleteSetLog")
    inner class CanComplete {
        @Test
        @DisplayName("needs weight and reps sides both satisfied")
        fun bothSides() {
            assertFalse(
                canCompleteSetLog(
                    showWeightInput = true,
                    weightText = "",
                    hasWeightRecommendation = false,
                    repsText = "8",
                    hasRepsRecommendation = false,
                ),
            )
            assertFalse(
                canCompleteSetLog(
                    showWeightInput = true,
                    weightText = "135",
                    hasWeightRecommendation = false,
                    repsText = "",
                    hasRepsRecommendation = false,
                ),
            )
            assertTrue(
                canCompleteSetLog(
                    showWeightInput = true,
                    weightText = "135",
                    hasWeightRecommendation = false,
                    repsText = "8",
                    hasRepsRecommendation = false,
                ),
            )
            assertFalse(
                canCompleteSetLog(
                    showWeightInput = true,
                    weightText = "abc",
                    hasWeightRecommendation = false,
                    repsText = "8",
                    hasRepsRecommendation = false,
                ),
            )
        }

        @Test
        @DisplayName("checkbox enabled when complete even if cannot re-complete")
        fun checkboxUnlogAlways() {
            assertTrue(isSetLogCheckboxEnabled(isComplete = true, canComplete = false))
            assertTrue(isSetLogCheckboxEnabled(isComplete = true, canComplete = true))
            assertFalse(isSetLogCheckboxEnabled(isComplete = false, canComplete = false))
            assertTrue(isSetLogCheckboxEnabled(isComplete = false, canComplete = true))
        }

        @Test
        @DisplayName("recommendations stand in for empty text")
        fun recsCount() {
            assertTrue(
                canCompleteSetLog(
                    showWeightInput = true,
                    weightText = "",
                    hasWeightRecommendation = true,
                    repsText = "",
                    hasRepsRecommendation = true,
                ),
            )
            assertTrue(
                canCompleteSetLog(
                    showWeightInput = true,
                    weightText = "100",
                    hasWeightRecommendation = false,
                    repsText = "",
                    hasRepsRecommendation = true,
                ),
            )
        }

        @Test
        @DisplayName("bodyweight only needs reps side")
        fun bodyweight() {
            assertTrue(
                canCompleteSetLog(
                    showWeightInput = false,
                    weightText = "",
                    hasWeightRecommendation = false,
                    repsText = "12",
                    hasRepsRecommendation = false,
                ),
            )
            assertTrue(
                canCompleteSetLog(
                    showWeightInput = false,
                    weightText = "",
                    hasWeightRecommendation = false,
                    repsText = "",
                    hasRepsRecommendation = true,
                ),
            )
            assertFalse(
                canCompleteSetLog(
                    showWeightInput = false,
                    weightText = "",
                    hasWeightRecommendation = false,
                    repsText = "",
                    hasRepsRecommendation = false,
                ),
            )
        }

        @Test
        @DisplayName("fromFields matches amount and placeholder recs")
        fun fromFields() {
            assertTrue(
                canCompleteSetLogFromFields(
                    showWeightInput = true,
                    weightText = "",
                    weightPlaceholder = "135",
                    recommendedWeightAmount = 135.0,
                    assistMode = false,
                    repsText = "",
                    repsPlaceholder = "8",
                    recommendedReps = 8,
                ),
            )
            assertTrue(
                canCompleteSetLogFromFields(
                    showWeightInput = true,
                    weightText = "",
                    weightPlaceholder = "135",
                    recommendedWeightAmount = 0.0,
                    assistMode = false,
                    repsText = "",
                    repsPlaceholder = "4-6",
                    recommendedReps = 0,
                ),
            )
        }
    }

    @Nested
    @DisplayName("primary icon and action")
    inner class Primary {
        @Test
        @DisplayName("canComplete true always shows check")
        fun canCompleteCheck() {
            assertEquals(
                SetLogKeyboardPrimaryIcon.Check,
                setLogKeyboardPrimaryIcon(canComplete = true),
            )
            assertEquals(
                SetLogKeyboardAction.Complete,
                resolveSetLogKeyboardAction(canComplete = true),
            )
        }

        @Test
        @DisplayName("cannot complete hides primary (no nav substitute in the check slot)")
        fun cannotCompleteHidesPrimary() {
            assertEquals(null, setLogKeyboardPrimaryIcon(canComplete = false))
            assertEquals(null, resolveSetLogKeyboardAction(canComplete = false))
        }
    }

    @Nested
    @DisplayName("elevated nav icon")
    inner class ElevatedNav {
        @Test
        @DisplayName("when weight input shown, elevates field chevron even without check")
        fun elevatesWhenWeightInputShown() {
            assertEquals(
                SetLogKeyboardPrimaryIcon.NextReps,
                setLogKeyboardElevatedNavIcon(
                    active = SetLogKeyboardField.Weight,
                    showWeightInput = true,
                ),
            )
            assertEquals(
                SetLogKeyboardPrimaryIcon.PrevWeight,
                setLogKeyboardElevatedNavIcon(
                    active = SetLogKeyboardField.Reps,
                    showWeightInput = true,
                ),
            )
            assertEquals(
                SetLogKeyboardAction.FocusReps,
                resolveSetLogKeyboardElevatedNavAction(
                    active = SetLogKeyboardField.Weight,
                    showWeightInput = true,
                ),
            )
            assertEquals(
                SetLogKeyboardAction.FocusWeight,
                resolveSetLogKeyboardElevatedNavAction(
                    active = SetLogKeyboardField.Reps,
                    showWeightInput = true,
                ),
            )
        }

        @Test
        @DisplayName("null on bodyweight (no second field to navigate)")
        fun nullBodyweight() {
            assertEquals(
                null,
                setLogKeyboardElevatedNavIcon(
                    active = SetLogKeyboardField.Reps,
                    showWeightInput = false,
                ),
            )
            assertEquals(
                null,
                resolveSetLogKeyboardElevatedNavAction(
                    active = SetLogKeyboardField.Reps,
                    showWeightInput = false,
                ),
            )
        }
    }

    @Nested
    @DisplayName("shouldShowSplitRepsCell")
    inner class SplitRepsCell {
        @Test
        @DisplayName("full mode without partials stays single cell")
        fun fullWithoutPartials() {
            assertFalse(shouldShowSplitRepsCell(RepsEntryMode.Full, ""))
        }

        @Test
        @DisplayName("full mode with partials splits")
        fun fullWithPartials() {
            assertTrue(shouldShowSplitRepsCell(RepsEntryMode.Full, "2"))
        }

        @Test
        @DisplayName("partial mode always splits")
        fun partialAlwaysSplits() {
            assertTrue(shouldShowSplitRepsCell(RepsEntryMode.Partial, ""))
            assertTrue(shouldShowSplitRepsCell(RepsEntryMode.Partial, "1"))
        }
    }
}
