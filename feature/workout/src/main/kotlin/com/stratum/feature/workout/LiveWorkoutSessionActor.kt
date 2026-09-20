package com.stratum.feature.workout

import com.stratum.domain.lift.Lift
import com.stratum.domain.program.GetProgramByIdUseCase
import com.stratum.domain.program.Program
import com.stratum.domain.program.SaveWorkoutUseCase
import com.stratum.domain.program.Workout
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.training.DeleteLoggedSetUseCase
import com.stratum.domain.training.FinalizeTrainingSessionUseCase
import com.stratum.domain.training.GetLoggedSetsForSessionUseCase
import com.stratum.domain.training.LogSetUseCase
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionDisplayName
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.UpdateLoggedSetUseCase
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.training.liveWorkoutRouteId
import com.stratum.domain.workout.ApplyOverlayToWorkoutUseCase
import com.stratum.domain.workout.EstimateAddedSetRepsUseCase
import com.stratum.domain.workout.GenerateProgressionSnapshotUseCase
import com.stratum.domain.workout.ManageWorkoutOverlayUseCase
import com.stratum.domain.workout.MergeRenderedWorkoutUseCase
import com.stratum.domain.workout.ProjectWorkoutStructureUseCase
import com.stratum.domain.workout.RecomputeRecommendationsUseCase
import com.stratum.domain.workout.RenderedWorkout
import com.stratum.domain.workout.SetSlotId
import com.stratum.domain.importing.ObserveLiftTrainingInsightsUseCase
import com.stratum.domain.logging.LogErrorUseCase
import com.stratum.feature.workout.ui.WorkoutUiEvent
import com.stratum.feature.workout.ui.WorkoutUiState
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Single-writer owner of live session memory (overlay / structure / snapshot).
 *
 * UI and collectors only enqueue [SessionCommand]s; this actor applies them FIFO on one
 * consumer job. Command work lives on [LiveWorkoutSessionRuntime] (+ Session*Commands).
 */
