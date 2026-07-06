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
import com.stratum.core.ui.component.StratumMuscleFilterCardState
import com.stratum.core.ui.component.toVolumeDisplayName
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LiftCategory
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

@HiltViewModel
class GymViewModel @Inject constructor(
    private val observeLiftCatalog: ObserveLiftCatalogUseCase,
    private val setLiftEnabled: SetLiftEnabledUseCase,
) : ViewModel() {

    private val filters = MutableStateFlow(GymFilters())

    val uiState: StateFlow<LiftSwapSheetState> =
        combine(observeLiftCatalog(), filters.asStateFlow()) { catalog, filters ->
            buildGymCatalogState(catalog, filters)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            initialValue = LiftSwapSheetState(
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
            )
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
            setLiftEnabled(LiftId(id), enabled)
        }
    }

    private fun <T> Set<T>.toggle(value: T): Set<T> =
        if (value in this) this - value else this + value
}

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
        muscleGroups = muscleTiers(),
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
        LiftSwapDropdownOption(LiftCategory.IsolationUpper, LiftCategory.IsolationUpper.displayName),
        LiftSwapDropdownOption(LiftCategory.IsolationLower, LiftCategory.IsolationLower.displayName),
        LiftSwapDropdownOption(LiftCategory.Core, LiftCategory.Core.displayName),
    )

private fun libraryDropdownOptions(): List<LiftSwapDropdownOption<LiftLibraryFilter?>> =
    listOf(
        LiftSwapDropdownOption(value = null, label = "All", leadingIconRes = R.drawable.ic_library_books),
        LiftSwapDropdownOption(LiftLibraryFilter.Custom, "Custom"),
        LiftSwapDropdownOption(LiftLibraryFilter.StratumDefault, "Stratum Default"),
    )

private fun muscleTiers(): LiftSwapMuscleTiers =
    LiftSwapMuscleTiers(
        upper = upperMuscles.map { muscle -> StratumMuscleFilterCardState(muscle, muscle.toVolumeDisplayName()) },
        lower = lowerMuscles.map { muscle -> StratumMuscleFilterCardState(muscle, muscle.toVolumeDisplayName()) },
    )

private val upperMuscles: List<MuscleGroup> =
    listOf(
        MuscleGroup.Chest,
        MuscleGroup.Back,
        MuscleGroup.AnteriorDeltoid,
        MuscleGroup.LateralDeltoid,
        MuscleGroup.PosteriorDeltoid,
        MuscleGroup.Tricep,
        MuscleGroup.Bicep,
        MuscleGroup.Trap,
        MuscleGroup.Forearm,
    )

private val lowerMuscles: List<MuscleGroup> =
    listOf(
        MuscleGroup.Quad,
        MuscleGroup.Hamstring,
        MuscleGroup.Glute,
        MuscleGroup.Calf,
        MuscleGroup.Abductor,
        MuscleGroup.Adductor,
    )
