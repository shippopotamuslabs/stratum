package com.stratum.feature.workout

import com.stratum.core.ui.component.SupersetLinkableLiftState
import com.stratum.core.ui.component.cleanupSupersetGroups
import com.stratum.core.ui.component.linkSupersetWithAdjacent
import com.stratum.core.ui.component.unlinkLiftFromSuperset
import com.stratum.domain.importing.FatigueDecay
import com.stratum.domain.importing.ObserveLiftTrainingInsightsUseCase
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.resolvedDefaultRestSeconds
import com.stratum.domain.lift.storageWeightFromDisplay
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SaveWorkoutUseCase
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.coerceForCompound
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutSupersetGroupId
import com.stratum.domain.training.DeleteLoggedSetUseCase
import com.stratum.domain.training.FinalizeTrainingSessionUseCase
import com.stratum.domain.training.GetLoggedSetsForSessionUseCase
import com.stratum.domain.training.LogSetCommand
import com.stratum.domain.training.LogSetUseCase
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionDisplayName
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.UpdateLoggedSetUseCase
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.training.liveWorkoutRouteId
import com.stratum.domain.workout.ApplyOverlayToWorkoutUseCase
import com.stratum.domain.workout.EstimateAddedSetRepsUseCase
import com.stratum.domain.workout.GenerateProgressionSnapshotUseCase
import com.stratum.domain.workout.LoggedSetValue
import com.stratum.domain.workout.ManageWorkoutOverlayUseCase
import com.stratum.domain.workout.MergeRenderedWorkoutUseCase
import com.stratum.domain.workout.OverlaySetInstanceId
import com.stratum.domain.workout.ProjectWorkoutStructureUseCase
import com.stratum.domain.workout.RecomputeRecommendationsUseCase
import com.stratum.domain.workout.RenderedWorkout
import com.stratum.domain.workout.SetSlot
import com.stratum.domain.workout.SetSlotId
import com.stratum.domain.workout.SlotRecommendation
import com.stratum.domain.workout.SnapshotRequest
import com.stratum.domain.workout.StructuralMutation
import com.stratum.domain.workout.WorkoutOverlay
import com.stratum.domain.workout.WorkoutStructureProjection
import com.stratum.domain.workout.intensifier.RestDefaultForTransition
import com.stratum.feature.workout.ui.WorkoutUiEvent
import com.stratum.feature.workout.ui.WorkoutUiMapper
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Set log/structure mutators for [LiveWorkoutSessionRuntime]. */

internal suspend fun LiveWorkoutSessionRuntime.handleLogSet(cmd: SessionCommand.LogSet) {
    val session = cachedSessionId ?: return
    val slot = resolveSlotByKey(cmd.slotKey) ?: return
    val slotId = slot.slotId
    val loadType = liftsById[slot.liftId]?.loadType ?: LoadType.Weighted
    val normalizedWeight =
        storageWeightFromDisplay(
            displayAmount = cmd.weight.amount,
            displayUnit = cmd.weight.unit,
            loadType = loadType,
        )

    val wasAlreadyLogged = slotId in overlay.loggedSets
    val persistOk =
        persistLoggedSet(
            session = session,
            slot = slot,
            weight = normalizedWeight,
            reps = cmd.reps,
            rir = cmd.rir,
        )
    if (!persistOk) {
        effects.emitEvent(WorkoutUiEvent.SetLogFailed(slotKey = cmd.slotKey))
        return
    }

    var next =
        overlay.copy(
            loggedSets =
                overlay.loggedSets +
                    (slotId to LoggedSetValue(slotId, normalizedWeight, cmd.reps, cmd.rir)),
        )
    next =
        LiveIntensifierOverlayOps.applyMyoLifecycleAfterLog(
            structure = structure,
            overlay = next,
            loggedSlotId = slotId,
        )
    commitOverlay(next)
    // Recompute before flush so DataStore keeps the post-log live recs. Restore must see
    // the frozen block weights; flushing pre-recompute left recs empty/stale and a reopen
    // re-ran last-set floor Reduce (e.g. remaining 315 → 295).
    recomputeRecommendations()
    flushOverlay()
    render()
    if (wasAlreadyLogged) {
        effects.emitEvent(WorkoutUiEvent.SetAmended(slotKey = cmd.slotKey))
    } else {
        effects.emitEvent(WorkoutUiEvent.SetLogged(slotKey = cmd.slotKey))
    }
}

