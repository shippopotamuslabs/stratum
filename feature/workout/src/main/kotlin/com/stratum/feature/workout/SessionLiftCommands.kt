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
import com.stratum.domain.lift.sanitizeLiftNoteBody
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

/** Lift / superset / rest mutators for [LiveWorkoutSessionRuntime]. */

internal suspend fun LiveWorkoutSessionRuntime.handleSwapLift(cmd: SessionCommand.SwapLift) {
    commitOverlay(
        overlay.copy(
            swappedLifts = overlay.swappedLifts + (cmd.workoutLiftId to cmd.newLiftId),
        ),
    )
    // Snapshot previous/recs are keyed by template history; recompute them for the
    // performed lift so the card does not keep the pre-swap baselines (D3).
    val workout = baselineWorkout
    val templateLift = workout?.workoutLifts?.firstOrNull { it.id == cmd.workoutLiftId }
    if (templateLift != null) {
        mergeSnapshotForLifts(listOf(templateLift.copy(liftId = cmd.newLiftId)))
    }
    recomputeRecommendations()
    render()
    flushOverlay()
}

internal suspend fun LiveWorkoutSessionRuntime.handleAddLifts(cmd: SessionCommand.AddLifts) {
    val currentIds = merge().lifts.map { it.workoutLiftId.value }
    commitOverlay(
        LiveWorkoutOverlayOps.addLifts(
            overlay = overlay,
            currentLiftIds = currentIds,
            liftIds = cmd.liftIds,
            asSuperset = cmd.asSuperset,
            liftsById = liftsById,
            zeroWeight = Weight(0.0, Weight.WeightUnit.Lb),
        ),
    )
    recomputeRecommendations()
    render()
    flushOverlay()
}

internal suspend fun LiveWorkoutSessionRuntime.handleReorderLifts(cmd: SessionCommand.ReorderLifts) {
    commitOverlay(overlay.copy(orderedLiftIds = cmd.orderedWorkoutLiftIds))
    recomputeRecommendations()
    render()
    flushOverlay()
}

internal suspend fun LiveWorkoutSessionRuntime.handleRemoveLift(cmd: SessionCommand.RemoveLift) {
    // Effective (merged) groups/rest flags before removal — includes template values and
    // session-added OverlayAddedLift groups, not only existing override map entries.
    val linkableBefore = getLinkableLifts()
    val currentLifts =
        linkableBefore.filterNot { it.workoutLiftId == cmd.workoutLiftId.value }
    val cleaned = cleanupSupersetGroups(currentLifts)
    val (newGroupIdOverrides, newRestTimerOverrides) =
        LiveWorkoutOverlayOps.differentialSupersetOverrides(
            prior = linkableBefore.map { it.toLinkableSupersetFields() },
            updated = cleaned.map { it.toLinkableSupersetFields() },
            existingGroupOverrides = overlay.supersetGroupIdOverrides - cmd.workoutLiftId,
            existingRestOverrides =
                overlay.useRestTimerBetweenLiftsOverrides - cmd.workoutLiftId,
        )

    val nextDeletedLiftIds =
        if (overlay.addedLifts.any { it.workoutLiftId == cmd.workoutLiftId }) {
            overlay.deletedLiftIds
        } else {
            overlay.deletedLiftIds + cmd.workoutLiftId
        }
    val baselineLiftIdsInOrder =
        structure
            ?.lifts
            ?.filterNot { it.workoutLiftId in nextDeletedLiftIds }
            ?.map { it.workoutLiftId.value }
            .orEmpty()

    commitOverlay(
        LiveWorkoutOverlayOps.removeLift(
            overlay = overlay,
            workoutLiftId = cmd.workoutLiftId,
            cleanedGroupOverrides = newGroupIdOverrides,
            cleanedRestOverrides = newRestTimerOverrides,
            resolveSlot = ::resolveSlot,
            baselineLiftIdsInOrder = baselineLiftIdsInOrder,
        ),
    )
    recomputeRecommendations()
    render()
    flushOverlay()
}

internal suspend fun LiveWorkoutSessionRuntime.handleLinkSuperset(cmd: SessionCommand.LinkSuperset) {
    val prior = getLinkableLifts()
    val updated =
        linkSupersetWithAdjacent(
            lifts = prior,
            workoutLiftId = cmd.workoutLiftId.value,
            direction = cmd.direction,
            nextGroupId = {
                WorkoutSupersetGroupId(java.util.UUID.randomUUID().toString()).value
            },
        )
    applySupersetLinkableUpdate(prior = prior, updated = updated)
}

