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
