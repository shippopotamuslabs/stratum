package com.stratum.domain.workout

import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutSupersetGroupId
import com.stratum.domain.workout.myorep.MyoRepKind

/** A logged value for a single slot (D2). Overlay layer — the only mutable layer. */
data class LoggedSetValue(
    val slotId: SetSlotId,
    val weight: Weight,
    val reps: Int,
    val rir: Int?,
    /**
     * Stable Room [com.stratum.domain.importing.identity.LoggedSetId] for this slot when known.
     * Used so amend/unlog/find survive position renumber (e.g. myo minis inserted between
     * match backoffs). [LoggedSet.setIndex] is kept in sync via reindex after structure changes.
     */
    val persistedLoggedSetId: String? = null,
)

/**
 * A lift added mid-session that is not part of the baseline template.
 * Initial set rows live in [WorkoutOverlay.addedSlots] keyed by [workoutLiftId].
 */
data class OverlayAddedLift(
    val workoutLiftId: WorkoutLiftId,
    val liftId: LiftId,
    val progressionScheme: ProgressionScheme = ProgressionScheme.Double,
    val supersetGroupId: WorkoutSupersetGroupId? = null,
    val useRestTimerBetweenLifts: Boolean = false,
)

/**
 * Overlay structural mutations (D5). Slice 3 ships `AddSet` / `DeleteSet`; Slice 5
 * adds `GenerateMyoRepMiniSet`; Slice 6 adds `AddDropSet`.
 */
sealed interface StructuralMutation {
    data class AddSet(val afterSlotId: SetSlotId, val newSlot: SetSlot) : StructuralMutation
    data class DeleteSet(val slotId: SetSlotId) : StructuralMutation
    data class GenerateMyoRepMiniSet(val activationSlotId: SetSlotId, val newSlot: SetSlot) : StructuralMutation
    data class AddDropSet(val afterSlotId: SetSlotId, val newSlot: SetSlot) : StructuralMutation
}

/**
 * Session-only structural shape for a slot (set type / myo kind / rep band / top-set).
 * Applied on merge and when saving the overlay back to the program template.
 * Rest seconds stay on [WorkoutOverlay.restSecondsOverrides].
 */
data class SlotShapeOverride(
    val setType: SetType,
    val myoRepKind: MyoRepKind? = null,
    val isTopSet: Boolean,
    val repMin: Int,
    val repMax: Int,
    val rirGoal: Int,
)

/**
 * The overlay layer (D2, D9). Owned by the `WorkoutViewModel` as in-memory session
 * state — never persisted to Room except via the existing `LogSetUseCase` on finalize.
 * Carries logged values, live recommendations, dynamic structural entries, and the
 * undo stack.
 */