internal suspend fun LiveWorkoutSessionRuntime.handleRemoveFromSuperset(cmd: SessionCommand.RemoveFromSuperset) {
    val prior = getLinkableLifts()
    val updated = unlinkLiftFromSuperset(prior, cmd.workoutLiftId.value)
    applySupersetLinkableUpdate(prior = prior, updated = updated)
}

internal suspend fun LiveWorkoutSessionRuntime.applySupersetLinkableUpdate(
    prior: List<SupersetLinkableLiftState>,
    updated: List<SupersetLinkableLiftState>,
) {
    val (groupOverrides, restOverrides) =
        LiveWorkoutOverlayOps.differentialSupersetOverrides(
            prior = prior.map { it.toLinkableSupersetFields() },
            updated = updated.map { it.toLinkableSupersetFields() },
            existingGroupOverrides = overlay.supersetGroupIdOverrides,
            existingRestOverrides = overlay.useRestTimerBetweenLiftsOverrides,
        )
    commitOverlay(
        overlay.copy(
            supersetGroupIdOverrides = groupOverrides,
            useRestTimerBetweenLiftsOverrides = restOverrides,
            addedLifts =
                LiveWorkoutOverlayOps.applyGroupOverridesToAddedLifts(
                    addedLifts = overlay.addedLifts,
                    groupOverrides = groupOverrides,
                    restOverrides = restOverrides,
                ),
        ),
    )
    recomputeRecommendations()
    render()
    flushOverlay()
}

internal suspend fun LiveWorkoutSessionRuntime.handleSetUseRestTimerBetweenLifts(
    cmd: SessionCommand.SetUseRestTimerBetweenLifts,
) {
    commitOverlay(
        overlay.copy(
            useRestTimerBetweenLiftsOverrides =
                overlay.useRestTimerBetweenLiftsOverrides + (cmd.workoutLiftId to cmd.enabled),
        ),
    )
    recomputeRecommendations()
    render()
    flushOverlay()
}

internal suspend fun LiveWorkoutSessionRuntime.handleSetRestSecondsForSlots(cmd: SessionCommand.SetRestSecondsForSlots) {
    if (cmd.updates.isEmpty()) return
    val next =
        LiveWorkoutOverlayOps.mergeRestSecondsOverrides(
            overlay = overlay,
            updates = cmd.updates,
            resolveSlotByKey = ::resolveSlotByKey,
        ) ?: return
    commitOverlay(overlay.copy(restSecondsOverrides = next))
    render()
    flushOverlay()
}

internal suspend fun LiveWorkoutSessionRuntime.handleSetWorkoutLiftPinnedNotes(
    cmd: SessionCommand.SetWorkoutLiftPinnedNotes,
) {
    val sanitized = cmd.bodies.mapNotNull(::sanitizeLiftNoteBody)
    val nextLists =
        if (sanitized.isEmpty()) {
            overlay.workoutLiftPinnedNoteLists - cmd.workoutLiftId
        } else {
            overlay.workoutLiftPinnedNoteLists + (cmd.workoutLiftId to sanitized)
        }
    commitOverlay(
        overlay.copy(
            workoutLiftPinnedNotes = overlay.workoutLiftPinnedNotes - cmd.workoutLiftId,
            workoutLiftPinnedNoteLists = nextLists,
        ),
    )
    render()
    flushOverlay()
}

internal fun LiveWorkoutSessionRuntime.getLinkableLifts(): List<SupersetLinkableLiftState> {
    val currentRendered = merge()
    return currentRendered.lifts.map { lift ->
        SupersetLinkableLiftState(
            workoutLiftId = lift.workoutLiftId.value,
            liftId = lift.liftId.value,
            name = lift.liftName,
            supersetGroupId = lift.supersetGroupId?.value,
            useRestTimerBetweenLifts =
                overlay.useRestTimerBetweenLiftsOverrides[lift.workoutLiftId]
                    ?: lift.useRestTimerBetweenLifts,
        )
    }
}


private fun SupersetLinkableLiftState.toLinkableSupersetFields() =
    LiveWorkoutOverlayOps.LinkableSupersetFields(
        workoutLiftId = workoutLiftId,
        supersetGroupId = supersetGroupId,
        useRestTimerBetweenLifts = useRestTimerBetweenLifts,
    )

