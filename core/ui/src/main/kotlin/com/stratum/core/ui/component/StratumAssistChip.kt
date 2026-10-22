package com.stratum.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
 *
 * §12.5: the dismiss target is a real 48dp layout box around the 18dp
 * glyph, so a dismissible chip's pill fills the chip's full 48dp target
 * height instead of floating in it at 32dp.
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
                    Box(
                        modifier =
                            Modifier
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .clickable(
                                    enabled = enabled,
                                    role = Role.Button,
                                    onClick = onDismissClick,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = dismissContentDescription,
                            tint = StratumThemeAccessor.colors.textMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            } else {
                null
            },
    )
}
