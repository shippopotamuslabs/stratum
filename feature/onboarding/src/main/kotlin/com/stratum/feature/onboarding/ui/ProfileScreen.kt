package com.stratum.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumChoiceGroup
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.onboarding.LAST_TRAINED_OPTIONS
import com.stratum.feature.onboarding.ProfileDraftUi
import com.stratum.feature.onboarding.R
import com.stratum.feature.onboarding.TRAINING_AGE_OPTIONS

/**
 * Step 2 of the wizard. Captures training age (required) and last-trained bucket (required).
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
        Section(label = stringResource(R.string.profile_training_age_heading)) {
            StratumText(
                text = stringResource(R.string.profile_training_age_hint),
                muted = true,
            )
            StratumChoiceGroup(
                options = TRAINING_AGE_OPTIONS,
                selectedOption = draft.trainingAgeSelection,
                onOptionSelected = onTrainingAgeSelected,
            )
            if (draft.hasTrainingAgeError) {
                StratumText(
                    text = stringResource(R.string.profile_error_training_age),
                    muted = true,
                )
            }
        }

        Section(label = stringResource(R.string.profile_last_trained_heading)) {
            StratumChoiceGroup(
                options = LAST_TRAINED_OPTIONS,
                selectedOption = draft.lastTrainedSelection,
                onOptionSelected = onLastTrainedSelected,
            )
            if (draft.hasLastTrainedError) {
                StratumText(
                    text = stringResource(R.string.profile_error_last_trained),
                    muted = true,
                )
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
