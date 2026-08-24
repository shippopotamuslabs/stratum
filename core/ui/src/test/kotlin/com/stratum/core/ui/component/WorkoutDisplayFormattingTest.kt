package com.stratum.core.ui.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("formatRepsWithPartials / formatSetPerformance")
class WorkoutDisplayFormattingTest {

    @Test
    @DisplayName("full reps only")
    fun fullRepsOnly() {
        assertEquals("5", formatRepsWithPartials(5, null))
        assertEquals("5", formatRepsWithPartials(5, 0))
    }

    @Test
    @DisplayName("full plus partials")
    fun fullPlusPartials() {
        assertEquals("5+2", formatRepsWithPartials(5, 2))
    }

    @Test
    @DisplayName("weighted set includes partials")
    fun weightedWithPartials() {
        assertEquals("185 × 5+2", formatSetPerformance(185.0, 5, 2))
    }

    @Test
    @DisplayName("bodyweight omits zero load and keeps +N")
    fun bodyweightWithPartials() {
        assertEquals("12+1", formatSetPerformance(0.0, 12, 1))
    }
}