internal suspend fun LiveWorkoutSessionRuntime.persistLoggedSet(
    session: TrainingSessionId,
    slot: SetSlot,
    weight: Weight,
    reps: Int,
    rir: Int?,
): Boolean {
    val dbSet = findLoggedSetForSlot(session, slot)
    if (dbSet != null) {
        val updateResult =
            updateLoggedSet(
                dbSet.copy(weight = weight, reps = reps, rir = rir),
            )
        if (updateResult.isFailure) {
            effects.publishUserMessage(LiveWorkoutSessionRuntime.MSG_LOG_SET_FAILED)
            return false
        }
        return true
    }
    val insertResult =
        logSetUseCase(
            LogSetCommand(
                sessionId = session,
                liftId = slot.liftId,
                workoutLiftId = slot.workoutLiftId,
                setIndex = slot.position,
                weight = weight,
                reps = reps,
                rir = rir,
                completedAt = Instant.now(),
                // Session-only lifts (overlay add / ad-hoc) are not on the template yet;
                // stamp live slot shape so LoggedLiftContext is complete without a lookup.
                setType = slot.setType,
                repMin = slot.repMin,
                repMax = slot.repMax,
                rirGoal = slot.rirGoal,
            ),
        )
    if (insertResult.isFailure) {
        effects.publishUserMessage(LiveWorkoutSessionRuntime.MSG_LOG_SET_FAILED)
        return false
    }
    return true
}

internal suspend fun LiveWorkoutSessionRuntime.findLoggedSetForSlot(
    session: TrainingSessionId,
    slot: SetSlot,
): LoggedSet? {
    val dbSets = getLoggedSetsForSession(session)
    return dbSets.firstOrNull {
        it.liftContext.workoutLiftId == slot.workoutLiftId && it.setIndex == slot.position
    }
}

internal suspend fun LiveWorkoutSessionRuntime.handleUnlogSet(cmd: SessionCommand.UnlogSet) {
    val session = cachedSessionId ?: return
    val slot = resolveSlotByKey(cmd.slotKey) ?: return
    val slotId = slot.slotId
    val dbSet = findLoggedSetForSlot(session, slot)
    if (dbSet != null) {
        val deleteResult = deleteLoggedSet(dbSet.id)
        if (deleteResult.isFailure) {
            effects.publishUserMessage(LiveWorkoutSessionRuntime.MSG_UNLOG_SET_FAILED)
            return
        }
    }
    commitOverlay(
        overlay.copy(loggedSets = overlay.loggedSets - slotId),
    )
    recomputeRecommendations()
    render()
    flushOverlay()
}

internal suspend fun LiveWorkoutSessionRuntime.handleAddSet(cmd: SessionCommand.AddSet) {
    val anchor = resolveSlot(cmd.afterSlotId) ?: return
    val newPosition = anchor.position + 1
    val added =
        SetSlot(
            slotId =
                SetSlotId.Overlay(
                    OverlaySetInstanceId("added-${overlay.addedSlots.size + 1}"),
                ),
            workoutLiftId = anchor.workoutLiftId,
            liftId = anchor.liftId,
            position = newPosition,
            setType = anchor.setType,
            isTopSet = false,
            repMin = anchor.repMin,
            repMax = anchor.repMax,
            rirGoal = anchor.rirGoal,
            restSeconds = anchor.restSeconds,
        )
    val baseline = snapshotBySlot[cmd.afterSlotId]
    val fatigue = fatigueByLiftId[anchor.liftId] ?: FatigueDecay(null, null)
    val coldWeight = Weight(0.0, Weight.WeightUnit.Lb)
    val estimatedReps =
        estimateAddedSetReps(
            previousRecommendation =
                baseline ?: SlotRecommendation(cmd.afterSlotId, coldWeight, anchor.repMin),
            nextSetIndex = newPosition,
            perLiftSlope = fatigue.slope,
            perLiftIntercept = fatigue.intercept,
        )
    val addedWeight = baseline?.recommendedWeight ?: coldWeight
    val addedRecommendation =
        SlotRecommendation(added.slotId, addedWeight, estimatedReps)
    commitOverlay(
        overlay
            .apply(StructuralMutation.AddSet(afterSlotId = cmd.afterSlotId, newSlot = added))
            .copy(
                dynamicBaselines =
                    overlay.dynamicBaselines + (added.slotId to addedRecommendation),
            ),
    )
    recomputeRecommendations()
    render()
    flushOverlay()
}

