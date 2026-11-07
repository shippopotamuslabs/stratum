package com.stratum.application.checkin

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.application.program.ProgramRepository
import com.stratum.core.testing.checkin.FakeCheckInPreferencesRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeProgramChangeEventRepository
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.ProgramChangeEventType
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.checkin.CheckInApplyCommand
import com.stratum.domain.checkin.CheckInPreferences

class ApplyCheckInRecommendationsUseCaseTest {

    private val workoutLiftId = WorkoutLiftId("wl-1")
    private val programId = ProgramId("p-1")

    @Test
    @DisplayName("batch apply: swap + set count in one save; completes pending")
    fun batchApplySingleSave() = runTest {
        val programRepo = FakeProgramRepository(programWithSets(setCount = 3, liftId = "bench"))
        val events = FakeProgramChangeEventRepository()
        val prefs = FakeCheckInPreferencesRepository()
        prefs.seed(
            CheckInPreferences(
                enabled = true,
                pendingSinceExtractedAt = Instant.parse("2026-03-01T00:00:00Z"),
            ),
        )
        val useCase =
            ApplyCheckInRecommendationsUseCase(
                programRepository = programRepo,
                programChangeEventRepository = events,
                completeCheckIn = CompleteCheckInUseCase(prefs, FakeLoggingPort()),
                loggingPort = FakeLoggingPort(),
            )

        val result =
            useCase(
                listOf(
                    CheckInApplyCommand.Swap(workoutLiftId, LiftId("incline")),
                    CheckInApplyCommand.SetWorkingSets(workoutLiftId, targetSetCount = 4),
                ),
            )

        assertEquals(ApplyCheckInRecommendationsResult.Success, result)
        assertEquals(1, programRepo.saveProgramCalls.size)
        val saved = programRepo.getProgram()!!
        val lift = saved.workouts.first().workoutLifts.first()
        assertEquals(LiftId("incline"), lift.liftId)
        assertEquals(4, lift.sets.size)
        assertNull(prefs.current.pendingSinceExtractedAt)
        val eventTypes = events.getEventsForProgram(programId).events.map { it.eventType }
        assertTrue(ProgramChangeEventType.LIFT_SWAPPED in eventTypes)
        assertTrue(ProgramChangeEventType.WORKOUT_EDITED in eventTypes)
    }

    @Test
    @DisplayName("empty commands still complete check-in")
    fun emptyCommandsComplete() = runTest {
        val prefs = FakeCheckInPreferencesRepository()
        prefs.seed(
            CheckInPreferences(pendingSinceExtractedAt = Instant.parse("2026-03-01T00:00:00Z")),
        )
        val programRepo = FakeProgramRepository(programWithSets(3, "bench"))
        val useCase =
            ApplyCheckInRecommendationsUseCase(
                programRepository = programRepo,
                programChangeEventRepository = FakeProgramChangeEventRepository(),
                completeCheckIn = CompleteCheckInUseCase(prefs, FakeLoggingPort()),
                loggingPort = FakeLoggingPort(),
            )

        assertEquals(ApplyCheckInRecommendationsResult.Success, useCase(emptyList()))
        assertEquals(0, programRepo.saveProgramCalls.size)
        assertNull(prefs.current.pendingSinceExtractedAt)
    }

    @Test
    @DisplayName("absolute set target is idempotent on re-apply")
    fun absoluteSetTargetIdempotent() = runTest {
        val programRepo = FakeProgramRepository(programWithSets(4, "bench"))
        val useCase =
            ApplyCheckInRecommendationsUseCase(
                programRepository = programRepo,
                programChangeEventRepository = FakeProgramChangeEventRepository(),
                completeCheckIn = CompleteCheckInUseCase(FakeCheckInPreferencesRepository(), FakeLoggingPort()),
                loggingPort = FakeLoggingPort(),
            )
        useCase(listOf(CheckInApplyCommand.SetWorkingSets(workoutLiftId, 3)))
        useCase(listOf(CheckInApplyCommand.SetWorkingSets(workoutLiftId, 3)))
        assertEquals(3, programRepo.getProgram()!!.workouts.first().workoutLifts.first().sets.size)
    }

    @Test
    @DisplayName("returns NoActiveProgram when no active program exists")
    fun returnsNoActiveProgramWhenNoActiveProgram() = runTest {
        val useCase =
            ApplyCheckInRecommendationsUseCase(
                programRepository = FakeProgramRepository(null),
                programChangeEventRepository = FakeProgramChangeEventRepository(),
                completeCheckIn = CompleteCheckInUseCase(FakeCheckInPreferencesRepository(), FakeLoggingPort()),
                loggingPort = FakeLoggingPort(),
            )

        val result = useCase(listOf(CheckInApplyCommand.SetWorkingSets(workoutLiftId, 3)))
        assertEquals(ApplyCheckInRecommendationsResult.NoActiveProgram, result)
    }

