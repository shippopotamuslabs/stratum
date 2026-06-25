package com.stratum.core.ui.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Covers the parse/range classification backing the duration stepper's tap-to-type
 * input. [resolveSegmentInput] is pure (no Compose/Android), so these run on the JVM.
 * Bounds mirror the onboarding schedule step: 15..240 minutes.
 */
class ResolveSegmentInputTest {

    private val min = 15
    private val max = 240

    @Nested
    @DisplayName("Hours field")
    inner class HoursField {
        private val unit = SegmentUnit.Hours

        @Test
        @DisplayName("blank input is rejected as Blank")
        fun blankRejected() {
            assertEquals(
                SegmentInputResult.Invalid(InvalidDurationInput.Blank),
                resolveSegmentInput(unit, "  ", hours = 0, minutes = 30, minMinutes = min, maxMinutes = max),
            )
        }

        @Test
        @DisplayName("non-numeric input is rejected as NotANumber")
        fun nonNumericRejected() {
            assertEquals(
                SegmentInputResult.Invalid(InvalidDurationInput.NotANumber),
                resolveSegmentInput(unit, "abc", hours = 0, minutes = 30, minMinutes = min, maxMinutes = max),
            )
        }

        @Test
        @DisplayName("valid hours commits the combined total")
        fun validHoursCommitsTotal() {
            // 2 hours + 30 minutes = 150
            assertEquals(
                SegmentInputResult.Valid(150),
                resolveSegmentInput(unit, "2", hours = 0, minutes = 30, minMinutes = min, maxMinutes = max),
            )
        }

        @Test
        @DisplayName("hours that drive the total below the minimum are TooShort")
        fun tooShortRejected() {
            // 0 hours + 0 minutes = 0, below the 15-minute floor
            assertEquals(
                SegmentInputResult.Invalid(InvalidDurationInput.TooShort),
                resolveSegmentInput(unit, "0", hours = 0, minutes = 0, minMinutes = min, maxMinutes = max),
            )
        }

        @Test
        @DisplayName("hours that drive the total above the maximum are TooLong")
        fun tooLongRejected() {
            // 5 hours + 0 minutes = 300, above the 240-minute ceiling
            assertEquals(
                SegmentInputResult.Invalid(InvalidDurationInput.TooLong),
                resolveSegmentInput(unit, "5", hours = 0, minutes = 0, minMinutes = min, maxMinutes = max),
            )
        }
    }

    @Nested
    @DisplayName("Minutes field")
    inner class MinutesField {
        private val unit = SegmentUnit.Minutes

        @Test
        @DisplayName("valid minutes commits the combined total")
        fun validMinutesCommitsTotal() {
            // 1 hour + 45 minutes = 105
            assertEquals(
                SegmentInputResult.Valid(105),
                resolveSegmentInput(unit, "45", hours = 1, minutes = 0, minMinutes = min, maxMinutes = max),
            )
        }

        @Test
        @DisplayName("minutes that drive the total below the minimum are TooShort")
        fun tooShortRejected() {
            // 0 hours + 5 minutes = 5, below the 15-minute floor
            assertEquals(
                SegmentInputResult.Invalid(InvalidDurationInput.TooShort),
                resolveSegmentInput(unit, "5", hours = 0, minutes = 0, minMinutes = min, maxMinutes = max),
            )
        }

        @Test
        @DisplayName("minutes that drive the total above the maximum are TooLong")
        fun tooLongRejected() {
            // 4 hours + 1 minute = 241, above the 240-minute ceiling
            assertEquals(
                SegmentInputResult.Invalid(InvalidDurationInput.TooLong),
                resolveSegmentInput(unit, "1", hours = 4, minutes = 0, minMinutes = min, maxMinutes = max),
            )
        }

        @Test
        @DisplayName("blank minutes is rejected as Blank")
        fun blankRejected() {
            assertEquals(
                SegmentInputResult.Invalid(InvalidDurationInput.Blank),
                resolveSegmentInput(unit, "", hours = 1, minutes = 30, minMinutes = min, maxMinutes = max),
            )
        }
    }
}
