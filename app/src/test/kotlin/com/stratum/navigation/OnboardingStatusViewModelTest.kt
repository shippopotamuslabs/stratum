package com.stratum.navigation

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeOnboardingRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.application.onboarding.ObserveOnboardingCompleteUseCase
import com.stratum.domain.onboarding.OnboardingProgress
import com.stratum.domain.onboarding.OnboardingStep
import com.stratum.application.onboarding.ReopenOnboardingWizardUseCase
import com.stratum.application.program.DeleteProgramUseCase
import com.stratum.application.program.DiscardActiveProgramUseCase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class OnboardingStatusViewModelTest {

    private val fakeRepository = FakeOnboardingRepository()
    private val programRepository = FakeProgramRepository()
    private val trainingLog = FakeTrainingLogRepository()
    private lateinit var subject: OnboardingStatusViewModel

    @BeforeEach
    fun setup() {
        fakeRepository.seed(
            OnboardingProgress(
                currentStep = OnboardingStep.Profile,
                startedAt = FakeOnboardingRepository.STARTED_AT,
                completedAt = null,
            ),
        )
        subject = buildViewModel()
    }

    private fun buildViewModel(): OnboardingStatusViewModel {
        val logging = FakeLoggingPort()
        return OnboardingStatusViewModel(
            observeComplete = ObserveOnboardingCompleteUseCase(fakeRepository),
            reopenOnboardingWizard = ReopenOnboardingWizardUseCase(fakeRepository, logging),
            discardActiveProgram =
                DiscardActiveProgramUseCase(
                    programRepository = programRepository,
                    deleteProgram = DeleteProgramUseCase(programRepository, trainingLog, logging),
                    loggingPort = logging,
                ),
        )
    }

    @Test
    @DisplayName("Given incomplete onboarding when observed then emits false and never true")
    fun incompleteOnboardingEmitsFalse() = runTest {
        subject.isComplete.test {
            while (true) {
                when (awaitItem()) {
                    null -> Unit
                    false -> {
                        cancelAndIgnoreRemainingEvents()
                        return@test
                    }
                    true -> error("Incomplete onboarding must not emit true")
                }
            }
        }
    }

    @Test
    @DisplayName("Given completed onboarding when observed then emits true without false flash")
    fun completedOnboardingEmitsTrueWithoutFalseFlash() = runTest {
        fakeRepository.seed(
            OnboardingProgress(
                currentStep = OnboardingStep.Profile,
                startedAt = FakeOnboardingRepository.STARTED_AT,
                completedAt = FakeOnboardingRepository.COMPLETED_AT,
            ),
        )
        subject = buildViewModel()

        subject.isComplete.test {
            var sawFalse = false
            while (true) {
                when (val value = awaitItem()) {
                    null -> Unit
                    false -> sawFalse = true
                    true -> {
                        assertFalse(sawFalse, "Returning users must not briefly see onboarding (false)")
                        cancelAndIgnoreRemainingEvents()
                        return@test
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Given onboarding completes later when observed then transitions to true")
    fun onboardingCompletionUpdatesStream() = runTest {
        subject.isComplete.test {
            assertEquals(false, awaitItem())

            fakeRepository.markComplete()

            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Discard program deletes active template and reopens onboarding to ChoosePath")
    fun discardProgramDeletesActiveAndReopens() = runTest {
        val program = WorkoutLoggingFixtures.twoWorkoutProgram()
        programRepository.saveProgram(program.activate())
        fakeRepository.seed(
            OnboardingProgress(
                currentStep = OnboardingStep.Review,
                startedAt = FakeOnboardingRepository.STARTED_AT,
                completedAt = FakeOnboardingRepository.COMPLETED_AT,
            ),
        )
        subject = buildViewModel()

        subject.isComplete.test {
            assertEquals(true, awaitItem())

            subject.discardProgramAndReopenOnboarding()

            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertNull(programRepository.getProgram())
        assertNull(programRepository.getProgramById(program.id))
        assertEquals(OnboardingStep.ChoosePath, fakeRepository.current.currentStep)
        assertNull(fakeRepository.current.completedAt)
    }

    @Test
    @DisplayName("Discard with no active program still reopens onboarding")
    fun discardWithNoProgramStillReopens() = runTest {
        fakeRepository.seed(
            OnboardingProgress(
                currentStep = OnboardingStep.Review,
                startedAt = FakeOnboardingRepository.STARTED_AT,
                completedAt = FakeOnboardingRepository.COMPLETED_AT,
            ),
        )
        subject = buildViewModel()

        subject.isComplete.test {
            assertEquals(true, awaitItem())
            subject.discardProgramAndReopenOnboarding()
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(OnboardingStep.ChoosePath, fakeRepository.current.currentStep)
    }
}
