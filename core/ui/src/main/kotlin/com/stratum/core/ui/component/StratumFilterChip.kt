package com.stratum.core.ui.component

import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Stratum's selectable chip. Wraps Material 3 [FilterChip] using `StratumTheme` semantic
 * roles (UI_DESIGN.md §4). Used for both multi-select (weekdays) and single-select groups
 * (experience, gender) — selection semantics are enforced by the caller, not the chip.
 *
 * Set [selected] to reflect current state; [onSelectedChange] toggles it.
 */
@Composable
fun StratumFilterChip(
    label: String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = { onSelectedChange(!selected) },
        label = { Text(text = label) },
        modifier = modifier,
        enabled = enabled,
        shape = StratumThemeAccessor.shapes.chip,
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = StratumThemeAccessor.colors.surfaceRaised,
            labelColor = StratumThemeAccessor.colors.textMuted,
            selectedContainerColor = StratumThemeAccessor.colors.accent,
            selectedLabelColor = StratumThemeAccessor.colors.onAccent,
            disabledContainerColor = StratumThemeAccessor.colors.surfaceSunken,
            disabledLabelColor = StratumThemeAccessor.colors.textMuted,
        ),
    )
}
