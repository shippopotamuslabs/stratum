package com.stratum.feature.onboarding.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.trainingsetup.MinimumVolumeDraftUi
import com.stratum.core.ui.trainingsetup.MinimumVolumePreferencesScreen

/**
 * Onboarding Minimum Volume step — thin wrapper over the shared [MinimumVolumePreferencesScreen].
 */
@Composable
fun MinimumVolumeScreen(
    draft: MinimumVolumeDraftUi,
    onMuscleToggled: (String) -> Unit,
    onSuggestedMuscleToggled: (String) -> Unit,
    modifier: Modifier = Modifier,
    // Glass (§12.8, STR-192): forwarded to the shared screen's in-scroll
    // clearance. 0 keeps other hosts unchanged.
    topClearance: Dp = 0.dp,
) {
    MinimumVolumePreferencesScreen(
        draft = draft,
        onMuscleToggled = onMuscleToggled,
        onSuggestedMuscleToggled = onSuggestedMuscleToggled,
        modifier = modifier,
        topClearance = topClearance,
    )
}
