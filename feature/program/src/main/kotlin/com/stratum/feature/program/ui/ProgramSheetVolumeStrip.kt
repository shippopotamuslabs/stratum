package com.stratum.feature.program.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.program.CreateProgramUiState
import com.stratum.feature.program.ProgramSheetVolumePivot
import com.stratum.feature.program.R
import com.stratum.feature.program.programSheetMetricsFromEditorDays
import com.stratum.feature.program.programSheetVolumeMuscleLabel
import com.stratum.feature.program.programSheetVolumePivot
import com.stratum.feature.program.programSheetVolumeStrip
import java.util.Locale

/**
 * Pinned program-volume line under the day jump strip. Tap opens the muscle
 * pivot. Open state stays in this composable, not on editor state.
 */
@Composable
internal fun ProgramSheetVolumeStrip(
    state: CreateProgramUiState,
    modifier: Modifier = Modifier,
) {
    val metrics = programSheetMetricsFromEditorDays(state.days)
    val strip = programSheetVolumeStrip(metrics, state.days, state.selectedDayId)
    val pivot = programSheetVolumePivot(metrics, state.days)
    var open by remember { mutableStateOf(false) }
    val muscleLine =
        strip.programMuscles.joinToString(separator = VOLUME_ITEM_GAP) { muscle ->
            programSheetVolumeMuscleLabel(muscle.muscleName, muscle.sets)
        }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = StratumSpacing.s48)
                .background(StratumThemeAccessor.colors.surfaceBase)
                .sheetHairline()
                .clickable(role = Role.Button, onClick = { open = true })
                .padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s8),
    ) {
        ProgramSheetVolumeStripHeader(
            workoutName = strip.workoutName,
            workoutSetTotal = strip.workoutSetTotal,
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
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.Top,
        ) {
            VolumeNameColumn(
                header = stringResource(R.string.edit_workout_volume_title),
                names = pivot.muscleNames,
            )
            pivot.workoutColumns.zip(pivot.columnTotals).forEach { (column, total) ->
                VolumeCountColumn(
                    header = column.workoutName,
                    cells = column.cells,
                    total = total,
                )
            }
            VolumeCountColumn(
                header = stringResource(R.string.edit_workout_volume_scope_program),
                cells = pivot.programTotals,
                total = pivot.cornerTotal,
            )
        }
    }
}

@Composable
private fun ProgramSheetVolumeStripHeader(
    workoutName: String,
    workoutSetTotal: Int?,
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
        if (workoutName.isNotEmpty()) {
            StratumText(
                text = workoutName,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = StratumSpacing.s8),
                style = StratumThemeAccessor.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
            )
        }
        if (workoutSetTotal != null) {
            StratumText(
                text =
                    pluralStringResource(
                        R.plurals.create_program_sheet_set_total,
                        workoutSetTotal,
                        workoutSetTotal,
                    ),
                modifier = Modifier.padding(start = if (workoutName.isEmpty()) StratumSpacing.s8 else StratumSpacing.s4),
                muted = true,
                style = StratumThemeAccessor.typography.bodyMedium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun VolumeNameColumn(
    header: String,
    names: List<String>,
) {
    Column {
        VolumeNameCell(text = header, header = true)
        names.forEach { name -> VolumeNameCell(text = name, header = false) }
        VolumeNameCell(text = "", header = true)
    }
}

@Composable
private fun VolumeCountColumn(
    header: String,
    cells: List<Double>,
    total: Double,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        VolumeNameCell(text = header, header = true, centered = true)
        cells.forEach { sets -> VolumeCountCell(sets) }
        VolumeCountCell(total)
    }
}

@Composable
private fun VolumeNameCell(
    text: String,
    header: Boolean,
    centered: Boolean = false,
) {
    Box(
        modifier =
            Modifier
                .widthIn(min = VolumeNameMinWidth)
                .heightIn(min = StratumSpacing.s32)
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
                .widthIn(min = VolumeCountMinWidth)
                .heightIn(min = StratumSpacing.s32)
                .sheetHairline()
                .padding(horizontal = StratumSpacing.s8, vertical = StratumSpacing.s4),
        contentAlignment = Alignment.Center,
    ) {
        StratumNumber(
            text = String.format(Locale.US, ONE_DECIMAL, sets),
            style = StratumThemeAccessor.typography.sectionLabel.copy(fontFeatureSettings = TABULAR_FIGURES),
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

private const val VOLUME_ITEM_GAP = " - "
private const val TABULAR_FIGURES = "tnum"
private const val ONE_DECIMAL = "%.1f"
private val VolumeNameMinWidth = StratumSpacing.s64 + StratumSpacing.s32
private val VolumeCountMinWidth = StratumSpacing.s64 + StratumSpacing.s8
