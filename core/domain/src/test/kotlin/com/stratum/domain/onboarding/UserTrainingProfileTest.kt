package com.stratum.domain.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class UserTrainingProfileTest {

    @Test
    @DisplayName("non-positive session length is unrepresentable")
    fun nonPositiveSessionLengthRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            UserTrainingProfile(sessionLengthMinutes = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            UserTrainingProfile(sessionLengthMinutes = -30)
        }
    }

    @Test
    @DisplayName("non-positive generate counts are unrepresentable")
    fun nonPositiveGenerateCountsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            UserTrainingProfile(generationTrainingDayCount = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            UserTrainingProfile(generationCycleLength = -7)
        }
    }

    @Test
    @DisplayName("rest indices outside the persisted cycle length are unrepresentable")
    fun restIndicesOutsideCycleRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            UserTrainingProfile(generationCycleLength = 7, generationRestSlotIndices = setOf(7))
        }
        assertThrows(IllegalArgumentException::class.java) {
            UserTrainingProfile(generationRestSlotIndices = setOf(-1))
        }
    }

    @Test
    @DisplayName("resolved count prefers the persisted generate count over legacy weekdays")
    fun resolvedCountPrefersGenerateCount() {
        val profile = UserTrainingProfile(
            weekdays = setOf(Weekday.Monday, Weekday.Wednesday),
            generationTrainingDayCount = 4,
        )
        assertEquals(4, profile.resolvedTrainingDayCount())
    }

    @Test
    @DisplayName("resolved count falls back to legacy weekdays, then to null")
    fun resolvedCountFallbacks() {
        val legacy = UserTrainingProfile(weekdays = setOf(Weekday.Monday, Weekday.Tuesday, Weekday.Thursday))
        assertEquals(3, legacy.resolvedTrainingDayCount())
        assertNull(UserTrainingProfile.Empty.resolvedTrainingDayCount())
    }

    @Test
    @DisplayName("persisted cycle-space detection covers each persisted field")
    fun persistedCycleSpaceDetection() {
        assertFalse(UserTrainingProfile.Empty.hasPersistedGenerateCycleSpace())
        assertTrue(UserTrainingProfile(generationTrainingDayCount = 4).hasPersistedGenerateCycleSpace())
        assertTrue(UserTrainingProfile(generationCycleLength = 7).hasPersistedGenerateCycleSpace())
        assertTrue(
            UserTrainingProfile(generationRestSlotIndices = setOf(6)).hasPersistedGenerateCycleSpace(),
        )
    }

    @Test
    @DisplayName("generate flush persists cycle-space and clears legacy weekdays")
    fun generateFlushShape() {
        val profile = UserTrainingProfile(weekdays = setOf(Weekday.Monday, Weekday.Friday))
            .withGenerateCycleSpace(trainingDayCount = 5, cycleLength = 7, restSlotIndices = setOf(3, 6))
        assertEquals(emptySet<Weekday>(), profile.weekdays)
        assertEquals(5, profile.generationTrainingDayCount)
        assertEquals(7, profile.generationCycleLength)
        assertEquals(setOf(3, 6), profile.generationRestSlotIndices)
    }

    @Test
    @DisplayName("import commit shape clears every schedule-identity field")
    fun importCommitShape() {
        val profile = UserTrainingProfile(
            sessionLengthMinutes = 60,
            weekdays = setOf(Weekday.Monday),
            generationTrainingDayCount = 4,
            generationCycleLength = 7,
            generationRestSlotIndices = setOf(6),
        ).clearedScheduleIdentity()
        assertEquals(UserTrainingProfile.Empty, profile)
    }
}
