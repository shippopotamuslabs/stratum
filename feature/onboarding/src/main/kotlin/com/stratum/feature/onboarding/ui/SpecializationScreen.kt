package com.stratum.feature.onboarding.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stratum.core.ui.trainingsetup.SpecializationDraftUi
import com.stratum.core.ui.trainingsetup.SpecializationPreferencesScreen

/**
 * Onboarding Specialization step — thin wrapper over the shared [SpecializationPreferencesScreen].
 */
@Composable
fun SpecializationScreen(
    draft: SpecializationDraftUi,
    onMuscleToggled: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SpecializationPreferencesScreen(
        draft = draft,
        onMuscleToggled = onMuscleToggled,
        modifier = modifier,
    )
}
