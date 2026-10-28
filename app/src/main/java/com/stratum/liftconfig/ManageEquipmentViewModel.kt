package com.stratum.liftconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.lift.AddCustomEquipmentUseCase
import com.stratum.domain.lift.DeleteCustomEquipmentUseCase
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.GetEquipmentDeleteImpactUseCase
import com.stratum.domain.lift.ForkEquipmentUseCase
import com.stratum.domain.lift.uniqueifyForkEquipmentName
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.ObserveEquipmentCatalogUseCase
import com.stratum.domain.lift.SeedEquipmentIds
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.EquipmentPickResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One weight-band chip on an equipment row. Bounds and increment are already formatted
 * for display; the composable chooses range vs open-ended string resources.
 */
data class EquipmentTierChipUi(
    val lowerBound: String,
    /** Null means open-ended ("and above"). */
    val upperBound: String?,
    val increment: String,
)

data class EquipmentRowUi(
    val id: String,
    val name: String,
    val tierChips: List<EquipmentTierChipUi>,
    val isCustom: Boolean,
    /** STR-105 eligibility: visible seed + custom rows delete; hidden system rows never do. */
    val canDelete: Boolean = false,
)

/** Equipment delete staged in the confirmation dialog (STR-105 impact + pick). */
data class PendingDeleteUi(
    val id: String,
    val name: String,
    val affectedLiftNames: List<String> = emptyList(),
    val affectedWorkouts: List<AffectedWorkoutUi> = emptyList(),
    val candidates: List<ReplacementOptionUi> = emptyList(),
    val selectedReplacementId: String? = null,
    val usesBodyweightFallback: Boolean = false,
) {
    val hasAffectedPointers: Boolean
        get() = affectedLiftNames.isNotEmpty() || affectedWorkouts.isNotEmpty()

    /** Single gate shared by dialog enablement and both ViewModel confirms. */
    val canConfirmDelete: Boolean
        get() = !hasAffectedPointers || usesBodyweightFallback || selectedReplacementId != null
}

/** One canonical impact -> dialog-state mapping used by both delete surfaces. */
internal fun com.stratum.domain.lift.EquipmentDeleteImpact.toPendingDeleteUi(): PendingDeleteUi =
    PendingDeleteUi(
        id = equipmentId.value,
        name = equipmentName,
        affectedLiftNames = affectedLifts.map { it.name },
        affectedWorkouts =
            affectedWorkouts.map {
                AffectedWorkoutUi(name = it.workoutName, slotCount = it.slotCount)
            },
        candidates =
            replacementCandidates.map {
                ReplacementOptionUi(id = it.id.value, name = it.name)
            },
        usesBodyweightFallback = usesBodyweightFallback,
    )

/**
 * One-shot navigation / sheet effects after create or fork succeeds.
 * Route collects [ManageEquipmentViewModel.events] instead of nested callbacks.
 */
sealed interface ManageEquipmentEvent {
    /** Custom equipment created; dismiss create sheet. Optional select+close in selection mode. */
    data class Created(val equipmentId: String) : ManageEquipmentEvent

    /** Fork finished; fork sheet already closed via state. Optional select+close. */
    data class Forked(val equipmentId: String) : ManageEquipmentEvent
}

data class ManageEquipmentUiState(
    val rows: List<EquipmentRowUi> = emptyList(),
    val nameDraft: String = "",
    val isCreating: Boolean = false,
    /** When true, the create-equipment bottom sheet should be visible. */
    val showCreateSheet: Boolean = false,
    /** When non-null, the fork sheet is open for this source equipment id. */
    val forkingFromId: String? = null,
    val forkingFromName: String? = null,
    val forkNameDraft: String = "",
    val isForking: Boolean = false,
    /** When non-null, the delete confirmation dialog is open for this equipment. */
    val pendingDelete: PendingDeleteUi? = null,
    val userMessage: String? = null,
)