    @Test
    @DisplayName("returns WorkoutLiftNotFound when workout lift not found")
    fun returnsWorkoutLiftNotFoundWhenWorkoutLiftNotFound() = runTest {
        val useCase =
            ApplyCheckInRecommendationsUseCase(
                programRepository = FakeProgramRepository(programWithSets(3, "bench")),
                programChangeEventRepository = FakeProgramChangeEventRepository(),
                completeCheckIn = CompleteCheckInUseCase(FakeCheckInPreferencesRepository(), FakeLoggingPort()),
                loggingPort = FakeLoggingPort(),
            )

        val missingId = WorkoutLiftId("missing")
        val swapResult = useCase(listOf(CheckInApplyCommand.Swap(missingId, LiftId("squat"))))
        assertEquals(ApplyCheckInRecommendationsResult.WorkoutLiftNotFound(missingId), swapResult)

        val setsResult = useCase(listOf(CheckInApplyCommand.SetWorkingSets(missingId, 4)))
        assertEquals(ApplyCheckInRecommendationsResult.WorkoutLiftNotFound(missingId), setsResult)
    }

    @Test
    @DisplayName("save failure returns PersistenceFailed and logs exactly one error (STR-243)")
    fun persistenceFailureOnSaveError() = runTest {
        val logging = FakeLoggingPort()
        val useCase =
            ApplyCheckInRecommendationsUseCase(
                programRepository = FakeProgramRepository(programWithSets(3, "bench"), failOnSave = true),
                programChangeEventRepository = FakeProgramChangeEventRepository(),
                completeCheckIn = CompleteCheckInUseCase(FakeCheckInPreferencesRepository(), FakeLoggingPort()),
                loggingPort = logging,
            )

        val result = useCase(listOf(CheckInApplyCommand.SetWorkingSets(workoutLiftId, 4)))

        assertEquals(ApplyCheckInRecommendationsResult.PersistenceFailed, result)
        assertEquals(1, logging.errors.size)
        assertEquals("ApplyCheckIn", logging.errors.single().tag)
        assertEquals(
            "Failed to apply check-in recommendations: FakeProgramRepository configured to fail on save",
            logging.errors.single().message,
        )
        assertTrue(logging.errors.single().throwable is IllegalStateException)
    }

    @Test
    @DisplayName("completion failure returns PersistenceFailed, logged once at the completion boundary (STR-243)")
    fun persistenceFailureOnCompleteError() = runTest {
        val logging = FakeLoggingPort()
        val failingPrefs =
            object : CheckInPreferencesRepository by FakeCheckInPreferencesRepository() {
                override suspend fun complete(now: Instant) {
                    throw IllegalStateException("prefs boom")
                }
            }
        val useCase =
            ApplyCheckInRecommendationsUseCase(
                programRepository = FakeProgramRepository(programWithSets(3, "bench")),
                programChangeEventRepository = FakeProgramChangeEventRepository(),
                completeCheckIn = CompleteCheckInUseCase(failingPrefs, logging),
                loggingPort = logging,
            )

        val result = useCase(emptyList())

        assertEquals(ApplyCheckInRecommendationsResult.PersistenceFailed, result)
        assertEquals(1, logging.errors.size)
        assertEquals("CompleteCheckIn", logging.errors.single().tag)
    }

    @Test
    @DisplayName("Cancellation propagates instead of being captured (STR-243)")
    fun cancellationPropagates() {
        val cancellingRepo =
            object : ProgramRepository by FakeProgramRepository(programWithSets(3, "bench")) {
                override suspend fun getProgram(): Program? =
                    throw kotlin.coroutines.cancellation.CancellationException("cancelled")
            }
        val useCase =
            ApplyCheckInRecommendationsUseCase(
                programRepository = cancellingRepo,
                programChangeEventRepository = FakeProgramChangeEventRepository(),
                completeCheckIn = CompleteCheckInUseCase(FakeCheckInPreferencesRepository(), FakeLoggingPort()),
                loggingPort = FakeLoggingPort(),
            )

        org.junit.jupiter.api.Assertions.assertThrows(
            kotlin.coroutines.cancellation.CancellationException::class.java,
        ) {
            kotlinx.coroutines.runBlocking {
                useCase(listOf(CheckInApplyCommand.SetWorkingSets(workoutLiftId, 3)))
            }
        }
    }

    private fun programWithSets(setCount: Int, liftId: String): Program {
        val sets =
            (0 until setCount).map { index ->
                LiftSet(
                    id = LiftSetId("ls-$index"),
                    workoutLiftId = workoutLiftId,
                    position = index,
                    setType = SetType.Standard,
                    repMin = 8,
                    repMax = 12,
                    rirGoal = 2,
                    isTopSet = index == 0,
                )
            }
        return Program(
            id = programId,
            name = "Split",
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            cycleLength = 1,
            trainingWeekdays = setOf(Weekday.Monday),
            workouts =
                listOf(
                    Workout(
                        id = WorkoutId("w-1"),
                        programId = programId,
                        name = "Push",
                        weekday = Weekday.Monday,
                        position = 0,
                        workoutLifts =
                            listOf(
                                WorkoutLift(
                                    id = workoutLiftId,
                                    workoutId = WorkoutId("w-1"),
                                    liftId = LiftId(liftId),
                                    progressionScheme = ProgressionScheme.Double,
                                    position = 0,
                                    sets = sets,
                                ),
                            ),
                    ),
                ),
        )
    }
}
