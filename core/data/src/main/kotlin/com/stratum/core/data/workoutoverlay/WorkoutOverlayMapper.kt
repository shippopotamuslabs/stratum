package com.stratum.core.data.workoutoverlay

import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutSupersetGroupId
import com.stratum.domain.workout.LiveSetRecommendation
import com.stratum.domain.workout.LoggedSetValue
import com.stratum.domain.workout.OverlayAddedLift
import com.stratum.domain.workout.OverlaySetInstanceId
import com.stratum.domain.workout.SetSlot
import com.stratum.domain.workout.SetSlotId
import com.stratum.domain.workout.SlotRecommendation
import com.stratum.domain.workout.SlotShapeOverride
import com.stratum.domain.workout.StructuralMutation
import com.stratum.domain.workout.WorkoutOverlay

/**
 * Maps [WorkoutOverlay] ↔ [WorkoutOverlayDto]. The domain model stays annotation-free
 * (ARCHITECTURE.md §5); these extensions are the single conversion boundary, mirroring the
 * `com.stratum.core.data.mapper.*` convention.
 *
 * Defensive parsing: every domain id value class forbids blank strings in its `init`, so the
 * `...orNull` helpers here skip any DTO entry whose id string is blank (e.g. from a
 * hand-edited or partially-corrupt record) rather than throwing.
 */

/** `WorkoutOverlay → DTO`. Live recommendations round-trip for session restore. */
fun WorkoutOverlay.toDto(): WorkoutOverlayDto = WorkoutOverlayDto(
    loggedSets = loggedSets.entries.map { (slotId, value) ->
        LoggedSetEntryDto(slotId.toDto(), value.toDto())
    },
    recommendations = recommendations.entries.map { (slotId, rec) ->
        LiveSetRecommendationEntryDto(slotId.toDto(), rec.weight.toDto(), rec.reps)
    },
    addedSlots = addedSlots.map { it.toDto() },
    deletedSlotIds = deletedSlotIds.map { it.toDto() },
    undoStack = undoStack.map { it.toDto() },
    dynamicBaselines = dynamicBaselines.entries.map { (slotId, baseline) ->
        DynamicBaselineEntryDto(slotId.toDto(), baseline.toDto())
    },
    swappedLifts = swappedLifts.entries.map { (workoutLiftId, liftId) ->
        SwappedLiftEntryDto(workoutLiftId.value, liftId.value)
    },
    deletedLiftIds = deletedLiftIds.map { it.value },
    addedLifts = addedLifts.map { it.toDto() },
    supersetGroupIdOverrides = supersetGroupIdOverrides.entries.map { (workoutLiftId, groupId) ->
        SupersetGroupOverrideEntryDto(workoutLiftId.value, groupId?.value)
    },
    useRestTimerBetweenLiftsOverrides = useRestTimerBetweenLiftsOverrides.entries.map { (workoutLiftId, enabled) ->
        RestTimerOverrideEntryDto(workoutLiftId.value, enabled)
    },
    restSecondsOverrides = restSecondsOverrides.entries.map { (slotId, restSeconds) ->
        RestSecondsOverrideEntryDto(slotId.toDto(), restSeconds)
    },
    slotShapeOverrides = slotShapeOverrides.entries.map { (slotId, shape) ->
        SlotShapeOverrideEntryDto(slotId.toDto(), shape.toDto())
    },
    orderedLiftIds = orderedLiftIds,
    workoutLiftPinnedNotes = workoutLiftPinnedNotes.entries.map { (workoutLiftId, body) ->
        WorkoutLiftPinnedNoteEntryDto(workoutLiftId.value, body)
    },
    workoutLiftPinnedNoteLists = workoutLiftPinnedNoteLists.entries.map { (workoutLiftId, bodies) ->
        WorkoutLiftPinnedNoteListEntryDto(workoutLiftId.value, bodies)
    },
)

