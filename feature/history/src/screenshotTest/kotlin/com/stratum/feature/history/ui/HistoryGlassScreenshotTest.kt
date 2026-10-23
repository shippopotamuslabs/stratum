package com.stratum.feature.history.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest

/**
 * Screenshot tests for the glazed history surfaces (STR-159, §12.8). Thin
 * `@PreviewTest` wrappers over the glass-host preview contents — the bars
 * render glazed (floor + blur + hairline) with content beneath, where the
 * unhosted previews in HistoryScreenshotTest pin the solid fallback and the
 * Neo carve-out.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun workoutHistoryGlass() {
    WorkoutHistoryGlassPreviewContent()
}

@PreviewTest
@PreviewLightDark
@Composable
fun historyDaySessionsGlass() {
    HistoryDaySessionsGlassPreviewContent()
}

@PreviewTest
@PreviewLightDark
@Composable
fun workoutSessionDetailGlass() {
    WorkoutSessionDetailGlassPreviewContent()
}

@PreviewTest
@PreviewLightDark
@Composable
fun editHistoricalWorkoutGlass() {
    EditHistoricalWorkoutGlassPreviewContent()
}
