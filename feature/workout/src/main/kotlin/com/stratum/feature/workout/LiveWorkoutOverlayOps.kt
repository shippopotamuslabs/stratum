package com.stratum.feature.workout

import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.isDoubleSidedPlateLoad
import com.stratum.domain.lift.isPlateLoaded
import com.stratum.domain.lift.resolvedDefaultRestSeconds
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutSupersetGroupId
import com.stratum.domain.workout.AddedLiftDefaults
import com.stratum.domain.workout.LoggedSetValue
import com.stratum.domain.workout.MergeRenderedWorkoutUseCase
import com.stratum.domain.workout.OverlayAddedLift
import com.stratum.domain.workout.OverlaySetInstanceId
import com.stratum.domain.workout.ProgressionSnapshot
import com.stratum.domain.workout.RenderedWorkout
import com.stratum.domain.workout.SetSlot
import com.stratum.domain.workout.SetSlotId
import com.stratum.domain.workout.SlotRecommendation
import com.stratum.domain.workout.WorkoutLiftProjection
import com.stratum.domain.workout.WorkoutOverlay
import com.stratum.domain.workout.WorkoutStructureProjection
import com.stratum.domain.workout.applyRestOverride
import com.stratum.domain.workout.applyShapeOverride
import com.stratum.domain.workout.resolvedLiftPinnedNote
import com.stratum.domain.workout.resolvedWorkoutPinnedNote
import java.util.UUID

/**
 * Pure overlay structure helpers for the live session ViewModel — keeps [WorkoutViewModel]
 * under Detekt LargeClass / TooManyFunctions thresholds.
 */
internal object LiveWorkoutOverlayOps {

    fun addLifts(
        overlay: WorkoutOverlay,
        currentLiftIds: List<String>,
        liftIds: List<LiftId>,
        asSuperset: Boolean,
        liftsById: Map<LiftId, Lift>,
        zeroWeight: Weight,
    ): WorkoutOverlay {
        if (liftIds.isEmpty()) return overlay
        val groupId =
            if (asSuperset && liftIds.size >= 2) {
                WorkoutSupersetGroupId(UUID.randomUUID().toString())
            } else {
                null
            }
        val newLifts = mutableListOf<OverlayAddedLift>()
        val newSlots = mutableListOf<SetSlot>()
        val newBaselines = mutableMapOf<SetSlotId, SlotRecommendation>()
        val newOrderIds = mutableListOf<String>()

        for (liftId in liftIds) {
            val catalog = liftsById[liftId]
            val restSeconds = catalog?.resolvedDefaultRestSeconds() ?: AddedLiftDefaults.REST_SECONDS
            val workoutLiftId = WorkoutLiftId(UUID.randomUUID().toString())
            newLifts += OverlayAddedLift(
                workoutLiftId = workoutLiftId,
                liftId = liftId,
                progressionScheme = ProgressionScheme.Double,
                supersetGroupId = groupId,
                useRestTimerBetweenLifts = false,
            )
            val slotId = SetSlotId.Overlay(
                OverlaySetInstanceId("added-lift-${workoutLiftId.value}-0"),
            )
            val slot = SetSlot(
                slotId = slotId,
                workoutLiftId = workoutLiftId,
                liftId = liftId,
                position = 0,
                setType = SetType.Standard,
                isTopSet = true,
                repMin = AddedLiftDefaults.REP_MIN,
                repMax = AddedLiftDefaults.REP_MAX,
                rirGoal = AddedLiftDefaults.RIR,
                restSeconds = restSeconds,
            )
            newSlots += slot
            newBaselines[slotId] = SlotRecommendation(
                slotId = slotId,
                recommendedWeight = zeroWeight,
                baselineExpectedReps = AddedLiftDefaults.REP_MIN,
            )
            newOrderIds += workoutLiftId.value
        }

        val nextOrdered = when (val existing = overlay.orderedLiftIds) {
            null -> currentLiftIds + newOrderIds
            else -> existing + newOrderIds
        }

        return overlay.copy(
            addedLifts = overlay.addedLifts + newLifts,
            addedSlots = overlay.addedSlots + newSlots,
            dynamicBaselines = overlay.dynamicBaselines + newBaselines,
            orderedLiftIds = nextOrdered,
        )
    }

