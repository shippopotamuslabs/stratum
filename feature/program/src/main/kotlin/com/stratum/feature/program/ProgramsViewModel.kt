package com.stratum.feature.program

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.program.ComputeProgramMuscleVolumeUseCase
import com.stratum.domain.program.ObserveProgramUseCase
import com.stratum.feature.program.ui.ProgramsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Exposes the persisted program as [ProgramsUiState] for the read-only Programs screen. Maps the
 * domain program to presentation view states and resolves lift ids through the catalog so the UI
 * shows human-readable lift names (ARCHITECTURE.md §4.3).
 *
 * Also owns the lift-swap bottom sheet state ([liftSwapSheet]). The sheet is opened with
 * [openLiftSwap] (targeting a workout lift); the ranked candidate stream is produced by
 * [RankSwapCandidatesUseCase] and the filter overlay is reduced here against [LiftSwapFilters]
 * (ARCHITECTURE.md §4.4 — only transient state lives in the ViewModel). Actual program-mutation
 * after a swap is a follow-up slice; this ViewModel emits the swap selection via
 * [swapSelections] for the route to persist.
 */
@HiltViewModel
class ProgramsViewModel @Inject constructor(
    observeProgram: ObserveProgramUseCase,
    observeLiftCatalog: ObserveLiftCatalogUseCase,
    computeProgramMuscleVolume: ComputeProgramMuscleVolumeUseCase,
) : ViewModel() {
    val programsUiState: StateFlow<ProgramsUiState> =
        combine(
            observeProgram(),
            observeLiftCatalog(),
        ) { program, lifts ->
            val volumeSummaries =
                program?.let { activeProgram ->
                    computeProgramMuscleVolume(activeProgram, lifts)
                }.orEmpty()
            program.toProgramsUiState(
                liftNamesById = lifts.associate { lift -> lift.id.value to lift.name },
                volumeSummaries = volumeSummaries,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ProgramsUiState.Empty,
        )

}
