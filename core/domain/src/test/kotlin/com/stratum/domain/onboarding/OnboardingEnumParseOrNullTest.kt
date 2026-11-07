package com.stratum.domain.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OnboardingEnumParseOrNullTest {
    @Test
    fun `TrainingAge parseOrNull returns the exact-name entry`() {
        assertEquals(TrainingAge.Beginner, TrainingAge.parseOrNull("Beginner"))
        assertEquals(TrainingAge.LateIntermediate, TrainingAge.parseOrNull("LateIntermediate"))
        assertEquals(TrainingAge.Advanced, TrainingAge.parseOrNull("Advanced"))
    }

    @Test
    fun `TrainingAge parseOrNull returns null for non-exact names`() {
        assertNull(TrainingAge.parseOrNull("beginner"))
        assertNull(TrainingAge.parseOrNull("Expert"))
        assertNull(TrainingAge.parseOrNull(""))
    }

    @Test
    fun `LastTrainedBucket parseOrNull returns the exact-name entry`() {
        assertEquals(
            LastTrainedBucket.WithinLastThreeMonths,
            LastTrainedBucket.parseOrNull("WithinLastThreeMonths"),
        )
        assertEquals(
            LastTrainedBucket.MoreThanThreeMonthsAgo,
            LastTrainedBucket.parseOrNull("MoreThanThreeMonthsAgo"),
        )
        assertEquals(LastTrainedBucket.Never, LastTrainedBucket.parseOrNull("Never"))
    }

    @Test
    fun `LastTrainedBucket parseOrNull returns null for non-exact names`() {
        assertNull(LastTrainedBucket.parseOrNull("never"))
        assertNull(LastTrainedBucket.parseOrNull("Recently"))
        assertNull(LastTrainedBucket.parseOrNull(""))
    }

    @Test
    fun `Weekday parseOrNull returns the exact-name entry`() {
        assertEquals(Weekday.Monday, Weekday.parseOrNull("Monday"))
        assertEquals(Weekday.Sunday, Weekday.parseOrNull("Sunday"))
    }

    @Test
    fun `Weekday parseOrNull returns null for non-exact names`() {
        assertNull(Weekday.parseOrNull("monday"))
        assertNull(Weekday.parseOrNull("Mon"))
        assertNull(Weekday.parseOrNull(""))
    }

    @Test
    fun `MuscleGroup parseOrNull returns the exact-name entry`() {
        assertEquals(MuscleGroup.Chest, MuscleGroup.parseOrNull("Chest"))
        assertEquals(MuscleGroup.LateralDeltoid, MuscleGroup.parseOrNull("LateralDeltoid"))
        assertEquals(MuscleGroup.Adductor, MuscleGroup.parseOrNull("Adductor"))
    }

    @Test
    fun `MuscleGroup parseOrNull returns null for non-exact names`() {
        assertNull(MuscleGroup.parseOrNull("chest"))
        assertNull(MuscleGroup.parseOrNull("Quads"))
        assertNull(MuscleGroup.parseOrNull(""))
    }
}
