package com.stratum.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Small action pill with an optional trailing dismiss. Wraps Material 3 [AssistChip] using
 * `StratumTheme` semantic roles (UI_DESIGN.md §4) — the quiet-hint counterpart to
 * [StratumFilterChip]'s selectable semantics.
 *
 * M3 offers no dismiss-chip variant, so the trailing X is a standard nested click
 * target: tapping the pill body calls [onClick]; tapping X calls [onDismissClick]
 * without triggering [onClick]. Both respect [enabled].
 */
@Composable
fun StratumAssistChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onDismissClick: (() -> Unit)? = null,
    dismissContentDescription: String? = null,
) {
    AssistChip(
        onClick = onClick,
        label = { StratumText(text = label) },
        modifier = modifier,
        enabled = enabled,
        trailingIcon =
            if (onDismissClick != null) {
                {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = dismissContentDescription,
                        tint = StratumThemeAccessor.colors.textMuted,
                        modifier =
                            Modifier
                                .size(18.dp)
                                .clickable(
                                    enabled = enabled,
                                    role = Role.Button,
                                    onClick = onDismissClick,
                                ),
                    )
                }
            } else {
                null
            },
        shape = StratumThemeAccessor.shapes.chip,
        colors =
            AssistChipDefaults.assistChipColors(
                containerColor = StratumThemeAccessor.colors.surfaceRaised,
                labelColor = StratumThemeAccessor.colors.accent,
                disabledContainerColor = StratumThemeAccessor.colors.surfaceSunken,
                disabledLabelColor = StratumThemeAccessor.colors.textMuted,
            ),
        border =
            AssistChipDefaults.assistChipBorder(
                enabled = enabled,
                borderColor = StratumThemeAccessor.colors.borderSubtle,
                borderWidth = StratumThemeAccessor.shapes.borderWidth,
            ),
    )
}