    /**
     * Effective (merged) superset fields for one lift — used only as prior/updated snapshots when
     * computing [differentialSupersetOverrides].
     */
    data class LinkableSupersetFields(
        val workoutLiftId: String,
        val supersetGroupId: String?,
        val useRestTimerBetweenLifts: Boolean,
    )

    /**
     * Writes only keys whose effective group/rest changed between [prior] and [updated].
     * Explicit `null` group values are intentional clears (merge uses [Map.containsKey], not
     * Elvis, so key-present-null ≠ key-absent).
     */
    fun differentialSupersetOverrides(
        prior: List<LinkableSupersetFields>,
        updated: List<LinkableSupersetFields>,
        existingGroupOverrides: Map<WorkoutLiftId, WorkoutSupersetGroupId?>,
        existingRestOverrides: Map<WorkoutLiftId, Boolean>,
    ): Pair<Map<WorkoutLiftId, WorkoutSupersetGroupId?>, Map<WorkoutLiftId, Boolean>> {
        val priorById = prior.associateBy { it.workoutLiftId }
        val newGroups = existingGroupOverrides.toMutableMap()
        val newRest = existingRestOverrides.toMutableMap()
        // Drop lifts no longer present (removed).
        val updatedIds = updated.map { it.workoutLiftId }.toSet()
        newGroups.keys.filter { it.value !in updatedIds }.forEach { newGroups.remove(it) }
        newRest.keys.filter { it.value !in updatedIds }.forEach { newRest.remove(it) }
        updated.forEach { lift ->
            val id = WorkoutLiftId(lift.workoutLiftId)
            val before = priorById[lift.workoutLiftId]
            if (lift.supersetGroupId != before?.supersetGroupId) {
                newGroups[id] = lift.supersetGroupId?.let { WorkoutSupersetGroupId(it) }
            }
            if (lift.useRestTimerBetweenLifts != before?.useRestTimerBetweenLifts) {
                newRest[id] = lift.useRestTimerBetweenLifts
            }
        }
        return newGroups to newRest
    }

    /**
     * Stamps [groupOverrides]/[restOverrides] onto session-added lifts so dual-source state
     * (`OverlayAddedLift.supersetGroupId` vs override map) stays consistent. Only keys present
     * in the maps are applied (including explicit null group clears).
     */
    fun applyGroupOverridesToAddedLifts(
        addedLifts: List<OverlayAddedLift>,
        groupOverrides: Map<WorkoutLiftId, WorkoutSupersetGroupId?>,
        restOverrides: Map<WorkoutLiftId, Boolean>,
    ): List<OverlayAddedLift> {
        if (addedLifts.isEmpty()) return addedLifts
        if (groupOverrides.isEmpty() && restOverrides.isEmpty()) return addedLifts
        return addedLifts.map { added ->
            val groupChanged = groupOverrides.containsKey(added.workoutLiftId)
            val restChanged = restOverrides.containsKey(added.workoutLiftId)
            if (!groupChanged && !restChanged) {
                added
            } else {
                added.copy(
                    supersetGroupId =
                        if (groupChanged) {
                            groupOverrides[added.workoutLiftId]
                        } else {
                            added.supersetGroupId
                        },
                    useRestTimerBetweenLifts =
                        if (restChanged) {
                            restOverrides.getValue(added.workoutLiftId)
                        } else {
                            added.useRestTimerBetweenLifts
                        },
                )
            }
        }
    }

