package com.stratum.feature.program

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.core.ui.component.LiftSwapFilterIntent
import com.stratum.core.ui.component.LiftSwapSheetState
import com.stratum.domain.lift.RankSwapCandidatesUseCase
import com.stratum.domain.lift.identity.LiftId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * Handles presentation logic and filtering for the Lift Swap screen (ARCHITECTURE.md v1.0).
 * State is computed by combining ranked candidates from [RankSwapCandidatesUseCase] with
 * locally held [LiftSwapFilters] and mapped via [buildLiftSwapSheetState].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LiftSwapViewModel @Inject constructor(
    private val rankSwapCandidates: RankSwapCandidatesUseCase,
) : ViewModel() {
    private val targetLiftId = MutableStateFlow<String?>(null)
    private val targetLiftName = MutableStateFlow<String?>(null)
    private val swapFilters = MutableStateFlow(LiftSwapFilters())

    val liftSwapState: StateFlow<LiftSwapSheetState?> =
        combine(targetLiftId.asStateFlow(), targetLiftName.asStateFlow()) { id, name ->
            id to name
        }.flatMapLatest { (id, name) ->
            if (id == null || name == null) {
                flowOf(null)
            } else {
                val rankedFlow = rankSwapCandidates(LiftId(id))
                combine(rankedFlow, swapFilters.asStateFlow()) { ranked, filters ->
                    buildLiftSwapSheetState(
                        targetLiftName = name,
                        ranked = ranked,
                        filters = filters,
                    )
                }
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    fun initialize(liftId: String, liftName: String) {
        if (targetLiftId.value == liftId) return
        targetLiftId.value = liftId
        targetLiftName.value = liftName
        swapFilters.value = LiftSwapFilters()
    }

    fun onFilterIntent(intent: LiftSwapFilterIntent) {
        swapFilters.value =
            when (intent) {
                is LiftSwapFilterIntent.ToggleMuscle ->
                    swapFilters.value.copy(
                        selectedMuscles = swapFilters.value.selectedMuscles.toggle(intent.muscle),
                    )

                is LiftSwapFilterIntent.SelectBias ->
                    swapFilters.value.copy(selectedBias = intent.bias)

                is LiftSwapFilterIntent.SelectCategory ->
                    swapFilters.value.copy(selectedCategory = intent.category)

                is LiftSwapFilterIntent.SelectLibrary ->
                    swapFilters.value.copy(selectedLibrary = intent.library)

                LiftSwapFilterIntent.ResetAll -> LiftSwapFilters()
            }
    }

    fun onSearchQueryChanged(query: String) {
        swapFilters.value = swapFilters.value.copy(searchQuery = query)
    }

    private fun <T> Set<T>.toggle(value: T): Set<T> =
        if (value in this) this - value else this + value
}
