package com.stratum.core.testing.training

import com.stratum.domain.importing.ImportHistoryTrainingMapper
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
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
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant

object WorkoutLoggingFixtures {
    const val PROGRAM_ID = "prog-1"
    const val WORKOUT_A_ID = "wkt-a"
    const val WORKOUT_B_ID = "wkt-b"
    const val WORKOUT_C_ID = "wkt-c"
    const val LIFT_BENCH_ID = "lift-bench"
    const val LIFT_SQUAT_ID = "lift-squat"
    /** LiftMapper ordinal bitmasks — `1 shl MovementPattern.HorizontalPress.ordinal`, etc. */
    const val BENCH_HORIZONTAL_BITMASK = 32
    const val CHEST_BITMASK = 1
    const val TRICEPS_BITMASK = 256
    const val BENCH_WEIGHT_LB = 135.0

    val programId = ProgramId(PROGRAM_ID)
    val workoutAId = WorkoutId(WORKOUT_A_ID)
    val workoutBId = WorkoutId(WORKOUT_B_ID)
    val workoutCId = WorkoutId(WORKOUT_C_ID)
    val liftBenchId = LiftId(LIFT_BENCH_ID)
    val liftSquatId = LiftId(LIFT_SQUAT_ID)

    fun backOnlyBench(): Lift = benchLift().copy(
        primaryMuscles = setOf(MuscleGroup.Back),
        secondaryMuscles = emptySet(),
    )

    fun verticalPullCatalogBench(): Lift = benchLift().copy(
        movementPatterns = setOf(MovementPattern.VerticalPull),
    )

    fun importTrainingSession(
        id: String = "ts-import-1",
        startedAt: Instant = Instant.parse("2025-01-01T10:00:00Z"),
        endedAt: Instant? = Instant.parse("2025-01-01T11:00:00Z"),
        sourceApp: String? = "liftlab",
    ): TrainingSession =
        ImportHistoryTrainingMapper.parseSession(
            id = id,
            startedAt = startedAt,
            endedAt = endedAt,
            sourceApp = sourceApp,
        )

    fun importLoggedSet(
        id: String = "import-set-1",
        sessionId: String = "ts-import-1",
        exerciseNameRaw: String = "Bench Press",
        liftId: LiftId? = liftBenchId,
        liftContext: LoggedLiftContext = importLiftContext(),
        completedAt: Instant = Instant.parse("2025-01-01T10:30:00Z"),
    ): LoggedSet =
        ImportHistoryTrainingMapper.parseSet(
            id = LoggedSetId(id),
            sessionId = TrainingSessionId(sessionId),
            exerciseNameRaw = exerciseNameRaw,
            setIndex = 0,
            weight = Weight(BENCH_WEIGHT_LB, Weight.WeightUnit.Lb),
            reps = 8,
            rir = 2,
            completedAt = completedAt,
        ).copy(
            liftId = liftId,
            liftContext = if (liftId != null) liftContext else importLiftContext(liftName = exerciseNameRaw),
        )

    fun benchLift(): Lift = Lift(
        id = liftBenchId,
        name = "Barbell Bench Press",
        movementPatterns = setOf(MovementPattern.HorizontalPress),
        primaryMuscles = setOf(MuscleGroup.Chest),
        secondaryMuscles = setOf(MuscleGroup.Tricep),
        allowWeightChangeBetweenSets = true,
        incrementTiers = emptyList(),
        presetTags = emptySet(),
        enabled = true,
        kinematicProfile = KinematicProfile.NonAxialCompound,
        muscleLengthBias = MuscleLengthBias.MidRange,
    )

    fun importLiftContext(liftName: String = "Barbell Bench Press"): LoggedLiftContext = LoggedLiftContext(
        liftName = liftName,
        movementPatternsBitmask = BENCH_HORIZONTAL_BITMASK,
        primaryMusclesBitmask = CHEST_BITMASK,
        secondaryMusclesBitmask = TRICEPS_BITMASK,
        kinematicProfile = KinematicProfile.NonAxialCompound,
        muscleLengthBias = MuscleLengthBias.MidRange,
    )

