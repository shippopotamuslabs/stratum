package com.stratum.liftconfig

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as UiR
import com.stratum.core.ui.component.LiftSwapOptionState
import com.stratum.core.ui.component.LiftThumbnail
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Pick which of the selected lifts survives the merge. Sources are deleted; history moves onto
 * the chosen lift. Selection does not clear on re-tap of the active row.
 */
@Composable
fun MergeTargetBottomSheet(
    candidates: List<LiftSwapOptionState>,
    onConfirm: (targetLiftId: String) -> Unit,
    onDismissRequest: () -> Unit,
    confirmEnabled: Boolean = true,
) {
    var selectedId by remember(candidates) {
        mutableStateOf(candidates.firstOrNull()?.id)
    }

    StratumBottomSheet(
        onDismissRequest = {
            if (confirmEnabled) onDismissRequest()
        },
        title = stringResource(UiR.string.gym_merge_target_title),
    ) {
        StratumText(
            text = stringResource(UiR.string.gym_merge_target_subtitle),
            muted = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
        ) {
            candidates.forEach { option ->
                MergeTargetChoiceRow(
                    name = option.name,
                    imageUrl = option.imageUrl,
                    selected = option.id == selectedId,
                    onClick = {
                        if (confirmEnabled) selectedId = option.id
                    },
                )
            }
        }
        StratumButton(
            text = stringResource(UiR.string.gym_merge_confirm),
            enabled = selectedId != null && confirmEnabled,
            onClick = {
                val id = selectedId ?: return@StratumButton
                onConfirm(id)
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MergeTargetChoiceRow(
    name: String,
    imageUrl: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val rowShape = RoundedCornerShape(shapes.chipCorner)
    val borderColor = if (selected) colors.accent else colors.borderSubtle

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(rowShape)
                .background(colors.surfaceRaised)
                .border(shapes.borderWidth, borderColor, rowShape)
                .semantics {
                    role = Role.RadioButton
                    this.selected = selected
                }
                .clickable(onClick = onClick)
                .padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s12),
        horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiftThumbnail(
            name = name,
            imageUrl = imageUrl,
            size = 40.dp,
        )
        StratumText(
            text = name,
            style = StratumThemeAccessor.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
    }
}

