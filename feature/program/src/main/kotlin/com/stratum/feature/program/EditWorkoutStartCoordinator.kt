package com.stratum.feature.program

import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.training.AbandonTrainingSessionUseCase
import com.stratum.domain.training.ObserveCycleProgressUseCase
import com.stratum.domain.training.StartTrainingSessionRequest
import com.stratum.domain.training.StartTrainingSessionResult
import com.stratum.domain.training.StartTrainingSessionUseCase
import com.stratum.domain.workout.ManageWorkoutOverlayUseCase
import com.stratum.feature.program.ui.EditWorkoutUiState
import kotlinx.coroutines.flow.first

/**
 * Full start pipeline from Edit Workout after UI gates (dock commit, dirty dialog).
 *
 * Order: resolve dirty (save or discard local) → optional abandon → start.
 * Persist always runs **before** abandon so a failed save never drops a live session.
 * Active-session conflicts never silent-resume; they surface [StartOutcome.NeedsActiveSessionDiscard]
 * or a hard failure after the user already confirmed discard.
 */
internal class EditWorkoutStartCoordinator(
    private val startTrainingSession: StartTrainingSessionUseCase,
    private val observeCycleProgress: ObserveCycleProgressUseCase,
    private val abandonTrainingSession: AbandonTrainingSessionUseCase,
    private val manageWorkoutOverlay: ManageWorkoutOverlayUseCase,
) {
    enum class DirtyResolution {
        Save,
        DiscardLocal,
    }

    enum class PersistResult {
        Success,
        Failed,
        Busy,
        Blocked,
    }

    /**
     * @param resolveDirty null means caller already left the editor clean; dirty state is refused.
     * @param persistIfDirty only invoked when [resolveDirty] is [DirtyResolution.Save]
     * @param discardLocalEdits only invoked when [resolveDirty] is [DirtyResolution.DiscardLocal]
     */
    suspend fun run(
        uiState: EditWorkoutUiState,
        scopedProgramId: ProgramId?,
        discardActiveFirst: Boolean,
        resolveDirty: DirtyResolution?,
        persistIfDirty: suspend () -> PersistResult,
        discardLocalEdits: () -> Unit,
    ): StartOutcome {
        val loaded = uiState as? EditWorkoutUiState.Loaded
        val gate = gateStart(loaded, scopedProgramId)
        if (gate != null) return gate

        val dirtyGate =
            resolveDirtyState(requireNotNull(loaded), resolveDirty, persistIfDirty, discardLocalEdits)
        if (dirtyGate != null) return dirtyGate

        return afterTemplateReady(requireNotNull(loaded).workoutId, discardActiveFirst)
    }

    private fun gateStart(
        loaded: EditWorkoutUiState.Loaded?,
        scopedProgramId: ProgramId?,
    ): StartOutcome? =
        when {
            scopedProgramId != null -> StartOutcome.LibraryScoped
            loaded == null -> StartOutcome.NotReady
            loaded.saveBlockedReason != null ->
                StartOutcome.UserMessage(MSG_START_BLOCKED_PLACEHOLDER)
            else -> null
        }

    private suspend fun afterTemplateReady(
        workoutId: String,
        discardActiveFirst: Boolean,
    ): StartOutcome {
        if (discardActiveFirst) {
            val abandonMessage = abandonActiveSessionOrNull()
            if (abandonMessage != null) return StartOutcome.UserMessage(abandonMessage)
        } else if (observeCycleProgress().first().activeSession != null) {
            return StartOutcome.NeedsActiveSessionDiscard
        }
        return startProgramWorkout(WorkoutId(workoutId))
    }

    private suspend fun resolveDirtyState(
        loaded: EditWorkoutUiState.Loaded,
        resolveDirty: DirtyResolution?,
        persistIfDirty: suspend () -> PersistResult,
        discardLocalEdits: () -> Unit,
    ): StartOutcome? =
        when (resolveDirty) {
            DirtyResolution.Save ->
                when (persistIfDirty()) {
                    PersistResult.Success -> null
                    PersistResult.Failed -> StartOutcome.PersistFailed
                    PersistResult.Busy -> StartOutcome.UserMessage(MSG_START_BUSY)
                    PersistResult.Blocked -> StartOutcome.UserMessage(MSG_START_BLOCKED_PLACEHOLDER)
                }
            DirtyResolution.DiscardLocal -> {
                discardLocalEdits()
                null
            }
            null ->
                if (loaded.isDirty) {
                    StartOutcome.UserMessage(MSG_START_DIRTY_UNRESOLVED)
                } else {
                    null
                }
        }

    private suspend fun abandonActiveSessionOrNull(): String? {
        val session = observeCycleProgress().first().activeSession ?: return null
        if (abandonTrainingSession(session.id).isFailure) return MSG_ABANDON_FAILED
        manageWorkoutOverlay.clear()
        return null
    }

    private suspend fun startProgramWorkout(workoutId: WorkoutId): StartOutcome {
        return when (startTrainingSession(StartTrainingSessionRequest.ProgramWorkout(workoutId))) {
            is StartTrainingSessionResult.Success -> StartOutcome.Navigate(workoutId.value)
            is StartTrainingSessionResult.ActiveSessionExists -> StartOutcome.UserMessage(MSG_START_WORKOUT_FAILED)
            else -> StartOutcome.UserMessage(MSG_START_WORKOUT_FAILED)
        }
    }

    sealed interface StartOutcome {
        data object LibraryScoped : StartOutcome
        data object NotReady : StartOutcome
        data object NeedsActiveSessionDiscard : StartOutcome
        /** Persist already set userMessage on failure path. */
        data object PersistFailed : StartOutcome
        data class Navigate(val workoutRouteId: String) : StartOutcome
        data class UserMessage(val message: String) : StartOutcome
    }

    companion object {
        const val MSG_START_WORKOUT_FAILED = "Couldn't start that workout. Try again."
        const val MSG_ABANDON_FAILED = "Couldn't cancel the workout. Try again."
        const val MSG_START_BLOCKED_PLACEHOLDER =
            "Choose an exercise for each new lift before starting."
        const val MSG_START_BUSY = "Still working — try again in a moment."
        const val MSG_START_DIRTY_UNRESOLVED =
            "Save or discard your edits before starting the workout."

        fun persistResultFor(
            loaded: EditWorkoutUiState.Loaded?,
            isSaving: Boolean,
        ): PersistResult? {
            if (loaded == null) return PersistResult.Failed
            if (loaded.saveBlockedReason != null) return PersistResult.Blocked
            if (isSaving || loaded.isSaving) return PersistResult.Busy
            return null
        }
    }
}
