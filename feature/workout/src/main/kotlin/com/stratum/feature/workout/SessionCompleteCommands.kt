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

/** Complete / finalize for [LiveWorkoutSessionRuntime]. */

internal suspend fun LiveWorkoutSessionRuntime.handleCompleteWorkout(cmd: SessionCommand.CompleteWorkout) {
    val sessionId = cachedSessionId
    val workoutId = activeWorkoutId
    if (sessionId == null || workoutId == null) {
        effects.markCompleteAttemptFinished()
        return
    }
    val workout = baselineWorkout
    try {
        var templateSaved = false
        // Hard rule: only PROGRAM_WORKOUT sessions may write the program template.
        // Ad-hoc baselines are synthetic (route id = session id) — never SaveWorkout.
        val templateToSave =
            if (cmd.saveTemplate && canSaveTemplateChanges && !templateSavedThisComplete) {
                workout
            } else {
                null
            }
        if (templateToSave != null) {
            val updated = applyOverlayToWorkout(templateToSave, overlay)
            val saveResult = saveWorkoutUseCase(workoutId, updated)
            if (saveResult.isFailure) {
                effects.publishUserMessage(LiveWorkoutSessionRuntime.MSG_COMPLETE_FAILED)
                return
            }
            // Fold applied structure into baseline so a finalize-only retry is safe and
            // does not re-apply (or double-mint) template ids. Keep
            // [templateSavedThisComplete] true across finalize failure — resetting it
            // would re-run applyOverlay and mint new LiftSetIds for the same overlay slots.
            baselineWorkout = updated
            templateSavedThisComplete = true
            templateSaved = true
        }
        // Arm before finalize so observe(null session) cannot flash NoActiveSession
        // while NavHost is still composing this route during the exit transition.
        effects.setRetainLoadedUiForExit(true)
        val finalizeResult = finalizeTrainingSession(sessionId)
        if (finalizeResult.isFailure) {
            effects.setRetainLoadedUiForExit(false)
            // Do NOT clear templateSavedThisComplete — template is already durable;
            // retry is finalize-only (see flag comment above).
            effects.publishUserMessage(
                if (templateSaved || templateSavedThisComplete) {
                    LiveWorkoutSessionRuntime.MSG_COMPLETE_FINALIZE_AFTER_TEMPLATE
                } else {
                    LiveWorkoutSessionRuntime.MSG_COMPLETE_FAILED
                },
            )
            return
        }
        templateSavedThisComplete = false
        manageWorkoutOverlay.clear()
        effects.clearLiveSurfaces()
        effects.emitEvent(WorkoutUiEvent.NavigateToFinalize(sessionId.value))
        // Terminal: drop session ids so double-complete / late mutators no-op.
        // Keep last Loaded UI via retainLoadedUiForExit (do not publish empty).
        sessionTerminal = true
        clearSessionMemory(publishEmpty = false)
    } finally {
        effects.markCompleteAttemptFinished()
    }
}

