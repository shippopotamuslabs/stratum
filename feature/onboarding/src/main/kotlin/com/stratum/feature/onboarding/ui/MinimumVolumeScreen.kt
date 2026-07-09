package com.stratum.feature.onboarding.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
) {
    MinimumVolumePreferencesScreen(
        draft = draft,
        onMuscleToggled = onMuscleToggled,
        onSuggestedMuscleToggled = onSuggestedMuscleToggled,
        modifier = modifier,
    )
}