@Suppress("LongParameterList")
internal class LiveWorkoutSessionActor(
    private val scope: CoroutineScope,
    effects: SessionEffects,
    generateSnapshot: GenerateProgressionSnapshotUseCase,
    mergeRenderedWorkout: MergeRenderedWorkoutUseCase,
    projectStructure: ProjectWorkoutStructureUseCase,
    recomputeRecommendationsUseCase: RecomputeRecommendationsUseCase,
    estimateAddedSetReps: EstimateAddedSetRepsUseCase,
    logSetUseCase: LogSetUseCase,
    updateLoggedSet: UpdateLoggedSetUseCase,
    getLoggedSetsForSession: GetLoggedSetsForSessionUseCase,
    deleteLoggedSet: DeleteLoggedSetUseCase,
    finalizeTrainingSession: FinalizeTrainingSessionUseCase,
    applyOverlayToWorkout: ApplyOverlayToWorkoutUseCase,
    saveWorkoutUseCase: SaveWorkoutUseCase,
    getProgramById: GetProgramByIdUseCase,
    observeLiftTrainingInsights: ObserveLiftTrainingInsightsUseCase,
    manageWorkoutOverlay: ManageWorkoutOverlayUseCase,
    private val logError: LogErrorUseCase,
) {
    private var commands = Channel<SessionCommand>(Channel.UNLIMITED)
    private var consumerJob: Job? = null

    private val runtime =
        LiveWorkoutSessionRuntime(
            effects = effects,
            generateSnapshot = generateSnapshot,
            mergeRenderedWorkout = mergeRenderedWorkout,
            projectStructure = projectStructure,
            recomputeRecommendationsUseCase = recomputeRecommendationsUseCase,
            estimateAddedSetReps = estimateAddedSetReps,
            logSetUseCase = logSetUseCase,
            updateLoggedSet = updateLoggedSet,
            getLoggedSetsForSession = getLoggedSetsForSession,
            deleteLoggedSet = deleteLoggedSet,
            finalizeTrainingSession = finalizeTrainingSession,
            applyOverlayToWorkout = applyOverlayToWorkout,
            saveWorkoutUseCase = saveWorkoutUseCase,
            getProgramById = getProgramById,
            observeLiftTrainingInsights = observeLiftTrainingInsights,
            manageWorkoutOverlay = manageWorkoutOverlay,
        )

    init {
        start()
    }

    fun start() {
        if (consumerJob?.isActive == true) return
        commands = Channel(Channel.UNLIMITED)
        consumerJob =
            scope.launch {
                for (cmd in commands) {
                    try {
                        handle(cmd)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        logError(TAG, "Session command failed: ${error.message}", error)
                        publishUnexpectedCommandFailure(cmd)
                    }
                }
            }
    }

    fun stop() {
        consumerJob?.cancel()
        consumerJob = null
        commands.close()
    }

    fun trySend(cmd: SessionCommand): Boolean = commands.trySend(cmd).isSuccess

    suspend fun send(cmd: SessionCommand) {
        commands.send(cmd)
    }

    // region Sync read APIs (best-effort snapshots — see LiveWorkoutSessionRuntime)

    fun isLoadedFor(workoutId: WorkoutId): Boolean =
        runtime.activeWorkoutId == workoutId && runtime.snapshotLoaded

    fun activeWorkoutId(): WorkoutId? = runtime.activeWorkoutId

    fun cachedSessionId(): TrainingSessionId? = runtime.cachedSessionId

    fun sessionStartedAtEpochMs(): Long? = runtime.sessionStartedAt?.toEpochMilli()

    fun requiresCascadeDeleteConfirm(slotId: SetSlotId): Boolean =
        LiveIntensifierOverlayOps.requiresCascadeConfirm(
            runtime.structure,
            runtime.overlay,
            slotId,
        )

    fun requiresMyoMiniDiscardConfirm(slotKey: String, kind: LiveSetKind): Boolean {
        val slot = runtime.resolveSlotByKey(slotKey) ?: return false
        return LiveSetTypeOverlayOps.requiresMyoMiniDiscardConfirm(
            structure = runtime.structure,
            overlay = runtime.overlay,
            slotId = slot.slotId,
            newKind = kind,
        )
    }

    fun mergeRendered(): RenderedWorkout = runtime.merge()

    // endregion

    private suspend fun handle(cmd: SessionCommand) {
        if (handleLifecycle(cmd)) return
        if (runtime.sessionTerminal || runtime.abandonInFlight) return
        dispatchSessionCommand(cmd)
    }

    /** @return true when [cmd] was fully handled as a lifecycle/abandon control command. */
    private suspend fun handleLifecycle(cmd: SessionCommand): Boolean =
        when (cmd) {
            is SessionCommand.StartSession -> {
                runtime.handleStartSession(cmd)
                true
            }
            SessionCommand.BeginAbandon -> {
                runtime.abandonInFlight = true
                true
            }
            SessionCommand.CancelAbandon -> {
                runtime.abandonInFlight = false
                true
            }
            SessionCommand.FinalizeAbandonLocal -> {
                runtime.abandonInFlight = false
                runtime.sessionTerminal = false
                runtime.clearSessionMemory(publishEmpty = true)
                true
            }
            else -> false
        }

    private suspend fun dispatchSessionCommand(cmd: SessionCommand) {
        when (cmd) {
            is SessionCommand.StartSession,
            SessionCommand.BeginAbandon,
            SessionCommand.CancelAbandon,
            SessionCommand.FinalizeAbandonLocal,
            -> Unit // handled in [handleLifecycle]
            is SessionCommand.ApplyCycleSnapshot -> runtime.handleApplyCycleSnapshot(cmd)
            SessionCommand.RenderOnly -> {
                if (runtime.structure != null) runtime.render()
            }
            is SessionCommand.LogSet -> runtime.handleLogSet(cmd)
            is SessionCommand.UnlogSet -> runtime.handleUnlogSet(cmd)
            is SessionCommand.AddSet -> runtime.handleAddSet(cmd)
            is SessionCommand.AddSetForLift -> runtime.handleAddSetForLift(cmd)
            is SessionCommand.DeleteSet -> runtime.handleDeleteSet(cmd)
            is SessionCommand.DeleteSetByKey -> runtime.handleDeleteSetByKey(cmd)
            is SessionCommand.DeleteSetWithCascade -> runtime.handleDeleteSetWithCascade(cmd)
            SessionCommand.Undo -> runtime.handleUndo()
            is SessionCommand.GenerateMyoMini -> runtime.handleGenerateMyoMini(cmd)
            is SessionCommand.AddDropSet -> runtime.handleAddDropSet(cmd)
            is SessionCommand.SetSetKind -> runtime.handleSetSetKind(cmd)
            is SessionCommand.SwapLift -> runtime.handleSwapLift(cmd)
            is SessionCommand.AddLifts -> runtime.handleAddLifts(cmd)
            is SessionCommand.RemoveLift -> runtime.handleRemoveLift(cmd)
            is SessionCommand.ReorderLifts -> runtime.handleReorderLifts(cmd)
            is SessionCommand.LinkSuperset -> runtime.handleLinkSuperset(cmd)
            is SessionCommand.RemoveFromSuperset -> runtime.handleRemoveFromSuperset(cmd)
            is SessionCommand.SetUseRestTimerBetweenLifts ->
                runtime.handleSetUseRestTimerBetweenLifts(cmd)
            is SessionCommand.SetRestSecondsForSlots ->
                runtime.handleSetRestSecondsForSlots(cmd)
            is SessionCommand.SetWorkoutLiftPinnedNotes ->
                runtime.handleSetWorkoutLiftPinnedNotes(cmd)
            is SessionCommand.CompleteWorkout -> runtime.handleCompleteWorkout(cmd)
        }
    }

    private fun publishUnexpectedCommandFailure(cmd: SessionCommand) {
        if (sessionCommandFailureIsTerminal(cmd)) {
            runtime.sessionTerminal = true
            runtime.effects.publishUi(WorkoutUiState.Error(MSG_SESSION_UNRECOVERABLE))
        } else {
            runtime.effects.publishUserMessage(MSG_SESSION_COMMAND_FAILED)
        }
    }

    private companion object {
        const val TAG = "LiveWorkoutSession"
    }
}

