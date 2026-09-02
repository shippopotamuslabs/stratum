package com.stratum.liftconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.lift.AddCustomEquipmentUseCase
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.ForkEquipmentUseCase
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.ObserveEquipmentCatalogUseCase
import com.stratum.domain.lift.SeedEquipmentIds
import com.stratum.domain.lift.identity.EquipmentId
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
    val userMessage: String? = null,
)

@HiltViewModel
class ManageEquipmentViewModel @Inject constructor(
    observeEquipmentCatalog: ObserveEquipmentCatalogUseCase,
    private val addCustomEquipment: AddCustomEquipmentUseCase,
    private val forkEquipment: ForkEquipmentUseCase,
    private val equipmentPickResult: EquipmentPickResult,
) : ViewModel() {

    private val nameDraft = MutableStateFlow("")
    private val isCreating = MutableStateFlow(false)
    private val showCreateSheet = MutableStateFlow(false)
    private val forkDraft = MutableStateFlow(ForkDraft())
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
        ) { slice, message ->
            ManageEquipmentUiState(
                rows = slice.rows,
                nameDraft = slice.nameDraft,
                isCreating = slice.isCreating,
                showCreateSheet = slice.showCreateSheet,
                forkingFromId = slice.fork.sourceId,
                forkingFromName = slice.fork.sourceName,
                forkNameDraft = slice.fork.nameDraft,
                isForking = slice.fork.isForking,
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
     * Opens the fork sheet with prefilled name `"{name} (copy)"`.
     */
    fun onStartFork(equipmentId: String) {
        val row = uiState.value.rows.firstOrNull { it.id == equipmentId } ?: return
        forkDraft.value =
            ForkDraft(
                sourceId = equipmentId,
                sourceName = row.name,
                nameDraft = "${row.name}$FORK_NAME_SUFFIX",
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
                .onSuccess { id ->
                    forkDraft.value = ForkDraft()
                    _userMessage.value = "Created $name."
                    _events.send(ManageEquipmentEvent.Forked(id.value))
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
        const val FORK_NAME_SUFFIX = " (copy)"
    }
}
