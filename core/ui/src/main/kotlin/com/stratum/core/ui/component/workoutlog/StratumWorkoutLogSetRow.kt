package com.stratum.core.ui.component.workoutlog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.RirTargetBadge
import com.stratum.core.ui.component.RirTargetBottomSheet
import com.stratum.core.ui.component.StratumCheckbox
import com.stratum.core.ui.component.StratumSwipeToRevealRow
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor

@Composable
fun StratumWorkoutLogSetRow(
    set: WorkoutLogSetUi,
    actions: WorkoutLogSetRowActions,
    modifier: Modifier = Modifier,
) {
    var showRirSheet by remember(set.setKey) { mutableStateOf(false) }
    val showTrailing = set.showCheckbox
    val weights = workoutLogColumnWeights(set.showWeightInput, showTrailing)
    val rowBg =
        if (set.isComplete) {
            StratumThemeAccessor.colors.setComplete
        } else {
            Color.Transparent
        }
    val setLabel =
        when (set.setTypeLabel) {
            "Warmup" -> "W"
            "DropSet" -> "D"
            else -> set.positionLabel
        }

    val rowContent: @Composable () -> Unit = {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(rowBg)
                    .padding(vertical = 4.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(weights.label), contentAlignment = Alignment.CenterStart) {
                // Same 32.dp circle as the add-set + so the Set column lines up (Edit Workout pattern).
                WorkoutLogSetColumnBadge {
                    StratumText(
                        text = setLabel,
                        style = StratumThemeAccessor.typography.sectionLabel,
                    )
                }
            }

            Box(modifier = Modifier.weight(weights.prev), contentAlignment = Alignment.Center) {
                StratumText(
                    text = set.previousLabel,
                    style = StratumThemeAccessor.typography.bodyLarge,
                    muted = true,
                )
            }

            if (set.showWeightInput) {
                StratumInlineNumericCell(
                    value = set.weightText,
                    placeholder = set.weightPlaceholder,
                    onValueChange = actions.onWeightChange,
                    enabled = set.fieldsEnabled,
                    modifier =
                        Modifier
                            .weight(weights.weight)
                            .padding(horizontal = 2.dp),
                )
            }

            StratumInlineNumericCell(
                value = set.repsText,
                placeholder = set.repsPlaceholder,
                onValueChange = actions.onRepsChange,
                enabled = set.fieldsEnabled,
                modifier =
                    Modifier
                        .weight(weights.reps)
                        .padding(horizontal = 2.dp),
            )

            Box(modifier = Modifier.weight(weights.rir), contentAlignment = Alignment.Center) {
                RirTargetBadge(
                    rirGoal = set.rir,
                    onClick = {
                        if (set.fieldsEnabled) {
                            showRirSheet = true
                        }
                    },
                )
            }

            if (showTrailing) {
                Box(modifier = Modifier.weight(weights.trailing), contentAlignment = Alignment.Center) {
                    if (actions.onCheckedChange != null) {
                        val weightMagnitude =
                            if (set.assistMode) {
                                kotlin.math.abs(set.recommendedWeightAmount)
                            } else {
                                set.recommendedWeightAmount
                            }
                        val isCheckboxEnabled =
                            !set.showWeightInput ||
                                weightMagnitude > 0.0 ||
                                set.weightText.isNotEmpty() ||
                                set.isComplete
                        StratumCheckbox(
                            checked = set.isComplete,
                            enabled = isCheckboxEnabled,
                            onCheckedChange = actions.onCheckedChange,
                        )
                    } else {
                        Box(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }

    // Historical edit (and any caller that supplies [onDelete]) uses swipe-to-reveal, matching
    // template Edit Workout. Live logging leaves onDelete null and keeps the checkbox column.
    if (actions.onDelete != null) {
        val swipeBg =
            if (set.isComplete) {
                StratumThemeAccessor.colors.setComplete
            } else {
                StratumThemeAccessor.colors.surfaceRaised
            }
        StratumSwipeToRevealRow(
            onActionClick = actions.onDelete,
            actionContentDescription = actions.deleteContentDescription,
            enabled = true,
            contentBackgroundColor = swipeBg,
            modifier = modifier,
        ) {
            rowContent()
        }
    } else {
        Box(modifier = modifier) {
            rowContent()
        }
    }

    if (showRirSheet) {
        RirTargetBottomSheet(
            selectedRirGoal = set.rir,
            onRirSelected = { rir ->
                actions.onRirChange(rir)
                showRirSheet = false
            },
            onDismiss = { showRirSheet = false },
        )
    }
}
