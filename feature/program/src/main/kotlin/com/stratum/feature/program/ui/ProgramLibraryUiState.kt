package com.stratum.feature.program.ui

import com.stratum.core.ui.component.ProgramLibraryCardState

data class ProgramLibraryUiState(
    val programs: List<ProgramLibraryCardState> = emptyList(),
    val expandedProgramIds: Set<String> = emptySet(),
    /** Program id waiting for delete confirmation; null when no dialog. */
    val pendingDeleteProgramId: String? = null,
    val pendingDeleteProgramName: String? = null,
    /** True when the program pending delete has an in-progress training session. */
    val pendingDeleteHasActiveSession: Boolean = false,
    val isDeleting: Boolean = false,
    val userMessage: String? = null,
)
