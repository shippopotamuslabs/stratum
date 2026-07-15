package com.stratum.feature.program

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.program.ActivateStoredProgramUseCase
import com.stratum.domain.program.DeleteProgramUseCase
import com.stratum.domain.program.ObserveLibraryProgramsUseCase
import com.stratum.domain.program.ObserveProgramUseCase
import com.stratum.domain.program.Program
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.training.AbandonTrainingSessionUseCase
import com.stratum.domain.training.ObserveActiveSessionUseCase
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.feature.program.ui.ProgramLibraryUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ProgramLibraryViewModel @Inject constructor(
    observeProgram: ObserveProgramUseCase,
    observeLibraryPrograms: ObserveLibraryProgramsUseCase,
    observeLiftCatalog: ObserveLiftCatalogUseCase,
    private val observeActiveSession: ObserveActiveSessionUseCase,
    private val activateStoredProgram: ActivateStoredProgramUseCase,
    private val abandonTrainingSession: AbandonTrainingSessionUseCase,
    private val deleteProgram: DeleteProgramUseCase,
) : ViewModel() {

    private val expandedProgramIds = MutableStateFlow<Set<String>>(emptySet())
    private val pendingDeleteProgramId = MutableStateFlow<String?>(null)
    private val pendingActivateProgramId = MutableStateFlow<String?>(null)
    private val isDeleting = MutableStateFlow(false)
    private val userMessage = MutableStateFlow<String?>(null)
    private val restDayLabel = MutableStateFlow("Rest Day")

    private val programsFlow =
        combine(
            observeProgram(),
            observeLibraryPrograms(),
        ) { active, library ->
            mergeActiveAndLibraryPrograms(active, library)
        }

    private val deleteTransientFlow =
        combine(
            expandedProgramIds,
            pendingDeleteProgramId,
            userMessage,
            isDeleting,
            restDayLabel,
        ) { expanded, pendingId, message, deleting, restDay ->
            DeleteTransientUi(
                expanded = expanded,
                pendingDeleteId = pendingId,
                userMessage = message,
                isDeleting = deleting,
                restDayName = restDay,
            )
        }

    private val transientFlow =
        combine(
            deleteTransientFlow,
            pendingActivateProgramId,
        ) { deleteTransient, pendingActivateId ->
            TransientUi(
                expanded = deleteTransient.expanded,
                pendingDeleteId = deleteTransient.pendingDeleteId,
                pendingActivateId = pendingActivateId,
                userMessage = deleteTransient.userMessage,
                isDeleting = deleteTransient.isDeleting,
                restDayName = deleteTransient.restDayName,
            )
        }

    val uiState: StateFlow<ProgramLibraryUiState> =
        combine(
            programsFlow,
            observeLiftCatalog(),
            observeActiveSession(),
            transientFlow,
        ) { programs, lifts, activeSession, transient ->
            mapUiState(programs, lifts, activeSession, transient)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProgramLibraryUiState(),
        )

    /** Call from the route with a localized rest-day string. */
    fun setRestDayLabel(label: String) {
        restDayLabel.value = label
    }

    fun toggleExpanded(programId: String) {
        expandedProgramIds.update { current ->
            if (programId in current) current - programId else current + programId
        }
    }

    fun requestDelete(programId: String) {
        if (isDeleting.value) return
        pendingDeleteProgramId.value = programId
    }

    fun dismissDelete() {
        if (isDeleting.value) return
        pendingDeleteProgramId.value = null
    }

    fun confirmDelete() {
        val id = pendingDeleteProgramId.value ?: return
        if (isDeleting.value) return
        isDeleting.value = true
        viewModelScope.launch {
            deleteProgram(ProgramId(id))
                .onSuccess {
                    pendingDeleteProgramId.value = null
                    expandedProgramIds.update { it - id }
                }
                .onFailure { error ->
                    userMessage.value = error.message ?: "Couldn't delete program. Try again."
                    pendingDeleteProgramId.value = null
                }
            isDeleting.value = false
        }
    }

    fun activateProgram(programId: String) {
        if (isDeleting.value) return
        viewModelScope.launch {
            val target = ProgramId(programId)
            val programs = programsFlow.first()
            val alreadyActive = programs.any { program -> program.id == target && program.isActive }
            if (alreadyActive) return@launch

            val session = observeActiveSession().first()
            val hasLiveSession =
                session != null && session.status == TrainingSessionStatus.IN_PROGRESS
            if (hasLiveSession) {
                pendingActivateProgramId.value = programId
                return@launch
            }
            performActivate(programId)
        }
    }

    fun dismissActivate() {
        pendingActivateProgramId.value = null
    }

    fun confirmActivateDiscardingSession() {
        val programId = pendingActivateProgramId.value ?: return
        viewModelScope.launch {
            val session = observeActiveSession().first()
            if (session != null && session.status == TrainingSessionStatus.IN_PROGRESS) {
                val abandonResult = abandonTrainingSession(session.id)
                if (abandonResult.isFailure) {
                    userMessage.value =
                        abandonResult.exceptionOrNull()?.message
                            ?: "Couldn't discard the in-progress workout. Try again."
                    pendingActivateProgramId.value = null
                    return@launch
                }
            }
            pendingActivateProgramId.value = null
            performActivate(programId)
        }
    }

    private suspend fun performActivate(programId: String) {
        activateStoredProgram(ProgramId(programId))
            .onFailure { error ->
                userMessage.value = error.message ?: "Couldn't activate program. Try again."
            }
    }

    fun onUserMessageShown() {
        userMessage.value = null
    }

    private fun mapUiState(
        programs: List<Program>,
        lifts: List<Lift>,
        activeSession: TrainingSession?,
        transient: TransientUi,
    ): ProgramLibraryUiState {
        val liftsById = lifts.associateBy { lift -> lift.id }
        val cards =
            programs.toProgramLibraryCards(
                liftsById = liftsById,
                restDayName = transient.restDayName,
            )
        val pendingDeleteProgram =
            programs.firstOrNull { program -> program.id.value == transient.pendingDeleteId }
        val pendingActivateProgram =
            programs.firstOrNull { program -> program.id.value == transient.pendingActivateId }
        val hasActiveSession =
            activeSession != null &&
                activeSession.status == TrainingSessionStatus.IN_PROGRESS &&
                transient.pendingDeleteId != null &&
                activeSession.programId?.value == transient.pendingDeleteId
        return ProgramLibraryUiState(
            programs = cards,
            expandedProgramIds = transient.expanded,
            pendingDeleteProgramId = transient.pendingDeleteId,
            pendingDeleteProgramName = pendingDeleteProgram?.name,
            pendingDeleteHasActiveSession = hasActiveSession,
            pendingActivateProgramId = transient.pendingActivateId,
            pendingActivateProgramName = pendingActivateProgram?.name,
            isDeleting = transient.isDeleting,
            userMessage = transient.userMessage,
        )
    }

    private data class DeleteTransientUi(
        val expanded: Set<String>,
        val pendingDeleteId: String?,
        val userMessage: String?,
        val isDeleting: Boolean,
        val restDayName: String,
    )

    private data class TransientUi(
        val expanded: Set<String>,
        val pendingDeleteId: String?,
        val pendingActivateId: String?,
        val userMessage: String?,
        val isDeleting: Boolean,
        val restDayName: String,
    )
}

/**
 * Merges active + library program lists for the library screen.
 *
 * Active and library flows can re-emit out of lockstep during [ActivateStoredProgramUseCase]
 * (demote/promote). Without de-duplication the same [ProgramId] can appear twice and crash
 * LazyColumn keys on the library UI.
 */
internal fun mergeActiveAndLibraryPrograms(
    active: Program?,
    library: List<Program>,
): List<Program> =
    buildList {
        val activeId = active?.id
        if (active != null) add(active)
        addAll(
            library
                .filter { program -> program.id != activeId }
                .sortedByDescending { program -> program.createdAt },
        )
    }
