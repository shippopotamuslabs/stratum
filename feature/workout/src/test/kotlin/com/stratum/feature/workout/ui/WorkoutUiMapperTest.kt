package com.stratum.feature.workout.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("WorkoutUiMapper lift navigator initials")
class WorkoutUiMapperTest {

    @Test
    @DisplayName("multi-word names use first letters of first two words")
    fun multiWordInitials() {
        assertEquals("BB", WorkoutUiMapper.liftNavigatorInitials("Barbell Bench Press"))
        assertEquals("DR", WorkoutUiMapper.liftNavigatorInitials("Dumbbell Row"))
    }

    @Test
    @DisplayName("single-word names use first two letters")
    fun singleWordInitials() {
        assertEquals("SQ", WorkoutUiMapper.liftNavigatorInitials("Squat"))
        assertEquals("DE", WorkoutUiMapper.liftNavigatorInitials("Deadlift"))
    }

    @Test
    @DisplayName("blank name yields empty initials")
    fun blankName() {
        assertEquals("", WorkoutUiMapper.liftNavigatorInitials("   "))
    }
}
