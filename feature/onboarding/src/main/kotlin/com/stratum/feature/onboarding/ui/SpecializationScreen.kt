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
import com.stratum.feature.onboarding.OnboardingValidationConstants
import com.stratum.feature.onboarding.R
import com.stratum.feature.onboarding.SpecializationDraftUi

/**
 * Step 4 of the wizard. Optional muscle emphasis with a cap.
 */
@Composable
fun SpecializationScreen(
    draft: SpecializationDraftUi,
    onMuscleToggled: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        StratumText(
            text = stringResource(
                R.string.specialization_intro,
                OnboardingValidationConstants.MAX_SPECIALIZE,
            ),
            muted = true,
        )

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