/** `DTO → WorkoutOverlay`. Blank-id entries are skipped. */
fun WorkoutOverlayDto.toDomain(): WorkoutOverlay = WorkoutOverlay(
    loggedSets = loggedSets.toLoggedSetMap(),
    recommendations = recommendations.toRecommendationMap(),
    addedSlots = addedSlots.mapNotNull { it.toDomain() },
    deletedSlotIds = deletedSlotIds.mapNotNull { it.toDomain() }.toSet(),
    undoStack = undoStack.mapNotNull { it.toDomain() },
    dynamicBaselines = dynamicBaselines.toBaselineMap(),
    swappedLifts = swappedLifts
        .mapNotNull { entry -> entry.workoutLiftId.toWorkoutLiftIdOrNull()?.let { it to entry } }
        .mapNotNull { (key, entry) -> entry.liftId.toLiftIdOrNull()?.let { key to it } }
        .toMap(),
    deletedLiftIds = deletedLiftIds.mapNotNull { it.toWorkoutLiftIdOrNull() }.toSet(),
    addedLifts = addedLifts.mapNotNull { it.toDomain() },
    supersetGroupIdOverrides = buildMap {
        for (entry in supersetGroupIdOverrides) {
            val key = entry.workoutLiftId.toWorkoutLiftIdOrNull() ?: continue
            put(key, entry.groupId?.toSupersetGroupIdOrNull())
        }
    },
    useRestTimerBetweenLiftsOverrides = buildMap {
        for (entry in useRestTimerBetweenLiftsOverrides) {
            val key = entry.workoutLiftId.toWorkoutLiftIdOrNull() ?: continue
            put(key, entry.enabled)
        }
    },
    restSecondsOverrides = buildMap {
        for (entry in restSecondsOverrides) {
            val key = entry.slotId.toDomain() ?: continue
            put(key, entry.restSeconds)
        }
    },
    slotShapeOverrides = buildMap {
        for (entry in slotShapeOverrides) {
            val key = entry.slotId.toDomain() ?: continue
            put(key, entry.shape.toDomain())
        }
    },
    orderedLiftIds = orderedLiftIds,
    workoutLiftPinnedNotes = buildMap {
        for (entry in workoutLiftPinnedNotes) {
            val key = entry.workoutLiftId.toWorkoutLiftIdOrNull()
            if (key == null || entry.body.isBlank()) continue
            put(key, entry.body)
        }
    },
    workoutLiftPinnedNoteLists = workoutLiftPinnedNoteLists
        .mapNotNull { entry -> entry.toNoteListPairOrNull() }
        .toMap(),
)

private fun List<LoggedSetEntryDto>.toLoggedSetMap(): Map<SetSlotId, LoggedSetValue> = buildMap {
    for (entry in this@toLoggedSetMap) {
        val key = entry.slotId.toDomain() ?: continue
        put(key, entry.value.toDomain(key))
    }
}

private fun List<LiveSetRecommendationEntryDto>.toRecommendationMap(): Map<SetSlotId, LiveSetRecommendation> =
    mapNotNull { entry -> entry.toDomainPairOrNull() }.toMap()

private fun WorkoutLiftPinnedNoteListEntryDto.toNoteListPairOrNull(): Pair<WorkoutLiftId, List<String>>? {
    val key = workoutLiftId.toWorkoutLiftIdOrNull() ?: return null
    val bodies = bodies.filter { it.isNotBlank() }
    if (bodies.isEmpty()) return null
    return key to bodies
}

private fun LiveSetRecommendationEntryDto.toDomainPairOrNull(): Pair<SetSlotId, LiveSetRecommendation>? {
    val key = slotId.toDomain() ?: return null
    val weight = weight.toDomainOrNull() ?: return null
    if (reps <= 0) return null
    return key to LiveSetRecommendation(weight = weight, reps = reps)
}

private fun List<DynamicBaselineEntryDto>.toBaselineMap(): Map<SetSlotId, SlotRecommendation> = buildMap {
    for (entry in this@toBaselineMap) {
        val key = entry.slotId.toDomain() ?: continue
        put(key, entry.value.toDomain(key))
    }
}

