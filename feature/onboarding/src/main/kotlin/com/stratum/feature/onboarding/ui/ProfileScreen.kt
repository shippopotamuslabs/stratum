package com.stratum.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumFilterChip
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.onboarding.ProfileDraftUi

private val TRAINING_AGE_OPTIONS =
    listOf("0–2 years", "2–4 years", "4–7 years", "7–10 years", "10+ years")
private val LAST_TRAINED_OPTIONS =
    listOf("Never", "Within the last month", "1–6 months ago", "6–12 months ago", "Over a year ago")

/**
 * Step 2 of the wizard. Captures training age (required) and last-trained bucket (required).
 * All inputs are presentation strings; the ViewModel owns the mapping to the domain draft.
 */
@Composable
fun ProfileScreen(
    draft: ProfileDraftUi,
    onTrainingAgeSelected: (String?) -> Unit,
    onLastTrainedSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        StratumText(text = "Your training profile", style = StratumThemeAccessor.typography.titleLarge)

        Section(label = "How many years of solid training have you done?") {
            StratumText(
                text = "Count only consistent, quality training.",
                muted = true,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TRAINING_AGE_OPTIONS.forEach { option ->
                    StratumFilterChip(
                        label = option,
                        selected = draft.trainingAgeSelection == option,
                        onSelectedChange = { selected ->
                            onTrainingAgeSelected(if (selected) option else null)
                        },
                    )
                }
            }
            draft.trainingAgeError?.let { error ->
                StratumText(text = error, muted = true)
            }
        }

        Section(label = "When did you last train seriously?") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LAST_TRAINED_OPTIONS.forEach { option ->
                    StratumFilterChip(
                        label = option,
                        selected = draft.lastTrainedSelection == option,
                        onSelectedChange = { selected ->
                            onLastTrainedSelected(if (selected) option else null)
                        },
                    )
                }
            }
            draft.lastTrainedError?.let { error ->
                StratumText(text = error, muted = true)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StratumText(text = label, style = StratumThemeAccessor.typography.bodyLarge)
        content()
    }
}
