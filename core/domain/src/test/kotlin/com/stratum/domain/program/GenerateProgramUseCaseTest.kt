package com.stratum.domain.program

import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.onboarding.FakeOnboardingRepository
import com.stratum.core.testing.onboarding.FakeUserTrainingProfileRepository
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.program.SCENARIO_LIFT_CATALOG
import com.stratum.core.testing.program.scenarioContext
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.CompleteOnboardingUseCase
import com.stratum.domain.onboarding.TrainingAge
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.program.generation.pipeline.ProgramGenerationPipeline
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * [GenerateProgramUseCase] orchestration tests. Scenarios PROG-GEN-100, PROG-GEN-204, PROG-GEN-205.
 */
class GenerateProgramUseCaseTest {
    @Nested
    @DisplayName("PROG-GEN-100 happy path persists program and completes onboarding")
    inner class HappyPath {
        @Test
        fun `invoke persists a generated program and completes onboarding`() = runTest {
            val useCase = buildUseCase(this)

            val result = useCase.invoke()

            assertTrue(result.isSuccess)
            assertTrue(programRepository.current != null)
            assertTrue(programRepository.saveProgramCalls.isNotEmpty())
            assertTrue(onboardingRepository.current.isComplete)
            assertEquals(SEEDED_PROFILE, profileRepository.current)
        }
    }

    @Nested
    @DisplayName("PROG-GEN-204 generation failure leaves no program and no completion")
    inner class FailurePath {
        @Test
        fun `invoke does not persist or complete on generation failure`() = runTest {
            val useCase = buildUseCase(this, contextLoader = ProgramGenerationContextLoader { error("boom") })

            val result = useCase.invoke()

            assertTrue(result.isFailure)
            assertTrue(programRepository.saveProgramCalls.isEmpty())
            assertEquals(null, programRepository.current)
            assertFalse(onboardingRepository.current.isComplete)
        }
    }

    @Nested
    @DisplayName("PROG-GEN-205 persist failure leaves onboarding incomplete")
    inner class PersistFailurePath {
        @Test
        fun `invoke does not complete onboarding when saveProgram fails`() = runTest {
            val useCase = buildUseCase(this, repositoryFailOnSave = true)

            val result = useCase.invoke()

            assertTrue(result.isFailure)
            assertEquals(null, programRepository.current)
            assertFalse(onboardingRepository.current.isComplete)
        }
    }

    @Nested
    @DisplayName("PROG-GEN-206 progress callbacks report orchestration phases")
    inner class ProgressPath {
        @Test
        fun `invoke reports pipeline and orchestration phases`() = runTest {
            val phases = mutableListOf<ProgramGenerationProgressPhase>()
            val useCase = buildUseCase(this)

            useCase.invoke { progress -> phases.add(progress.phase) }

            val collapsed =
                phases.fold(mutableListOf<ProgramGenerationProgressPhase>()) { acc, phase ->
                    if (acc.lastOrNull() != phase) acc.add(phase)
                    acc
                }
            assertEquals(
                listOf(
                    ProgramGenerationProgressPhase.PreparingPayload,
                    ProgramGenerationProgressPhase.ReviewingProfile,
                    ProgramGenerationProgressPhase.PlanningVolume,
                    ProgramGenerationProgressPhase.SynthesizingProgram,
                    ProgramGenerationProgressPhase.SelectingExercises,
                    ProgramGenerationProgressPhase.PolishingProgram,
                    ProgramGenerationProgressPhase.FinalizingProgram,
                    ProgramGenerationProgressPhase.SavingProgram,
                    ProgramGenerationProgressPhase.Complete,
                ),
                collapsed,
            )
        }
    }

    private lateinit var programRepository: FakeProgramRepository
    private lateinit var onboardingRepository: FakeOnboardingRepository
    private lateinit var profileRepository: FakeUserTrainingProfileRepository

    private fun buildUseCase(
        testScope: TestScope,
        contextLoader: ProgramGenerationContextLoader = ProgramGenerationContextLoader {
            scenarioContext(days = 3, specialization = emptyMap())
        },
        repositoryFailOnSave: Boolean = false,
    ): GenerateProgramUseCase {
        programRepository = FakeProgramRepository(failOnSave = repositoryFailOnSave)
        onboardingRepository = FakeOnboardingRepository()
        profileRepository = FakeUserTrainingProfileRepository(initial = SEEDED_PROFILE)
        val liftIds = SCENARIO_LIFT_CATALOG.map { LiftId(it.id) }.toSet()
        return GenerateProgramUseCase(
            loadContext = contextLoader,
            pipeline = testProgramGenerationPipeline(),
            repository = programRepository,
            liftRepository = FakeLiftRepository(
                initialLifts =
                    liftIds.map { id ->
                        com.stratum.domain.lift.Lift(
                            id = id,
                            name = id.value,
                            movementPatterns = emptySet(),
                            primaryMuscles = emptySet(),
                            secondaryMuscles = emptySet(),
                            allowWeightChangeBetweenSets = false,
                            incrementTiers = emptyList(),
                            presetTags = emptySet(),
                            enabled = true,
                        )
                    },
            ),
            completeOnboarding = CompleteOnboardingUseCase(onboardingRepository, profileRepository),
            profileRepository = profileRepository,
            generationDispatcher = UnconfinedTestDispatcher(testScope.testScheduler),
        )
    }

    private val SEEDED_PROFILE = UserTrainingProfile.Empty.copy(trainingAge = TrainingAge.Advanced)
}
