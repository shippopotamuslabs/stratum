package com.stratum.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor

private val OneLineHeight = 56.dp
private val TwoLineHeight = 72.dp
private val ThreeLineHeight = 88.dp

/**
 * Settings-style list row (§4.1, §12.3 LIST ITEM): one line 56dp, two lines
 * 72dp, three lines (overline + headline + supporting) 88dp. Horizontal
 * padding 16dp (12dp [compact] in dense panels). The [leading] slot is sized
 * by the caller — 24dp for an icon, 40dp for avatar/media thumbnails; the
 * trailing slot takes chevrons, values, or controls. Both slots inherit
 * textMuted as their content color (an explicit tint in the slot wins).
 *
 * For rows inside bottom sheets with a trailing switch, [StratumPreferenceRow]
 * remains the specialized variant (its label keeps its own click target).
 */
@Composable
fun StratumListRow(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    overline: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    val minHeight: Dp =
        when {
            overline != null && supporting != null -> ThreeLineHeight
            overline != null || supporting != null -> TwoLineHeight
            else -> OneLineHeight
        }
    var rowModifier = modifier.fillMaxWidth()
    if (onClick != null) {
        rowModifier = rowModifier.clickable(role = Role.Button, onClick = onClick)
    }
    Row(
        modifier =
            rowModifier
                .heightIn(min = minHeight)
                .padding(
                    horizontal = if (compact) StratumSpacing.s12 else StratumSpacing.s16,
                    vertical = StratumSpacing.s8,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s16),
    ) {
        if (leading != null) {
            CompositionLocalProvider(LocalContentColor provides StratumThemeAccessor.colors.textMuted) {
                leading()
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
        ) {
            if (overline != null) {
                StratumText(
                    text = overline,
                    style = StratumThemeAccessor.typography.sectionLabel,
                    muted = true,
                )
            }
            StratumText(
                text = headline,
                style = StratumThemeAccessor.typography.bodyLarge,
            )
            if (supporting != null) {
                StratumText(
                    text = supporting,
                    style = StratumThemeAccessor.typography.bodyMedium,
                    muted = true,
                )
            }
        }
        if (trailing != null) {
            CompositionLocalProvider(LocalContentColor provides StratumThemeAccessor.colors.textMuted) {
                trailing()
            }
        }
    }
}
