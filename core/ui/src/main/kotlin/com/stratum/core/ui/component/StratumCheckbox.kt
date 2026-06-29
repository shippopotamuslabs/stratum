package com.stratum.core.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Square checkbox for workout skip / completion affordances. Wraps M3 [Checkbox] with Stratum
 * semantic colors for gym-contrast visibility.
 */
@Composable
fun StratumCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Box(
        modifier =
            modifier
                .size(48.dp)
                .border(
                    width = shapes.borderWidth,
                    color = if (enabled) colors.borderSubtle else colors.borderSubtle.copy(alpha = 0.5f),
                    shape = shapes.chip,
                ),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.size(48.dp),
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
}
