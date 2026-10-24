package com.stratum.feature.onboarding.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    // Glass (§12.8, STR-192): forwarded to the shared screen's in-scroll
    // clearance. 0 keeps other hosts unchanged.
    topClearance: Dp = 0.dp,
) {
    SpecializationPreferencesScreen(
        draft = draft,
        onMuscleToggled = onMuscleToggled,
        modifier = modifier,
        topClearance = topClearance,
    )
}
