package com.stratum.feature.program.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.stratum.core.ui.component.StratumGlassHost
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.StratumThemeId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.feature.program.ProgramSheetVolumeColumn
import com.stratum.feature.program.ProgramSheetVolumePivot
import com.stratum.feature.program.ProgramSheetVolumePivotRow
import com.stratum.feature.program.programSheetMetricsFromEditorDays
import com.stratum.feature.program.programSheetVolumePivot

/**
 * Strip and pivot-table previews. `darkTheme` stays null so [PreviewLightDark]
 * varies light and dark. Glass wrappers host the same content.
 */
@PreviewLightDark
@Composable
private fun ProgramSheetVolumeStripPreview() {
    ProgramSheetVolumeStripPreviewContent()
}

@PreviewLightDark
@Composable
private fun ProgramSheetVolumeStripGlassPreview() {
    ProgramSheetVolumeStripGlassPreviewContent()
}

@PreviewLightDark
@Composable
private fun ProgramSheetVolumePivotPreview() {
    ProgramSheetVolumePivotPreviewContent()
}

@PreviewLightDark
@Composable
private fun ProgramSheetVolumePivotGlassPreview() {
    ProgramSheetVolumePivotGlassPreviewContent()
}

@Composable
internal fun ProgramSheetVolumeStripPreviewContent() {
    StratumTheme(themeId = StratumThemeId.Tonal) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(StratumThemeAccessor.colors.surfaceBase),
        ) {
            ProgramSheetVolumeStrip(state = previewProgramSheetState())
        }
    }
}

@Composable
internal fun ProgramSheetVolumeStripGlassPreviewContent() {
    StratumGlassHost {
        ProgramSheetVolumeStripPreviewContent()
    }
}

@Composable
internal fun ProgramSheetVolumePivotPreviewContent() {
    val state = previewProgramSheetState()
    val pivot = programSheetVolumePivot(programSheetMetricsFromEditorDays(state.days), state.days)
    StratumTheme(themeId = StratumThemeId.Tonal) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(StratumThemeAccessor.colors.surfaceInset)
                    .padding(StratumSpacing.s12),
        ) {
            ProgramSheetVolumePivotTable(pivot = pivot)
        }
    }
}

@Composable
internal fun ProgramSheetVolumePivotGlassPreviewContent() {
    StratumGlassHost {
        ProgramSheetVolumePivotPreviewContent()
    }
}

@PreviewLightDark
@Composable
private fun ProgramSheetVolumePivotTallPreview() {
    ProgramSheetVolumePivotTallPreviewContent()
}

@PreviewLightDark
@Composable
private fun ProgramSheetVolumePivotTallGlassPreview() {
    ProgramSheetVolumePivotTallGlassPreviewContent()
}

/**
 * Every tracked muscle, so the table overflows its viewport and the name and
 * value columns ride their shared scroll. The standard fixture fits the
 * viewport, which is how the end-of-scroll name cutoff shipped unnoticed.
 */
@Composable
internal fun ProgramSheetVolumePivotTallPreviewContent() {
    StratumTheme(themeId = StratumThemeId.Tonal) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(StratumThemeAccessor.colors.surfaceInset)
                    .padding(StratumSpacing.s12),
        ) {
            ProgramSheetVolumePivotTable(pivot = tallPreviewPivot())
        }
    }
}

@Composable
internal fun ProgramSheetVolumePivotTallGlassPreviewContent() {
    StratumGlassHost {
        ProgramSheetVolumePivotTallPreviewContent()
    }
}

private fun tallPreviewPivot(): ProgramSheetVolumePivot {
    val workoutNames = listOf("Upper A", "Lower A", "Upper B", "Lower B")
    val rows =
        MuscleGroup.entries.mapIndexed { index, muscle ->
            val cells =
                List(workoutNames.size) { column ->
                    if ((index + column) % 3 == 0) 0.0 else 1.5 + ((index * 7 + column * 3) % 5)
                }
            ProgramSheetVolumePivotRow(
                muscle = muscle,
                muscleName = muscle.displayLabel,
                programTotal = cells.sum(),
                cells = cells,
            )
        }
    val columns =
        workoutNames.mapIndexed { index, name ->
            ProgramSheetVolumeColumn(
                dayId = "day-$index",
                workoutName = name,
                total = rows.sumOf { row -> row.cells[index] },
            )
        }
    return ProgramSheetVolumePivot(
        columns = columns,
        rows = rows,
        cornerTotal = rows.sumOf { row -> row.programTotal },
    )
}
