package com.stratum.feature.program

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.feature.program.ui.ProgramsScreen
import com.stratum.feature.program.ui.ProgramsScreenCallbacks

@Composable
fun ProgramsRoute(
    viewModel: ProgramsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
    callbacks: ProgramsScreenCallbacks = ProgramsScreenCallbacks(),
    onSwapLiftClick: (workoutLiftId: String, liftId: String, liftName: String) -> Unit = { _, _, _ -> },
    isPreviewMode: Boolean = false,
) {
    val state by viewModel.programsUiState.collectAsStateWithLifecycle()
    ProgramsScreen(
        state = state,
        callbacks = callbacks,
        onSwapLiftClick = { target ->
            onSwapLiftClick(target.workoutLiftId, target.liftId, target.name)
        },
        modifier = modifier,
        isPreviewMode = isPreviewMode,
    )
}
