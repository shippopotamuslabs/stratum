package com.stratum.feature.program.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.program.R

/**
 * Read-only Programs overview (OS-7 D3). Renders program name plus workout cards listing each lift
 * as `Lift Name - [sets] x [rep range]`. Uses `StratumTheme` + `Stratum*` components only
 * (UI_DESIGN.md); tabular figures for set/rep data via [StratumNumber].
 */
@Composable
fun ProgramsScreen(
    state: ProgramsUiState,
    onBackToOnboarding: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    StratumScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            StratumTopBar(
                title = stringResource(R.string.programs_screen_title),
                navigationIcon =
                    if (state is ProgramsUiState.Loaded && onBackToOnboarding != null) {
                        {
                            StratumText(
                                text = stringResource(R.string.programs_back_to_onboarding),
                                modifier =
                                    Modifier
                                        .clickable(onClick = onBackToOnboarding)
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                style = StratumThemeAccessor.typography.bodyLarge,
                            )
                        }
                    } else {
                        null
                    },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state) {
                is ProgramsUiState.Empty ->
                    StratumText(text = stringResource(R.string.programs_empty), muted = true)
                is ProgramsUiState.Loaded -> LoadedProgram(state.program)
            }
        }
    }
}

@Composable
private fun LoadedProgram(program: ProgramViewState) {
    StratumText(
        text = program.name,
        style = StratumThemeAccessor.typography.titleLarge,
    )
    program.workouts.sortedBy { it.position }.forEach { workout ->
        WorkoutCard(workout)
    }
}

@Composable
private fun WorkoutCard(workout: WorkoutViewState) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color = StratumThemeAccessor.colors.surfaceRaised,
                    shape = StratumThemeAccessor.shapes.panel,
                )
                .border(
                    width = StratumThemeAccessor.shapes.borderWidth,
                    color = StratumThemeAccessor.colors.borderSubtle,
                    shape = StratumThemeAccessor.shapes.panel,
                )
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StratumText(text = workout.name, style = StratumThemeAccessor.typography.titleLarge)
        workout.weekdayLabel?.let { weekday ->
            StratumText(text = weekday, muted = true)
        }
        workout.workoutLifts.sortedBy { it.position }.forEach { lift ->
            StratumNumber(text = lift.summaryLine)
        }
    }
}
