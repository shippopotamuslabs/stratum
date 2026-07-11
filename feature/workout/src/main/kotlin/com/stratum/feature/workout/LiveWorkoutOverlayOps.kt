package com.stratum.feature.workout

import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.resolvedDefaultRestSeconds
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutSupersetGroupId
import com.stratum.domain.workout.AddedLiftDefaults
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
        val remainingAdded =
            overlay.addedLifts.filterNot { it.workoutLiftId == workoutLiftId }
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
        val added = overlay.addedSlots
            .filter { it.workoutLiftId == workoutLiftId }
            .filterNot { it.slotId in overlay.deletedSlotIds }
        // Apply swaps + rest overrides so callers see live-session values.
        return (templateSlots + added).map { applyOverlaySlotFields(it, overlay) }
    }

    fun slotKey(slotId: SetSlotId): String = when (slotId) {
        is SetSlotId.Template -> slotId.liftSetId.value
        is SetSlotId.Overlay -> slotId.instanceId.value
    }

    fun resolveSlot(
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

    fun resolveSlotByKey(
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

    private fun applyOverlaySlotFields(slot: SetSlot, overlay: WorkoutOverlay): SetSlot =
        applyRestOverrideToSlot(applySwapToSlot(slot, overlay), overlay)

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
            if (swappedLiftId != null) {
                currentLiftId = swappedLiftId
                currentLiftName = liftNamesById[swappedLiftId].orEmpty()
                currentLoadType = liftsById[swappedLiftId]?.loadType ?: LoadType.Weighted
            }

            val currentSupersetGroupId = if (overlay.supersetGroupIdOverrides.containsKey(lift.workoutLiftId)) {
                overlay.supersetGroupIdOverrides[lift.workoutLiftId]
            } else {
                lift.supersetGroupId
            }

            val baseSlots = lift.slots.map { applyOverlaySlotFields(it, overlay) }
            val appended = overlay.addedSlots
                .filter { it.workoutLiftId == lift.workoutLiftId }
                .map { applyOverlaySlotFields(it, overlay) }

            lift.copy(
                liftId = currentLiftId,
                liftName = currentLiftName,
                loadType = currentLoadType,
                supersetGroupId = currentSupersetGroupId,
                slots = baseSlots + appended,
                useRestTimerBetweenLifts = overlay.useRestTimerBetweenLiftsOverrides[lift.workoutLiftId]
                    ?: lift.useRestTimerBetweenLifts,
            )
        }

        val sessionAdded = overlay.addedLifts
            .filterNot { it.workoutLiftId in overlay.deletedLiftIds }
            .map { added ->
                val catalog = liftsById[added.liftId]
                val groupId = if (overlay.supersetGroupIdOverrides.containsKey(added.workoutLiftId)) {
                    overlay.supersetGroupIdOverrides[added.workoutLiftId]
                } else {
                    added.supersetGroupId
                }
                val slots = overlay.addedSlots
                    .filter { it.workoutLiftId == added.workoutLiftId }
                    .sortedBy { it.position }
                WorkoutLiftProjection(
                    workoutLiftId = added.workoutLiftId,
                    liftId = added.liftId,
                    liftName = catalog?.name ?: liftNamesById[added.liftId].orEmpty(),
                    progressionScheme = added.progressionScheme,
                    slots = slots,
                    supersetGroupId = groupId,
                    loadType = catalog?.loadType ?: LoadType.Weighted,
                    useRestTimerBetweenLifts = overlay.useRestTimerBetweenLiftsOverrides[added.workoutLiftId]
                        ?: added.useRestTimerBetweenLifts,
                )
            }

        val withAdded = base.copy(lifts = mappedLifts + sessionAdded)
        val snapshot = ProgressionSnapshot(withAdded.workoutId, snapshotBySlot)
        return mergeRenderedWorkout(withAdded, snapshot, overlay)
    }
}
