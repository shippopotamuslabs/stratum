package com.stratum.core.data.repository

import androidx.room.Room
import app.cash.turbine.test
import com.stratum.core.data.db.StratumDatabase
import com.stratum.domain.onboarding.LastTrainedBucket
import com.stratum.domain.onboarding.TrainingAge
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.onboarding.Weekday
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
class RoomUserTrainingProfileRepositoryTest {

    private lateinit var database: StratumDatabase
    private lateinit var subject: RoomUserTrainingProfileRepository

    @BeforeEach
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StratumDatabase::class.java,
        ).allowMainThreadQueries().build()
        subject = RoomUserTrainingProfileRepository(dao = database.userProfileDao())
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    @DisplayName("Given no profile when observed then null is emitted")
    fun emptyEmitsNull() = runTest {
        subject.observeProfile().test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a profile when saved then observe emits it")
    fun savePersistsProfile() = runTest {
        val profile = UserTrainingProfile(
            trainingAge = TrainingAge.Intermediate,
            lastTrained = LastTrainedBucket.WithinLastMonth,
            weekdays = setOf(Weekday.Monday, Weekday.Friday),
            sessionLengthMinutes = 60,
        )

        subject.saveProfile(profile)

        subject.observeProfile().test {
            assertEquals(profile, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a saved profile when overwritten then observe emits the new profile")
    fun saveOverwritesExisting() = runTest {
        subject.saveProfile(
            UserTrainingProfile(
                trainingAge = TrainingAge.Beginner,
                lastTrained = LastTrainedBucket.Never,
                weekdays = setOf(Weekday.Tuesday),
                sessionLengthMinutes = 45,
            ),
        )

        val updated = UserTrainingProfile(
            trainingAge = TrainingAge.Advanced,
            lastTrained = LastTrainedBucket.MoreThanAYearAgo,
            weekdays = setOf(Weekday.Saturday, Weekday.Sunday),
            sessionLengthMinutes = 90,
        )
        subject.saveProfile(updated)

        subject.observeProfile().test {
            assertEquals(updated, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
