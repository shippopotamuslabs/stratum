// STR-142 allowlist: section rhythm (20.dp) pinned; screen margins adopted.
@file:Suppress("ForbiddenRawSpacingLiteralInUi")
package com.stratum.core.ui.trainingsetup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R
import com.stratum.core.ui.component.StratumFilterChip
import com.stratum.core.ui.component.StratumMusclePicker
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumSpacing


/**
 * Shared specialization preferences step. Used by onboarding and create program.
 */
@Composable
fun SpecializationPreferencesScreen(
    draft: SpecializationDraftUi,
    onMuscleToggled: (String) -> Unit,
    modifier: Modifier = Modifier,
    // Glass (§12.8): added inside the scroll (padding sits after
    // verticalScroll in the chain), so content scrolls beneath the bar.
    // 0 keeps other hosts (onboarding) unchanged.
    topClearance: Dp = 0.dp,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = StratumSpacing.s16)
                .padding(top = inScrollTopClearance(topClearance), bottom = StratumSpacing.s12),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        StratumText(
            text = stringResource(
                R.string.training_setup_specialization_intro,
                TrainingSetupValidationConstants.MAX_SPECIALIZE,
            ),
            muted = true,
        )

        if (draft.saturatedSpecializeWarningLabels.isNotEmpty()) {
            SaturatedSpecializeWarning(labels = draft.saturatedSpecializeWarningLabels)
        }

        MuscleSelectionSummary(
            selectedLabels = draft.selectedMuscleLabels,
            onMuscleToggled = onMuscleToggled,
        )

        StratumMusclePicker(
            selectedMuscleLabels = draft.selectedMuscleLabels,
            onMuscleToggled = onMuscleToggled,
            enabled = !draft.atCap,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SaturatedSpecializeWarning(
    labels: List<String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(StratumSpacing.s8)) {
        labels.forEach { label ->
            StratumText(
                text = stringResource(R.string.training_setup_specialization_saturated_warning, label),
                muted = true,
            )
        }
    }
}

@Composable
internal fun MuscleSelectionSummary(
    selectedLabels: Set<String>,
    onMuscleToggled: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(StratumSpacing.s8)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
            verticalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
        ) {
            selectedLabels.forEach { label ->
                StratumFilterChip(
                    label = label,
                    selected = true,
                    onSelectedChange = { onMuscleToggled(label) },
                )
            }
        }
    }
}
