package com.stratum.feature.program.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest

/**
 * Sheet volume strip and pivot table. Thin wrappers over the main-source
 * previews so light/dark and glass on/off stay in lockstep with Studio.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun programSheetVolumeStripTonal() {
    ProgramSheetVolumeStripPreviewContent()
}

@PreviewTest
@PreviewLightDark
@Composable
fun programSheetVolumeStripGlass() {
    ProgramSheetVolumeStripGlassPreviewContent()
}

@PreviewTest
@PreviewLightDark
@Composable
fun programSheetVolumePivotTonal() {
    ProgramSheetVolumePivotPreviewContent()
}

@PreviewTest
@PreviewLightDark
@Composable
fun programSheetVolumePivotGlass() {
    ProgramSheetVolumePivotGlassPreviewContent()
}

@PreviewTest
@PreviewLightDark
@Composable
fun programSheetVolumePivotTallTonal() {
    ProgramSheetVolumePivotTallPreviewContent()
}

@PreviewTest
@PreviewLightDark
@Composable
fun programSheetVolumePivotTallGlass() {
    ProgramSheetVolumePivotTallGlassPreviewContent()
}
