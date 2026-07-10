package com.stratum.domain.checkin

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
                completeCheckIn = CompleteCheckInUseCase(prefs),
            )

        val result =
            useCase(
                listOf(
                    CheckInApplyCommand.Swap(workoutLiftId, LiftId("incline")),
                    CheckInApplyCommand.SetWorkingSets(workoutLiftId, targetSetCount = 4),
                ),
            )

        assertTrue(result.isSuccess)
        assertEquals(1, programRepo.saveProgramCalls.size)
        val saved = programRepo.getProgram()!!
        val lift = saved.workouts.first().workoutLifts.first()
        assertEquals(LiftId("incline"), lift.liftId)
        assertEquals(4, lift.sets.size)
        assertNull(prefs.current.pendingSinceExtractedAt)
        val eventTypes = events.getEventsForProgram(programId).map { it.eventType }
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
                completeCheckIn = CompleteCheckInUseCase(prefs),
            )

        assertTrue(useCase(emptyList()).isSuccess)
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
                completeCheckIn = CompleteCheckInUseCase(FakeCheckInPreferencesRepository()),
            )
        useCase(listOf(CheckInApplyCommand.SetWorkingSets(workoutLiftId, 3))).getOrThrow()
        useCase(listOf(CheckInApplyCommand.SetWorkingSets(workoutLiftId, 3))).getOrThrow()
        assertEquals(3, programRepo.getProgram()!!.workouts.first().workoutLifts.first().sets.size)
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