    fun removeLift(
        overlay: WorkoutOverlay,
        workoutLiftId: WorkoutLiftId,
        cleanedGroupOverrides: Map<WorkoutLiftId, WorkoutSupersetGroupId?>,
        cleanedRestOverrides: Map<WorkoutLiftId, Boolean>,
        resolveSlot: (SetSlotId) -> SetSlot?,
        /**
         * Baseline template lift ids in template order, excluding already-deleted template lifts
         * (and [workoutLiftId] when it is a baseline lift being removed). Used to drop
         * [WorkoutOverlay.orderedLiftIds] when it no longer encodes a real reorder.
         */
        baselineLiftIdsInOrder: List<String>,
    ): WorkoutOverlay {
        val isSessionAdded = overlay.addedLifts.any { it.workoutLiftId == workoutLiftId }
        val slotsToDrop = overlay.addedSlots
            .filter { it.workoutLiftId == workoutLiftId }
            .map { it.slotId }
            .toSet()
        // Also drop any deleted-slot markers for those overlay instances (fossils after remove).
        val cleanedDeletedSlotIds = overlay.deletedSlotIds - slotsToDrop
        // Clear dual-source group/rest on remaining session-added lifts (not only override map).
        val remainingAdded =
            applyGroupOverridesToAddedLifts(
                addedLifts = overlay.addedLifts.filterNot { it.workoutLiftId == workoutLiftId },
                groupOverrides = cleanedGroupOverrides,
                restOverrides = cleanedRestOverrides,
            )
        val nextOrderedRaw = overlay.orderedLiftIds?.filterNot { it == workoutLiftId.value }
        val naturalOrder =
            baselineLiftIdsInOrder + remainingAdded.map { it.workoutLiftId.value }
        // Drop orderedLiftIds when empty or when it matches the natural (unordered) sequence —
        // otherwise isDirty stays true after add→remove of a session lift.
        val nextOrdered =
            nextOrderedRaw
                ?.takeUnless { it.isEmpty() }
                ?.takeUnless { ordered ->
                    ordered == naturalOrder || ordered == baselineLiftIdsInOrder
                }

        // Drop rest overrides for slots belonging to the removed lift (template + overlay).
        val restOverrideKeysToDrop =
            overlay.restSecondsOverrides.keys.filter { key ->
                key in slotsToDrop || resolveSlot(key)?.workoutLiftId == workoutLiftId
            }.toSet()

        return if (isSessionAdded) {
            overlay.copy(
                addedLifts = remainingAdded,
                addedSlots = overlay.addedSlots.filterNot { it.workoutLiftId == workoutLiftId },
                deletedSlotIds = cleanedDeletedSlotIds,
                dynamicBaselines = overlay.dynamicBaselines.filterKeys { it !in slotsToDrop },
                loggedSets = overlay.loggedSets.filterKeys { it !in slotsToDrop },
                recommendations = overlay.recommendations.filterKeys { it !in slotsToDrop },
                restSecondsOverrides = overlay.restSecondsOverrides - restOverrideKeysToDrop,
                supersetGroupIdOverrides = cleanedGroupOverrides,
                useRestTimerBetweenLiftsOverrides = cleanedRestOverrides,
                orderedLiftIds = nextOrdered,
            )
        } else {
            overlay.copy(
                deletedLiftIds = overlay.deletedLiftIds + workoutLiftId,
                addedLifts = remainingAdded,
                addedSlots = overlay.addedSlots.filterNot { it.workoutLiftId == workoutLiftId },
                deletedSlotIds = cleanedDeletedSlotIds,
                dynamicBaselines = overlay.dynamicBaselines.filterKeys { it !in slotsToDrop },
                loggedSets = overlay.loggedSets.filterKeys { key ->
                    resolveSlot(key)?.workoutLiftId != workoutLiftId
                },
                recommendations = overlay.recommendations.filterKeys { key ->
                    resolveSlot(key)?.workoutLiftId != workoutLiftId
                },
                restSecondsOverrides = overlay.restSecondsOverrides - restOverrideKeysToDrop,
                supersetGroupIdOverrides = cleanedGroupOverrides,
                useRestTimerBetweenLiftsOverrides = cleanedRestOverrides,
                orderedLiftIds = nextOrdered,
            )
        }
    }

