package com.stratum.core.data.mapper

import com.stratum.core.data.db.UserProfileEntity
import com.stratum.domain.onboarding.LastTrainedBucket
import com.stratum.domain.onboarding.TrainingAge
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.onboarding.Weekday
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class UserProfileMapperTest {

    @Test
    @DisplayName("Given a complete profile when mapped to entity and back then it round-trips")
    fun completeProfileRoundTrips() {
        val profile = UserTrainingProfile(
            trainingAge = TrainingAge.Intermediate,
            lastTrained = LastTrainedBucket.WithinLastMonth,
            weekdays = setOf(Weekday.Monday, Weekday.Wednesday, Weekday.Friday),
            sessionLengthMinutes = 60,
        )

        val roundTripped = profile.toEntity().toDomain()

        assertEquals(profile, roundTripped)
    }

    @Test
    @DisplayName("Given a profile with null optionals when mapped then nulls round-trip")
    fun nullOptionalsRoundTrip() {
        val profile = UserTrainingProfile(
            trainingAge = null,
            lastTrained = LastTrainedBucket.Never,
            weekdays = emptySet(),
            sessionLengthMinutes = 45,
        )

        val roundTripped = profile.toEntity().toDomain()

        assertNull(roundTripped.trainingAge)
        assertTrue(roundTripped.weekdays.isEmpty())
        assertEquals(profile, roundTripped)
    }

    @Test
    @DisplayName("Given a profile with all weekdays when mapped then order-independent round-trip")
    fun allWeekdaysRoundTrip() {
        val profile = UserTrainingProfile(
            trainingAge = TrainingAge.Advanced,
            lastTrained = LastTrainedBucket.MoreThanAYearAgo,
            weekdays = Weekday.entries.toSet(),
            sessionLengthMinutes = 90,
        )

        val roundTripped = profile.toEntity().toDomain()

        assertEquals(profile.weekdays, roundTripped.weekdays)
    }

    @Test
    @DisplayName("Given an entity with null training age when mapped then the domain field is null")
    fun nullTrainingAgeRoundTripsToNull() {
        val entity = UserProfileEntity(
            trainingAge = null,
            lastTrained = LastTrainedBucket.WithinLastMonth,
            weekdays = setOf(Weekday.Monday, Weekday.Friday),
            sessionLengthMinutes = 60,
        )

        val domain = entity.toDomain()

        assertNull(domain.trainingAge)
        assertEquals(LastTrainedBucket.WithinLastMonth, domain.lastTrained)
        assertEquals(setOf(Weekday.Monday, Weekday.Friday), domain.weekdays)
    }
}
