package com.stratum.feature.program

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.program.ObserveProgramUseCase
import com.stratum.feature.program.ui.ProgramsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Exposes the persisted program as [ProgramsUiState] for the read-only Programs screen. Maps the
 * domain program to presentation view states so composables never receive domain types
 * (ARCHITECTURE.md §4.3). Depends on [ObserveProgramUseCase] (not the repository) per the
 * `ForbiddenRepositoryInViewModel` contract.
 */
@HiltViewModel
class ProgramsViewModel @Inject constructor(
    observeProgram: ObserveProgramUseCase,
) : ViewModel() {
    val programsUiState: StateFlow<ProgramsUiState> = observeProgram()
        .map { it.toProgramsUiState() }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ProgramsUiState.Empty,
        )
}
