package com.stratum.feature.workout

import com.stratum.domain.lift.Weight
import com.stratum.domain.program.SetType
import com.stratum.domain.workout.OverlaySetInstanceId
import com.stratum.domain.workout.SetSlot
import com.stratum.domain.workout.SetSlotId
import com.stratum.domain.workout.SlotRecommendation
import com.stratum.domain.workout.StructuralMutation
import com.stratum.domain.workout.WorkoutOverlay
import com.stratum.domain.workout.WorkoutStructureProjection
import com.stratum.domain.workout.myorep.DefaultMyoRepLifecycle
import com.stratum.domain.workout.myorep.MyoRepBlockState
import com.stratum.domain.workout.myorep.MyoRepKind
import com.stratum.domain.workout.myorep.MyoRepLifecycle
import java.util.UUID

/**
 * Live-session intensifier helpers (myo mini generation + cascade deletes).
 * Split from [LiveWorkoutOverlayOps] to stay under Detekt TooManyFunctions.
 */
internal object LiveIntensifierOverlayOps {

    fun applyMyoLifecycleAfterLog(
        structure: WorkoutStructureProjection?,
        overlay: WorkoutOverlay,
        loggedSlotId: SetSlotId,
        lifecycle: MyoRepLifecycle = DefaultMyoRepLifecycle(),
    ): WorkoutOverlay {
        val workoutLiftId = LiveWorkoutOverlayOps.resolveSlot(structure, overlay, loggedSlotId)
            ?.workoutLiftId
            ?: return overlay
        val slots = LiveWorkoutOverlayOps.visibleSlotsForLift(structure, overlay, workoutLiftId)
            .sortedBy { it.position }
        val context = myoLogContext(slots, overlay, loggedSlotId) ?: return overlay
        if (!shouldGenerateMyoMini(context)) return overlay

        val next = lifecycle.nextMiniSet(context.blockState) ?: return overlay
        // Minis inherit the activation set's programmed rest (Edit Workout / overrides).
        val unique = next.copy(
            slotId = SetSlotId.Overlay(
                OverlaySetInstanceId(
                    "mini-${slotIdKey(context.activation.slotId)}-" +
                        "${context.minis.size + 1}-${UUID.randomUUID().toString().take(8)}",
                ),
            ),
            restSeconds = context.activation.restSeconds.coerceAtLeast(0),
            myoRepKind = context.kind,
        )
        // Seed a dynamic baseline so merge keeps the mini visible until recompute fills recs.
        // Without this, MergeRenderedWorkout drops slots that have no snapshot/baseline.
        val activationLog = overlay.loggedSets[context.activation.slotId]
        val seedWeight = activationLog?.weight ?: Weight(0.0, Weight.WeightUnit.Lb)
        val seedBaseline = SlotRecommendation(
            slotId = unique.slotId,
            recommendedWeight = seedWeight,
            baselineExpectedReps = 0,
        )
        val withMini =
            overlay.apply(
                StructuralMutation.GenerateMyoRepMiniSet(
                    activationSlotId = context.activation.slotId,
                    newSlot = unique,
                ),
            )
        return withMini.copy(
            dynamicBaselines = withMini.dynamicBaselines + (unique.slotId to seedBaseline),
        )
    }

    fun cascadeDeleteTargets(
        structure: WorkoutStructureProjection?,
        overlay: WorkoutOverlay,
        slotId: SetSlotId,
    ): List<SetSlotId> {
        val slot = LiveWorkoutOverlayOps.resolveSlot(structure, overlay, slotId)
            ?: return listOf(slotId)
        val slots = LiveWorkoutOverlayOps.visibleSlotsForLift(structure, overlay, slot.workoutLiftId)
        val idx = slots.indexOfFirst { it.slotId == slotId }
        if (idx < 0) return listOf(slotId)

        return when (slot.setType) {
            SetType.MyoRep -> myoCascadeDeleteTargets(slots, slot, slotId)
            else -> dropCascadeDeleteTargets(slots, idx, slot, slotId)
        }
    }