// ---- Leaf type mappers -----------------------------------------------------

private fun Weight.toDto(): WeightDto = WeightDto(amount, WeightDto.WeightUnitDto.valueOf(unit.name))

private fun WeightDto.toDomainOrNull(): Weight? =
    runCatching { Weight(amount, Weight.WeightUnit.valueOf(unit.name)) }.getOrNull()

private fun SetSlotId.toDto(): SetSlotIdDto = when (this) {
    is SetSlotId.Template -> SetSlotIdDto.Template(liftSetId.value)
    is SetSlotId.Overlay -> SetSlotIdDto.Overlay(instanceId.value)
}

private fun SetSlotIdDto.toDomain(): SetSlotId? = when (this) {
    is SetSlotIdDto.Template -> liftSetId.toLiftSetIdOrNull()?.let { SetSlotId.Template(it) }
    is SetSlotIdDto.Overlay -> instanceId.toOverlayInstanceIdOrNull()?.let { SetSlotId.Overlay(it) }
}

private fun SetSlot.toDto(): SetSlotDto = SetSlotDto(
    slotId = slotId.toDto(),
    workoutLiftId = workoutLiftId.value,
    liftId = liftId.value,
    position = position,
    setType = setType,
    isTopSet = isTopSet,
    repMin = repMin,
    repMax = repMax,
    rirGoal = rirGoal,
    restSeconds = restSeconds,
    myoRepKind = myoRepKind,
)

private fun SetSlotDto.toDomain(): SetSlot? {
    val slotId = slotId.toDomain() ?: return null
    val workoutLiftId = workoutLiftId.toWorkoutLiftIdOrNull() ?: return null
    val liftId = liftId.toLiftIdOrNull() ?: return null
    return SetSlot(
        slotId = slotId,
        workoutLiftId = workoutLiftId,
        liftId = liftId,
        position = position,
        setType = setType,
        isTopSet = isTopSet,
        repMin = repMin,
        repMax = repMax,
        rirGoal = rirGoal,
        restSeconds = restSeconds,
        myoRepKind = myoRepKind,
    )
}

private fun LoggedSetValue.toDto(): LoggedSetValueDto = LoggedSetValueDto(
    slotId = slotId.toDto(),
    weight = weight.toDto(),
    reps = reps,
    rir = rir,
    partialReps = partialReps,
    persistedLoggedSetId = persistedLoggedSetId,
)

private fun LoggedSetValueDto.toDomain(slotId: SetSlotId): LoggedSetValue {
    val weight = weight.toDomainOrNull() ?: Weight(0.0, Weight.WeightUnit.Lb)
    return LoggedSetValue(
        slotId = slotId,
        weight = weight,
        reps = reps,
        rir = rir,
        partialReps = partialReps,
        persistedLoggedSetId = persistedLoggedSetId,
    )
}

private fun SlotRecommendation.toDto(): SlotRecommendationDto = SlotRecommendationDto(
    slotId = slotId.toDto(),
    recommendedWeight = recommendedWeight.toDto(),
    baselineExpectedReps = baselineExpectedReps,
    previousWeight = previousWeight?.toDto(),
    previousReps = previousReps,
    previousRir = previousRir,
    previousPartialReps = previousPartialReps,
)

private fun SlotRecommendationDto.toDomain(slotId: SetSlotId): SlotRecommendation {
    val weight = recommendedWeight.toDomainOrNull() ?: Weight(0.0, Weight.WeightUnit.Lb)
    val prev = previousWeight?.toDomainOrNull()
    return SlotRecommendation(
        slotId = slotId,
        recommendedWeight = weight,
        baselineExpectedReps = baselineExpectedReps,
        previousWeight = prev,
        previousReps = previousReps,
        previousRir = previousRir,
        previousPartialReps = previousPartialReps,
    )
}

