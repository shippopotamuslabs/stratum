package com.stratum.application.lift

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.onboarding.FakeMergeLiftsPort
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.response.FakeResponseModelingSchedulerPort
import com.stratum.core.testing.response.FakeResponseProfilePersistencePort
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.workout.FakeWorkoutOverlayStore
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
import com.stratum.domain.workout.OverlayAddedLift
import com.stratum.domain.workout.WorkoutOverlay
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
import com.stratum.application.training.ComputePersonalRecordsUseCase
import com.stratum.application.training.RebuildLocalDerivedTrainingDataUseCase
import com.stratum.application.training.TrainingLogRepository
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.MuscleLengthBias

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
    private lateinit var overlayStore: FakeWorkoutOverlayStore
    private lateinit var logging: FakeLoggingPort
    private lateinit var rebuildLocalDerived: RebuildLocalDerivedTrainingDataUseCase
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
        overlayStore = FakeWorkoutOverlayStore()
        logging = FakeLoggingPort()
        rebuildLocalDerived =
            RebuildLocalDerivedTrainingDataUseCase(
                localDerivedStages =
                    com.stratum.core.testing.training.localDerivedTrainingStages(
                        trainingLog = logRepo,
                        liftRepository = liftRepo,
                    ),
                responseModelingScheduler = FakeResponseModelingSchedulerPort(),
                loggingPort = logging,
            )
        subject =
            MergeLiftsUseCase(
                liftRepository = liftRepo,
                trainingLogRepository = logRepo,
                mergeLiftsPort = mergePort,
                computePersonalRecords = ComputePersonalRecordsUseCase(logRepo),
                rebuildLocalDerived = rebuildLocalDerived,
                workoutOverlayStore = overlayStore,
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
    @DisplayName("Given seed source when merged then seed is tombstoned not disabled")
    fun tombstonesSeedSources() = runTest {
        val result = subject(targetLiftId = targetId, sourceLiftIds = setOf(seedSourceId))

        assertTrue(result.isSuccess)
        val tombstoned = liftRepo.currentLifts.singleOrNull { it.id == seedSourceId }
        assertNotNull(tombstoned)
        // STR-131: tombstoned, with enabled left as-is; gone from catalog reads.
        assertTrue(tombstoned!!.isDeleted)
        assertTrue(tombstoned.enabled)
        assertFalse(tombstoned.isCustom)
        assertTrue(liftRepo.currentLifts.any { it.id == targetId && it.enabled })
    }

    @Test
    @DisplayName("Given seed source merged into custom target then seed tombstones too (target-independent)")
    fun seedIntoCustomTargetTombstones() = runTest {
        val result = subject(targetLiftId = sourceAId, sourceLiftIds = setOf(seedSourceId))

        assertTrue(result.isSuccess)
        assertTrue(liftRepo.currentLifts.single { it.id == seedSourceId }.isDeleted)
        val survivor = liftRepo.currentLifts.single { it.id == sourceAId }
        assertFalse(survivor.isDeleted)
    }

    @Test
    @DisplayName("Given mixed seed+custom sources when merged then seed tombstoned and custom deleted")
    fun mixedSeedAndCustom() = runTest {
        val result =
            subject(
                targetLiftId = targetId,
                sourceLiftIds = setOf(seedSourceId, sourceAId),
            )

        assertTrue(result.isSuccess)
        assertTrue(liftRepo.currentLifts.single { it.id == seedSourceId }.isDeleted)
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
        val slots = saved.workouts.single().workoutLifts
        // Both slots remain; source is re-pointed onto the survivor (duplicates allowed).
        assertEquals(2, slots.size)
        assertEquals(
            listOf(WorkoutLiftId("wl-a"), WorkoutLiftId("wl-target")),
            slots.map { it.id },
        )
        assertTrue(slots.all { it.liftId == targetId })
    }

    @Test
    @DisplayName("Given two source slots without target when merged then both slots re-point to target")
    fun repointsMultipleSourcesInSameWorkout() = runTest {
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
        assertEquals(2, slots.size)
        assertEquals(
            listOf(WorkoutLiftId("wl-a"), WorkoutLiftId("wl-b")),
            slots.map { it.id },
        )
        assertTrue(slots.all { it.liftId == targetId })
    }

    @Test
    @DisplayName("Given active session overlay when merged then swapped and added lift ids remap to target")
    fun remapsActiveSessionOverlay() = runTest {
        logRepo.seedSession(inProgressSession())
        overlayStore.seed(
            sessionId,
            WorkoutOverlay(
                swappedLifts = mapOf(WorkoutLiftId("wl-template") to sourceAId),
                addedLifts =
                    listOf(
                        OverlayAddedLift(
                            workoutLiftId = WorkoutLiftId("wl-added"),
                            liftId = sourceBId,
                        ),
                    ),
            ),
        )

        val result = subject(targetLiftId = targetId, sourceLiftIds = setOf(sourceAId, sourceBId))

        assertTrue(result.isSuccess)
        val stored = overlayStore.storedOverlay()
        assertEquals(targetId, stored.swappedLifts[WorkoutLiftId("wl-template")])
        assertEquals(targetId, stored.addedLifts.single().liftId)
    }

    @Test
    @DisplayName("Given no active session when merged then succeeds without writing overlay store")
    fun noActiveSessionSkipsOverlayWrite() = runTest {
        // No IN_PROGRESS session seeded.
        val result = subject(targetLiftId = targetId, sourceLiftIds = setOf(sourceAId))

        assertTrue(result.isSuccess)
        assertEquals(0, overlayStore.saveCallCount)
        assertNull(overlayStore.storedSessionId())
    }

    @Test
    @DisplayName("Given active session with empty overlay store when merged then does not write overlay")
    fun emptyOverlayStoreSkipsWrite() = runTest {
        logRepo.seedSession(inProgressSession())
        // Store has no row for this session — load returns null.

        val result = subject(targetLiftId = targetId, sourceLiftIds = setOf(sourceAId))

        assertTrue(result.isSuccess)
        assertEquals(0, overlayStore.saveCallCount)
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
    @DisplayName("Given PR recompute fails when merged then succeeds carrying the bound diagnostic")
    fun prRecomputeFailureSurfacedAsDiagnostic() = runTest {
        logRepo.seedSession(completedSession())
        logRepo.seedSet(
            loggedSet(
                id = "set-target",
                liftId = targetId,
                name = targetLift.name,
                weight = 90.0,
                isPr = true,
            ),
        )
        val failingSubject =
            MergeLiftsUseCase(
                liftRepository = liftRepo,
                trainingLogRepository = logRepo,
                mergeLiftsPort = mergePort,
                computePersonalRecords = ComputePersonalRecordsUseCase(FailingPrReadRepository(logRepo)),
                rebuildLocalDerived = rebuildLocalDerived,
                workoutOverlayStore = overlayStore,
                loggingPort = logging,
            )

        val result = failingSubject(targetLiftId = targetId, sourceLiftIds = setOf(sourceAId))

        assertTrue(result.isSuccess)
        val outcome = result.getOrThrow()
        assertEquals(1, outcome.diagnostics.size)
        val diagnostic = outcome.diagnostics.single() as MergeLiftsDiagnostic.PersonalRecordsNotRecomputed
        assertEquals(targetId, diagnostic.targetLiftId)
        assertTrue(diagnostic.cause is IllegalStateException)
        // The structural merge itself still applied.
        assertNull(liftRepo.currentLifts.firstOrNull { it.id == sourceAId })
    }

    @Test
    @DisplayName("Given healthy PR recompute when merged then diagnostics are empty")
    fun happyPathCarriesNoDiagnostics() = runTest {
        logRepo.seedSession(completedSession())
        logRepo.seedSet(
            loggedSet(
                id = "set-target",
                liftId = targetId,
                name = targetLift.name,
                weight = 90.0,
                isPr = true,
            ),
        )

        val result = subject(targetLiftId = targetId, sourceLiftIds = setOf(sourceAId))

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().diagnostics.isEmpty())
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

    @Test
    @DisplayName("Given missing lift id when invoked then fails")
    fun rejectsMissingLift() = runTest {
        val unknownId = LiftId("unknown-lift")
        val result = subject(targetLiftId = targetId, sourceLiftIds = setOf(unknownId))
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

    private fun inProgressSession(): TrainingSession =
        TrainingSession(
            id = sessionId,
            programId = ProgramId("prog-1"),
            workoutId = WorkoutId("w1"),
            status = TrainingSessionStatus.IN_PROGRESS,
            kind = TrainingSessionKind.PROGRAM_WORKOUT,
            origin = TrainingSessionOrigin.LIVE,
            cycleNumberSnapshot = 1,
            weekdaySnapshot = null,
            startedAt = Instant.parse("2026-03-01T12:00:00Z"),
            endedAt = null,
            sourceApp = null,
        )

    /**
     * Delegates every [TrainingLogRepository] call except the PR recompute read,
     * which throws — forces [ComputePersonalRecordsUseCase] to fail while the
     * merge's own reads (via the raw fake) keep succeeding.
     */
    private class FailingPrReadRepository(
        delegate: TrainingLogRepository,
    ) : TrainingLogRepository by delegate {
        override suspend fun getSetsForLift(liftId: LiftId): List<LoggedSet> =
            throw IllegalStateException("PR read boom")
    }

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
