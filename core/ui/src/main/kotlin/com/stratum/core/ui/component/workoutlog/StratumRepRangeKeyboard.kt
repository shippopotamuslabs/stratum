package com.stratum.core.ui.component.workoutlog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Slim numeric pad for editing a min–max rep range (no RIR, no decimal).
 * Hosted as an in-hierarchy bottom dock; primary key is a chevron that tabs Min ↔ Max.
 *
 * [errorMessage] is optional validation copy shown above the pad (e.g. floor / max > min).
 */
@Composable
fun StratumRepRangeKeyboardContent(
    activeField: RepRangeKeyboardField,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onOver: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
) {
    val primaryDescription =
        when (activeField) {
            RepRangeKeyboardField.Min ->
                stringResource(CoreUiR.string.rep_range_keyboard_over_to_max)
            RepRangeKeyboardField.Max ->
                stringResource(CoreUiR.string.rep_range_keyboard_over_to_min)
        }
    val primaryIcon =
        when (activeField) {
            RepRangeKeyboardField.Min -> CoreUiR.drawable.ic_chevron_right
            RepRangeKeyboardField.Max -> CoreUiR.drawable.ic_chevron_left
        }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = StratumSpacing.s4, vertical = StratumSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
    ) {
        if (errorMessage != null) {
            StratumText(
                text = errorMessage,
                style = StratumThemeAccessor.typography.sectionLabel,
                color = StratumThemeAccessor.colors.error,
                modifier = Modifier.fillMaxWidth().padding(horizontal = StratumSpacing.s4),
            )
        }
        StratumNumericPad(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            allowDecimal = false,
            onDigit = onDigit,
            onDecimal = {},
            onBackspace = onBackspace,
            onDismiss = onDismiss,
            primaryContentDescription = primaryDescription,
            onPrimaryAction = onOver,
            primaryContent = {
                Icon(
                    painter = painterResource(primaryIcon),
                    contentDescription = null,
                    tint = StratumThemeAccessor.colors.textPrimary,
                    modifier = Modifier.size(NumericPadKeyIconSize),
                )
            },
        )
    }
}