internal suspend fun LiveWorkoutSessionRuntime.handleAddSetForLift(cmd: SessionCommand.AddSetForLift) {
    val lastSlot =
        visibleSlotsForLift(WorkoutLiftId(cmd.workoutLiftId)).maxByOrNull { it.position }
            ?: return
    handleAddSet(SessionCommand.AddSet(afterSlotId = lastSlot.slotId))
}

internal suspend fun LiveWorkoutSessionRuntime.handleDeleteSet(cmd: SessionCommand.DeleteSet) {
    // DeleteSet also drops restSecondsOverrides[slotId] (domain apply). Undo does not
    // restore rest overrides — they are not on the undo stack.
    commitOverlay(overlay.apply(StructuralMutation.DeleteSet(cmd.slotId)))
    recomputeRecommendations()
    render()
    flushOverlay()
}

internal suspend fun LiveWorkoutSessionRuntime.handleDeleteSetByKey(cmd: SessionCommand.DeleteSetByKey) {
    val slot = resolveSlotByKey(cmd.slotKey) ?: return
    val remainingForLift = visibleSlotsForLift(slot.workoutLiftId)
    if (remainingForLift.size <= 1) return

    val slotId = slot.slotId
    val workoutLiftId = slot.workoutLiftId
    val session = cachedSessionId

    if (slotId in overlay.loggedSets && session != null) {
        val dbSet = findLoggedSetForSlot(session, slot)
        if (dbSet != null) {
            val deleteResult = deleteLoggedSet(dbSet.id)
            if (deleteResult.isFailure) {
                effects.publishUserMessage(LiveWorkoutSessionRuntime.MSG_UNLOG_SET_FAILED)
                return
            }
        }
        // Re-check after suspend — another mutation may have removed this set.
        // (Single-writer: only concurrent channel commands, not parallel mutations.)
        if (slotId in overlay.deletedSlotIds) return
        if (visibleSlotsForLift(workoutLiftId).size <= 1) return
        commitOverlay(
            overlay
                .copy(loggedSets = overlay.loggedSets - slotId)
                .apply(StructuralMutation.DeleteSet(slotId)),
        )
    } else {
        if (slotId in overlay.loggedSets) {
            commitOverlay(
                overlay
                    .copy(loggedSets = overlay.loggedSets - slotId)
                    .apply(StructuralMutation.DeleteSet(slotId)),
            )
        } else {
            commitOverlay(overlay.apply(StructuralMutation.DeleteSet(slotId)))
        }
    }
    recomputeRecommendations()
    render()
    flushOverlay()
}

internal suspend fun LiveWorkoutSessionRuntime.handleUndo() {
    commitOverlay(overlay.undo())
    recomputeRecommendations()
    render()
    flushOverlay()
}

internal suspend fun LiveWorkoutSessionRuntime.handleGenerateMyoMini(cmd: SessionCommand.GenerateMyoMini) {
    commitOverlay(
        LiveIntensifierOverlayOps.applyMyoLifecycleAfterLog(
            structure = structure,
            overlay = overlay,
            loggedSlotId = cmd.activationSlotId,
        ),
    )
    recomputeRecommendations()
    render()
    flushOverlay()
}

