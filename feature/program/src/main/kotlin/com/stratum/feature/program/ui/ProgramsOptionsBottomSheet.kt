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
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.program.R

@Composable
fun ProgramsOptionsBottomSheet(
    onNewProgram: () -> Unit,
    onProgramLibrary: () -> Unit,
    onDismiss: () -> Unit,
) {
    StratumBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.programs_options_title),
    ) {
        ProgramsOptionRow(
            text = stringResource(R.string.programs_menu_create_program),
            leadingIconRes = CoreUiR.drawable.ic_add,
            onClick = onNewProgram,
        )
        ProgramsOptionRow(
            text = stringResource(R.string.programs_menu_program_library),
            leadingIconRes = CoreUiR.drawable.ic_library_books,
            onClick = onProgramLibrary,
        )
    }
}

@Composable
private fun ProgramsOptionRow(
    text: String,
    @DrawableRes leadingIconRes: Int,
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
                .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(leadingIconRes),
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
