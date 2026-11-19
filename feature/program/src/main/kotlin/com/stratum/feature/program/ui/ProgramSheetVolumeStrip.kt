package com.stratum.feature.program.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTextButton
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.program.CreateProgramUiState
import com.stratum.feature.program.ProgramSheetVolumePivot
import com.stratum.feature.program.R
import com.stratum.feature.program.formatSheetSetCount
import com.stratum.feature.program.programSheetMetricsFromEditorDays
import com.stratum.feature.program.programSheetVolumeMuscleLine
import com.stratum.feature.program.programSheetVolumePivot
import com.stratum.feature.program.programSheetVolumeStrip

/**
 * Pinned volume line under the day jump strip. The muscle line is the program
 * total. Pivot opens the muscle table. Open state stays in this composable,
 * not on editor state.
 */
@Composable
internal fun ProgramSheetVolumeStrip(
    state: CreateProgramUiState,
    modifier: Modifier = Modifier,
) {
    val metrics = programSheetMetricsFromEditorDays(state.days)
    val strip = programSheetVolumeStrip(metrics)
    val pivot = programSheetVolumePivot(metrics, state.days)
    var open by remember { mutableStateOf(false) }
    val muscleLine = programSheetVolumeMuscleLine(strip.programMuscles)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = StratumSpacing.s48)
                .background(StratumThemeAccessor.colors.surfaceBase)
                .sheetHairline()
                .padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s8),
    ) {
        ProgramSheetVolumeStripHeader(
            programSetTotal = strip.programSetTotal,
            onOpenPivot = { open = true },
        )
        if (muscleLine.isNotEmpty()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StratumText(
                    text = muscleLine,
                    style =
                        StratumThemeAccessor.typography.bodyMedium.copy(
                            fontFeatureSettings = TABULAR_FIGURES,
                        ),
                    maxLines = 1,
                )
            }
        }
    }
    if (open) {
        StratumBottomSheet(
            onDismissRequest = { open = false },
            title = stringResource(R.string.edit_workout_volume_title),
        ) {
            ProgramSheetVolumePivotTable(pivot = pivot)
        }
    }
}

@Composable
internal fun ProgramSheetVolumePivotTable(
    pivot: ProgramSheetVolumePivot,
    modifier: Modifier = Modifier,
) {
    val horizontal = rememberScrollState()
    val vertical = rememberScrollState()
    val muscleHeader = stringResource(R.string.edit_workout_volume_title)
    val programHeader = stringResource(R.string.edit_workout_volume_scope_program)
    val tableHeight = LocalConfiguration.current.screenHeightDp.dp * PIVOT_TABLE_HEIGHT_FRACTION
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(tableHeight),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.width(VolumeNameMinWidth).fillMaxHeight()) {
            VolumeNameCell(text = muscleHeader, header = true)
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds(),
            ) {
                Column(modifier = Modifier.offset { IntOffset(x = 0, y = -vertical.value) }) {
                    pivot.rows.forEach { row ->
                        VolumeNameCell(text = row.muscleName, header = false)
                    }
                }
            }
            VolumeNameCell(text = "", header = true)
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(horizontal),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                pivot.columns.forEach { column ->
                    VolumeNameCell(
                        text = column.workoutName,
                        header = true,
                        centered = true,
                        countWidth = true,
                    )
                }
                VolumeNameCell(text = programHeader, header = true, centered = true, countWidth = true)
            }
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(vertical),
            ) {
                pivot.rows.forEach { row ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        row.cells.forEach { cell -> VolumeCountCell(cell) }
                        VolumeCountCell(row.programTotal)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                pivot.columns.forEach { column -> VolumeCountCell(column.total) }
                VolumeCountCell(pivot.cornerTotal)
            }
        }
    }
}

@Composable
private fun ProgramSheetVolumeStripHeader(
    programSetTotal: Int,
    onOpenPivot: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StratumText(
            text = stringResource(R.string.edit_workout_volume_title),
            style = StratumThemeAccessor.typography.bodyLarge,
            maxLines = 1,
        )
        StratumText(
            text = stringResource(R.string.edit_workout_volume_scope_program),
            modifier = Modifier.padding(start = StratumSpacing.s12),
            muted = true,
            style = StratumThemeAccessor.typography.bodyMedium,
            maxLines = 1,
        )
        StratumText(
            text =
                pluralStringResource(
                    R.plurals.create_program_sheet_set_total,
                    programSetTotal,
                    programSetTotal,
                ),
            modifier = Modifier.padding(start = StratumSpacing.s4),
            muted = true,
            style = StratumThemeAccessor.typography.bodyMedium,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.weight(1f))
        StratumTextButton(
            text = stringResource(R.string.create_program_sheet_volume_pivot),
            onClick = onOpenPivot,
        )
    }
}

@Composable
private fun VolumeNameCell(
    text: String,
    header: Boolean,
    centered: Boolean = false,
    countWidth: Boolean = false,
) {
    Box(
        modifier =
            Modifier
                .width(if (countWidth) VolumeCountMinWidth else VolumeNameMinWidth)
                .height(VolumeRowHeight)
                .sheetHairline()
                .padding(horizontal = StratumSpacing.s8, vertical = StratumSpacing.s4),
        contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart,
    ) {
        StratumText(
            text = text,
            muted = header,
            style =
                if (header) {
                    StratumThemeAccessor.typography.sectionLabel
                } else {
                    StratumThemeAccessor.typography.bodyMedium
                },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
    }
}

@Composable
private fun VolumeCountCell(sets: Double) {
    Box(
        modifier =
            Modifier
                .width(VolumeCountMinWidth)
                .height(VolumeRowHeight)
                .sheetHairline()
                .padding(horizontal = StratumSpacing.s8, vertical = StratumSpacing.s4),
        contentAlignment = Alignment.Center,
    ) {
        StratumNumber(
            text = formatSheetSetCount(sets),
            style = StratumThemeAccessor.typography.sectionLabel.copy(fontFeatureSettings = TABULAR_FIGURES),
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

private const val TABULAR_FIGURES = "tnum"
private const val PIVOT_TABLE_HEIGHT_FRACTION = 0.55f
private val VolumeRowHeight = StratumSpacing.s32
private val VolumeNameMinWidth = StratumSpacing.s64 + StratumSpacing.s32
private val VolumeCountMinWidth = StratumSpacing.s64 + StratumSpacing.s8