private fun SlotShapeOverride.toDto(): SlotShapeOverrideDto = SlotShapeOverrideDto(
    setType = setType,
    myoRepKind = myoRepKind,
    isTopSet = isTopSet,
    repMin = repMin,
    repMax = repMax,
    rirGoal = rirGoal,
)

private fun SlotShapeOverrideDto.toDomain(): SlotShapeOverride = SlotShapeOverride(
    setType = setType,
    myoRepKind = myoRepKind,
    isTopSet = isTopSet,
    repMin = repMin,
    repMax = repMax,
    rirGoal = rirGoal,
)

private fun OverlayAddedLift.toDto(): OverlayAddedLiftDto = OverlayAddedLiftDto(
    workoutLiftId = workoutLiftId.value,
    liftId = liftId.value,
    progressionScheme = progressionScheme,
    supersetGroupId = supersetGroupId?.value,
    useRestTimerBetweenLifts = useRestTimerBetweenLifts,
)

private fun OverlayAddedLiftDto.toDomain(): OverlayAddedLift? {
    val workoutLiftId = workoutLiftId.toWorkoutLiftIdOrNull() ?: return null
    val liftId = liftId.toLiftIdOrNull() ?: return null
    return OverlayAddedLift(
        workoutLiftId = workoutLiftId,
        liftId = liftId,
        progressionScheme = progressionScheme,
        supersetGroupId = supersetGroupId?.toSupersetGroupIdOrNull(),
        useRestTimerBetweenLifts = useRestTimerBetweenLifts,
    )
}

private fun StructuralMutation.toDto(): StructuralMutationDto = when (this) {
    is StructuralMutation.AddSet -> StructuralMutationDto.AddSet(afterSlotId.toDto(), newSlot.toDto())
    is StructuralMutation.DeleteSet -> StructuralMutationDto.DeleteSet(slotId.toDto())
    is StructuralMutation.GenerateMyoRepMiniSet ->
        StructuralMutationDto.GenerateMyoRepMiniSet(activationSlotId.toDto(), newSlot.toDto())
    is StructuralMutation.AddDropSet -> StructuralMutationDto.AddDropSet(afterSlotId.toDto(), newSlot.toDto())
}

private fun StructuralMutationDto.toDomain(): StructuralMutation? = when (this) {
    is StructuralMutationDto.AddSet -> {
        val after = afterSlotId.toDomain() ?: return null
        val slot = newSlot.toDomain() ?: return null
        StructuralMutation.AddSet(after, slot)
    }
    is StructuralMutationDto.DeleteSet -> {
        val slot = slotId.toDomain() ?: return null
        StructuralMutation.DeleteSet(slot)
    }
    is StructuralMutationDto.GenerateMyoRepMiniSet -> {
        val activation = activationSlotId.toDomain() ?: return null
        val slot = newSlot.toDomain() ?: return null
        StructuralMutation.GenerateMyoRepMiniSet(activation, slot)
    }
    is StructuralMutationDto.AddDropSet -> {
        val after = afterSlotId.toDomain() ?: return null
        val slot = newSlot.toDomain() ?: return null
        StructuralMutation.AddDropSet(after, slot)
    }
}

// ---- String → value class (defensive; null on blank so callers can `mapNotNull`) ----

private fun String.toLiftIdOrNull(): LiftId? = takeIf { isNotBlank() }?.let { LiftId(it) }
private fun String.toWorkoutLiftIdOrNull(): WorkoutLiftId? = takeIf { isNotBlank() }?.let { WorkoutLiftId(it) }
private fun String.toLiftSetIdOrNull(): LiftSetId? = takeIf { isNotBlank() }?.let { LiftSetId(it) }
private fun String.toSupersetGroupIdOrNull(): WorkoutSupersetGroupId? =
    takeIf { isNotBlank() }?.let { WorkoutSupersetGroupId(it) }

private fun String.toOverlayInstanceIdOrNull(): OverlaySetInstanceId? =
    takeIf { isNotBlank() }?.let { OverlaySetInstanceId(it) }