internal suspend fun LiveWorkoutSessionRuntime.handleAddDropSet(cmd: SessionCommand.AddDropSet) {
    val anchor = resolveSlot(cmd.afterSlotId) ?: return
    val nextRest =
        RestDefaultForTransition.restAfter(
            nextType = null,
            liftDefaultRestSeconds =
                liftsById[anchor.liftId]?.resolvedDefaultRestSeconds() ?: 120,
        )
    val drop =
        SetSlot(
            slotId =
                SetSlotId.Overlay(
                    OverlaySetInstanceId("drop-${overlay.addedSlots.size + 1}"),
                ),
            workoutLiftId = anchor.workoutLiftId,
            liftId = anchor.liftId,
            position = anchor.position + 1,
            setType = com.stratum.domain.program.SetType.DropSet,
            isTopSet = false,
            repMin = 0,
            repMax = 0,
            rirGoal = 0,
            restSeconds = nextRest,
        )
    // Force zero rest on the anchor when it now leads into a drop.
    val withAnchorRest =
        if (anchor.restSeconds != 0) {
            overlay.copy(
                restSecondsOverrides =
                    overlay.restSecondsOverrides + (cmd.afterSlotId to 0),
            )
        } else {
            overlay
        }
    commitOverlay(
        withAnchorRest.apply(
            StructuralMutation.AddDropSet(afterSlotId = cmd.afterSlotId, newSlot = drop),
        ),
    )
    recomputeRecommendations()
    render()
    flushOverlay()
}

internal suspend fun LiveWorkoutSessionRuntime.handleDeleteSetWithCascade(cmd: SessionCommand.DeleteSetWithCascade) {
    val targets =
        LiveIntensifierOverlayOps.cascadeDeleteTargets(structure, overlay, cmd.slotId)
    val session = cachedSessionId
    if (session != null) {
        for (id in targets) {
            val targetSlot = resolveSlot(id) ?: continue
            val dbSet = findLoggedSetForSlot(session, targetSlot)
            if (dbSet != null) {
                val deleteResult = deleteLoggedSet(dbSet.id)
                if (deleteResult.isFailure) {
                    effects.publishUserMessage(LiveWorkoutSessionRuntime.MSG_UNLOG_SET_FAILED)
                    return
                }
            }
        }
    }
    var next = overlay
    for (id in targets) {
        next =
            next.copy(loggedSets = next.loggedSets - id)
                .apply(StructuralMutation.DeleteSet(id))
    }
    commitOverlay(next)
    recomputeRecommendations()
    render()
    flushOverlay()
}

internal suspend fun LiveWorkoutSessionRuntime.handleSetSetKind(cmd: SessionCommand.SetSetKind) {
    val slot = resolveSlotByKey(cmd.slotKey) ?: return
    val scheme = progressionSchemeForLift(slot.workoutLiftId)
    val loadType =
        liftsById[slot.liftId]?.loadType ?: LoadType.Weighted
    val liftDefaultRest =
        liftsById[slot.liftId]?.resolvedDefaultRestSeconds()
            ?: LiveWorkoutSessionRuntime.DEFAULT_LIFT_REST_SECONDS

    // Unlog minis from Room before dropping them from the overlay.
    val minisToDrop =
        if (
            LiveSetTypeOverlayOps.requiresMyoMiniDiscardConfirm(
                structure,
                overlay,
                slot.slotId,
                cmd.kind,
            )
        ) {
            LiveSetTypeOverlayOps.miniSlotsDiscardedOnDemote(
                structure,
                overlay,
                slot.slotId,
            )
        } else {
            emptyList()
        }
    val session = cachedSessionId
    if (session != null && minisToDrop.isNotEmpty()) {
        for (miniId in minisToDrop) {
            val miniSlot = resolveSlot(miniId) ?: continue
            val dbSet = findLoggedSetForSlot(session, miniSlot)
            if (dbSet != null) {
                val deleteResult = deleteLoggedSet(dbSet.id)
                if (deleteResult.isFailure) {
                    effects.publishUserMessage(LiveWorkoutSessionRuntime.MSG_UNLOG_SET_FAILED)
                    return
                }
            }
        }
    }

    val next =
        LiveSetTypeOverlayOps.applySetKind(
            structure = structure,
            overlay = overlay,
            slotId = slot.slotId,
            newKind = cmd.kind,
            scheme = scheme,
            loadType = loadType,
            liftDefaultRestSeconds = liftDefaultRest,
        ) ?: return
    commitOverlay(next)
    recomputeRecommendations()
    render()
    flushOverlay()
}

