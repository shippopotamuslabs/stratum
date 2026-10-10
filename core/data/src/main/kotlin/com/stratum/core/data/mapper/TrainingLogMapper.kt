package com.stratum.core.data.mapper

import com.stratum.core.data.db.LoggedSetEntity
import com.stratum.core.data.db.TrainingSessionEntity
import com.stratum.core.data.sync.SyncFlags
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession

internal fun TrainingSessionEntity.toDomain(): TrainingSession = TrainingSession(
    id = id,
    programId = programId,
    workoutId = workoutId,
    status = status,
    kind = kind,
    origin = origin,
    cycleNumberSnapshot = cycleNumberSnapshot,
    weekdaySnapshot = weekdaySnapshot,
    cycleSlotIndexSnapshot = cycleSlotIndexSnapshot,
    startedAt = java.time.Instant.ofEpochMilli(startedAtEpochMillis),
    endedAt = endedAtEpochMillis?.let(java.time.Instant::ofEpochMilli),
    sourceApp = sourceApp,
    programNameSnapshot = programNameSnapshot,
    workoutNameSnapshot = workoutNameSnapshot,
)

internal fun TrainingSession.toEntity(
    nowMillis: Long = System.currentTimeMillis(),
): TrainingSessionEntity = TrainingSessionEntity(
    id = id,
    programId = programId,
    workoutId = workoutId,
    status = status,
    kind = kind,
    origin = origin,
    cycleNumberSnapshot = cycleNumberSnapshot,
    weekdaySnapshot = weekdaySnapshot,
    cycleSlotIndexSnapshot = cycleSlotIndexSnapshot,
    startedAtEpochMillis = startedAt.toEpochMilli(),
    endedAtEpochMillis = endedAt?.toEpochMilli(),
    sourceApp = sourceApp,
    programNameSnapshot = programNameSnapshot,
    workoutNameSnapshot = workoutNameSnapshot,
    updatedAtEpochMillis = nowMillis,
    pendingSync = SyncFlags.PENDING_UPSERT,
)

internal fun LoggedSetEntity.toTrainingDomain(): LoggedSet = LoggedSet(
    id = id,
    sessionId = sessionId,
    exerciseNameRaw = exerciseNameRaw,
    liftId = liftId,
    setIndex = setIndex,
    weight = com.stratum.domain.lift.Weight(weightAmount, com.stratum.domain.lift.Weight.WeightUnit.parse(weightUnit)),
    reps = reps,
    rir = rir,
    completedAt = java.time.Instant.ofEpochMilli(completedAtEpochMillis),
    partialReps = partialReps,
    liftContext =
        LoggedLiftContext(
            liftName = liftName,
            movementPatternsBitmask = movementPatternsBitmask,
            primaryMusclesBitmask = primaryMusclesBitmask,
            secondaryMusclesBitmask = secondaryMusclesBitmask,
            fatigueProfile = fatigueProfile,
            muscleLengthBias = muscleLengthBias,
            workoutLiftId = workoutLiftId,
            progressionScheme = progressionScheme,
            setType = setType,
            repMin = repMin,
            repMax = repMax,
            rirGoal = rirGoal,
        ),
    isPersonalRecord = isPersonalRecord ?: false,
    estimatedOneRm = estimatedOneRm,
)

internal fun LoggedSet.toEntity(
    nowMillis: Long = System.currentTimeMillis(),
): LoggedSetEntity = LoggedSetEntity(
    id = id,
    sessionId = sessionId,
    exerciseNameRaw = exerciseNameRaw,
    liftId = liftId,
    setIndex = setIndex,
    weightAmount = weight.amount,
    weightUnit = weight.unit.name,
    reps = reps,
    rir = rir,
    partialReps = partialReps,
    completedAtEpochMillis = completedAt.toEpochMilli(),
    liftName = liftContext.liftName,
    movementPatternsBitmask = liftContext.movementPatternsBitmask,
    primaryMusclesBitmask = liftContext.primaryMusclesBitmask,
    secondaryMusclesBitmask = liftContext.secondaryMusclesBitmask,
    fatigueProfile = liftContext.fatigueProfile,
    muscleLengthBias = liftContext.muscleLengthBias,
    workoutLiftId = liftContext.workoutLiftId,
    progressionScheme = liftContext.progressionScheme,
    setType = liftContext.setType,
    repMin = liftContext.repMin,
    repMax = liftContext.repMax,
    rirGoal = liftContext.rirGoal,
    isPersonalRecord = if (isPersonalRecord) true else null,
    estimatedOneRm = estimatedOneRm,
    updatedAtEpochMillis = nowMillis,
    pendingSync = SyncFlags.PENDING_UPSERT,
)
