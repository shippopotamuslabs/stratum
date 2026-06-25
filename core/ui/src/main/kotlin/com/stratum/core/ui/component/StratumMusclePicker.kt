package com.stratum.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stratum.core.ui.component.musclepicker.StratumMusclePicker as MusclePicker

/**
 * Re-export preserving the public import path `com.stratum.core.ui.component.StratumMusclePicker`.
 */
@Composable
fun StratumMusclePicker(
    selectedMuscleLabels: Set<String>,
    onMuscleToggled: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = MusclePicker(
    selectedMuscleLabels = selectedMuscleLabels,
    onMuscleToggled = onMuscleToggled,
    modifier = modifier,
    enabled = enabled,
)