@HiltViewModel
class ManageEquipmentViewModel @Inject constructor(
    observeEquipmentCatalog: ObserveEquipmentCatalogUseCase,
    private val addCustomEquipment: AddCustomEquipmentUseCase,
    private val forkEquipment: ForkEquipmentUseCase,
    private val deleteCustomEquipment: DeleteCustomEquipmentUseCase,
    private val getDeleteImpact: GetEquipmentDeleteImpactUseCase,
    private val equipmentPickResult: EquipmentPickResult,
) : ViewModel() {

    private val nameDraft = MutableStateFlow("")
    private val isCreating = MutableStateFlow(false)
    private val showCreateSheet = MutableStateFlow(false)
    private val forkDraft = MutableStateFlow(ForkDraft())
    private val pendingDelete = MutableStateFlow<PendingDeleteUi?>(null)
    private val _userMessage = MutableStateFlow<String?>(null)
    private val _events = Channel<ManageEquipmentEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState: StateFlow<ManageEquipmentUiState> =
        combine(
            combine(
                observeEquipmentCatalog(),
                nameDraft,
                isCreating,
                showCreateSheet,
                forkDraft,
            ) { catalog, draft, creating, showCreate, fork ->
                CatalogSlice(
                    rows =
                        catalog
                            .filterNot { it.id in SeedEquipmentIds.hiddenFromCatalog }
                            .map { it.toRowUi() },
                    nameDraft = draft,
                    isCreating = creating,
                    showCreateSheet = showCreate,
                    fork = fork,
                )
            },
            _userMessage,
            pendingDelete,
        ) { slice, message, delete ->
            ManageEquipmentUiState(
                rows = slice.rows,
                nameDraft = slice.nameDraft,
                isCreating = slice.isCreating,
                showCreateSheet = slice.showCreateSheet,
                forkingFromId = slice.fork.sourceId,
                forkingFromName = slice.fork.sourceName,
                forkNameDraft = slice.fork.nameDraft,
                isForking = slice.fork.isForking,
                pendingDelete = delete,
                userMessage = message,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ManageEquipmentUiState(),
        )

    fun onNameDraftChange(value: String) {
        nameDraft.value = value
    }

    fun onForkNameDraftChange(value: String) {
        forkDraft.value = forkDraft.value.copy(nameDraft = value)
    }

    fun userMessageShown() {
        _userMessage.value = null
    }

    fun onOpenCreate() {
        showCreateSheet.value = true
    }

    fun onDismissCreate() {
        if (isCreating.value) return
        showCreateSheet.value = false
    }

    /**
     * Opens the fork sheet prefilled with the source's own name, uniqueified against
     * the catalog (STR-246): unchanged when the name is free, otherwise the lowest
     * free numbered suffix (`"Name (1)"`, `"Name (2)"`, …).
     */
    fun onStartFork(equipmentId: String) {
        val row = uiState.value.rows.firstOrNull { it.id == equipmentId } ?: return
        forkDraft.value =
            ForkDraft(
                sourceId = equipmentId,
                sourceName = row.name,
                nameDraft =
                    uniqueifyForkEquipmentName(
                        row.name,
                        uiState.value.rows.map { it.name },
                    ),
                isForking = false,
            )
    }

    fun onDismissFork() {
        if (forkDraft.value.isForking) return
        forkDraft.value = ForkDraft()
    }

    /** Forks the staged source equipment. Emits [ManageEquipmentEvent.Forked] on success. */
    fun onConfirmFork() {
        val fork = forkDraft.value
        val sourceId = fork.sourceId ?: return
        val name = fork.nameDraft.trim()
        if (name.isEmpty() || fork.isForking) return
        viewModelScope.launch {
            forkDraft.value = fork.copy(isForking = true)
            val result = forkEquipment(EquipmentId(sourceId), name)
            result
                .onSuccess { forked ->
                    forkDraft.value = ForkDraft()
                    _userMessage.value = "Created $name."
                    _events.send(ManageEquipmentEvent.Forked(forked.id.value))
                }
                .onFailure { error ->
                    forkDraft.value = forkDraft.value.copy(isForking = false)
                    _userMessage.value =
                        error.message?.takeIf { it.isNotBlank() }
                            ?: MSG_FORK_FAILED
                }
        }
    }

    /**
     * Creates equipment from [nameDraft]. Emits [ManageEquipmentEvent.Created] on success.
     * Does not clear the draft / dismiss UI until success.
     */
    fun onCreateEquipment() {
        val name = nameDraft.value.trim()
        if (name.isEmpty() || isCreating.value) return
        viewModelScope.launch {
            isCreating.value = true
            val result = addCustomEquipment(name)
            isCreating.value = false
            result
                .onSuccess { id ->
                    nameDraft.value = ""
                    showCreateSheet.value = false
                    _events.send(ManageEquipmentEvent.Created(id.value))
                }
                .onFailure { error ->
                    _userMessage.value =
                        error.message?.takeIf { it.isNotBlank() }
                            ?: MSG_CREATE_FAILED
                }
        }
    }

    /** Selection-mode: publish pick for Create Lift / Lift Information collectors. */
    fun selectEquipment(equipmentId: String) {
        equipmentPickResult.publish(EquipmentId(equipmentId))
    }

    private fun Equipment.toRowUi(): EquipmentRowUi =
        EquipmentRowUi(
            id = id.value,
            name = name,
            tierChips = incrementTiers.map { it.toChipUi() },
            isCustom = isCustom,
            canDelete = id !in SeedEquipmentIds.hiddenFromCatalog && !isTombstoned,
        )

    private fun IncrementTier.toChipUi(): EquipmentTierChipUi =
        EquipmentTierChipUi(
            lowerBound = IncrementTierDraftOps.formatAmount(lowerBound.amount),
            upperBound = upperBound?.amount?.let(IncrementTierDraftOps::formatAmount),
            increment = IncrementTierDraftOps.formatAmount(increment.amount),
        )

    private data class ForkDraft(
        val sourceId: String? = null,
        val sourceName: String? = null,
        val nameDraft: String = "",
        val isForking: Boolean = false,
    )

    /** Arms the delete confirmation dialog; loads the STR-105 impact first. */
    private var deleteLoadJob: kotlinx.coroutines.Job? = null

    fun onStartDelete(equipmentId: String) {
        val row = uiState.value.rows.firstOrNull { it.id == equipmentId } ?: return
        if (!row.canDelete) return
        deleteLoadJob?.cancel()
        deleteLoadJob =
            viewModelScope.launch {
                val impact = getDeleteImpact(EquipmentId(equipmentId))
                pendingDelete.value =
                    impact?.toPendingDeleteUi()
                        ?: PendingDeleteUi(id = equipmentId, name = row.name)
            }
    }

    /** Single replacement pick for the whole delete operation. */
    fun onSelectReplacement(replacementId: String) {
        val pending = pendingDelete.value ?: return
        pendingDelete.value = pending.copy(selectedReplacementId = replacementId)
    }

    fun onDismissDelete() {
        deleteLoadJob?.cancel()
        deleteLoadJob = null
        pendingDelete.value = null
    }

    /** Tombstones the staged equipment with its chosen replacement. */
    fun onConfirmDelete() {
        val pending = pendingDelete.value ?: return
        if (!pending.canConfirmDelete) return
        val replacement =
            if (pending.hasAffectedPointers && !pending.usesBodyweightFallback) {
                pending.selectedReplacementId?.let(::EquipmentId)
            } else {
                null
            }
        viewModelScope.launch {
            val result = deleteCustomEquipment(EquipmentId(pending.id), replacement)
            pendingDelete.value = null
            result
                .onSuccess {
                    _userMessage.value = "Deleted ${pending.name}."
                }
                .onFailure { error ->
                    _userMessage.value =
                        error.message?.takeIf { it.isNotBlank() }
                            ?: MSG_DELETE_FAILED
                }
        }
    }

    private data class CatalogSlice(
        val rows: List<EquipmentRowUi>,
        val nameDraft: String,
        val isCreating: Boolean,
        val showCreateSheet: Boolean,
        val fork: ForkDraft,
    )

    private companion object {
        const val MSG_CREATE_FAILED = "Couldn't create equipment. Try again."
        const val MSG_FORK_FAILED = "Couldn't copy equipment. Try again."
        const val MSG_DELETE_FAILED = "Couldn't delete equipment. Try again."
    }
}
