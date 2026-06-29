package com.stratum.feature.program

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.feature.program.ui.ProgramsScreen
import com.stratum.feature.program.ui.ProgramsUiState

/**
 * Hosts the read-only Programs overview. Collects [ProgramsViewModel.programsUiState] and forwards
 * it to [ProgramsScreen]. Owned by `:feature:program`; reached via [com.stratum.feature.program.navigation.ProgramsKey].
 */
@Composable
fun ProgramsRoute(
    onBackToOnboarding: () -> Unit,
    viewModel: ProgramsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state: ProgramsUiState by viewModel.programsUiState.collectAsStateWithLifecycle()
    ProgramsScreen(
        state = state,
        onBackToOnboarding = { viewModel.backToOnboarding(onBackToOnboarding) },
        modifier = modifier,
    )
}
