package com.stratum.feature.program.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.program.R

@Composable
fun ProgressionSchemeExplainerSheet(onDismiss: () -> Unit) {
    StratumBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.edit_workout_progression_scheme_sheet_title),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StratumText(
                text = stringResource(R.string.edit_workout_progression_scheme_linear_body),
                style = StratumThemeAccessor.typography.bodyLarge,
            )
            StratumText(
                text = stringResource(R.string.edit_workout_progression_scheme_double_body),
                style = StratumThemeAccessor.typography.bodyLarge,
            )
            StratumText(
                text = stringResource(R.string.edit_workout_progression_scheme_eligibility_body),
                style = StratumThemeAccessor.typography.bodyLarge,
                muted = true,
            )
            StratumText(
                text = stringResource(R.string.edit_workout_progression_scheme_graduation_body),
                style = StratumThemeAccessor.typography.bodyLarge,
                muted = true,
            )
        }
    }
}
