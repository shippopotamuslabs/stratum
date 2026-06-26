package com.stratum.feature.onboarding

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.domain.importing.HistoricalImportEvent
import com.stratum.domain.importing.ImportProgress
import com.stratum.domain.importing.ImportProgressPhase
import com.stratum.domain.lift.GymPreset
import com.stratum.domain.onboarding.OnboardingProgress
import com.stratum.domain.onboarding.OnboardingStep
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class OnboardingGymLiftsStepViewModelTest {

    private lateinit var subject: OnboardingViewModel

    @BeforeEach
    fun seedGymLiftsStep() {
        OnboardingViewModelTestFixtures.resetDefaults()
        OnboardingViewModelTestFixtures.fakeRepository.seed(
            OnboardingProgress(OnboardingStep.GymAndLifts, startedAt = 0L, completedAt = null),
        )
        subject = OnboardingViewModelTestFixtures.buildViewModel()
    }

    @Test
    @DisplayName("Given a preset when selected then it is reflected in the draft")
    fun presetSelectionReflectedInDraft() = runTest {
        OnboardingViewModelTestFixtures.fakeLiftRepository.seedLifts(
            listOf(
                OnboardingViewModelTestFixtures.aLift("bench-press", tags = setOf(GymPreset.CommercialGym)),
                OnboardingViewModelTestFixtures.aLift("pull-up", tags = setOf(GymPreset.GarageGym)),
            ),
        )
        subject = OnboardingViewModelTestFixtures.buildViewModel()

        subject.onPresetSelected(GymPreset.CommercialGym.displayName)
        advanceUntilIdle()

        subject.uiState.test {
            val draft = awaitItem().gymLiftsDraft
            assertEquals(GymPreset.CommercialGym.displayName, draft.selectedPresetLabel)
            assertTrue(draft.isValid)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a preset selected when a different preset is selected then the draft switches")
    fun switchingPresetsUpdatesDraft() = runTest {
        OnboardingViewModelTestFixtures.fakeLiftRepository.seedLifts(
            listOf(
                OnboardingViewModelTestFixtures.aLift("bench-press", tags = setOf(GymPreset.CommercialGym)),
                OnboardingViewModelTestFixtures.aLift("pull-up", tags = setOf(GymPreset.GarageGym)),
            ),
        )
        subject = OnboardingViewModelTestFixtures.buildViewModel()

        subject.onPresetSelected(GymPreset.CommercialGym.displayName)
        subject.onPresetSelected(GymPreset.GarageGym.displayName)
        advanceUntilIdle()

        subject.uiState.test {
            val draft = awaitItem().gymLiftsDraft
            assertEquals(GymPreset.GarageGym.displayName, draft.selectedPresetLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a preset selected when deselected then label clears and draft requires preset or import")
    fun deselectingPresetClearsLabel() = runTest {
        OnboardingViewModelTestFixtures.fakeLiftRepository.seedLifts(
            listOf(OnboardingViewModelTestFixtures.aLift("squat", tags = setOf(GymPreset.CommercialGym))),
        )
        subject = OnboardingViewModelTestFixtures.buildViewModel()
        subject.onPresetSelected(GymPreset.CommercialGym.displayName)
        advanceUntilIdle()
        subject.onPresetSelected(null)
        advanceUntilIdle()

        subject.uiState.test {
            val draft = awaitItem().gymLiftsDraft
            assertNull(draft.selectedPresetLabel)
            assertFalse(draft.isValid)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a preset when selected then its tagged lifts are enabled in the repository")
    fun presetSelectionEnablesTaggedLifts() = runTest {
        OnboardingViewModelTestFixtures.fakeLiftRepository.seedLifts(
            listOf(
                OnboardingViewModelTestFixtures.aLift("bench-press", tags = setOf(GymPreset.CommercialGym)),
                OnboardingViewModelTestFixtures.aLift("pull-up", tags = setOf(GymPreset.GarageGym)),
            ),
        )
        subject = OnboardingViewModelTestFixtures.buildViewModel()

        subject.onPresetSelected(GymPreset.CommercialGym.displayName)
        advanceUntilIdle()

        val lifts = OnboardingViewModelTestFixtures.fakeLiftRepository.currentLifts
        assertTrue(lifts.first { it.id.value == "bench-press" }.enabled)
        assertFalse(lifts.first { it.id.value == "pull-up" }.enabled)
    }

    @Test
    @DisplayName("Given errors shown when a preset is selected then the error clears")
    fun presetSelectionClearsError() = runTest {
        OnboardingViewModelTestFixtures.fakeLiftRepository.seedLifts(
            listOf(OnboardingViewModelTestFixtures.aLift("squat", tags = setOf(GymPreset.CommercialGym))),
        )
        subject = OnboardingViewModelTestFixtures.buildViewModel()
        subject.next()
        advanceUntilIdle()

        subject.uiState.test {
            assertTrue(awaitItem().gymLiftsDraft.showNoPresetError)
            subject.onPresetSelected(GymPreset.CommercialGym.displayName)
            advanceUntilIdle()
            val draft = awaitItem().gymLiftsDraft
            assertFalse(draft.showNoPresetError)
            assertTrue(draft.isValid)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given no preset selected when next() then it is blocked and errors show")
    fun nextBlockedWhenNoPresetSelected() = runTest {
        OnboardingViewModelTestFixtures.fakeLiftRepository.seedLifts(
            listOf(OnboardingViewModelTestFixtures.aLift("squat", enabled = false)),
        )
        subject = OnboardingViewModelTestFixtures.buildViewModel()

        subject.next()
        advanceUntilIdle()

        subject.uiState.test {
            val state = awaitItem()
            assertEquals("GymAndLifts", state.step.id)
            assertTrue(state.gymLiftsDraft.showNoPresetError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a preset with enabled lifts when next() then it advances to Review")
    fun nextAdvancesAfterPresetSelection() = runTest {
        OnboardingViewModelTestFixtures.fakeLiftRepository.seedLifts(
            listOf(OnboardingViewModelTestFixtures.aLift("squat", tags = setOf(GymPreset.CommercialGym))),
        )
        subject = OnboardingViewModelTestFixtures.buildViewModel()
        subject.onPresetSelected(GymPreset.CommercialGym.displayName)
        advanceUntilIdle()

        subject.uiState.test {
            assertEquals("GymAndLifts", awaitItem().step.id)
            subject.next()
            advanceUntilIdle()
            assertEquals("Review", awaitItem().step.id)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(OnboardingStep.Review, OnboardingViewModelTestFixtures.fakeRepository.current.currentStep)
    }

    @Test
    @DisplayName("Given orphan history from failed import when gym step loads then import is not complete")
    fun orphanHistoryFromFailedImportIsReconciled() = runTest {
        OnboardingViewModelTestFixtures.fakeLiftRepository.seedUsedHistoricalImport(true)
        OnboardingViewModelTestFixtures.seedOrphanImportHistory()
        subject = OnboardingViewModelTestFixtures.buildViewModel()
        advanceUntilIdle()

        subject.uiState.test {
            val draft = awaitItem().gymLiftsDraft
            assertFalse(draft.importComplete)
            assertFalse(draft.usedHistoricalImport)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun staleImportFlagReconciledOnLoad() = runTest {
        OnboardingViewModelTestFixtures.fakeLiftRepository.seedUsedHistoricalImport(true)
        subject = OnboardingViewModelTestFixtures.buildViewModel()
        advanceUntilIdle()

        subject.uiState.test {
            val draft = awaitItem().gymLiftsDraft
            assertFalse(draft.importComplete)
            assertFalse(draft.usedHistoricalImport)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given import failed when progress is Failed then import is not marked complete")
    fun failedImportDoesNotShowComplete() = runTest {
        OnboardingViewModelTestFixtures.fakeLiftRepository.seedUsedHistoricalImport(true)
        OnboardingViewModelTestFixtures.fakeHistoricalImport.setEvents(
            listOf(HistoricalImportEvent.Failed("reps must be positive")),
        )
        subject = OnboardingViewModelTestFixtures.buildViewModel()

        subject.onImportFileSelected("bad".toByteArray(), "export.csv")
        advanceUntilIdle()

        subject.uiState.test {
            val draft = awaitItem().gymLiftsDraft
            assertTrue(draft.importFailed)
            assertFalse(draft.importComplete)
            assertEquals("reps must be positive", draft.importError)
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(OnboardingViewModelTestFixtures.fakeLiftRepository.currentUsedHistoricalImport)
    }
}
