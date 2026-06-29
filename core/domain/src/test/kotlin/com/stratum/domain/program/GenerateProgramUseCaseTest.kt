package com.stratum.domain.program

import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.onboarding.FakeOnboardingRepository
import com.stratum.core.testing.onboarding.FakeUserTrainingProfileRepository
import com.stratum.core.testing.program.FakeProgramGeneratorPort
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.CompleteOnboardingUseCase
import com.stratum.domain.onboarding.TrainingAge
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * [GenerateProgramUseCase] orchestration tests. Scenarios PROG-GEN-100, PROG-GEN-204, PROG-GEN-205.
 *
 * The loader is substituted with a fake ([ProgramGenerationPayloadLoader] seam) so orchestration is
 * tested without wiring the loader's many repository dependencies.
 */
class GenerateProgramUseCaseTest {
    @Nested
    @DisplayName("PROG-GEN-100 happy path persists program and completes onboarding")
    inner class HappyPath {
        @Test
        fun `invoke persists the generated program, completes onboarding, and forwards the payload`() =
            runTest {
                val useCase = buildUseCase(generatorProgram = PROGRAM_P1)

                val result = useCase.invoke()

                assertTrue(result.isSuccess)
                assertEquals(PROGRAM_P1, programRepository.current)
                assertEquals(listOf(PROGRAM_P1), programRepository.saveProgramCalls)
                assertEquals(PAYLOAD_JSON, generator.invokedPayload)
                assertTrue(onboardingRepository.current.isComplete)
                assertEquals(SEEDED_PROFILE, profileRepository.current)
            }
    }

    @Nested
    @DisplayName("PROG-GEN-204 generation failure leaves no program and no completion")
    inner class FailurePath {
        @Test
        fun `invoke does not persist or complete on generation failure`() = runTest {
            val useCase = buildUseCase(generatorFail = true)

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
            val useCase = buildUseCase(repositoryFailOnSave = true)

            val result = useCase.invoke()

            assertTrue(result.isFailure)
            assertEquals(null, programRepository.current)
            assertFalse(onboardingRepository.current.isComplete)
            assertNotNull(generator.invokedPayload)
        }
    }

    @Nested
    @DisplayName("PROG-GEN-206 progress callbacks report orchestration phases")
    inner class ProgressPath {
        @Test
        fun `invoke reports preparing payload saving and complete`() = runTest {
            val phases = mutableListOf<ProgramGenerationProgressPhase>()
            val useCase = buildUseCase()

            useCase.invoke { progress -> phases.add(progress.phase) }

            assertEquals(
                listOf(
                    ProgramGenerationProgressPhase.PreparingPayload,
                    ProgramGenerationProgressPhase.SavingProgram,
                    ProgramGenerationProgressPhase.Complete,
                ),
                phases,
            )
        }
    }

    // region Fixture wiring
    private lateinit var generator: FakeProgramGeneratorPort
    private lateinit var programRepository: FakeProgramRepository
    private lateinit var onboardingRepository: FakeOnboardingRepository
    private lateinit var profileRepository: FakeUserTrainingProfileRepository

    private fun buildUseCase(
        generatorProgram: Program = PROGRAM_P1,
        generatorFail: Boolean = false,
        repositoryFailOnSave: Boolean = false,
    ): GenerateProgramUseCase {
        generator = FakeProgramGeneratorPort(program = generatorProgram, fail = generatorFail)
        programRepository = FakeProgramRepository(failOnSave = repositoryFailOnSave)
        onboardingRepository = FakeOnboardingRepository()
        profileRepository = FakeUserTrainingProfileRepository(initial = SEEDED_PROFILE)
        val loadPayload = ProgramGenerationPayloadLoader { PAYLOAD_JSON }

        return GenerateProgramUseCase(
            loadPayload = loadPayload,
            generator = generator,
            repository = programRepository,
            liftRepository = FakeLiftRepository(
                initialLifts =
                    listOf(
                        com.stratum.domain.lift.Lift(
                            id = LiftId("back-squat"),
                            name = "Back Squat",
                            movementPattern = com.stratum.domain.lift.MovementPattern.SquatPattern,
                            primaryMuscles = emptySet(),
                            secondaryMuscles = emptySet(),
                            allowWeightChangeBetweenSets = false,
                            incrementTiers = emptyList(),
                            presetTags = emptySet(),
                            enabled = true,
                        ),
                    ),
            ),
            completeOnboarding = CompleteOnboardingUseCase(onboardingRepository, profileRepository),
            profileRepository = profileRepository,
        )
    }

    private val SEEDED_PROFILE = UserTrainingProfile.Empty.copy(trainingAge = TrainingAge.Beginner)
    // endregion

    companion object {
        private const val PAYLOAD_JSON = """{"profile":{"trainingAge":"Beginner"}}"""

        private val PROGRAM_P1: Program = Program(
            id = ProgramId("p-1"),
            name = "Full Body",
            createdAt = Instant.parse("2026-06-26T12:00:00Z"),
            workouts = listOf(
                Workout(
                    id = WorkoutId("w-1"),
                    programId = ProgramId("p-1"),
                    name = "Session A",
                    weekday = null,
                    position = 0,
                    workoutLifts = listOf(
                        WorkoutLift(
                            id = WorkoutLiftId("wl-1"),
                            workoutId = WorkoutId("w-1"),
                            liftId = LiftId("back-squat"),
                            progressionScheme = ProgressionScheme.Linear,
                            position = 0,
                            sets = listOf(
                                LiftSet(
                                    id = LiftSetId("ls-1"),
                                    workoutLiftId = WorkoutLiftId("wl-1"),
                                    position = 0,
                                    setType = SetType.Standard,
                                    repMin = 5,
                                    repMax = 5,
                                    rirGoal = 2,
                                    isTopSet = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }
}
