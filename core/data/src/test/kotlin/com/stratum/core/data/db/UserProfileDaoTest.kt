package com.stratum.core.data.db

import androidx.room.Room
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
class UserProfileDaoTest {

    private lateinit var database: StratumDatabase
    private lateinit var subject: UserProfileDao

    @BeforeEach
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StratumDatabase::class.java,
        ).allowMainThreadQueries().build()
        subject = database.userProfileDao()
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    @DisplayName("Given an empty table when observed then it emits null")
    fun emptyTableEmitsNull() = runTest {
        subject.observe().test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a profile row when upserted then observe emits the row")
    fun upsertPersistsRowAndObserveEmitsIt() = runTest {
        val row = UserProfileEntity(
            trainingAge = "Intermediate",
            lastTrained = "WithinLastMonth",
            weekdays = "Monday,Wednesday,Friday",
            sessionLengthMinutes = 60,
        )

        subject.upsert(row)

        subject.observe().test {
            val emitted = awaitItem()
            assertNotNull(emitted)
            assertEquals("Intermediate", emitted?.trainingAge)
            assertEquals("Monday,Wednesday,Friday", emitted?.weekdays)
            assertEquals(60, emitted?.sessionLengthMinutes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given an existing row when upserted again then it overwrites the single row")
    fun upsertOverwritesExistingRow() = runTest {
        subject.upsert(
            UserProfileEntity(
                trainingAge = "Beginner",
                lastTrained = "Never",
                weekdays = "Tuesday",
                sessionLengthMinutes = 45,
            ),
        )
        subject.upsert(
            UserProfileEntity(
                trainingAge = "Advanced",
                lastTrained = "MoreThanAYearAgo",
                weekdays = "Saturday,Sunday",
                sessionLengthMinutes = 75,
            ),
        )

        val current = subject.getOrNull()
        assertEquals("Advanced", current?.trainingAge)
        assertEquals("Saturday,Sunday", current?.weekdays)
        assertEquals(75, current?.sessionLengthMinutes)
    }

    @Test
    @DisplayName("Given a profile with null optional fields when upserted then nulls round-trip")
    fun nullOptionalsRoundTrip() = runTest {
        subject.upsert(
            UserProfileEntity(
                trainingAge = null,
                lastTrained = null,
                weekdays = "",
                sessionLengthMinutes = 60,
            ),
        )

        val current = subject.getOrNull()
        assertNull(current?.trainingAge)
        assertNull(current?.lastTrained)
        assertEquals("", current?.weekdays)
    }
}