data class WorkoutOverlay(
    val loggedSets: Map<SetSlotId, LoggedSetValue> = emptyMap(),
    val recommendations: Map<SetSlotId, LiveSetRecommendation> = emptyMap(),
    val addedSlots: List<SetSlot> = emptyList(),
    val deletedSlotIds: Set<SetSlotId> = emptySet(),
    val undoStack: List<StructuralMutation> = emptyList(),
    /** Fatigue-estimated baselines for dynamically-added slots (overlay-only; D3). */
    val dynamicBaselines: Map<SetSlotId, SlotRecommendation> = emptyMap(),
    val swappedLifts: Map<WorkoutLiftId, LiftId> = emptyMap(),
    val deletedLiftIds: Set<WorkoutLiftId> = emptySet(),
    /** Mid-session lifts not present on the baseline template. */
    val addedLifts: List<OverlayAddedLift> = emptyList(),
    /**
     * Per-lift superset group overrides for the live session.
     *
     * **Key-present with `null` is intentional** and means “clear the group” — merge/save
     * must use [Map.containsKey] (or equivalent), not `overrides[id] ?: baseline`, or orphan
     * survivors of a removed superset partner keep their template/`OverlayAddedLift` group id.
     * Key-absent means “use baseline / addedLift value.”
     */
    val supersetGroupIdOverrides: Map<WorkoutLiftId, WorkoutSupersetGroupId?> = emptyMap(),
    val useRestTimerBetweenLiftsOverrides: Map<WorkoutLiftId, Boolean> = emptyMap(),
    /**
     * Per-slot rest-duration overrides for the live session. Applied on merge into rendered
     * slots and written back to the template when save-template applies the overlay.
     */
    val restSecondsOverrides: Map<SetSlotId, Int> = emptyMap(),
    /**
     * Per-slot set-type / shape overrides for the live session (template Edit Workout
     * parity). Applied on merge and written back when save-template applies the overlay.
     */
    val slotShapeOverrides: Map<SetSlotId, SlotShapeOverride> = emptyMap(),
    val orderedLiftIds: List<String>? = null,
) {
    /**
     * True when the overlay carries unsaved structural edits (swaps, added/deleted sets or
     * lifts, superset/rest-timer overrides, reorder) that differ from the baseline workout
     * template. Logged sets and recomputed recommendations are session activity, not dirty
     * edits — they are excluded.
     */
    val isDirty: Boolean
        get() =
            addedSlots.isNotEmpty() ||
                deletedSlotIds.isNotEmpty() ||
                swappedLifts.isNotEmpty() ||
                deletedLiftIds.isNotEmpty() ||
                addedLifts.isNotEmpty() ||
                supersetGroupIdOverrides.isNotEmpty() ||
                useRestTimerBetweenLiftsOverrides.isNotEmpty() ||
                restSecondsOverrides.isNotEmpty() ||
                slotShapeOverrides.isNotEmpty() ||
                orderedLiftIds != null

    fun apply(mutation: StructuralMutation): WorkoutOverlay {
        val nextUndoStack = undoStack + mutation
        return when (mutation) {
            is StructuralMutation.AddSet ->
                copy(
                    addedSlots = addedSlots + mutation.newSlot,
                    undoStack = nextUndoStack,
                )
            is StructuralMutation.DeleteSet ->
                // Drop rest/shape overrides for the deleted slot. Undo restores the slot
                // identity only — those override maps are not on the undo stack, so a
                // DeleteSet → undo path intentionally does not re-apply prior overrides.
                copy(
                    deletedSlotIds = deletedSlotIds + mutation.slotId,
                    restSecondsOverrides = restSecondsOverrides - mutation.slotId,
                    slotShapeOverrides = slotShapeOverrides - mutation.slotId,
                    undoStack = nextUndoStack,
                )
            is StructuralMutation.GenerateMyoRepMiniSet ->
                copy(addedSlots = addedSlots + mutation.newSlot, undoStack = nextUndoStack)
            is StructuralMutation.AddDropSet ->
                copy(addedSlots = addedSlots + mutation.newSlot, undoStack = nextUndoStack)
        }
    }

    fun undo(): WorkoutOverlay {
        val last = undoStack.lastOrNull() ?: return this
        val remaining = undoStack.dropLast(1)
        return when (last) {
            is StructuralMutation.AddSet ->
                copy(
                    addedSlots = addedSlots.filterNot { it.slotId == last.newSlot.slotId },
                    dynamicBaselines = dynamicBaselines - last.newSlot.slotId,
                    undoStack = remaining,
                )
            is StructuralMutation.DeleteSet ->
                // Rest overrides are not restored (never pushed on the undo stack).
                copy(deletedSlotIds = deletedSlotIds - last.slotId, undoStack = remaining)
            is StructuralMutation.GenerateMyoRepMiniSet ->
                copy(
                    addedSlots = addedSlots.filterNot { it.slotId == last.newSlot.slotId },
                    dynamicBaselines = dynamicBaselines - last.newSlot.slotId,
                    undoStack = remaining,
                )
            is StructuralMutation.AddDropSet ->
                copy(
                    addedSlots = addedSlots.filterNot { it.slotId == last.newSlot.slotId },
                    dynamicBaselines = dynamicBaselines - last.newSlot.slotId,
                    undoStack = remaining,
                )
        }
    }

    /**
     * Rewrites in-session catalog identity after a lift merge: any [swappedLifts],
     * [addedLifts], or [addedSlots] still pointing at a source id move onto [targetLiftId].
     * Template slots are re-pointed in the program layer — not here.
     */
    fun remapCatalogLiftIds(
        sourceLiftIds: Set<LiftId>,
        targetLiftId: LiftId,
    ): WorkoutOverlay {
        if (sourceLiftIds.isEmpty()) return this
        fun mapId(id: LiftId): LiftId = if (id in sourceLiftIds) targetLiftId else id

        val nextSwapped =
            swappedLifts.mapValues { (_, liftId) -> mapId(liftId) }
        val nextAddedLifts =
            addedLifts.map { added ->
                val next = mapId(added.liftId)
                if (next == added.liftId) added else added.copy(liftId = next)
            }
        val nextAddedSlots =
            addedSlots.map { slot ->
                val next = mapId(slot.liftId)
                if (next == slot.liftId) slot else slot.copy(liftId = next)
            }
        return if (
            nextSwapped == swappedLifts &&
            nextAddedLifts == addedLifts &&
            nextAddedSlots == addedSlots
        ) {
            this
        } else {
            copy(
                swappedLifts = nextSwapped,
                addedLifts = nextAddedLifts,
                addedSlots = nextAddedSlots,
            )
        }
    }
}

/**
 * Applies [WorkoutOverlay.restSecondsOverrides] onto this slot when present.
 * Canonical rest-override apply path for merge, live session resolve, and template save.
 */
fun SetSlot.applyRestOverride(overlay: WorkoutOverlay): SetSlot {
    val override = overlay.restSecondsOverrides[slotId] ?: return this
    return if (restSeconds == override) this else copy(restSeconds = override)
}

/**
 * Applies [WorkoutOverlay.slotShapeOverrides] onto this slot when present.
 * Canonical shape-override apply path for merge, live session resolve, and template save.
 */
fun SetSlot.applyShapeOverride(overlay: WorkoutOverlay): SetSlot {
    val override = overlay.slotShapeOverrides[slotId] ?: return this
    return copy(
        setType = override.setType,
        myoRepKind = override.myoRepKind,
        isTopSet = override.isTopSet,
        repMin = override.repMin,
        repMax = override.repMax,
        rirGoal = override.rirGoal,
    )
}

