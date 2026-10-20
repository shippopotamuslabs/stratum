package com.stratum.feature.program.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTextField
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.program.R

/**
 * Program-level options for Create/Edit Program editor (Lift Options styling).
 * [CreateProgramOptionsMode.RenameProgram] swaps content in the same sheet rather than stacking a second sheet.
 */
@Composable
fun CreateProgramOptionsBottomSheet(
    mode: CreateProgramOptionsMode,
    programName: String,
    onModeChange: (CreateProgramOptionsMode) -> Unit,
    onReorderWorkouts: () -> Unit,
    onReorderLifts: () -> Unit,
    onRenameProgram: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    when (mode) {
        CreateProgramOptionsMode.Menu -> {
            StratumBottomSheet(
                onDismissRequest = onDismiss,
                title = stringResource(R.string.create_program_options_title),
            ) {
                CreateProgramOptionRow(
                    text = stringResource(R.string.create_program_reorder_workouts),
                    leadingIconRes = CoreUiR.drawable.ic_swap_vert,
                    onClick = onReorderWorkouts,
                )
                CreateProgramOptionRow(
                    text = stringResource(CoreUiR.string.edit_workout_reorder_lifts),
                    leadingIconRes = CoreUiR.drawable.ic_swap_vert,
                    onClick = onReorderLifts,
                )
                CreateProgramOptionRow(
                    text = stringResource(R.string.create_program_rename_program_menu),
                    leadingIcon = rememberVectorPainter(Icons.Outlined.Edit),
                    onClick = { onModeChange(CreateProgramOptionsMode.RenameProgram) },
                )
            }
        }
        CreateProgramOptionsMode.RenameProgram -> {
            var value by remember(programName) { mutableStateOf(programName) }
            StratumBottomSheet(
                onDismissRequest = onDismiss,
                title = stringResource(R.string.create_program_rename_program_title),
            ) {
                StratumTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = stringResource(R.string.create_program_rename_program_title),
                    singleLine = true,
                )
                StratumButton(
                    text = stringResource(R.string.create_program_rename_confirm),
                    onClick = { onRenameProgram(value) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun CreateProgramOptionRow(
    text: String,
    onClick: () -> Unit,
    @DrawableRes leadingIconRes: Int,
) {
    CreateProgramOptionRow(
        text = text,
        leadingIcon = painterResource(leadingIconRes),
        onClick = onClick,
    )
}

@Composable
private fun CreateProgramOptionRow(
    text: String,
    leadingIcon: Painter,
    onClick: () -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color = colors.surfaceRaised, shape = shapes.panel)
                .border(width = shapes.borderWidth, color = colors.borderSubtle, shape = shapes.panel)
                .clickable(onClick = onClick)
                .padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s16),
        horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = leadingIcon,
            contentDescription = null,
            tint = colors.textPrimary,
            modifier = Modifier.size(24.dp),
        )
        StratumText(
            text = text,
            style = StratumThemeAccessor.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
    }
}

enum class CreateProgramOptionsMode {
    Menu,
    RenameProgram,
}
