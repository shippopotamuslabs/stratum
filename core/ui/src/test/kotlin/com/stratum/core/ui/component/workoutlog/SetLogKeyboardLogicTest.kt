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
    @DisplayName("appendSetLogKey")
    inner class Append {
        @Test
        @DisplayName("digits append")
        fun digitsAppend() {
            assertEquals("12", appendSetLogKey("1", '2', allowDecimal = true))
            assertEquals("5", appendSetLogKey("", '5', allowDecimal = false))
        }

        @Test
        @DisplayName("decimal appends once when allowed")
        fun decimalOnce() {
            assertEquals("1.", appendSetLogKey("1", '.', allowDecimal = true))
            assertEquals("1.5", appendSetLogKey("1.", '5', allowDecimal = true))
            assertEquals("1.5", appendSetLogKey("1.5", '.', allowDecimal = true))
        }

        @Test
        @DisplayName("decimal ignored when not allowed")
        fun decimalDisallowed() {
            assertEquals("12", appendSetLogKey("12", '.', allowDecimal = false))
        }

        @Test
        @DisplayName("non digit non decimal ignored")
        fun junkIgnored() {
            assertEquals("1", appendSetLogKey("1", 'a', allowDecimal = true))
        }
    }

    @Nested
    @DisplayName("backspaceSetLogValue")
    inner class Backspace {
        @Test
        @DisplayName("drops last char")
        fun dropsLast() {
            assertEquals("13", backspaceSetLogValue("135"))
            assertEquals("", backspaceSetLogValue("1"))
        }

        @Test
        @DisplayName("empty stays empty")
        fun empty() {
            assertEquals("", backspaceSetLogValue(""))
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
                setLogKeyboardPrimaryIcon(
                    active = SetLogKeyboardField.Weight,
                    canComplete = true,
                    showWeightInput = true,
                ),
            )
            assertEquals(
                SetLogKeyboardPrimaryIcon.Check,
                setLogKeyboardPrimaryIcon(
                    active = SetLogKeyboardField.Reps,
                    canComplete = true,
                    showWeightInput = true,
                ),
            )
            assertEquals(
                SetLogKeyboardAction.Complete,
                resolveSetLogKeyboardAction(
                    active = SetLogKeyboardField.Reps,
                    canComplete = true,
                    showWeightInput = true,
                ),
            )
        }

        @Test
        @DisplayName("cannot complete on weight navigates to reps")
        fun weightNext() {
            assertEquals(
                SetLogKeyboardPrimaryIcon.NextReps,
                setLogKeyboardPrimaryIcon(
                    active = SetLogKeyboardField.Weight,
                    canComplete = false,
                    showWeightInput = true,
                ),
            )
            assertEquals(
                SetLogKeyboardAction.FocusReps,
                resolveSetLogKeyboardAction(
                    active = SetLogKeyboardField.Weight,
                    canComplete = false,
                    showWeightInput = true,
                ),
            )
        }

        @Test
        @DisplayName("cannot complete on reps navigates to weight")
        fun repsPrev() {
            assertEquals(
                SetLogKeyboardPrimaryIcon.PrevWeight,
                setLogKeyboardPrimaryIcon(
                    active = SetLogKeyboardField.Reps,
                    canComplete = false,
                    showWeightInput = true,
                ),
            )
            assertEquals(
                SetLogKeyboardAction.FocusWeight,
                resolveSetLogKeyboardAction(
                    active = SetLogKeyboardField.Reps,
                    canComplete = false,
                    showWeightInput = true,
                ),
            )
        }

        @Test
        @DisplayName("check without canComplete dismisses")
        fun checkDismiss() {
            assertEquals(
                SetLogKeyboardAction.Dismiss,
                resolveSetLogKeyboardAction(
                    active = SetLogKeyboardField.Reps,
                    canComplete = false,
                    showWeightInput = false,
                ),
            )
        }
    }

    @Nested
    @DisplayName("elevated nav icon")
    inner class ElevatedNav {
        @Test
        @DisplayName("when check is primary and weight input shown, elevates field chevron")
        fun elevatesWhenCheckWithWeight() {
            assertEquals(
                SetLogKeyboardPrimaryIcon.NextReps,
                setLogKeyboardElevatedNavIcon(
                    active = SetLogKeyboardField.Weight,
                    canComplete = true,
                    showWeightInput = true,
                ),
            )
            assertEquals(
                SetLogKeyboardPrimaryIcon.PrevWeight,
                setLogKeyboardElevatedNavIcon(
                    active = SetLogKeyboardField.Reps,
                    canComplete = true,
                    showWeightInput = true,
                ),
            )
            assertEquals(
                SetLogKeyboardAction.FocusReps,
                resolveSetLogKeyboardElevatedNavAction(
                    active = SetLogKeyboardField.Weight,
                    canComplete = true,
                    showWeightInput = true,
                ),
            )
            assertEquals(
                SetLogKeyboardAction.FocusWeight,
                resolveSetLogKeyboardElevatedNavAction(
                    active = SetLogKeyboardField.Reps,
                    canComplete = true,
                    showWeightInput = true,
                ),
            )
        }

        @Test
        @DisplayName("null when primary is already a nav chevron (no duplicate)")
        fun nullWhenPrimaryIsNav() {
            assertEquals(
                null,
                setLogKeyboardElevatedNavIcon(
                    active = SetLogKeyboardField.Weight,
                    canComplete = false,
                    showWeightInput = true,
                ),
            )
            assertEquals(
                null,
                setLogKeyboardElevatedNavIcon(
                    active = SetLogKeyboardField.Reps,
                    canComplete = false,
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
                    canComplete = true,
                    showWeightInput = false,
                ),
            )
            assertEquals(
                null,
                setLogKeyboardElevatedNavIcon(
                    active = SetLogKeyboardField.Reps,
                    canComplete = false,
                    showWeightInput = false,
                ),
            )
        }
    }
}
