package com.stratum.feature.program.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumMuscleVolumeRow
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTextButton
import com.stratum.core.ui.component.StratumVolumeBodyTier
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.program.CreateProgramUiState
import com.stratum.feature.program.ProgramSheetVolumeChip
import com.stratum.feature.program.ProgramSheetVolumePivot
import com.stratum.feature.program.R
import com.stratum.feature.program.formatSheetSetCount
import com.stratum.feature.program.programSheetMetricsFromEditorDays
import com.stratum.feature.program.programSheetVolumePivot

/**
 * Pinned program volume under the day jump strip. Same Upper and Lower
 * carousel as the program library. By workout opens the muscle table.
 * Open state stays in this composable, not on editor state.
 */
@Composable
internal fun ProgramSheetVolumeStrip(
    state: CreateProgramUiState,
    modifier: Modifier = Modifier,
) {
    val metrics = programSheetMetricsFromEditorDays(state.days)
    val upperCards = metrics.programUpperVolumeByMuscle
    val lowerCards = metrics.programLowerVolumeByMuscle
    val pivot = programSheetVolumePivot(metrics, state.days)
    var open by remember { mutableStateOf(false) }
    if (upperCards.isEmpty() && lowerCards.isEmpty()) return
    val initialTier =
        if (upperCards.isEmpty()) {
            StratumVolumeBodyTier.Lower
        } else {
            StratumVolumeBodyTier.Upper
        }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(StratumThemeAccessor.colors.surfaceBase)
                .sheetHairline()
                .padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s8),
    ) {
        StratumMuscleVolumeRow(
            upperCards = upperCards,
            lowerCards = lowerCards,
            initialTier = initialTier,
            headerTrailing = {
                StratumTextButton(
                    text = stringResource(R.string.create_program_sheet_volume_by_workout),
                    onClick = { open = true },
                    modifier = Modifier.padding(end = StratumSpacing.s8),
                    contentPadding =
                        PaddingValues(
                            horizontal = StratumSpacing.s8,
                            vertical = StratumSpacing.s4,
                        ),
                )
            },
        )
    }
    if (open) {
        StratumBottomSheet(
            onDismissRequest = { open = false },
            title = stringResource(R.string.create_program_sheet_volume_by_workout),
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
    val totalLabel = stringResource(R.string.create_program_sheet_volume_total)
    val headerLabels = remember(pivot.columns, programHeader) {
        buildList {
            for (column in pivot.columns) {
                add(column.workoutName)
            }
            add(programHeader)
        }
    }
    val columnWidths = rememberPivotColumnWidths(headerLabels)
    val layout =
        remember(pivot, columnWidths, programHeader) {
            buildList {
                pivot.columns.forEachIndexed { index, column ->
                    add(
                        PivotColumn(
                            header = column.workoutName,
                            width = columnWidths[index],
                            total = column.total,
                        ),
                    )
                }
                add(
                    PivotColumn(
                        header = programHeader,
                        width = columnWidths[pivot.columns.size],
                        total = pivot.cornerTotal,
                    ),
                )
            }
        }
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
                    for (row in pivot.rows) {
                        VolumeNameCell(text = row.muscleName, header = false)
                    }
                }
            }
            VolumeNameCell(text = totalLabel, header = true, emphasize = true, totals = true)
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(horizontal),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                for (column in layout) {
                    VolumeNameCell(
                        text = column.header,
                        header = true,
                        centered = true,
                        width = column.width,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(vertical),
            ) {
                for (row in pivot.rows) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val values = row.cells + row.programTotal
                        for ((column, value) in layout.zip(values)) {
                            VolumeCountCell(sets = value, width = column.width)
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                for (column in layout) {
                    VolumeCountCell(
                        sets = column.total,
                        width = column.width,
                        emphasize = true,
                        totals = true,
                    )
                }
            }
        }
    }
}

/**
 * Workout columns are at least as wide as their header text, and at least
 * [VolumeCountMinWidth]. The muscle column stays pinned. The value columns scroll.
 */
@Composable
private fun rememberPivotColumnWidths(headers: List<String>): List<Dp> {
    val style = StratumThemeAccessor.typography.sectionLabel
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val padding = StratumSpacing.s8 * 2 + StratumSpacing.s8
    return remember(headers, style, density, padding) {
        headers.map { header ->
            val textWidth =
                with(density) {
                    measurer
                        .measure(
                            text = header,
                            style = style,
                            maxLines = 1,
                            softWrap = false,
                        ).size
                        .width
                        .toDp()
                }
            maxOf(textWidth + padding, VolumeCountMinWidth)
        }
    }
}

