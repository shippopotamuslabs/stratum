package com.stratum.liftconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.lift.AddCustomEquipmentUseCase
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.ObserveEquipmentCatalogUseCase
import com.stratum.domain.lift.identity.EquipmentId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

data class ManageEquipmentUiState(
    val rows: List<EquipmentRowUi> = emptyList(),
    val nameDraft: String = "",
    val isCreating: Boolean = false,
    val userMessage: String? = null,
)

@HiltViewModel
class ManageEquipmentViewModel @Inject constructor(
    observeEquipmentCatalog: ObserveEquipmentCatalogUseCase,
    private val addCustomEquipment: AddCustomEquipmentUseCase,
    private val equipmentPickResult: EquipmentPickResult,
) : ViewModel() {

    private val nameDraft = MutableStateFlow("")
    private val isCreating = MutableStateFlow(false)
    private val _userMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ManageEquipmentUiState> =
        combine(
            observeEquipmentCatalog(),
            nameDraft,
            isCreating,
            _userMessage,
        ) { catalog, draft, creating, message ->
            ManageEquipmentUiState(
                rows =
                    catalog
                        .filterNot { it.id == BODYWEIGHT_EQUIPMENT_ID }
                        .map { it.toRowUi() },
                nameDraft = draft,
                isCreating = creating,
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

    fun userMessageShown() {
        _userMessage.value = null
    }

    /**
     * Creates equipment from [nameDraft]. Invokes [onResult] with the new id on success, or
     * null on failure. Does not clear the draft / dismiss UI until success.
     */
    fun onCreateEquipment(onResult: (String?) -> Unit = {}) {
        val name = nameDraft.value.trim()
        if (name.isEmpty() || isCreating.value) return
        viewModelScope.launch {
            isCreating.value = true
            val result = addCustomEquipment(name)
            isCreating.value = false
            result
                .onSuccess { id ->
                    nameDraft.value = ""
                    onResult(id.value)
                }
                .onFailure { error ->
                    _userMessage.value =
                        error.message?.takeIf { it.isNotBlank() }
                            ?: MSG_CREATE_FAILED
                    onResult(null)
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

    private companion object {
        const val MSG_CREATE_FAILED = "Couldn't create equipment. Try again."

        /** Seed equipment with no meaningful weight bands — omit from manage/pick lists. */
        val BODYWEIGHT_EQUIPMENT_ID = EquipmentId("bodyweight")
    }
}
