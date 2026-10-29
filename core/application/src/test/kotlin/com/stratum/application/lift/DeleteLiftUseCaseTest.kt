package com.stratum.application.lift

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeDeleteLiftPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.response.FakeResponseModelingSchedulerPort
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.localDerivedTrainingStages
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.application.training.RebuildLocalDerivedTrainingDataUseCase
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.Weight

class DeleteLiftUseCaseTest {

    private val seedId = LiftId("lift-seed")
    private val customId = LiftId("lift-custom")
    private val otherId = LiftId("lift-other")
    private val sessionId = TrainingSessionId("session-1")

    private lateinit var liftRepo: FakeLiftRepository
    private lateinit var logRepo: FakeTrainingLogRepository
    private lateinit var programRepo: FakeProgramRepository
    private lateinit var deletePort: FakeDeleteLiftPort
    private lateinit var subject: DeleteLiftUseCase

    @BeforeEach
    fun setup() {
        liftRepo =
            FakeLiftRepository(
                listOf(
                    lift(seedId, "Seed Lift", isCustom = false),
                    lift(customId, "Custom Lift", isCustom = true),
                    lift(otherId, "Other Lift", isCustom = false),
                ),
            )
        logRepo = FakeTrainingLogRepository()
        programRepo = FakeProgramRepository()
        deletePort = FakeDeleteLiftPort(liftRepo, programRepo)
        val logging = FakeLoggingPort()
        subject =
            DeleteLiftUseCase(
                deleteLiftPort = deletePort,
                rebuildDerived =
                    RebuildLocalDerivedTrainingDataUseCase(
                        localDerivedStages =
                            localDerivedTrainingStages(
                                trainingLog = logRepo,
                                liftRepository = liftRepo,
                            ),
                        responseModelingScheduler = FakeResponseModelingSchedulerPort(),
                        loggingPort = logging,
                    ),
                loggingPort = logging,
            )
    }

    @Test
    @DisplayName("Seed and custom lifts delete through the same tombstone path")
    fun seedAndCustomBothTombstone() = runTest {
        assertTrue(subject(seedId).isSuccess)
        assertTrue(subject(customId).isSuccess)

        val seed = liftRepo.currentLifts.single { it.id == seedId }
        val custom = liftRepo.currentLifts.single { it.id == customId }
        assertTrue(seed.isDeleted)
        assertTrue(custom.isDeleted)
        // Rows are retained (history resolution), never hard-deleted.
        assertEquals(3, liftRepo.currentLifts.size)
    }

    @Test
    @DisplayName("Delete sweeps template slots across programs; workouts survive possibly empty")
    fun deleteSweepsSlots() = runTest {
        programRepo.saveProgram(
            program(
                id = "prog-1",
                workoutId = "w-1",
                slots = listOf("wl-seed" to seedId, "wl-other" to otherId),
            ),
        )
        programRepo.saveToLibrary(
            program(
                id = "prog-2",
                workoutId = "w-2",
                slots = listOf("wl-seed-2" to seedId),
            ),
        )

        assertTrue(subject(seedId).isSuccess)

        val active = programRepo.getProgramById(ProgramId("prog-1"))!!
        assertEquals(listOf(otherId), active.workouts.single().workoutLifts.map { it.liftId })
        val library = programRepo.getProgramById(ProgramId("prog-2"))!!
        assertTrue(library.workouts.single().workoutLifts.isEmpty())
    }

    @Test
    @DisplayName("Logged sets keep pointing at the tombstoned row, byte-identical")
    fun loggedSetsUntouched() = runTest {
        logRepo.seedSession(completedSession())
        val original =
            loggedSet(id = "set-1", liftId = seedId, isPr = true)
        logRepo.seedSet(original)

        assertTrue(subject(seedId).isSuccess)

        val after = logRepo.getAllSets().single()
        assertEquals(original, after)
        assertEquals(seedId, after.liftId)
        assertEquals(true, after.isPersonalRecord)
    }

