package com.stratum.domain.lift

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.onboarding.FakeMergeLiftsPort
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.response.FakeResponseModelingSchedulerPort
import com.stratum.core.testing.response.FakeResponseProfilePersistencePort
import com.stratum.core.testing.training.FakeTrainingLogRepository
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
import com.stratum.domain.training.ComputePersonalRecordsUseCase
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.RebuildLocalDerivedTrainingDataUseCase
import com.stratum.domain.training.RefreshWeeklySeriesUseCase
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class MergeLiftsUseCaseTest {

    private val targetId = LiftId("lift-target")
    private val sourceAId = LiftId("lift-source-a")
    private val sourceBId = LiftId("lift-source-b")
    private val seedSourceId = LiftId("seed-source")
    private val sessionId = TrainingSessionId("session-1")

    private lateinit var liftRepo: FakeLiftRepository
    private lateinit var logRepo: FakeTrainingLogRepository
    private lateinit var programRepo: FakeProgramRepository
    private lateinit var mergePort: FakeMergeLiftsPort
    private lateinit var subject: MergeLiftsUseCase

    private val targetLift =
        lift(
            id = targetId,
            name = "Barbell Bench Press",
            primary = MuscleGroup.Chest,
            isCustom = false,
        )
    private val sourceA =
        lift(
            id = sourceAId,
            name = "Bench (import)",
            primary = MuscleGroup.Back,
            isCustom = true,
        )
    private val sourceB =
        lift(
            id = sourceBId,
            name = "Flat Bench",
            primary = MuscleGroup.Chest,
            isCustom = true,
        )
    private val seedSource =
        lift(
            id = seedSourceId,
            name = "Dumbbell Bench Press",
            primary = MuscleGroup.Chest,
            isCustom = false,
        )

    @BeforeEach
    fun setup() {
        liftRepo = FakeLiftRepository(listOf(targetLift, sourceA, sourceB, seedSource))
        logRepo = FakeTrainingLogRepository()
        programRepo = FakeProgramRepository()
        mergePort = FakeMergeLiftsPort(liftRepo, logRepo, programRepo)
        val logging = FakeLoggingPort()
        subject =
            MergeLiftsUseCase(
                liftRepository = liftRepo,
                trainingLogRepository = logRepo,
                mergeLiftsPort = mergePort,
                computePersonalRecords = ComputePersonalRecordsUseCase(logRepo),
                rebuildLocalDerived =
                    RebuildLocalDerivedTrainingDataUseCase(
                        refreshWeeklySeries =
                            RefreshWeeklySeriesUseCase(
                                trainingLogRepository = logRepo,
                                liftRepository = liftRepo,
                                persistence = FakeResponseProfilePersistencePort(),
                            ),
                        responseModelingScheduler = FakeResponseModelingSchedulerPort(),
                        loggingPort = logging,
                    ),
                loggingPort = logging,
            )
    }

    @Test
    @DisplayName("Given source history when merged then sets reassign to target with target name")
    fun reassignsHistoryAndNames() = runTest {
        logRepo.seedSession(completedSession())
        logRepo.seedSet(
            loggedSet(
                id = "set-a",
                liftId = sourceAId,
                name = sourceA.name,
                weight = 100.0,
                isPr = true,
            ),
        )
        logRepo.seedSet(
            loggedSet(
                id = "set-b",
                liftId = sourceBId,
                name = sourceB.name,
                weight = 120.0,
                isPr = true,
            ),
        )
        logRepo.seedSet(
            loggedSet(
                id = "set-target",
                liftId = targetId,
                name = targetLift.name,
                weight = 90.0,
                isPr = true,
            ),
        )

        val result = subject(targetLiftId = targetId, sourceLiftIds = setOf(sourceAId, sourceBId))

        assertTrue(result.isSuccess)
        val remaining = logRepo.getAllSets()
        assertEquals(3, remaining.size)
        assertTrue(remaining.all { it.liftId == targetId })
        assertTrue(remaining.all { it.liftContext.liftName == targetLift.name })
        assertTrue(remaining.all { it.exerciseNameRaw == targetLift.name })
        assertEquals(1, remaining.count { it.isPersonalRecord })
        assertTrue(remaining.single { it.isPersonalRecord }.id.value == "set-b")
    }

    @Test
    @DisplayName("Given custom sources when merged then customs deleted and target metadata kept")
    fun deletesCustomSourcesKeepsTargetMetadata() = runTest {
        val result = subject(targetLiftId = targetId, sourceLiftIds = setOf(sourceAId))

        assertTrue(result.isSuccess)
        assertNull(liftRepo.currentLifts.firstOrNull { it.id == sourceAId })
        assertTrue(liftRepo.currentLifts.any { it.id == sourceBId })
        val survivor = liftRepo.currentLifts.single { it.id == targetId }
        assertEquals(targetLift.name, survivor.name)
        assertEquals(targetLift.primaryMuscles, survivor.primaryMuscles)
        assertFalse(survivor.isCustom)
    }

    @Test
    @DisplayName("Given seed source when merged then seed is disabled not deleted")
    fun hidesSeedSources() = runTest {
        val result = subject(targetLiftId = targetId, sourceLiftIds = setOf(seedSourceId))

        assertTrue(result.isSuccess)
        val hidden = liftRepo.currentLifts.singleOrNull { it.id == seedSourceId }
        assertNotNull(hidden)
        assertFalse(hidden!!.enabled)
        assertFalse(hidden.isCustom)
        assertTrue(liftRepo.currentLifts.any { it.id == targetId && it.enabled })
    }

    @Test
    @DisplayName("Given mixed seed+custom sources when merged then seed hidden and custom deleted")
    fun mixedSeedAndCustom() = runTest {
        val result =
            subject(
                targetLiftId = targetId,
                sourceLiftIds = setOf(seedSourceId, sourceAId),
            )

        assertTrue(result.isSuccess)
        assertFalse(liftRepo.currentLifts.single { it.id == seedSourceId }.enabled)
        assertNull(liftRepo.currentLifts.firstOrNull { it.id == sourceAId })
    }

    @Test
    @DisplayName("Given program workout lifts on sources when merged then templates repoint to target")
    fun repointsProgramTemplates() = runTest {
        val program =
            Program(
                id = ProgramId("prog-1"),
                name = "Main",
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                cycleLength = 4,
                isActive = true,
                workouts =
                    listOf(
                        Workout(
                            id = WorkoutId("w1"),
                            programId = ProgramId("prog-1"),
                            name = "Push",
                            weekday = null,
                            position = 0,
                            workoutLifts =
                                listOf(
                                    WorkoutLift(
                                        id = WorkoutLiftId("wl-a"),
                                        workoutId = WorkoutId("w1"),
                                        liftId = sourceAId,
                                        progressionScheme = ProgressionScheme.Linear,
                                        position = 0,
                                        sets = emptyList(),
                                    ),
                                    WorkoutLift(
                                        id = WorkoutLiftId("wl-target"),
                                        workoutId = WorkoutId("w1"),
                                        liftId = targetId,
                                        progressionScheme = ProgressionScheme.Linear,
                                        position = 1,
                                        sets = emptyList(),
                                    ),
                                ),
                        ),
                    ),
            )
        programRepo.saveProgram(program)

        val result = subject(targetLiftId = targetId, sourceLiftIds = setOf(sourceAId))

        assertTrue(result.isSuccess)
        val saved = programRepo.getProgram()!!
        val liftIds = saved.workouts.single().workoutLifts.map { it.liftId }
        // Duplicate slots for the same lift collapse to the existing target slot.
        assertEquals(listOf(targetId), liftIds)
        assertEquals(WorkoutLiftId("wl-target"), saved.workouts.single().workoutLifts.single().id)
    }

    @Test
    @DisplayName("Given two source slots without target when merged then one slot remains on target")
    fun collapsesMultipleSourcesInSameWorkout() = runTest {
        val program =
            Program(
                id = ProgramId("prog-2"),
                name = "Main",
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                cycleLength = 4,
                isActive = true,
                workouts =
                    listOf(
                        Workout(
                            id = WorkoutId("w2"),
                            programId = ProgramId("prog-2"),
                            name = "Push",
                            weekday = null,
                            position = 0,
                            workoutLifts =
                                listOf(
                                    WorkoutLift(
                                        id = WorkoutLiftId("wl-a"),
                                        workoutId = WorkoutId("w2"),
                                        liftId = sourceAId,
                                        progressionScheme = ProgressionScheme.Linear,
                                        position = 0,
                                        sets = emptyList(),
                                    ),
                                    WorkoutLift(
                                        id = WorkoutLiftId("wl-b"),
                                        workoutId = WorkoutId("w2"),
                                        liftId = sourceBId,
                                        progressionScheme = ProgressionScheme.Linear,
                                        position = 1,
                                        sets = emptyList(),
                                    ),
                                ),
                        ),
                    ),
            )
        programRepo.saveProgram(program)

        val result =
            subject(
                targetLiftId = targetId,
                sourceLiftIds = setOf(sourceAId, sourceBId),
            )

        assertTrue(result.isSuccess)
        val slots = programRepo.getProgram()!!.workouts.single().workoutLifts
        assertEquals(1, slots.size)
        assertEquals(targetId, slots.single().liftId)
        assertEquals(WorkoutLiftId("wl-a"), slots.single().id)
    }

    @Test
    @DisplayName("Given library program when merged then inactive program templates repoint too")
    fun repointsLibraryPrograms() = runTest {
        val library =
            Program(
                id = ProgramId("lib-1"),
                name = "Library",
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                cycleLength = 3,
                isActive = false,
                workouts =
                    listOf(
                        Workout(
                            id = WorkoutId("lw1"),
                            programId = ProgramId("lib-1"),
                            name = "Day A",
                            weekday = null,
                            position = 0,
                            workoutLifts =
                                listOf(
                                    WorkoutLift(
                                        id = WorkoutLiftId("lwl-1"),
                                        workoutId = WorkoutId("lw1"),
                                        liftId = seedSourceId,
                                        progressionScheme = ProgressionScheme.Linear,
                                        position = 0,
                                        sets = emptyList(),
                                    ),
                                ),
                        ),
                    ),
            )
        programRepo.saveToLibrary(library)

        val result = subject(targetLiftId = targetId, sourceLiftIds = setOf(seedSourceId))

        assertTrue(result.isSuccess)
        val saved = programRepo.getProgramById(ProgramId("lib-1"))!!
        assertEquals(targetId, saved.workouts.single().workoutLifts.single().liftId)
    }

    @Test
    @DisplayName("Given empty sources when invoked then fails")
    fun rejectsEmptySources() = runTest {
        val result = subject(targetLiftId = targetId, sourceLiftIds = emptySet())
        assertTrue(result.isFailure)
        assertEquals(0, mergePort.applyMergeCallCount)
    }

    @Test
    @DisplayName("Given target also in sources when invoked then fails")
    fun rejectsTargetInSources() = runTest {
        val result = subject(targetLiftId = targetId, sourceLiftIds = setOf(targetId, sourceAId))
        assertTrue(result.isFailure)
        assertEquals(0, mergePort.applyMergeCallCount)
    }

    private fun lift(
        id: LiftId,
        name: String,
        primary: MuscleGroup,
        isCustom: Boolean,
    ): Lift =
        Lift(
            id = id,
            name = name,
            movementPatterns = emptySet(),
            primaryMuscles = setOf(primary),
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
            isCustom = isCustom,
            muscleLengthBias = MuscleLengthBias.MidRange,
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

    private fun loggedSet(
        id: String,
        liftId: LiftId,
        name: String,
        weight: Double,
        isPr: Boolean = false,
    ): LoggedSet =
        LoggedSet(
            id = LoggedSetId(id),
            sessionId = sessionId,
            exerciseNameRaw = name,
            liftId = liftId,
            setIndex = 0,
            weight = Weight(weight, Weight.WeightUnit.Lb),
            reps = 5,
            rir = 1,
            completedAt = Instant.parse("2026-03-01T10:30:00Z"),
            liftContext = LoggedLiftContext(liftName = name),
            isPersonalRecord = isPr,
            estimatedOneRm = weight * 1.15,
        )
}
