package com.stratum.core.ui.component.workoutlog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SetLogKeyboardLogicTest {

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
}
