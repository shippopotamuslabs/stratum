package com.stratum.core.ui.component

import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.rememberStratumHaptics

/**
 * Square checkbox for workout skip / completion affordances. Wraps M3 [Checkbox] with Stratum
 * semantic colors for gym-contrast visibility. User toggles fire the §12.5
 * light toggle haptic (StratumHaptics).
 */
@Composable
fun StratumCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = StratumThemeAccessor.colors
    val haptics = rememberStratumHaptics()
    Checkbox(
        checked = checked,
        onCheckedChange = { newChecked ->
            haptics.toggle(newChecked)
            onCheckedChange(newChecked)
        },
        enabled = enabled,
        modifier = modifier,
        colors =
            CheckboxDefaults.colors(
                checkedColor = colors.accent,
                uncheckedColor = colors.textMuted,
                checkmarkColor = colors.onAccent,
                disabledCheckedColor = colors.accentMuted,
                disabledUncheckedColor = colors.textMuted,
            ),
    )
}
