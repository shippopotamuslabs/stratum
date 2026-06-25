package com.stratum.feature.onboarding

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.OnboardingProgress
import com.stratum.domain.onboarding.OnboardingStep
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.onboarding.SpecializationPreference
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class OnboardingSpecializationStepViewModelTest {

    private lateinit var subject: OnboardingViewModel

    @BeforeEach
    fun seedSpecializationStep() {
        OnboardingViewModelTestFixtures.resetDefaults()
        OnboardingViewModelTestFixtures.fakeRepository.seed(
            OnboardingProgress(OnboardingStep.Specialization, startedAt = 0L, completedAt = null),
        )
        OnboardingViewModelTestFixtures.fakeSpecializationRepository.seed(SpecializationPreference.Empty)
        subject = OnboardingViewModelTestFixtures.buildViewModel()
    }

    @Test
    @DisplayName("Given an empty specialization when on Specialization step then canAdvance is true (skip is valid)")
    fun emptySpecializationCanAdvance() = runTest {
        subject.uiState.test {
            assertTrue(awaitItem().canAdvance)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a muscle toggle when onMuscleToggled then the selection updates")
    fun muscleToggleUpdatesSelection() = runTest {
        subject.onMuscleToggled("Chest")

        subject.uiState.test {
            assertTrue(awaitItem().specializationDraft.selectedMuscleLabels.contains("Chest"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a selected muscle when toggled again then it is removed")
    fun muscleToggleRemovesWhenAlreadySelected() = runTest {
        subject.onMuscleToggled("Chest")
        subject.onMuscleToggled("Chest")

        subject.uiState.test {
            assertFalse(awaitItem().specializationDraft.selectedMuscleLabels.contains("Chest"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a muscle toggled ON, then OFF, then ON again then it is selected")
    fun muscleToggleCycleWorks() = runTest {
        subject.onMuscleToggled("Chest")
        subject.onMuscleToggled("Chest")
        subject.onMuscleToggled("Chest")

        subject.uiState.test {
            assertTrue(awaitItem().specializationDraft.selectedMuscleLabels.contains("Chest"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given three muscles selected when checked then the draft is at cap")
    fun threeMusclesIsAtCap() = runTest {
        subject.onMuscleToggled("Chest")
        subject.onMuscleToggled("Back")
        subject.onMuscleToggled("Quads")

        subject.uiState.test {
            val draft = awaitItem().specializationDraft
            assertEquals(3, draft.specializedCount)
            assertTrue(draft.atCap)
            assertTrue(draft.isValid)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a skipped specialization when next() then step advances and Empty is persisted")
    fun skippedSpecializationAdvancesAndPersistsEmpty() = runTest {
        subject.uiState.test {
            awaitItem()
            subject.next()
            assertEquals("GymAndLifts", awaitItem().step.id)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(SpecializationPreference.Empty, OnboardingViewModelTestFixtures.fakeSpecializationRepository.current)
    }

    @Test
    @DisplayName("Given a specialization selection when next() then it is persisted and step advances")
    fun selectionAdvancesAndPersists() = runTest {
        subject.onMuscleToggled("Chest")
        subject.onMuscleToggled("Back")

        subject.uiState.test {
            awaitItem()
            subject.next()
            assertEquals("GymAndLifts", awaitItem().step.id)
            cancelAndIgnoreRemainingEvents()
        }

        val saved = OnboardingViewModelTestFixtures.fakeSpecializationRepository.current
        assertEquals(setOf(MuscleGroup.Chest, MuscleGroup.Back), saved.specializedMuscles)
    }

    @Test
    @DisplayName("Given a persisted specialization when VM created then it seeds the UI")
    fun persistedSpecializationSeedsUi() = runTest {
        OnboardingViewModelTestFixtures.fakeSpecializationRepository.seed(
            SpecializationPreference(
                levels = mapOf(MuscleGroup.Quad to SpecializationLevel.Specialize),
            ),
        )
        subject = OnboardingViewModelTestFixtures.buildViewModel()

        subject.uiState.test {
            assertTrue(awaitItem().specializationDraft.selectedMuscleLabels.contains("Quads"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a specialization edit when back() then it is persisted before leaving")
    fun backPersistsSpecialization() = runTest {
        subject.onMuscleToggled("Chest")

        subject.back()

        val saved = OnboardingViewModelTestFixtures.fakeSpecializationRepository.current
        assertEquals(setOf(MuscleGroup.Chest), saved.specializedMuscles)
        assertEquals(OnboardingStep.Schedule, OnboardingViewModelTestFixtures.fakeRepository.current.currentStep)
    }
}