internal const val MSG_SESSION_COMMAND_FAILED = "Couldn't update the workout. Try again."
internal const val MSG_SESSION_UNRECOVERABLE =
    "Couldn't continue this workout. Go back and reopen it."

internal fun sessionCommandFailureIsTerminal(cmd: SessionCommand): Boolean =
    when (cmd) {
        is SessionCommand.StartSession,
        is SessionCommand.ApplyCycleSnapshot,
        is SessionCommand.CompleteWorkout,
        -> true
        SessionCommand.RenderOnly,
        is SessionCommand.LogSet,
        is SessionCommand.UnlogSet,
        is SessionCommand.AddSet,
        is SessionCommand.AddSetForLift,
        is SessionCommand.DeleteSet,
        is SessionCommand.DeleteSetByKey,
        is SessionCommand.DeleteSetWithCascade,
        SessionCommand.Undo,
        is SessionCommand.GenerateMyoMini,
        is SessionCommand.AddDropSet,
        is SessionCommand.SetSetKind,
        is SessionCommand.SwapLift,
        is SessionCommand.AddLifts,
        is SessionCommand.RemoveLift,
        is SessionCommand.ReorderLifts,
        is SessionCommand.LinkSuperset,
        is SessionCommand.RemoveFromSuperset,
        is SessionCommand.SetUseRestTimerBetweenLifts,
        is SessionCommand.SetRestSecondsForSlots,
        is SessionCommand.SetWorkoutLiftPinnedNotes,
        SessionCommand.BeginAbandon,
        SessionCommand.CancelAbandon,
        SessionCommand.FinalizeAbandonLocal,
        -> false
    }

// Start session as extension for symmetry
internal suspend fun LiveWorkoutSessionRuntime.handleStartSession(cmd: SessionCommand.StartSession) {
    sessionTerminal = false
    abandonInFlight = false
    clearSessionMemory(publishEmpty = false)
    activeWorkoutId = cmd.routeWorkoutId
    fatigueByLiftId = observeLiftTrainingInsights()
}

/**
 * Resolves the structural baseline for the live session. Ad-hoc uses a synthetic empty
 * [Workout] so overlay add-lift / log flows work without a program template row.
 * Shared by [WorkoutViewModel] when building [SessionCommand.ApplyCycleSnapshot].
 */
internal fun resolveSessionWorkout(
    session: TrainingSession,
    program: Program,
    routeWorkoutId: WorkoutId,
): Workout? =
    when (session.kind) {
        TrainingSessionKind.PROGRAM_WORKOUT ->
            session.workoutId?.let { boundId ->
                program.workouts.firstOrNull { it.id == boundId }
            }
        TrainingSessionKind.AD_HOC ->
            Workout(
                id = routeWorkoutId,
                programId = program.id,
                name = TrainingSessionDisplayName.resolve(session = session, program = program),
                weekday = null,
                position = 0,
                workoutLifts = emptyList(),
            )
        TrainingSessionKind.REST_DAY -> null
    }

/** Program workouts match on template id; ad-hoc empty sessions match on session id. */
internal fun sessionMatchesRoute(session: TrainingSession, routeWorkoutId: WorkoutId): Boolean =
    session.liveWorkoutRouteId() == routeWorkoutId.value
