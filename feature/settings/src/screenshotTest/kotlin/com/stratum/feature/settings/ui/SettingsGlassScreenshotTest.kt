package com.stratum.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest

/**
 * Screenshot tests for the glazed settings surfaces (STR-161, §12.8). Thin
 * `@PreviewTest` wrappers over the glass-host preview contents — the bars
 * render glazed (floor + blur + hairline) with content beneath, where the
 * unhosted previews in SettingsScreenshotTest pin the solid fallback and
 * the Neo carve-out.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun settingsGlass() {
    SettingsGlassPreviewContent()
}

@PreviewTest
@PreviewLightDark
@Composable
fun legalGlass() {
    LegalGlassPreviewContent()
}

@PreviewTest
@PreviewLightDark
@Composable
fun importDataGlass() {
    ImportDataGlassPreviewContent()
}

@PreviewTest
@PreviewLightDark
@Composable
fun exportDataGlass() {
    ExportDataGlassPreviewContent()
}

@PreviewTest
@PreviewLightDark
@Composable
fun exportLogsGlass() {
    ExportLogsGlassPreviewContent()
}

@PreviewTest
@PreviewLightDark
@Composable
fun exportProgramsGlass() {
    ExportProgramsGlassPreviewContent()
}

@PreviewTest
@PreviewLightDark
@Composable
fun importLogsGlass() {
    ImportLogsGlassPreviewContent()
}

@PreviewTest
@PreviewLightDark
@Composable
fun importProgramGlass() {
    ImportProgramGlassPreviewContent()
}
