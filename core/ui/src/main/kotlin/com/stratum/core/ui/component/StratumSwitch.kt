package com.stratum.core.ui.component

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Binary on/off switch. Wraps Material 3 [Switch] with Stratum semantic colors so feature
 * modules never import raw M3 switch APIs.
 */
@Composable
fun StratumSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = StratumThemeAccessor.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        colors =
            SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = colors.accent,
                checkedBorderColor = colors.accent,
                uncheckedThumbColor = colors.textMuted,
                uncheckedTrackColor = colors.surfaceSunken,
                uncheckedBorderColor = colors.borderSubtle,
                disabledCheckedThumbColor = colors.onAccent,
                disabledCheckedTrackColor = colors.accentMuted,
                disabledUncheckedThumbColor = colors.textMuted,
                disabledUncheckedTrackColor = colors.surfaceSunken,
            ),
    )
}
