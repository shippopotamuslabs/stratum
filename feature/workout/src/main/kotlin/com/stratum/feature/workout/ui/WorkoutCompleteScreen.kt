package com.stratum.feature.workout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTextButton
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.workout.R

@Composable
fun WorkoutCompleteScreen(
    state: WorkoutCompleteUiState.Loaded,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StratumScaffold(
        modifier = modifier,
        topBar = {
            StratumTopBar(
                title = state.workoutName,
                actions = {
                    StratumTextButton(
                        text = stringResource(R.string.workout_complete_close),
                        onClick = onClose,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StratumText(
                text = stringResource(R.string.workout_complete_title),
                style = StratumThemeAccessor.typography.titleLarge,
            )

            // Duration
            Column(
                modifier = Modifier
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
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                StratumText(
                    text = stringResource(R.string.workout_complete_duration),
                    style = StratumThemeAccessor.typography.sectionLabel,
                    muted = true,
                )
                StratumText(
                    text = state.durationFormatted,
                    style = StratumThemeAccessor.typography.titleLarge,
                )
            }

            // Best sets per lift
            if (state.bestSets.isNotEmpty()) {
                StratumText(
                    text = stringResource(R.string.workout_complete_best_set),
                    style = StratumThemeAccessor.typography.sectionLabel,
                    muted = true,
                )
                state.bestSets.forEach { bestSet ->
                    Row(
                        modifier = Modifier
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
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StratumText(
                            text = bestSet.liftName,
                            style = StratumThemeAccessor.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        StratumNumber(
                            text = bestSet.bestSetLabel,
                            style = StratumThemeAccessor.typography.sectionLabel.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                        )
                    }
                }
            }
        }
    }
}