    fun twoWorkoutProgram(
        cyclePosition: Int = 0,
        currentCycleNumber: Int = 1,
        workouts: List<Workout> = defaultWorkouts(),
    ): Program = Program(
        id = programId,
        name = "Test Program",
        createdAt = Instant.parse("2025-01-01T00:00:00Z"),
        cycleLength = 2,
        trainingWeekdays = setOf(Weekday.Monday, Weekday.Wednesday),
        workouts = workouts,
        currentCycleNumber = currentCycleNumber,
        cyclePosition = cyclePosition,
    )

    fun defaultWorkouts(): List<Workout> = listOf(
        Workout(
            id = workoutAId,
            programId = programId,
            name = "Workout A",
            weekday = Weekday.Monday,
            position = 0,
            workoutLifts = emptyList(),
        ),
        Workout(
            id = workoutBId,
            programId = programId,
            name = "Workout B",
            weekday = Weekday.Wednesday,
            position = 1,
            workoutLifts = emptyList(),
        ),
    )

    fun completedProgramWorkoutSession(
        id: String,
        workoutId: WorkoutId = workoutAId,
        cycleNumber: Int = 1,
    ): TrainingSession = TrainingSession(
        id = TrainingSessionId(id),
        programId = programId,
        workoutId = workoutId,
        status = TrainingSessionStatus.COMPLETED,
        kind = TrainingSessionKind.PROGRAM_WORKOUT,
        origin = TrainingSessionOrigin.IMPORT,
        cycleNumberSnapshot = cycleNumber,
        weekdaySnapshot = Weekday.Monday,
        startedAt = Instant.parse("2025-01-01T10:00:00Z"),
        endedAt = Instant.parse("2025-01-01T11:00:00Z"),
        sourceApp = "liftlab",
    )

    fun completedLiveSession(
        id: String,
        workoutId: WorkoutId? = workoutAId,
        kind: TrainingSessionKind = TrainingSessionKind.PROGRAM_WORKOUT,
    ): TrainingSession = TrainingSession(
        id = TrainingSessionId(id),
        programId = programId,
        workoutId = workoutId,
        status = TrainingSessionStatus.COMPLETED,
        kind = kind,
        origin = TrainingSessionOrigin.LIVE,
        cycleNumberSnapshot = 1,
        weekdaySnapshot = Weekday.Monday,
        startedAt = Instant.parse("2025-01-01T10:00:00Z"),
        endedAt = Instant.parse("2025-01-01T11:00:00Z"),
        sourceApp = null,
    )

    fun inProgressSession(
        id: String = "ts-live-1",
        workoutId: WorkoutId? = workoutAId,
        kind: TrainingSessionKind = TrainingSessionKind.PROGRAM_WORKOUT,
    ): TrainingSession = TrainingSession(
        id = TrainingSessionId(id),
        programId = programId,
        workoutId = workoutId,
        status = TrainingSessionStatus.IN_PROGRESS,
        kind = kind,
        origin = TrainingSessionOrigin.LIVE,
        cycleNumberSnapshot = 1,
        weekdaySnapshot = Weekday.Monday,
        startedAt = Instant.parse("2025-01-01T10:00:00Z"),
        endedAt = null,
        sourceApp = null,
    )

    fun loggedSet(
        id: String,
        sessionId: TrainingSessionId,
        liftContext: LoggedLiftContext = importLiftContext(),
    ): LoggedSet = LoggedSet(
        id = LoggedSetId(id),
        sessionId = sessionId,
        exerciseNameRaw = "Bench Press",
        liftId = liftBenchId,
        setIndex = 0,
        weight = Weight(BENCH_WEIGHT_LB, Weight.WeightUnit.Lb),
        reps = 8,
        rir = 2,
        completedAt = Instant.parse("2025-01-01T10:30:00Z"),
        liftContext = liftContext,
    )

    fun workoutLiftWithDoubleProgression(): WorkoutLift = WorkoutLift(
        id = WorkoutLiftId("wl-1"),
        workoutId = workoutAId,
        liftId = liftBenchId,
        progressionScheme = ProgressionScheme.Double,
        position = 0,
        sets = listOf(
            LiftSet(
                id = LiftSetId("ls-1"),
                workoutLiftId = WorkoutLiftId("wl-1"),
                position = 0,
                setType = SetType.Standard,
                repMin = 6,
                repMax = 10,
                rirGoal = 2,
                isTopSet = true,
            ),
        ),
    )
}
