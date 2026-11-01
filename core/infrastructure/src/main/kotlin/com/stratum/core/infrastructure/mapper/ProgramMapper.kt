package com.stratum.core.infrastructure.mapper

import com.stratum.core.infrastructure.db.LiftSetEntity
import com.stratum.core.infrastructure.db.ProgramEntity
import com.stratum.core.infrastructure.db.WorkoutEntity
import com.stratum.core.infrastructure.db.WorkoutLiftEntity
import com.stratum.core.infrastructure.sync.SyncFlags
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.Program
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import java.time.Instant

/**
 * Pure translations between the OS-7 program hierarchy domain models and the four normalized
 * entities. `Program.createdAt` ↔ stored ISO-8601 string; [Weekday] is typed on [WorkoutEntity]
 * via [com.stratum.core.infrastructure.db.EnumConverters]. Typed ids round-trip via `EnumConverters` and stay typed in the entity layer.
 */
internal fun ProgramEntity.toDomain(workouts: List<Workout>): Program = Program(
    id = id,
    name = name,
    // STR-129: never throw on malformed timestamps — fall back to epoch so a single
    // bad row cannot brick finalize.
    createdAt = runCatching { Instant.parse(createdAt) }.getOrElse { Instant.EPOCH },
    cycleLength = cycleLength,
    trainingWeekdays = trainingWeekdays,
    workouts = workouts,
    currentCycleNumber = currentCycleNumber,
    cyclePosition = cyclePosition,
    isActive = isActive,
    restSlotIndices = restSlotIndices,
    cycleStartEpochDay = cycleStartEpochDay,
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
    equipmentId = equipmentId,
    progressionScheme = progressionScheme,
    position = position,
    sets = sets,
    supersetGroupId = supersetGroupId,
    useRestTimerBetweenLifts = useRestTimerBetweenLifts,
    pinnedNote = pinnedNote,
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
    myoRepKind = myoRepKind,
)

internal fun Program.toEntity(
    nowMillis: Long = System.currentTimeMillis(),
): ProgramEntity = ProgramEntity(
    id = id,
    name = name,
    createdAt = createdAt.toString(),
    cycleLength = cycleLength,
    trainingWeekdays = trainingWeekdays,
    currentCycleNumber = currentCycleNumber,
    cyclePosition = cyclePosition,
    isActive = isActive,
    restSlotIndices = restSlotIndices,
    cycleStartEpochDay = cycleStartEpochDay,
    updatedAtEpochMillis = nowMillis,
    pendingSync = SyncFlags.PENDING_UPSERT,
)

internal fun Workout.toEntity(
    nowMillis: Long = System.currentTimeMillis(),
): WorkoutEntity = WorkoutEntity(
    id = id,
    programId = programId,
    name = name,
    weekday = weekday,
    position = position,
    updatedAtEpochMillis = nowMillis,
    pendingSync = SyncFlags.PENDING_UPSERT,
)

internal fun WorkoutLift.toEntity(
    nowMillis: Long = System.currentTimeMillis(),
): WorkoutLiftEntity = WorkoutLiftEntity(
    id = id,
    workoutId = workoutId,
    liftId = liftId,
    equipmentId = equipmentId,
    progressionScheme = progressionScheme,
    position = position,
    supersetGroupId = supersetGroupId,
    useRestTimerBetweenLifts = useRestTimerBetweenLifts,
    pinnedNote = pinnedNote,
    updatedAtEpochMillis = nowMillis,
    pendingSync = SyncFlags.PENDING_UPSERT,
)

internal fun LiftSet.toEntity(
    nowMillis: Long = System.currentTimeMillis(),
): LiftSetEntity = LiftSetEntity(
    id = id,
    workoutLiftId = workoutLiftId,
    position = position,
    setType = setType,
    repMin = repMin,
    repMax = repMax,
    rirGoal = rirGoal,
    isTopSet = isTopSet,
    restSeconds = restSeconds,
    myoRepKind = myoRepKind,
    updatedAtEpochMillis = nowMillis,
    pendingSync = SyncFlags.PENDING_UPSERT,
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
