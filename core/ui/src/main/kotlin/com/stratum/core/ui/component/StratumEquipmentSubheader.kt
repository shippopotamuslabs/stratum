package com.stratum.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Equipment subheader rendered under a lift-card title (live workout + edit workout).
 * Shows the in-use equipment name with a swap affordance; tapping opens the swap sheet.
 * A null [equipmentName] renders the unassigned placeholder.
 */
@Composable
fun StratumEquipmentSubheader(
    equipmentName: String?,
    onSwapClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.equipment_subheader_swap_cd),
                    onClick = onSwapClick,
                )
                .padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s8),
        horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_swap_horiz),
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(16.dp),
        )
        StratumText(
            text = equipmentName ?: stringResource(R.string.equipment_subheader_unassigned),
            style = StratumThemeAccessor.typography.bodyLarge,
            muted = true,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
