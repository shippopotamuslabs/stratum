// STR-142 allowlist: section rhythm (20.dp) pinned; screen margins adopted.
@file:Suppress("ForbiddenRawSpacingLiteralInUi")
package com.stratum.core.ui.trainingsetup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
 * Shared minimum-volume preferences step. Used by onboarding and create program.
 */
@Composable
fun MinimumVolumePreferencesScreen(
    draft: MinimumVolumeDraftUi,
    onMuscleToggled: (String) -> Unit,
    onSuggestedMuscleToggled: (String) -> Unit,
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
                R.string.training_setup_minimum_volume_intro,
                TrainingSetupValidationConstants.MAX_MINIMUM_VOLUME,
            ),
            muted = true,
        )

        if (draft.suggestedMuscleLabels.isNotEmpty()) {
            StratumText(
                text = stringResource(R.string.training_setup_minimum_volume_suggestions_heading),
                muted = true,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
                verticalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
            ) {
                draft.suggestedMuscleLabels.forEach { label ->
                    val selected = label in draft.selectedMuscleLabels
                    StratumFilterChip(
                        label = label,
                        selected = selected,
                        onSelectedChange = { onSuggestedMuscleToggled(label) },
                    )
                }
            }
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