    /** Activation deletes the contiguous myo block; a mini deletes only itself. */
    private fun myoCascadeDeleteTargets(
        slots: List<SetSlot>,
        slot: SetSlot,
        slotId: SetSlotId,
    ): List<SetSlotId> {
        val activation = findMyoActivation(slots, slot) ?: return listOf(slotId)
        if (slot.slotId != activation.slotId) return listOf(slotId)
        val actIdx = slots.indexOfFirst { it.slotId == activation.slotId }
        if (actIdx < 0) return listOf(slotId)
        val targets = mutableListOf<SetSlotId>()
        var i = actIdx
        while (i < slots.size && slots[i].setType == SetType.MyoRep) {
            if (i > actIdx && slots[i].isTopSet) break
            targets += slots[i].slotId
            i++
        }
        return targets
    }

    private fun dropCascadeDeleteTargets(
        slots: List<SetSlot>,
        idx: Int,
        slot: SetSlot,
        slotId: SetSlotId,
    ): List<SetSlotId> {
        val targets = mutableListOf(slotId)
        var i = idx + 1
        while (i < slots.size && slots[i].setType == SetType.DropSet) {
            targets += slots[i].slotId
            i++
        }
        return if (slot.setType == SetType.DropSet || targets.size > 1) targets else listOf(slotId)
    }

    fun requiresCascadeConfirm(
        structure: WorkoutStructureProjection?,
        overlay: WorkoutOverlay,
        slotId: SetSlotId,
    ): Boolean = cascadeDeleteTargets(structure, overlay, slotId).size > 1

    private data class MyoLogContext(
        val activation: SetSlot,
        val minis: List<SetSlot>,
        val kind: MyoRepKind,
        val blockState: MyoRepBlockState,
        val loggedSlotId: SetSlotId,
        val loggedSlotIds: Set<SetSlotId>,
    )

    private fun myoLogContext(
        slots: List<SetSlot>,
        overlay: WorkoutOverlay,
        loggedSlotId: SetSlotId,
    ): MyoLogContext? {
        val loggedSlot = slots.firstOrNull { it.slotId == loggedSlotId } ?: return null
        if (loggedSlot.setType != SetType.MyoRep) return null
        val activation = findMyoActivation(slots, loggedSlot) ?: return null
        val kind = activation.myoRepKind ?: MyoRepKind.RepFloor
        // Only contiguous overlay minis in this myo cluster — trailing Standards do not count.
        val minis = contiguousMyoMinisAfter(slots, activation)
        val activationLogged = overlay.loggedSets[activation.slotId] ?: return null
        val miniLogs = minis.mapNotNull { mini -> overlay.loggedSets[mini.slotId] }
        val state = MyoRepBlockState(
            workoutLiftId = activation.workoutLiftId,
            activationSlotId = activation.slotId,
            kind = kind,
            activationReps = activationLogged.reps,
            repFloor = MyoRepLifecycle.DEFAULT_REP_FLOOR,
            cumulativeMiniSetReps = miniLogs.sumOf { it.reps },
            lastMiniSetReps = miniLogs.lastOrNull()?.reps,
            generatedSlots = minis,
            liftId = activation.liftId,
            activationPosition = activation.position,
            restSeconds = activation.restSeconds.coerceAtLeast(0),
        )
        return MyoLogContext(
            activation = activation,
            minis = minis,
            kind = kind,
            blockState = state,
            loggedSlotId = loggedSlotId,
            loggedSlotIds = overlay.loggedSets.keys,
        )
    }

