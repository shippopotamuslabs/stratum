package com.stratum.core.ui.importing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.stratum.core.ui.R
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumOutlinedButton
import com.stratum.core.ui.component.StratumSegmentedControl
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Blocking prompt before historical log import. File-explicit units always win; this choice
 * only stamps sets that omit a unit after conversion.
 */
@Composable
fun ImportWeightUnitPrompt(
    selected: ImportWeightUnitUi,
    onSelectedChange: (ImportWeightUnitUi) -> Unit,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val options =
        listOf(
            ImportWeightUnitUi.Lbs to stringResource(R.string.import_weight_unit_lbs),
            ImportWeightUnitUi.Kg to stringResource(R.string.import_weight_unit_kg),
        )
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                modifier
                    .fillMaxWidth()
                    .background(color = colors.surfaceRaised, shape = shapes.panel)
                    .border(
                        width = shapes.borderWidth,
                        color = colors.borderStrong,
                        shape = shapes.panel,
                    )
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StratumText(
                text = stringResource(R.string.import_weight_unit_title),
                style = StratumThemeAccessor.typography.titleLarge,
            )
            StratumText(
                text = stringResource(R.string.import_weight_unit_body),
                style = StratumThemeAccessor.typography.bodyLarge,
                muted = true,
            )
            StratumSegmentedControl(
                modifier = Modifier.fillMaxWidth(),
                segments = options.map { it.second },
                selectedIndex = options.indexOfFirst { it.first == selected }.takeIf { it >= 0 },
                onSelectedIndexChange = { index -> onSelectedChange(options[index].first) },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                StratumOutlinedButton(
                    text = stringResource(R.string.import_weight_unit_cancel),
                    onClick = onDismiss,
                )
                StratumButton(
                    text = stringResource(R.string.import_weight_unit_continue),
                    onClick = onContinue,
                )
            }
        }
    }
}