    fun visibleSlotsForLift(
        structure: WorkoutStructureProjection?,
        overlay: WorkoutOverlay,
        workoutLiftId: WorkoutLiftId,
    ): List<SetSlot> {
        val templateSlots = structure
            ?.lifts
            ?.firstOrNull { it.workoutLiftId == workoutLiftId }
            ?.slots
            .orEmpty()
            .filterNot { it.slotId in overlay.deletedSlotIds }
            // Shape overrides (e.g. retyped Myo) must be visible before mini re-homing.
            .map { applyShapeOverrideToSlot(it, overlay) }
        val added = overlay.addedSlots
            .filter { it.workoutLiftId == workoutLiftId }
            .filterNot { it.slotId in overlay.deletedSlotIds }
            .map { applyShapeOverrideToSlot(it, overlay) }
        // Minis insert at activationIndex + 1; renumber positions.
        return LiveIntensifierOverlayOps.orderLiftSlots(templateSlots, added)
            .map { applyOverlaySlotFields(it, overlay) }
    }

    fun slotKey(slotId: SetSlotId): String = when (slotId) {
        is SetSlotId.Template -> slotId.liftSetId.value
        is SetSlotId.Overlay -> slotId.instanceId.value
    }

    /**
     * Room rows that need [com.stratum.domain.training.LoggedSet.setIndex] updated so they
     * match the current visible order after minis / deletes renumber slots.
     *
     * Matching is by [LoggedSetValue.persistedLoggedSetId] (stable), never by stale setIndex.
     * Returns updated [LoggedSet] copies ready for [UpdateLoggedSetUseCase] / bulk update.
     */
    fun roomSetIndexUpdates(
        orderedSlots: List<SetSlot>,
        loggedSets: Map<SetSlotId, LoggedSetValue>,
        dbSets: List<com.stratum.domain.training.LoggedSet>,
    ): List<com.stratum.domain.training.LoggedSet> {
        if (orderedSlots.isEmpty() || loggedSets.isEmpty() || dbSets.isEmpty()) return emptyList()
        val dbById = dbSets.associateBy { it.id.value }
        return orderedSlots.mapNotNull { slot ->
            val persistedId = loggedSets[slot.slotId]?.persistedLoggedSetId ?: return@mapNotNull null
            val db = dbById[persistedId] ?: return@mapNotNull null
            if (db.setIndex == slot.position) null else db.copy(setIndex = slot.position)
        }
    }

    /**
     * Resolves [slotId] with **renumbered visible position** (template + overlay order).
     * Room [LoggedSet.setIndex] must use this position so minis between match backoffs
     * do not collide with later template indices.
     */
    fun resolveSlot(
        structure: WorkoutStructureProjection?,
        overlay: WorkoutOverlay,
        slotId: SetSlotId,
    ): SetSlot? {
        val raw = rawSlot(structure, overlay, slotId) ?: return null
        return visibleSlotsForLift(structure, overlay, raw.workoutLiftId)
            .firstOrNull { it.slotId == slotId }
            ?: applyOverlaySlotFields(raw, overlay)
    }

    fun resolveSlotByKey(
        structure: WorkoutStructureProjection?,
        overlay: WorkoutOverlay,
        slotKey: String,
    ): SetSlot? {
        val raw = rawSlotByKey(structure, overlay, slotKey) ?: return null
        return visibleSlotsForLift(structure, overlay, raw.workoutLiftId)
            .firstOrNull { slotKey(it.slotId) == slotKey }
            ?: applyOverlaySlotFields(raw, overlay)
    }

    /** Identity lookup without renumbering (shape/rest/swap only). */
    fun rawSlot(
        structure: WorkoutStructureProjection?,
        overlay: WorkoutOverlay,
        slotId: SetSlotId,
    ): SetSlot? {
        val struct = structure ?: return null
        val slot = struct.lifts.flatMap { it.slots }.firstOrNull { it.slotId == slotId }
            ?: overlay.addedSlots.firstOrNull { it.slotId == slotId }
            ?: return null
        return applyOverlaySlotFields(slot, overlay)
    }

