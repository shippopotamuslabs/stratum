package com.stratum.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumMusclePicker
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.onboarding.SpecializationDraftUi
import com.stratum.feature.onboarding.antagonistHintFor
import com.stratum.feature.onboarding.muscleGroupFromLabel

/**
 * Step 4 of the wizard. Optional muscle emphasis with a cap and advisory coaching. The user
 * taps regions on a front/back body illustration to Specialize up to
 * [SpecializationDraftUi.MAX_SPECIALIZE] muscles; the picker disables further selection at
 * the cap. Selecting none (skip) is valid — non-selected muscles default to maintenance.
 * Antagonist hints are advisory and never block.
 *
 * The picker emits the same display-label strings the chip grid did, so the ViewModel,
 * mapping, and domain layer are unchanged.
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
        StratumText(text = "Specialize (Optional)", style = StratumThemeAccessor.typography.titleLarge)
        StratumText(
            text = "Tap up to ${SpecializationDraftUi.MAX_SPECIALIZE} muscle groups on the body to prioritize. " +
                "Everything else stays at maintenance. Or skip to keep a balanced program.",
            muted = true,
        )

        StratumMusclePicker(
            selectedMuscleLabels = draft.selectedMuscleLabels,
            onMuscleToggled = onMuscleToggled,
            // Once the cap is reached, the picker disables unselected regions; selected
            // regions remain tappable to deselect (mirrors the prior chip behavior).
            enabled = !draft.atCap,
            modifier = Modifier.fillMaxSize(),
        )

        // Advisory antagonist hint (never blocks) when one side of a pair is selected.
        val hint = antagonistHintFor(
            draft.selectedMuscleLabels.mapNotNull { muscleGroupFromLabel(it) }.toSet(),
        )
        if (hint != null) {
            StratumText(text = hint, muted = true)
        }

        draft.capError?.let { cap -> StratumText(text = cap, muted = true) }
        draft.overCapError?.let { error -> StratumText(text = error, muted = true) }
    }
}
