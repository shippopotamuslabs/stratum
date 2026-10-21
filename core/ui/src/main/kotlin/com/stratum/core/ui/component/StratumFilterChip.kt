package com.stratum.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumSpacing
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
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    FilterChip(
        selected = selected,
        onClick = { onSelectedChange(!selected) },
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8)
            ) {
                Text(text = label)
                if (trailingIcon != null) {
                    trailingIcon()
                }
            }
        },
        modifier = modifier,
        enabled = enabled,
        shape = StratumThemeAccessor.shapes.chip,
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = StratumThemeAccessor.colors.borderSubtle,
            selectedBorderColor = StratumThemeAccessor.colors.borderSubtle,
            disabledBorderColor = StratumThemeAccessor.colors.borderSubtle,
            borderWidth = StratumThemeAccessor.shapes.borderWidth,
            selectedBorderWidth = StratumThemeAccessor.shapes.borderWidth,
        ),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = StratumThemeAccessor.colors.surfaceRaised,
            labelColor = StratumThemeAccessor.colors.textMuted,
            // §12.3: selected filter chips are tonal (accent-muted container).
            selectedContainerColor = StratumThemeAccessor.colors.accentMuted,
            selectedLabelColor = StratumThemeAccessor.colors.onAccentMuted,
            disabledContainerColor = StratumThemeAccessor.colors.surfaceSunken,
            disabledLabelColor = StratumThemeAccessor.colors.textMuted,
        ),
    )
}
