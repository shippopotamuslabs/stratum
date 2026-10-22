package com.stratum.feature.program.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest

/**
 * Screenshot tests for the glazed program detail + editor surfaces (STR-158,
 * §12.8). Thin `@PreviewTest` wrappers over the glass-host preview contents —
 * the bars render glazed (floor + blur + hairline) with content beneath,
 * where the unhosted previews in the sibling test files pin the solid
 * fallback and the Neo carve-out.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun createProgramEditorGlass() {
    CreateProgramGlassPreviewContent()
}

@PreviewTest
@PreviewLightDark
@Composable
fun editWorkoutGlass() {
    EditWorkoutGlassPreviewContent()
}

@PreviewTest
@PreviewLightDark
@Composable
fun liftSwapGlass() {
    LiftSwapGlassPreviewContent()
}

@PreviewTest
@PreviewLightDark
@Composable
fun programLibraryGlass() {
    ProgramLibraryGlassPreviewContent()
}

@PreviewTest
@PreviewLightDark
@Composable
fun checkInGlass() {
    CheckInGlassPreviewContent()
}
