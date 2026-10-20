package com.stratum.liftconfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as UiR
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumPreferenceRow
import com.stratum.core.ui.component.StratumSwitch
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumSpacing


/**
 * Hub sheet for a catalog lift: recommendation toggle, increments, information, and merge.
 */
@Composable
fun LiftConfigBottomSheet(
    liftName: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onIncrementsClick: () -> Unit,
    onInformationClick: () -> Unit,
    onMergeWithLiftsClick: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    StratumBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
        ) {
            StratumText(text = liftName)
            StratumPreferenceRow(
                title = stringResource(UiR.string.lift_config_use_in_recommendations),
                subtitle = stringResource(UiR.string.lift_config_use_in_recommendations_sub),
                trailing = {
                    StratumSwitch(
                        checked = enabled,
                        onCheckedChange = onEnabledChange,
                    )
                },
            )
            StratumPreferenceRow(
                title = stringResource(UiR.string.lift_config_increments),
                subtitle = stringResource(UiR.string.lift_config_increments_sub),
                onClick = onIncrementsClick,
            )
            StratumPreferenceRow(
                title = stringResource(UiR.string.lift_config_information),
                subtitle = stringResource(UiR.string.lift_config_information_sub),
                onClick = onInformationClick,
            )
            StratumPreferenceRow(
                title = stringResource(UiR.string.lift_config_merge_lifts),
                subtitle = stringResource(UiR.string.lift_config_merge_lifts_sub),
                onClick = onMergeWithLiftsClick,
            )
        }
    }
}
