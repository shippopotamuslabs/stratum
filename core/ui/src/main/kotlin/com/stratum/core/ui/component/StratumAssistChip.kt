package com.stratum.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Small action pill with an optional trailing dismiss — the quiet-hint
 * counterpart to [StratumFilterChip]'s selectable semantics. Implemented on
 * the unified [StratumChip] (§4.1); the dismiss X is a standard nested click
 * target: tapping the pill body calls [onClick]; tapping X calls
 * [onDismissClick] without triggering [onClick]. Both respect [enabled].
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
    StratumChip(
        label = label,
        modifier = modifier,
        onClick = onClick,
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
    )
}
