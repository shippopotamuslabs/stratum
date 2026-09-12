package com.stratum.domain.workout

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeProgramChangeEventRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.ProgramChangeEventType
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ApplyRepRangeShiftUseCase")
class ApplyRepRangeShiftUseCaseTest {

    private val wlId = WorkoutLiftId("wl-bench")

    @Test
    @DisplayName("Double: only top set stores the new band; backoffs stay 0/0")
    fun doubleTopOnly() = runTest {
        val program =
            programWithLift(
                WorkoutLift(
                    id = wlId,
                    workoutId = WorkoutLoggingFixtures.workoutAId,
                    liftId = WorkoutLoggingFixtures.liftBenchId,
                    progressionScheme = ProgressionScheme.Double,
                    position = 0,
                    sets =
                        listOf(
                            set(0, repMin = 8, repMax = 10, isTopSet = true),
                            set(1, repMin = 0, repMax = 0, isTopSet = false),
                            set(2, repMin = 0, repMax = 0, isTopSet = false),
                            set(3, repMin = 0, repMax = 0, isTopSet = false),
                        ),
                ),
            )
        val programRepo = FakeProgramRepository(program)
        val events = FakeProgramChangeEventRepository()
        val useCase = ApplyRepRangeShiftUseCase(programRepo, events, FakeLoggingPort())

        val result =
            useCase(
                workoutLiftId = wlId,
                expectedCurrentRepMin = 8,
                expectedCurrentRepMax = 10,
                proposedRepMin = 14,
                proposedRepMax = 16,
                fatigueSlope = -3.0,
                setCount = 4,
            )
        assertTrue(result.isSuccess)

        val updated = programRepo.current!!.workouts.single().workoutLifts.single()
        assertEquals(14, updated.sets[0].repMin)
        assertEquals(16, updated.sets[0].repMax)
        assertTrue(updated.sets[0].isTopSet)
        assertEquals(0, updated.sets[1].repMin)
        assertEquals(false, updated.sets[1].isTopSet)
        assertEquals(LiftSetId("ls-0"), updated.sets[0].id)
        val appended = events.getEventsForProgram(WorkoutLoggingFixtures.programId)
        assertEquals(1, appended.size)
        assertEquals(ProgramChangeEventType.WORKOUT_EDITED, appended.single().eventType)
    }

    @Test
    @DisplayName("Linear: shared band applied to every set")
    fun linearSharedBand() = runTest {
        val program =
            programWithLift(
                WorkoutLift(
                    id = wlId,
                    workoutId = WorkoutLoggingFixtures.workoutAId,
                    liftId = WorkoutLoggingFixtures.liftBenchId,
                    progressionScheme = ProgressionScheme.Linear,
                    position = 0,
                    sets =
                        listOf(
                            set(0, repMin = 8, repMax = 10, isTopSet = true),
                            set(1, repMin = 8, repMax = 10, isTopSet = false),
                            set(2, repMin = 8, repMax = 10, isTopSet = false),
                            set(3, repMin = 8, repMax = 10, isTopSet = false),
                        ),
                ),
            )
        val programRepo = FakeProgramRepository(program)
        val useCase =
            ApplyRepRangeShiftUseCase(
                programRepo,
                FakeProgramChangeEventRepository(),
                FakeLoggingPort(),
            )

        assertTrue(
            useCase(
                workoutLiftId = wlId,
                expectedCurrentRepMin = 8,
                expectedCurrentRepMax = 10,
                proposedRepMin = 14,
                proposedRepMax = 16,
                fatigueSlope = -3.0,
                setCount = 4,
            ).isSuccess,
        )
        val sets = programRepo.current!!.workouts.single().workoutLifts.single().sets
        assertTrue(sets.all { it.repMin == 14 && it.repMax == 16 })
    }

    @Test
    @DisplayName("stale expected band → failure without write")
    fun staleBandFails() = runTest {
        val program =
            programWithLift(
                WorkoutLift(
                    id = wlId,
                    workoutId = WorkoutLoggingFixtures.workoutAId,
                    liftId = WorkoutLoggingFixtures.liftBenchId,
                    progressionScheme = ProgressionScheme.Double,
                    position = 0,
                    sets =
                        listOf(
                            set(0, repMin = 10, repMax = 12, isTopSet = true),
                            set(1, repMin = 0, repMax = 0, isTopSet = false),
                            set(2, repMin = 0, repMax = 0, isTopSet = false),
                            set(3, repMin = 0, repMax = 0, isTopSet = false),
                        ),
                ),
            )
        val programRepo = FakeProgramRepository(program)
        val useCase =
            ApplyRepRangeShiftUseCase(
                programRepo,
                FakeProgramChangeEventRepository(),
                FakeLoggingPort(),
            )

        val result =
            useCase(
                workoutLiftId = wlId,
                expectedCurrentRepMin = 8,
                expectedCurrentRepMax = 10,
                proposedRepMin = 14,
                proposedRepMax = 16,
                fatigueSlope = -3.0,
                setCount = 4,
            )
        assertTrue(result.isFailure)
        assertTrue(result is ApplyRepRangeShiftResult.ProposalMismatch)
        assertEquals(0, programRepo.saveProgramCalls.size)
    }

    @Test
    @DisplayName("missing workout lift → failure")
    fun missingLiftFails() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val useCase =
            ApplyRepRangeShiftUseCase(
                programRepo,
                FakeProgramChangeEventRepository(),
                FakeLoggingPort(),
            )
        val result =
            useCase(
                workoutLiftId = WorkoutLiftId("missing"),
                expectedCurrentRepMin = 8,
                expectedCurrentRepMax = 10,
                proposedRepMin = 10,
                proposedRepMax = 12,
                fatigueSlope = -2.0,
                setCount = 3,
            )
        assertTrue(result.isFailure)
        assertEquals(ApplyRepRangeShiftResult.LiftNotFound, result)
    }

    private fun set(
        position: Int,
        repMin: Int,
        repMax: Int,
        isTopSet: Boolean,
    ): LiftSet =
        LiftSet(
            id = LiftSetId("ls-$position"),
            workoutLiftId = wlId,
            position = position,
            setType = SetType.Standard,
            repMin = repMin,
            repMax = repMax,
            rirGoal = if (isTopSet) 2 else 1,
            isTopSet = isTopSet,
        )

    private fun programWithLift(lift: WorkoutLift) =
        WorkoutLoggingFixtures.twoWorkoutProgram(
            workouts =
                listOf(
                    Workout(
                        id = WorkoutLoggingFixtures.workoutAId,
                        programId = WorkoutLoggingFixtures.programId,
                        name = "Workout A",
                        weekday = com.stratum.domain.onboarding.Weekday.Monday,
                        position = 0,
                        workoutLifts = listOf(lift),
                    ),
                ),
        ).copy(createdAt = Instant.parse("2025-01-01T00:00:00Z"))
}
