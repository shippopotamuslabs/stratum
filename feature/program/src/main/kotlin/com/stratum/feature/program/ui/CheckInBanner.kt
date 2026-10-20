// STR-142 allowlist: dense 2.dp banner stack pinned.
@file:Suppress("ForbiddenRawSpacingLiteralInUi")
package com.stratum.feature.program.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTextButton
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.program.R

/**
 * Pending coach check-in banner. Tapping the banner opens the check-in sheet; Skip dismisses
 * until the next re-extract.
 */
@Composable
fun CheckInBanner(
    onTap: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .border(
                    width = shapes.borderWidth,
                    color = colors.accent,
                    shape = shapes.panel,
                )
                .background(color = colors.surfaceRaised, shape = shapes.panel)
                .clickable(role = Role.Button, onClick = onTap)
                .padding(horizontal = StratumSpacing.s12, vertical = StratumSpacing.s12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            StratumText(
                text = stringResource(R.string.checkin_banner_title),
                style = StratumThemeAccessor.typography.titleMedium,
            )
            StratumText(
                text = stringResource(R.string.checkin_banner_subtitle),
                muted = true,
                style = StratumThemeAccessor.typography.sectionLabel,
            )
        }
        StratumTextButton(
            text = stringResource(R.string.checkin_skip),
            onClick = onSkip,
        )
    }
}
