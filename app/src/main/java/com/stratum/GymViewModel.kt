package com.stratum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.core.ui.R
import com.stratum.core.ui.component.LiftLibraryFilter
import com.stratum.core.ui.component.LiftSwapDropdownOption
import com.stratum.core.ui.component.LiftSwapFilterIntent
import com.stratum.core.ui.component.LiftSwapMuscleTiers
import com.stratum.core.ui.component.LiftSwapOptionState
import com.stratum.core.ui.component.LiftSwapSheetState
import com.stratum.core.ui.component.defaultMuscleFilterTiers
import com.stratum.core.ui.component.toVolumeDisplayName
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LiftCategory
import com.stratum.domain.lift.MergeLiftsUseCase
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.lift.SetLiftEnabledUseCase
import com.stratum.domain.lift.classifyLiftCategory
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GymFilters(
    val selectedMuscles: Set<MuscleGroup> = emptySet(),
    val selectedBias: MuscleLengthBias? = null,
    val selectedCategory: LiftCategory? = null,
    val selectedLibrary: LiftLibraryFilter? = null,
    val searchQuery: String = "",
) {
    val anyActive: Boolean
        get() =
            selectedMuscles.isNotEmpty() ||
                selectedBias != null ||
                selectedCategory != null ||
                selectedLibrary != null
}

data class GymUiState(
    val sheet: LiftSwapSheetState,
    val isMergeMode: Boolean = false,
    val isMerging: Boolean = false,
) {
    val canConfirmMerge: Boolean
        get() = isMergeMode && !isMerging && sheet.selectedOptions.size >= 2
}

@HiltViewModel
class GymViewModel @Inject constructor(
    private val observeLiftCatalog: ObserveLiftCatalogUseCase,
    private val setLiftEnabled: SetLiftEnabledUseCase,
    private val mergeLifts: MergeLiftsUseCase,
) : ViewModel() {

    private val filters = MutableStateFlow(GymFilters())
    private val mergeSelection = MutableStateFlow(MergeSelection())
    private val isMerging = MutableStateFlow(false)
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun userMessageShown() {
        _userMessage.value = null
    }

    val uiState: StateFlow<GymUiState> =
        combine(
            observeLiftCatalog(),
            filters.asStateFlow(),
            mergeSelection.asStateFlow(),
            isMerging.asStateFlow(),
        ) { catalog, filters, merge, merging ->
            val base = buildGymCatalogState(catalog, filters)
            val selectedOptions =
                if (!merge.active) {
                    emptyList()
                } else {
                    merge.selectedIds.mapNotNull { id ->
                        base.options.firstOrNull { it.id == id }
                            ?: catalog.firstOrNull { it.id.value == id }?.toOptionState()
                    }
                }
            GymUiState(
                sheet =
                    base.copy(
                        selectedOptions = selectedOptions,
                    ),
                isMergeMode = merge.active,
                isMerging = merging,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            initialValue =
                GymUiState(
                    sheet =
                        LiftSwapSheetState(
                            targetLiftName = "",
                            options = emptyList(),
                            biasOptions = emptyList(),
                            categoryOptions = emptyList(),
                            libraryOptions = emptyList(),
                            muscleGroups = LiftSwapMuscleTiers(emptyList(), emptyList()),
                            selectedMuscles = emptySet(),
                            selectedBias = null,
                            selectedCategory = null,
                            selectedLibrary = null,
                            searchQuery = "",
                        ),
                ),
        )

    fun onFilterIntent(intent: LiftSwapFilterIntent) {
        filters.value =
            when (intent) {
                is LiftSwapFilterIntent.ToggleMuscle ->
                    filters.value.copy(
                        selectedMuscles = filters.value.selectedMuscles.toggle(intent.muscle),
                    )

                is LiftSwapFilterIntent.SelectBias ->
                    filters.value.copy(selectedBias = intent.bias)

                is LiftSwapFilterIntent.SelectCategory ->
                    filters.value.copy(selectedCategory = intent.category)

                is LiftSwapFilterIntent.SelectLibrary ->
                    filters.value.copy(selectedLibrary = intent.library)

                LiftSwapFilterIntent.ResetMuscles ->
                    filters.value.copy(selectedMuscles = emptySet())

                LiftSwapFilterIntent.ResetAll -> GymFilters()
            }
    }

    fun onSearchQueryChanged(query: String) {
        filters.value = filters.value.copy(searchQuery = query)
    }

    fun toggleLiftEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            setLiftEnabled(LiftId(id), enabled).onFailure {
                _userMessage.value = MSG_TOGGLE_FAILED
            }
        }
    }

    fun enterMergeMode(originLiftId: String) {
        mergeSelection.value = MergeSelection(active = true, selectedIds = listOf(originLiftId))
    }

    fun exitMergeMode() {
        if (isMerging.value) return
        mergeSelection.value = MergeSelection()
    }

    fun toggleMergeSelection(option: LiftSwapOptionState) {
        if (isMerging.value) return
        mergeSelection.update { current ->
            if (!current.active) return@update current
            val nextIds =
                if (option.id in current.selectedIds) {
                    current.selectedIds - option.id
                } else {
                    current.selectedIds + option.id
                }
            current.copy(selectedIds = nextIds)
        }
    }

    fun deselectFromMerge(liftId: String) {
        if (isMerging.value) return
        mergeSelection.update { current ->
            current.copy(selectedIds = current.selectedIds - liftId)
        }
    }

    fun confirmMerge(targetLiftId: String) {
        if (isMerging.value) return
        if (!uiState.value.canConfirmMerge) {
            _userMessage.value = MSG_NEED_TWO
            return
        }
        // Snapshot selection at click so concurrent toggles cannot change the job.
        val sourceIds =
            mergeSelection.value.selectedIds
                .filter { it != targetLiftId }
                .map { LiftId(it) }
                .toSet()
        if (sourceIds.isEmpty()) {
            _userMessage.value = MSG_NEED_TWO
            return
        }
        isMerging.value = true
        viewModelScope.launch {
            try {
                mergeLifts(
                    targetLiftId = LiftId(targetLiftId),
                    sourceLiftIds = sourceIds,
                ).onSuccess {
                    mergeSelection.value = MergeSelection()
                    _userMessage.value = MSG_MERGE_SUCCESS
                }.onFailure {
                    _userMessage.value = MSG_MERGE_FAILED
                }
            } finally {
                isMerging.value = false
            }
        }
    }

    private companion object {
        const val MSG_TOGGLE_FAILED = "Couldn't update that lift. Try again."
        const val MSG_MERGE_SUCCESS = "Lifts merged"
        const val MSG_MERGE_FAILED = "Couldn't merge those lifts. Try again."
        const val MSG_NEED_TWO = "Select at least two lifts to merge"
    }

    private fun <T> Set<T>.toggle(value: T): Set<T> =
        if (value in this) this - value else this + value
}

