package com.stratum.feature.workout.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest

/**
 * Screenshot tests for the workout surfaces under a real glass host
 * (STR-157). The solid-host cases in WorkoutScreenScreenshotTest and
 * WorkoutNotesAndCompleteScreenshotTest pin the fallback geometry; these
 * pin the glazed rendering the Nav3 entries produce on device: top bar
 * glazed with its permanent hairline, the session's navigator row on its
 * own glazed strip directly beneath the bar (STR-196) with the lift list
 * starting beneath both and scrolling under both, and the complete
 * screen's summary scrolling under the bar from its top clearance.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun workoutSessionGlassTonal() {
    WorkoutSessionGlassPreviewContent()
}

@PreviewTest
@PreviewLightDark
@Composable
fun workoutCompleteGlassTonal() {
    WorkoutCompleteGlassPreviewContent()
}

@PreviewTest
@PreviewLightDark
@Composable
fun manageNotesGlassTonal() {
    ManageNotesGlassPreviewContent()
}
