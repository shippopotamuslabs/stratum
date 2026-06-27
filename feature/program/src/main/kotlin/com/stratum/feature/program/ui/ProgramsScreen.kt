package com.stratum.feature.program.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.program.R

/**
 * Read-only Programs overview (OS-7 D3). Renders Program → Workouts → expandable WorkoutLifts →
 * Sets. No edit/swap/regenerate affordance — those are deferred. Uses `StratumTheme` + `Stratum*`
 * components only (UI_DESIGN.md); tabular figures for rep/RIR data via [StratumNumber].
 */
@Composable
fun ProgramsScreen(
    state: ProgramsUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (state) {
            is ProgramsUiState.Empty -> StratumText(text = stringResource(R.string.programs_empty), muted = true)
            is ProgramsUiState.Loaded -> LoadedProgram(state.program)
        }
    }
}

@Composable
private fun LoadedProgram(program: ProgramViewState) {
    StratumText(
        text = program.name,
        style = StratumThemeAccessor.typography.titleLarge,
    )
    program.workouts.sortedBy { it.position }.forEach { workout -> WorkoutSection(workout) }
}

@Composable
private fun WorkoutSection(workout: WorkoutViewState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val heading = listOfNotNull(workout.name, workout.weekdayLabel).joinToString(" · ")
        StratumText(text = heading, style = StratumThemeAccessor.typography.titleLarge)
        workout.workoutLifts.sortedBy { it.position }.forEach { lift -> WorkoutLiftRow(lift) }
    }
}

@Composable
private fun WorkoutLiftRow(lift: WorkoutLiftViewState) {
    var expanded by remember(lift.liftId) { mutableStateOf(false) }
    Column(
        modifier = Modifier.clickable { expanded = !expanded },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StratumText(
            text = "${lift.liftName} · ${lift.progressionSchemeLabel}",
            style = StratumThemeAccessor.typography.bodyLarge,
        )
        if (expanded) {
            lift.sets.forEach { set -> SetRow(set) }
        }
    }
}

@Composable
private fun SetRow(set: LiftSetViewState) {
    // Omitted rep ranges (back-off sets) render as just "@ RIR X", not "0–0 @ RIR X".
    val repRange = if (set.repMin > 0 || set.repMax > 0) {
        "${set.repMin}\u2013${set.repMax} @ RIR ${set.rirGoal}"
    } else {
        "@ RIR ${set.rirGoal}"
    }
    val label = if (set.isTopSet) {
        "$repRange (${stringResource(R.string.programs_top_set)})"
    } else {
        repRange
    }
    StratumNumber(text = label)
}