private data class MergeSelection(
    val active: Boolean = false,
    val selectedIds: List<String> = emptyList(),
)

private fun buildGymCatalogState(
    catalog: List<Lift>,
    filters: GymFilters,
): LiftSwapSheetState {
    val filtered =
        catalog
            .asSequence()
            .filter { lift -> filters.selectedMuscles.isEmpty() || lift.primaryMuscles.any { it in filters.selectedMuscles } }
            .filter { lift -> filters.selectedBias == null || lift.muscleLengthBias == filters.selectedBias }
            .filter { lift -> filters.selectedCategory == null || lift.classifyLiftCategory() == filters.selectedCategory }
            .filter { lift ->
                when (filters.selectedLibrary) {
                    null -> true
                    LiftLibraryFilter.Custom -> lift.isCustom
                    LiftLibraryFilter.StratumDefault -> !lift.isCustom
                }
            }
            .filter { lift ->
                filters.searchQuery.isBlank() ||
                    lift.name.contains(filters.searchQuery.trim(), ignoreCase = true)
            }
            .map { lift -> lift.toOptionState() }
            .sortedWith(
                compareByDescending<LiftSwapOptionState> { it.isCustom }
                    .thenBy { it.name.lowercase() }
            )
            .toList()

    return LiftSwapSheetState(
        targetLiftName = "",
        options = filtered,
        biasOptions = biasDropdownOptions(),
        categoryOptions = categoryDropdownOptions(),
        libraryOptions = libraryDropdownOptions(),
        muscleGroups = defaultMuscleFilterTiers(),
        selectedMuscles = filters.selectedMuscles,
        selectedBias = filters.selectedBias,
        selectedCategory = filters.selectedCategory,
        selectedLibrary = filters.selectedLibrary,
        searchQuery = filters.searchQuery,
    )
}

private fun Lift.toOptionState(): LiftSwapOptionState =
    LiftSwapOptionState(
        id = id.value,
        name = name,
        categoryLabel = classifyLiftCategory().displayName,
        primaryMuscleLabels = primaryMuscles.map { muscle -> muscle.toVolumeDisplayName() },
        lengthBiasLabel = muscleLengthBias.toDisplayLabel(),
        similarity = 1.0f,
        isCustom = isCustom,
        imageUrl = imageUris.firstOrNull(),
        enabled = enabled,
    )

private fun MuscleLengthBias.toDisplayLabel(): String =
    when (this) {
        MuscleLengthBias.Lengthened -> "Lengthened"
        MuscleLengthBias.MidRange -> "Mid"
        MuscleLengthBias.Shortened -> "Shortened"
    }

private fun biasDropdownOptions(): List<LiftSwapDropdownOption<MuscleLengthBias?>> =
    listOf(
        LiftSwapDropdownOption(value = null, label = "Any", leadingIconRes = R.drawable.ic_bias),
        LiftSwapDropdownOption(MuscleLengthBias.Lengthened, "Lengthened"),
        LiftSwapDropdownOption(MuscleLengthBias.MidRange, "Mid"),
        LiftSwapDropdownOption(MuscleLengthBias.Shortened, "Shortened"),
    )

private fun categoryDropdownOptions(): List<LiftSwapDropdownOption<LiftCategory?>> =
    listOf(
        LiftSwapDropdownOption(value = null, label = "All", leadingIconRes = R.drawable.ic_lift_type),
        LiftSwapDropdownOption(LiftCategory.CompoundUpper, LiftCategory.CompoundUpper.displayName),
        LiftSwapDropdownOption(LiftCategory.CompoundLower, LiftCategory.CompoundLower.displayName),
        LiftSwapDropdownOption(LiftCategory.AccessoryUpper, LiftCategory.AccessoryUpper.displayName),
        LiftSwapDropdownOption(LiftCategory.AccessoryLower, LiftCategory.AccessoryLower.displayName),
        LiftSwapDropdownOption(LiftCategory.Core, LiftCategory.Core.displayName),
    )

private fun libraryDropdownOptions(): List<LiftSwapDropdownOption<LiftLibraryFilter?>> =
    listOf(
        LiftSwapDropdownOption(value = null, label = "All", leadingIconRes = R.drawable.ic_library_books),
        LiftSwapDropdownOption(LiftLibraryFilter.Custom, "Custom"),
        LiftSwapDropdownOption(LiftLibraryFilter.StratumDefault, "Stratum Default"),
    )