    fun rawSlotByKey(
        structure: WorkoutStructureProjection?,
        overlay: WorkoutOverlay,
        slotKey: String,
    ): SetSlot? {
        val struct = structure ?: return null
        val slot = struct.lifts.flatMap { it.slots }.firstOrNull { slotKey(it.slotId) == slotKey }
            ?: overlay.addedSlots.firstOrNull { slotKey(it.slotId) == slotKey }
            ?: return null
        return applyOverlaySlotFields(slot, overlay)
    }

    /**
     * In-session lift swaps live only on [WorkoutOverlay.swappedLifts]; template
     * [SetSlot.liftId] values stay original until template save. Log/unlog/add-set
     * paths resolve slots outside of [merge], so they must re-apply the swap here —
     * otherwise [LoggedSet.liftId] is attributed to the pre-swap (template) lift.
     */
    internal fun applySwapToSlot(slot: SetSlot, overlay: WorkoutOverlay): SetSlot {
        val swappedLiftId = overlay.swappedLifts[slot.workoutLiftId] ?: return slot
        return if (slot.liftId == swappedLiftId) slot else slot.copy(liftId = swappedLiftId)
    }

    /** Applies [WorkoutOverlay.restSecondsOverrides] onto a resolved slot. */
    internal fun applyRestOverrideToSlot(slot: SetSlot, overlay: WorkoutOverlay): SetSlot =
        slot.applyRestOverride(overlay)

    /** Applies [WorkoutOverlay.slotShapeOverrides] onto a resolved slot. */
    internal fun applyShapeOverrideToSlot(slot: SetSlot, overlay: WorkoutOverlay): SetSlot =
        slot.applyShapeOverride(overlay)

    private fun applyOverlaySlotFields(slot: SetSlot, overlay: WorkoutOverlay): SetSlot =
        applyRestOverrideToSlot(
            applyShapeOverrideToSlot(applySwapToSlot(slot, overlay), overlay),
            overlay,
        )

    /**
     * Merges presentation slotKey → restSeconds updates into the overlay override map.
     * Returns `null` when no resolvable slots changed.
     */
    fun mergeRestSecondsOverrides(
        overlay: WorkoutOverlay,
        updates: Map<String, Int>,
        resolveSlotByKey: (String) -> SetSlot?,
    ): Map<SetSlotId, Int>? {
        if (updates.isEmpty()) return null
        val next = overlay.restSecondsOverrides.toMutableMap()
        var changed = false
        for ((slotKey, restSeconds) in updates) {
            val slot = resolveSlotByKey(slotKey) ?: continue
            next[slot.slotId] = restSeconds.coerceAtLeast(0)
            changed = true
        }
        return if (changed) next else null
    }

