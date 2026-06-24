package com.stratum.core.data.db

import androidx.room.Room
import app.cash.turbine.test
import com.stratum.domain.onboarding.OnboardingStep
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
class OnboardingProgressDaoTest {

    private lateinit var database: StratumDatabase
    private lateinit var subject: OnboardingProgressDao

    @BeforeEach
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StratumDatabase::class.java,
        ).allowMainThreadQueries().build()
        subject = database.onboardingProgressDao()
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
    @DisplayName("Given a progress row when upserted then observe emits the row")
    fun upsertPersistsRowAndObserveEmitsIt() = runTest {
        val row = OnboardingProgressEntity(
            currentStep = OnboardingStep.Profile,
            startedAt = 1L,
            completedAt = null,
        )

        subject.upsert(row)

        subject.observe().test {
            val emitted = awaitItem()
            assertNotNull(emitted)
            assertEquals(OnboardingStep.Profile, emitted?.currentStep)
            assertEquals(1L, emitted?.startedAt)
            assertNull(emitted?.completedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given an existing row when upserted again then it overwrites the single row")
    fun upsertOverwritesExistingRow() = runTest {
        subject.upsert(OnboardingProgressEntity(currentStep = OnboardingStep.Welcome, startedAt = 1L, completedAt = null))
        subject.upsert(OnboardingProgressEntity(currentStep = OnboardingStep.Generate, startedAt = 1L, completedAt = 99L))

        val current = subject.getOrNull()
        assertEquals(OnboardingStep.Generate, current?.currentStep)
        assertEquals(99L, current?.completedAt)
    }
}