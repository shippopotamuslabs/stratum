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
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumFilterChip
import com.stratum.core.ui.component.StratumMusclePicker
import com.stratum.core.ui.component.StratumText
import com.stratum.feature.onboarding.SpecializationDraftUi

/**
 * Step 4 of the wizard. Optional muscle emphasis with a cap. The user
 * taps regions on a front/back body illustration to Specialize up to
 * [SpecializationDraftUi.MAX_SPECIALIZE] muscles; the picker disables further selection at
 * the cap. Selecting none (skip) is valid — non-selected muscles default to maintenance.
 *
 * The picker emits the same display-label strings the chip grid did, so the ViewModel,
 * mapping, and domain layer are unchanged. A selection summary above the diagram lists the
 * currently chosen groups (tap a chip to deselect) so the user sees explicitly what they
 * picked even after flipping away from a region.
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
            text = "Tap up to ${SpecializationDraftUi.MAX_SPECIALIZE} muscle groups on the body to prioritize. " +
                "Skip to keep a balanced program.",
            muted = true,
        )

        SelectionSummary(
            selectedLabels = draft.selectedMuscleLabels,
            onMuscleToggled = onMuscleToggled,
        )

        StratumMusclePicker(
            selectedMuscleLabels = draft.selectedMuscleLabels,
            onMuscleToggled = onMuscleToggled,
            // Once the cap is reached, the picker disables unselected regions; selected
            // regions remain tappable to deselect (mirrors the prior chip behavior).
            enabled = !draft.atCap,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Lists the currently-specialized muscle groups with a count. Sits above the body map so the
 * selection stays visible regardless of which view (front/back) is showing — useful once the
 * user flips to the back to pick posterior muscles. Each chip is selected (accent) and tappable
 * to deselect, round-tripping through the same [onMuscleToggled] the picker uses. Empty when
 * nothing is selected.
 */
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
