package com.stratum.feature.onboarding.ui

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
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumFilterChip
import com.stratum.core.ui.component.StratumMusclePicker
import com.stratum.core.ui.component.StratumText
import com.stratum.feature.onboarding.MinimumVolumeDraftUi
import com.stratum.feature.onboarding.OnboardingValidationConstants
import com.stratum.feature.onboarding.R

/**
 * Optional step after Specialization: park muscle groups at maintenance volume (MV).
 */
@Composable
fun MinimumVolumeScreen(
    draft: MinimumVolumeDraftUi,
    onMuscleToggled: (String) -> Unit,
    onSuggestedMuscleToggled: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        StratumText(
            text = stringResource(
                R.string.minimum_volume_intro,
                OnboardingValidationConstants.MAX_MINIMUM_VOLUME,
            ),
            muted = true,
        )

        if (draft.suggestedMuscleLabels.isNotEmpty()) {
            StratumText(
                text = stringResource(R.string.minimum_volume_suggestions_heading),
                muted = true,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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

        SelectionSummary(
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
private fun SelectionSummary(
    selectedLabels: Set<String>,
    onMuscleToggled: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