    fun merge(
        structure: WorkoutStructureProjection?,
        overlay: WorkoutOverlay,
        snapshotBySlot: Map<SetSlotId, SlotRecommendation>,
        liftsById: Map<LiftId, Lift>,
        liftNamesById: Map<LiftId, String>,
        mergeRenderedWorkout: MergeRenderedWorkoutUseCase,
    ): RenderedWorkout {
        val base = structure ?: return RenderedWorkout(
            com.stratum.domain.program.identity.WorkoutId(""),
            "",
            emptyList(),
        )
        val filtered = base.lifts.filterNot { it.workoutLiftId in overlay.deletedLiftIds }
        val mappedLifts = filtered.map { lift ->
            val swappedLiftId = overlay.swappedLifts[lift.workoutLiftId]

            var currentLiftId = lift.liftId
            var currentLiftName = lift.liftName
            var currentLoadType = lift.loadType
            var currentIsPlateLoaded = lift.isPlateLoaded
            var currentIsDoubleSided = lift.isDoubleSided
            if (swappedLiftId != null) {
                currentLiftId = swappedLiftId
                currentLiftName = liftNamesById[swappedLiftId].orEmpty()
                val catalog = liftsById[swappedLiftId]
                currentLoadType = catalog?.loadType ?: LoadType.Weighted
                currentIsPlateLoaded = catalog?.isPlateLoaded ?: false
                currentIsDoubleSided = catalog?.isDoubleSidedPlateLoad ?: true
            }

            val currentSupersetGroupId = if (overlay.supersetGroupIdOverrides.containsKey(lift.workoutLiftId)) {
                overlay.supersetGroupIdOverrides[lift.workoutLiftId]
            } else {
                lift.supersetGroupId
            }

            val templateSlots = lift.slots
                .filterNot { it.slotId in overlay.deletedSlotIds }
                .map { applyShapeOverrideToSlot(it, overlay) }
            val added = overlay.addedSlots
                .filter { it.workoutLiftId == lift.workoutLiftId }
                .filterNot { it.slotId in overlay.deletedSlotIds }
                .map { applyShapeOverrideToSlot(it, overlay) }
            val orderedSlots = LiveIntensifierOverlayOps.orderLiftSlots(templateSlots, added)
                .map { applyOverlaySlotFields(it, overlay) }

            lift.copy(
                liftId = currentLiftId,
                liftName = currentLiftName,
                loadType = currentLoadType,
                isPlateLoaded = currentIsPlateLoaded,
                isDoubleSided = currentIsDoubleSided,
                supersetGroupId = currentSupersetGroupId,
                slots = orderedSlots,
                useRestTimerBetweenLifts = overlay.useRestTimerBetweenLiftsOverrides[lift.workoutLiftId]
                    ?: lift.useRestTimerBetweenLifts,
                workoutPinnedNote = resolvedWorkoutPinnedNote(
                    overlay.workoutLiftPinnedNotes,
                    lift.workoutLiftId,
                    lift.workoutPinnedNote,
                ),
                liftPinnedNote = resolvedLiftPinnedNote(
                    liftsById[currentLiftId],
                    lift.liftPinnedNote,
                ),
            )
        }

        val sessionAdded = overlay.addedLifts
            .filterNot { it.workoutLiftId in overlay.deletedLiftIds }
            .map { added ->
                // Match template-lift path: in-session swaps rebind catalog id / name / load type.
                val effectiveLiftId = overlay.swappedLifts[added.workoutLiftId] ?: added.liftId
                val catalog = liftsById[effectiveLiftId]
                val groupId = if (overlay.supersetGroupIdOverrides.containsKey(added.workoutLiftId)) {
                    overlay.supersetGroupIdOverrides[added.workoutLiftId]
                } else {
                    added.supersetGroupId
                }
                val slots = overlay.addedSlots
                    .filter { it.workoutLiftId == added.workoutLiftId }
                    .sortedBy { it.position }
                    .map { applyOverlaySlotFields(it, overlay) }
                WorkoutLiftProjection(
                    workoutLiftId = added.workoutLiftId,
                    liftId = effectiveLiftId,
                    liftName = catalog?.name ?: liftNamesById[effectiveLiftId].orEmpty(),
                    progressionScheme = added.progressionScheme,
                    slots = slots,
                    supersetGroupId = groupId,
                    loadType = catalog?.loadType ?: LoadType.Weighted,
                    isPlateLoaded = catalog?.isPlateLoaded ?: false,
                    isDoubleSided = catalog?.isDoubleSidedPlateLoad ?: true,
                    useRestTimerBetweenLifts = overlay.useRestTimerBetweenLiftsOverrides[added.workoutLiftId]
                        ?: added.useRestTimerBetweenLifts,
                    workoutPinnedNote = overlay.workoutLiftPinnedNotes[added.workoutLiftId],
                    liftPinnedNote = resolvedLiftPinnedNote(catalog, projectedNote = null),
                )
            }

        val withAdded = base.copy(lifts = mappedLifts + sessionAdded)
        val snapshot = ProgressionSnapshot(withAdded.workoutId, snapshotBySlot)
        return mergeRenderedWorkout(withAdded, snapshot, overlay)
    }

