package com.stratum.core.data.repository

import androidx.room.Room
import app.cash.turbine.test
import com.stratum.core.data.db.StratumDatabase
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
class RoomOnboardingRepositoryTest {

    private lateinit var database: StratumDatabase
    private lateinit var subject: RoomOnboardingRepository

    @BeforeEach
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StratumDatabase::class.java,
        ).allowMainThreadQueries().build()
        subject = RoomOnboardingRepository(dao = database.onboardingProgressDao())
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    @DisplayName("Given no progress when observed then null is emitted")
    fun emptyEmitsNull() = runTest {
        subject.observeProgress().test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a step when saved then observe emits in-progress progress")
    fun saveStepPersists() = runTest {
        subject.saveStep(OnboardingStep.Profile)

        subject.observeProgress().test {
            val emitted = awaitItem()
            assertNotNull(emitted)
            assertEquals(OnboardingStep.Profile, emitted?.currentStep)
            assertNull(emitted?.completedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given saved progress when marked complete then completedAt is set")
    fun markCompleteSetsCompletedAt() = runTest {
        subject.saveStep(OnboardingStep.Specialization)

        subject.markComplete()

        subject.observeProgress().test {
            val emitted = awaitItem()
            assertNotNull(emitted?.completedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given completed onboarding when reopened then completedAt is cleared and step is Review")
    fun reopenWizardClearsCompletion() = runTest {
        subject.saveStep(OnboardingStep.Generate)
        subject.markComplete()

        subject.reopenWizard()

        subject.observeProgress().test {
            val emitted = awaitItem()
            assertEquals(OnboardingStep.Review, emitted?.currentStep)
            assertNull(emitted?.completedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
