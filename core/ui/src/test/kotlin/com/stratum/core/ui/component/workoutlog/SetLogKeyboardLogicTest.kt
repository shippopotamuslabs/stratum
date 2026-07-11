package com.stratum.core.ui.component.workoutlog

import org.junit.jupiter.api.Assertions.assertEquals
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
    @DisplayName("primary icon and action")
    inner class Primary {
        @Test
        @DisplayName("weight with empty reps shows next")
        fun weightEmptyRepsNext() {
            assertEquals(
                SetLogKeyboardPrimaryIcon.NextReps,
                setLogKeyboardPrimaryIcon(
                    active = SetLogKeyboardField.Weight,
                    weightText = "135",
                    repsText = "",
                    showWeightInput = true,
                ),
            )
            assertEquals(
                SetLogKeyboardAction.FocusReps,
                resolveSetLogKeyboardAction(
                    active = SetLogKeyboardField.Weight,
                    weightText = "135",
                    repsText = "",
                    showWeightInput = true,
                    canComplete = true,
                ),
            )
        }

        @Test
        @DisplayName("weight with reps filled shows check and completes when allowed")
        fun weightRepsFilledComplete() {
            assertEquals(
                SetLogKeyboardPrimaryIcon.Check,
                setLogKeyboardPrimaryIcon(
                    active = SetLogKeyboardField.Weight,
                    weightText = "135",
                    repsText = "5",
                    showWeightInput = true,
                ),
            )
            assertEquals(
                SetLogKeyboardAction.Complete,
                resolveSetLogKeyboardAction(
                    active = SetLogKeyboardField.Weight,
                    weightText = "135",
                    repsText = "5",
                    showWeightInput = true,
                    canComplete = true,
                ),
            )
        }

        @Test
        @DisplayName("weight with reps filled dismisses when complete unavailable")
        fun weightRepsFilledDismiss() {
            assertEquals(
                SetLogKeyboardAction.Dismiss,
                resolveSetLogKeyboardAction(
                    active = SetLogKeyboardField.Weight,
                    weightText = "135",
                    repsText = "5",
                    showWeightInput = true,
                    canComplete = false,
                ),
            )
        }

        @Test
        @DisplayName("reps with empty weight shows prev")
        fun repsEmptyWeightPrev() {
            assertEquals(
                SetLogKeyboardPrimaryIcon.PrevWeight,
                setLogKeyboardPrimaryIcon(
                    active = SetLogKeyboardField.Reps,
                    weightText = "",
                    repsText = "8",
                    showWeightInput = true,
                ),
            )
            assertEquals(
                SetLogKeyboardAction.FocusWeight,
                resolveSetLogKeyboardAction(
                    active = SetLogKeyboardField.Reps,
                    weightText = "",
                    repsText = "8",
                    showWeightInput = true,
                    canComplete = true,
                ),
            )
        }

        @Test
        @DisplayName("reps with weight filled shows check")
        fun repsWeightFilledCheck() {
            assertEquals(
                SetLogKeyboardPrimaryIcon.Check,
                setLogKeyboardPrimaryIcon(
                    active = SetLogKeyboardField.Reps,
                    weightText = "100",
                    repsText = "",
                    showWeightInput = true,
                ),
            )
            assertEquals(
                SetLogKeyboardAction.Complete,
                resolveSetLogKeyboardAction(
                    active = SetLogKeyboardField.Reps,
                    weightText = "100",
                    repsText = "",
                    showWeightInput = true,
                    canComplete = true,
                ),
            )
        }

        @Test
        @DisplayName("bodyweight reps always check")
        fun bodyweightRepsCheck() {
            assertEquals(
                SetLogKeyboardPrimaryIcon.Check,
                setLogKeyboardPrimaryIcon(
                    active = SetLogKeyboardField.Reps,
                    weightText = "",
                    repsText = "12",
                    showWeightInput = false,
                ),
            )
            assertEquals(
                SetLogKeyboardAction.Complete,
                resolveSetLogKeyboardAction(
                    active = SetLogKeyboardField.Reps,
                    weightText = "",
                    repsText = "12",
                    showWeightInput = false,
                    canComplete = true,
                ),
            )
        }
    }
}