    /**
     * Rebuilds a minimal session-added overlay from durable [LoggedSet] rows when the
     * DataStore overlay is missing (process death after a failed/partial persist).
     *
     * Only lifts with a stamped [LoggedLiftContext.workoutLiftId] and catalog [LoggedSet.liftId]
     * are reconstructed. Returns null when nothing can be rebuilt.
     */
    fun rebuildFromSessionLoggedSets(
        dbSets: List<com.stratum.domain.training.LoggedSet>,
        zeroWeight: Weight,
    ): WorkoutOverlay? {
        val eligible = dbSets.filter { set ->
            set.liftContext.workoutLiftId != null && set.liftId != null
        }
        if (eligible.isEmpty()) return null

        val byWorkoutLift = eligible.groupBy { it.liftContext.workoutLiftId!! }
        val orderedGroups =
            byWorkoutLift.entries.sortedBy { (_, sets) -> sets.minOf { it.completedAt } }

        val addedLifts = mutableListOf<OverlayAddedLift>()
        val addedSlots = mutableListOf<SetSlot>()
        val dynamicBaselines = mutableMapOf<SetSlotId, SlotRecommendation>()
        val loggedSets = mutableMapOf<SetSlotId, LoggedSetValue>()
        val orderedIds = mutableListOf<String>()

        for ((workoutLiftId, sets) in orderedGroups) {
            val orderedSets = sets.sortedBy { it.setIndex }
            val first = orderedSets.first()
            val liftId = first.liftId!!
            val scheme = first.liftContext.progressionScheme ?: ProgressionScheme.Double
            addedLifts += OverlayAddedLift(
                workoutLiftId = workoutLiftId,
                liftId = liftId,
                progressionScheme = scheme,
                supersetGroupId = null,
                useRestTimerBetweenLifts = false,
            )
            orderedIds += workoutLiftId.value

            val positions = orderedSets.map { it.setIndex }.toSet()
            val maxIndex = positions.maxOrNull() ?: 0
            for (position in 0..maxIndex) {
                val logged = orderedSets.firstOrNull { it.setIndex == position }
                val repMin = logged?.liftContext?.repMin ?: AddedLiftDefaults.REP_MIN
                val repMax = logged?.liftContext?.repMax ?: AddedLiftDefaults.REP_MAX
                val rirGoal = logged?.liftContext?.rirGoal ?: AddedLiftDefaults.RIR
                val setType = logged?.liftContext?.setType ?: SetType.Standard
                val slotId = SetSlotId.Overlay(
                    OverlaySetInstanceId("rebuilt-${workoutLiftId.value}-$position"),
                )
                val slot = SetSlot(
                    slotId = slotId,
                    workoutLiftId = workoutLiftId,
                    liftId = liftId,
                    position = position,
                    setType = setType,
                    isTopSet = position == 0,
                    repMin = repMin,
                    repMax = repMax,
                    rirGoal = rirGoal,
                    restSeconds = AddedLiftDefaults.REST_SECONDS,
                )
                addedSlots += slot
                dynamicBaselines[slotId] = SlotRecommendation(
                    slotId = slotId,
                    recommendedWeight = zeroWeight,
                    baselineExpectedReps = repMin,
                )
                if (logged != null) {
                    loggedSets[slotId] = LoggedSetValue(
                        slotId = slotId,
                        weight = logged.weight,
                        reps = logged.reps,
                        rir = logged.rir,
                        persistedLoggedSetId = logged.id.value,
                    )
                }
            }
        }

        return WorkoutOverlay(
            addedLifts = addedLifts,
            addedSlots = addedSlots,
            dynamicBaselines = dynamicBaselines,
            loggedSets = loggedSets,
            orderedLiftIds = orderedIds,
        )
    }
}
