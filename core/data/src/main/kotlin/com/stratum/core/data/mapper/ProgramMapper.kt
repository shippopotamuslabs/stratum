package com.stratum.core.data.mapper

import com.stratum.core.data.db.LiftSetEntity
import com.stratum.core.data.db.ProgramEntity
import com.stratum.core.data.db.WorkoutEntity
import com.stratum.core.data.db.WorkoutLiftEntity
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.Program
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import java.time.Instant

/**
 * Pure translations between the OS-7 program hierarchy domain models and the four normalized
 * entities. `Program.createdAt` ↔ stored ISO-8601 string; [Weekday] is typed on [WorkoutEntity]
 * via [com.stratum.core.data.db.EnumConverters]. Typed ids round-trip via `EnumConverters` and stay typed in the entity layer.
 */
internal fun ProgramEntity.toDomain(workouts: List<Workout>): Program = Program(
    id = id,
    name = name,
    createdAt = Instant.parse(createdAt),
    cycleLength = cycleLength,
    trainingWeekdays = trainingWeekdays,
    workouts = workouts,
    currentCycleNumber = currentCycleNumber,
    cyclePosition = cyclePosition,
    isActive = isActive,
)

internal fun WorkoutEntity.toDomain(workoutLifts: List<WorkoutLift>): Workout = Workout(
    id = id,
    programId = programId,
    name = name,
    weekday = weekday,
    position = position,
    workoutLifts = workoutLifts,
)

internal fun WorkoutLiftEntity.toDomain(sets: List<LiftSet>): WorkoutLift = WorkoutLift(
    id = id,
    workoutId = workoutId,
    liftId = liftId,
    progressionScheme = progressionScheme,
    position = position,
    sets = sets,
    supersetGroupId = supersetGroupId,
    useRestTimerBetweenLifts = useRestTimerBetweenLifts,
)

internal fun LiftSetEntity.toDomain(): LiftSet = LiftSet(
    id = id,
    workoutLiftId = workoutLiftId,
    position = position,
    setType = setType,
    repMin = repMin,
    repMax = repMax,
    rirGoal = rirGoal,
    isTopSet = isTopSet,
    restSeconds = restSeconds,
)

internal fun Program.toEntity(): ProgramEntity = ProgramEntity(
    id = id,
    name = name,
    createdAt = createdAt.toString(),
    cycleLength = cycleLength,
    trainingWeekdays = trainingWeekdays,
    currentCycleNumber = currentCycleNumber,
    cyclePosition = cyclePosition,
    isActive = isActive,
)

internal fun Workout.toEntity(): WorkoutEntity = WorkoutEntity(
    id = id,
    programId = programId,
    name = name,
    weekday = weekday,
    position = position,
)

internal fun WorkoutLift.toEntity(): WorkoutLiftEntity = WorkoutLiftEntity(
    id = id,
    workoutId = workoutId,
    liftId = liftId,
    progressionScheme = progressionScheme,
    position = position,
    supersetGroupId = supersetGroupId,
    useRestTimerBetweenLifts = useRestTimerBetweenLifts,
)

internal fun LiftSet.toEntity(): LiftSetEntity = LiftSetEntity(
    id = id,
    workoutLiftId = workoutLiftId,
    position = position,
    setType = setType,
    repMin = repMin,
    repMax = repMax,
    rirGoal = rirGoal,
    isTopSet = isTopSet,
    restSeconds = restSeconds,
)

/**
 * Rejoins the four normalized tables into a [Program] (or `null` if the program row is missing).
 * Keeps the entity-traversal out of the repository so [RoomProgramRepository] stays clean of
 * mapping detail. DAO results are pre-loaded and passed in so this is a pure function over data.
 */
internal fun rejoinProgram(
    programEntity: ProgramEntity,
    workouts: List<WorkoutEntity>,
    workoutLiftsByWorkout: Map<String, List<WorkoutLiftEntity>>,
    setsByLift: Map<String, List<LiftSetEntity>>,
): Program = programEntity.toDomain(
    workouts = workouts.map { w ->
        w.toDomain(
            workoutLifts = workoutLiftsByWorkout[w.id.value].orEmpty().map { wl ->
                wl.toDomain(sets = setsByLift[wl.id.value].orEmpty().map { it.toDomain() })
            },
        )
    },
)