    @Test
    @DisplayName("Re-delete of a tombstoned lift is a no-op success")
    fun redeleteIsNoOpSuccess() = runTest {
        assertTrue(subject(seedId).isSuccess)
        val slotsBefore = deletePort.deleteCallCount
        assertTrue(subject(seedId).isSuccess)
        assertEquals(slotsBefore + 1, deletePort.deleteCallCount)
        assertTrue(liftRepo.currentLifts.single { it.id == seedId }.isDeleted)
    }

    @Test
    @DisplayName("Deleting a missing lift succeeds as a no-op")
    fun missingLiftIsNoOpSuccess() = runTest {
        assertTrue(subject(LiftId("nope")).isSuccess)
        assertFalse(liftRepo.currentLifts.any { it.isDeleted })
    }

    @Test
    @DisplayName("Port failure surfaces as failure and leaves the lift live")
    fun failureLeavesLiftLive() = runTest {
        val failing =
            DeleteLiftUseCase(
                deleteLiftPort = FakeDeleteLiftPort(liftRepo, programRepo, failOnDelete = true),
                rebuildDerived =
                    RebuildLocalDerivedTrainingDataUseCase(
                        localDerivedStages =
                            localDerivedTrainingStages(
                                trainingLog = logRepo,
                                liftRepository = liftRepo,
                            ),
                        responseModelingScheduler = FakeResponseModelingSchedulerPort(),
                        loggingPort = FakeLoggingPort(),
                    ),
                loggingPort = FakeLoggingPort(),
            )

        assertTrue(failing(seedId).isFailure)
        assertFalse(liftRepo.currentLifts.single { it.id == seedId }.isDeleted)
    }

    private fun lift(id: LiftId, name: String, isCustom: Boolean): Lift =
        Lift(
            id = id,
            name = name,
            movementPatterns = emptySet(),
            primaryMuscles = setOf(MuscleGroup.Chest),
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
            isCustom = isCustom,
        )

    private fun program(
        id: String,
        workoutId: String,
        slots: List<Pair<String, LiftId>>,
    ): Program =
        Program(
            id = ProgramId(id),
            name = "Program $id",
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            cycleLength = 1,
            isActive = id == "prog-1",
            workouts =
                listOf(
                    Workout(
                        id = WorkoutId(workoutId),
                        programId = ProgramId(id),
                        name = "Day",
                        weekday = null,
                        position = 0,
                        workoutLifts =
                            slots.mapIndexed { index, (slotId, liftId) ->
                                WorkoutLift(
                                    id = WorkoutLiftId(slotId),
                                    workoutId = WorkoutId(workoutId),
                                    liftId = liftId,
                                    progressionScheme = ProgressionScheme.Linear,
                                    position = index,
                                    sets = emptyList(),
                                )
                            },
                    ),
                ),
        )

    private fun completedSession(): TrainingSession =
        TrainingSession(
            id = sessionId,
            programId = null,
            workoutId = null,
            status = TrainingSessionStatus.COMPLETED,
            kind = TrainingSessionKind.PROGRAM_WORKOUT,
            origin = TrainingSessionOrigin.LIVE,
            cycleNumberSnapshot = 1,
            weekdaySnapshot = null,
            startedAt = Instant.parse("2026-03-01T10:00:00Z"),
            endedAt = Instant.parse("2026-03-01T11:00:00Z"),
            sourceApp = null,
        )

    private fun loggedSet(id: String, liftId: LiftId, isPr: Boolean): LoggedSet =
        LoggedSet(
            id = LoggedSetId(id),
            sessionId = sessionId,
            exerciseNameRaw = "Seed Lift",
            liftId = liftId,
            setIndex = 0,
            weight = Weight(100.0, Weight.WeightUnit.Lb),
            reps = 5,
            rir = 1,
            completedAt = Instant.parse("2026-03-01T10:30:00Z"),
            liftContext = LoggedLiftContext(liftName = "Seed Lift"),
            isPersonalRecord = isPr,
            estimatedOneRm = 115.0,
        )
}
