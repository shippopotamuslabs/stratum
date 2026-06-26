package com.stratum.feature.onboarding

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.domain.importing.HistoricalImportResumeState
import com.stratum.domain.importing.HistoricalImportEvent
import com.stratum.domain.importing.HistoricalImportLimits
import com.stratum.domain.importing.ImportFileInput
import com.stratum.domain.importing.ImportedHistoryChunk
import com.stratum.domain.importing.ImportProgress
import com.stratum.domain.importing.ImportProgressPhase
import com.stratum.domain.lift.GymPreset
import com.stratum.domain.onboarding.OnboardingProgress
import com.stratum.domain.onboarding.OnboardingStep
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
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

        subject.onImportFilesSelected(listOf(ImportFileInput("bad".toByteArray(), "export.csv")))
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

    @Test
    @DisplayName("Given multiple files when import starts then runner receives all files")
    fun multiFileImportInvokesRunnerWithAllFiles() = runTest {
        subject = OnboardingViewModelTestFixtures.buildViewModel()
        val files =
            listOf(
                ImportFileInput("a".toByteArray(), "a.csv"),
                ImportFileInput("b".toByteArray(), "b.csv"),
            )

        subject.onImportFilesSelected(files)
        advanceUntilIdle()

        assertEquals(1, OnboardingViewModelTestFixtures.fakeHistoricalImport.invocations.size)
        assertEquals(files, OnboardingViewModelTestFixtures.fakeHistoricalImport.invocations.single().files)
        assertNull(OnboardingViewModelTestFixtures.fakeHistoricalImport.invocations.single().resume)
    }

    @Test
    @DisplayName("Given resumable multi-file failure when retry tapped then runner resumes with cached files")
    fun retryFromFailedFileUsesCachedFilesWithoutNewSelection() = runTest {
        val resume =
            HistoricalImportResumeState(
                files =
                    listOf(
                        ImportFileInput("a".toByteArray(), "a.csv"),
                        ImportFileInput("bad".toByteArray(), "b.csv"),
                    ),
                completedChunks =
                    listOf(
                        ImportedHistoryChunk(
                            sourceLabel = "a.csv",
                            sessions = emptyList(),
                            sets = emptyList(),
                        ),
                    ),
            )
        OnboardingViewModelTestFixtures.fakeHistoricalImport.setEvents(
            listOf(HistoricalImportEvent.Failed("first failure", resume = resume)),
        )
        subject = OnboardingViewModelTestFixtures.buildViewModel()
        subject.onImportFilesSelected(
            listOf(
                ImportFileInput("a".toByteArray(), "a.csv"),
                ImportFileInput("bad".toByteArray(), "b.csv"),
            ),
        )
        advanceUntilIdle()

        OnboardingViewModelTestFixtures.fakeHistoricalImport.resetInvocations()
        OnboardingViewModelTestFixtures.fakeHistoricalImport.setEvents(
            listOf(
                HistoricalImportEvent.Progress(ImportProgress(ImportProgressPhase.Complete)),
                HistoricalImportEvent.Completed(
                    com.stratum.domain.importing.ExtractTrainingInsightsSummary(
                        liftsWithInsights = 1,
                        supersetPairs = 0,
                        musclesWithBaselines = 1,
                    ),
                ),
            ),
        )

        subject.onRetryImportFromFailedFile()
        advanceUntilIdle()

        assertEquals(1, OnboardingViewModelTestFixtures.fakeHistoricalImport.invocations.size)
        val invocation = OnboardingViewModelTestFixtures.fakeHistoricalImport.invocations.single()
        assertNotNull(invocation.resume)
        assertEquals(1, invocation.resume?.completedChunks?.size)
        assertEquals("bad".toByteArray().toList(), invocation.files[1].bytes.toList())
    }

    @Test
    @DisplayName("Given failed import when cancel tapped then import UI resets")
    fun cancelImportClearsFailedState() = runTest {
        OnboardingViewModelTestFixtures.fakeHistoricalImport.setEvents(
            listOf(HistoricalImportEvent.Failed("Conversion failed.")),
        )
        subject = OnboardingViewModelTestFixtures.buildViewModel()
        subject.onImportFilesSelected(listOf(ImportFileInput("bad".toByteArray(), "export.csv")))
        advanceUntilIdle()

        subject.onCancelImport()
        advanceUntilIdle()

        subject.uiState.test {
            val draft = awaitItem().gymLiftsDraft
            assertNull(draft.importProgress)
            assertNull(draft.importError)
            assertFalse(draft.importFailed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given more than max files when import starts then it fails without invoking runner")
    fun tooManyFilesRejected() = runTest {
        subject = OnboardingViewModelTestFixtures.buildViewModel()
        val files =
            List(HistoricalImportLimits.MAX_FILES + 1) { index ->
                ImportFileInput("x".toByteArray(), "export-$index.csv")
            }

        subject.onImportFilesSelected(files)
        advanceUntilIdle()

        assertTrue(OnboardingViewModelTestFixtures.fakeHistoricalImport.invocations.isEmpty())
        subject.uiState.test {
            val draft = awaitItem().gymLiftsDraft
            assertTrue(draft.importFailed)
            assertEquals(
                "Select at most ${HistoricalImportLimits.MAX_FILES} files.",
                draft.importError,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
}