/**
 * Muscle chips for a workout header on one scrolling line, in the tonal
 * chip chrome (accentMuted container, onAccentMuted content, the
 * [com.stratum.core.ui.component.StratumChip] convention): the earlier
 * surfaceInset fill sat within a shade of the sheet background in light theme
 * and the chips disappeared. The count uses a fixed slot so a set edit does
 * not change chip width or reflow the sheet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SheetVolumeChips(
    chips: List<ProgramSheetVolumeChip>,
    modifier: Modifier = Modifier,
) {
    if (chips.isEmpty()) return
    FlowRow(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = StratumSpacing.s4),
        horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
        maxLines = 1,
    ) {
        for (chip in chips) {
            SheetVolumeChip(name = chip.muscleName, countText = chip.countText)
        }
    }
}

@Composable
private fun SheetVolumeChip(
    name: String,
    countText: String,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Row(
        modifier =
            Modifier
                .background(color = colors.accentMuted, shape = shapes.chip)
                .padding(horizontal = StratumSpacing.s8, vertical = StratumSpacing.s4),
        horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StratumText(
            text = name,
            style = StratumThemeAccessor.typography.sectionLabel,
            color = colors.onAccentMuted,
            maxLines = 1,
        )
        StratumNumber(
            text = countText,
            modifier = Modifier.width(StratumSpacing.s48),
            style =
                StratumThemeAccessor.typography.sectionLabel.copy(
                    color = colors.onAccentMuted,
                    fontFeatureSettings = TABULAR_FIGURES,
                ),
            maxLines = 1,
            textAlign = TextAlign.End,
        )
    }
}

/** One pivot value column: header label, measured width, and column total. */
private data class PivotColumn(
    val header: String,
    val width: Dp,
    val total: Double,
)

/**
 * The shared pivot cell shell: fixed row height, the totals rule on totals
 * rows, the bottom hairline, and the cell padding. Name and count cells
 * differ only in content and alignment.
 */
@Composable
private fun VolumeCellBox(
    width: Dp,
    totals: Boolean,
    contentAlignment: Alignment,
    content: @Composable BoxScope.() -> Unit,
) {
    var cellModifier =
        Modifier
            .width(width)
            .height(VolumeRowHeight)
    if (totals) {
        cellModifier =
            cellModifier.sheetRule(
                color = StratumThemeAccessor.colors.borderStrong,
                top = true,
            )
    }
    Box(
        modifier =
            cellModifier
                .sheetHairline()
                .padding(horizontal = StratumSpacing.s8, vertical = StratumSpacing.s4),
        contentAlignment = contentAlignment,
        content = content,
    )
}

@Composable
private fun VolumeNameCell(
    text: String,
    header: Boolean,
    centered: Boolean = false,
    width: Dp = VolumeNameMinWidth,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    emphasize: Boolean = false,
    totals: Boolean = false,
) {
    VolumeCellBox(
        width = width,
        totals = totals,
        contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart,
    ) {
        val label = StratumThemeAccessor.typography.sectionLabel
        StratumText(
            text = text,
            muted = header && !emphasize,
            style =
                when {
                    emphasize -> label.copy(fontWeight = FontWeight.Bold)
                    header -> label
                    else -> StratumThemeAccessor.typography.bodyMedium
                },
            maxLines = 1,
            overflow = overflow,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
    }
}

@Composable
private fun VolumeCountCell(
    sets: Double,
    width: Dp,
    emphasize: Boolean = false,
    totals: Boolean = false,
) {
    val colors = StratumThemeAccessor.colors
    var style =
        StratumThemeAccessor.typography.sectionLabel.copy(
            fontFeatureSettings = TABULAR_FIGURES,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Medium,
        )
    if (sets == 0.0) {
        style = style.copy(color = colors.textMuted.copy(alpha = ZERO_CELL_ALPHA))
    }
    VolumeCellBox(
        width = width,
        totals = totals,
        contentAlignment = Alignment.Center,
    ) {
        StratumNumber(
            text = formatSheetSetCount(sets),
            style = style,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

private const val TABULAR_FIGURES = "tnum"
private const val ZERO_CELL_ALPHA = 0.4f
private const val PIVOT_TABLE_HEIGHT_FRACTION = 0.55f
private val VolumeRowHeight = StratumSpacing.s32
private val VolumeNameMinWidth = StratumSpacing.s64 + StratumSpacing.s32
private val VolumeCountMinWidth = StratumSpacing.s64 + StratumSpacing.s8
