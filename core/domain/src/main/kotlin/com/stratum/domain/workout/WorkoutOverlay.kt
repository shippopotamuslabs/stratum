package com.stratum.domain.workout

import com.stratum.domain.lift.Weight

/** A logged value for a single slot (D2). Overlay layer — the only mutable layer. */
data class LoggedSetValue(
    val slotId: SetSlotId,
    val weight: Weight,
    val reps: Int,
    val rir: Int?,
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
 * The overlay layer (D2, D9). Owned by the `WorkoutViewModel` as in-memory session
 * state — never persisted to Room except via the existing `LogSetUseCase` on finalize.
 * Carries logged values, live recommendations, dynamic structural entries, and the
 * undo stack.
 */
data class WorkoutOverlay(
    val loggedSets: Map<SetSlotId, LoggedSetValue> = emptyMap(),
    val recommendations: Map<SetSlotId, Weight> = emptyMap(),
    val addedSlots: List<SetSlot> = emptyList(),
    val deletedSlotIds: Set<SetSlotId> = emptySet(),
    val undoStack: List<StructuralMutation> = emptyList(),
    /** Fatigue-estimated baselines for dynamically-added slots (overlay-only; D3). */
    val dynamicBaselines: Map<SetSlotId, SlotRecommendation> = emptyMap(),
) {
    fun apply(mutation: StructuralMutation): WorkoutOverlay {
        val nextUndoStack = undoStack + mutation
        return when (mutation) {
            is StructuralMutation.AddSet ->
                copy(
                    addedSlots = addedSlots + mutation.newSlot,
                    undoStack = nextUndoStack,
                )
            is StructuralMutation.DeleteSet ->
                copy(deletedSlotIds = deletedSlotIds + mutation.slotId, undoStack = nextUndoStack)
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
                copy(deletedSlotIds = deletedSlotIds - last.slotId, undoStack = remaining)
            is StructuralMutation.GenerateMyoRepMiniSet ->
                copy(addedSlots = addedSlots.filterNot { it.slotId == last.newSlot.slotId }, undoStack = remaining)
            is StructuralMutation.AddDropSet ->
                copy(addedSlots = addedSlots.filterNot { it.slotId == last.newSlot.slotId }, undoStack = remaining)
        }
    }
}
