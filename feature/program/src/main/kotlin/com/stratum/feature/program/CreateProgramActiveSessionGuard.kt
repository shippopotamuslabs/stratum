package com.stratum.feature.program

import com.stratum.domain.program.Program
import com.stratum.domain.training.AbandonTrainingSessionUseCase
import com.stratum.domain.training.ObserveActiveSessionUseCase
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.isOrphanedAgainstActiveProgram
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/** Persist targets that may orphan a live session when applied. */
internal enum class CreateProgramPersistMode {
    SaveToLibrary,
    SavePreservingActive,
    Activate,
}

/**
 * Confirms and abandons an in-progress training session when create/edit program actions would
 * leave that session without a resumable template.
 */
internal class CreateProgramActiveSessionGuard(
    private val state: MutableStateFlow<CreateProgramUiState>,
    private val observeActiveSession: ObserveActiveSessionUseCase,
    private val abandonTrainingSession: AbandonTrainingSessionUseCase,
) {
    private var pendingPersistMode: CreateProgramPersistMode? = null
    private var pendingPersistOnDone: (() -> Unit)? = null

    suspend fun inProgressSessionOrNull(): TrainingSession? {
        val session = observeActiveSession().first() ?: return null
        return session.takeIf { it.status == TrainingSessionStatus.IN_PROGRESS }
    }

    fun isActiveWorkoutDay(dayId: String, session: TrainingSession?): Boolean =
        session?.workoutId?.value == dayId

    /**
     * True when applying [draft] under [mode] would leave the live session without a resumable
     * template (deleted workout, program switch, demote-active, etc.).
     */
    fun wouldDiscardActiveSession(
        session: TrainingSession,
        draft: Program,
        mode: CreateProgramPersistMode,
        current: CreateProgramUiState,
    ): Boolean {
        val resultingActive: Program? =
            when (mode) {
                CreateProgramPersistMode.Activate -> draft.copy(isActive = true)
                CreateProgramPersistMode.SavePreservingActive -> draft
                CreateProgramPersistMode.SaveToLibrary -> {
                    // Library save of an inactive program does not rewrite the active template.
                    // Demoting the currently active program (edit + save to library) leaves no active.
                    if (current.originalIsActive || draft.isActive) {
                        null
                    } else {
                        return false
                    }
                }
            }
        return session.isOrphanedAgainstActiveProgram(resultingActive)
    }

    fun stageRemoveDayDiscard() {
        state.update {
            it.copy(pendingActiveSessionDiscard = ActiveSessionDiscardReason.RemoveSelectedDay)
        }
    }

    fun stagePersistDiscard(mode: CreateProgramPersistMode, onDone: () -> Unit) {
        pendingPersistMode = mode
        pendingPersistOnDone = onDone
        state.update {
            it.copy(pendingActiveSessionDiscard = discardReasonFor(mode))
        }
    }

    fun dismiss() {
        pendingPersistMode = null
        pendingPersistOnDone = null
        state.update { it.copy(pendingActiveSessionDiscard = null) }
    }

    /**
     * Abandons the live session (if any) and returns the confirmed reason, or null when abandon
     * failed (error already written to [state]).
     */
    suspend fun confirmAbandon(): ActiveSessionDiscardReason? {
        val reason = state.value.pendingActiveSessionDiscard ?: return null
        val session = inProgressSessionOrNull()
        if (session != null) {
            abandonTrainingSession(session.id).onFailure { error ->
                pendingPersistMode = null
                pendingPersistOnDone = null
                state.update {
                    it.copy(
                        pendingActiveSessionDiscard = null,
                        saveErrorMessage =
                            error.message ?: "Couldn't discard the in-progress workout. Try again.",
                    )
                }
                return null
            }
        }
        state.update { it.copy(pendingActiveSessionDiscard = null) }
        return reason
    }

    fun takePendingPersist(): Pair<CreateProgramPersistMode, () -> Unit>? {
        val mode = pendingPersistMode
        val onDone = pendingPersistOnDone
        pendingPersistMode = null
        pendingPersistOnDone = null
        return if (mode != null && onDone != null) mode to onDone else null
    }

    private fun discardReasonFor(mode: CreateProgramPersistMode): ActiveSessionDiscardReason =
        when (mode) {
            CreateProgramPersistMode.Activate -> ActiveSessionDiscardReason.Activate
            CreateProgramPersistMode.SavePreservingActive -> ActiveSessionDiscardReason.SaveChanges
            CreateProgramPersistMode.SaveToLibrary -> ActiveSessionDiscardReason.SaveToLibrary
        }
}