    /**
     * Spawn another mini only when the cluster has no open (unlogged) mini already, and the
     * log that triggered this was either the activation or the last mini in the cluster.
     * Straight sets after the myo block are ignored.
     */
    private fun shouldGenerateMyoMini(context: MyoLogContext): Boolean {
        val lastMini = context.minis.maxByOrNull { it.position }
        val openClusterMini = lastMini != null && lastMini.slotId !in context.loggedSlotIds
        if (openClusterMini) return false
        return if (context.loggedSlotId == context.activation.slotId) {
            true
        } else {
            lastMini != null && lastMini.slotId == context.loggedSlotId
        }
    }

    /** Minis that sit immediately after [activation] until a non-MyoRep (or second top myo). */
    private fun contiguousMyoMinisAfter(
        slots: List<SetSlot>,
        activation: SetSlot,
    ): List<SetSlot> {
        val actIdx = slots.indexOfFirst { it.slotId == activation.slotId }
        if (actIdx < 0) return emptyList()
        return slots
            .drop(actIdx + 1)
            .takeWhile { slot ->
                slot.setType == SetType.MyoRep &&
                    !slot.isTopSet &&
                    // Live minis are overlay slots; never treat a later template Myo as a mini.
                    slot.slotId !is SetSlotId.Template
            }
    }

    /**
     * Resolves the activation for a myo mini (or the slot itself when it is the activation).
     * Prefers top-set myo, then template-authored myo, then the nearest prior MyoRep.
     */
    private fun findMyoActivation(slots: List<SetSlot>, from: SetSlot): SetSlot? {
        if (from.setType != SetType.MyoRep) return null
        if (from.isTopSet) return from
        if (from.slotId is SetSlotId.Template) return from
        val priorOrSelf = slots.filter { it.position <= from.position }
        return priorOrSelf.lastOrNull { it.setType == SetType.MyoRep && it.isTopSet }
            ?: priorOrSelf.lastOrNull {
                it.setType == SetType.MyoRep && it.slotId is SetSlotId.Template
            }
            ?: priorOrSelf.lastOrNull { it.setType == SetType.MyoRep }
    }

    private fun slotIdKey(id: SetSlotId): String = when (id) {
        is SetSlotId.Template -> id.liftSetId.value
        is SetSlotId.Overlay -> id.instanceId.value
    }

    /**
     * Orders template + overlay slots so live myo minis sit immediately after their
     * activation (even when straight sets follow the activation in the template).
     * Drops / other overlay sets insert by [SetSlot.position]. Result positions are
     * renumbered 0..n-1 for stable labels and recommendation indexing.
     */
    fun orderLiftSlots(
        templateSlots: List<SetSlot>,
        addedSlots: List<SetSlot>,
    ): List<SetSlot> {
        val result = templateSlots.sortedBy { it.position }.toMutableList()
        for (added in addedSlots) {
            when (added.setType) {
                SetType.MyoRep -> {
                    val activationIndex = indexOfMyoActivationForInsert(result)
                    if (activationIndex < 0) {
                        result.add(added)
                        continue
                    }
                    var insertAt = activationIndex + 1
                    while (
                        insertAt < result.size &&
                        result[insertAt].setType == SetType.MyoRep &&
                        !result[insertAt].isTopSet
                    ) {
                        insertAt++
                    }
                    result.add(insertAt, added)
                }
                else -> {
                    var insertAt = result.indexOfLast { it.position < added.position } + 1
                    if (insertAt < 0) insertAt = 0
                    while (
                        insertAt < result.size &&
                        result[insertAt].position == added.position
                    ) {
                        insertAt++
                    }
                    result.add(insertAt.coerceIn(0, result.size), added)
                }
            }
        }
        return result.mapIndexed { index, slot -> slot.copy(position = index) }
    }

    private fun indexOfMyoActivationForInsert(slots: List<SetSlot>): Int {
        val top = slots.indexOfLast { it.setType == SetType.MyoRep && it.isTopSet }
        if (top >= 0) return top
        val template = slots.indexOfLast {
            it.setType == SetType.MyoRep && it.slotId is SetSlotId.Template
        }
        if (template >= 0) return template
        return slots.indexOfFirst { it.setType == SetType.MyoRep }
    }
}
