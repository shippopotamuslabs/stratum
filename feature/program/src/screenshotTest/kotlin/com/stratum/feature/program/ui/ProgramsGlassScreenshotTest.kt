package com.stratum.feature.program.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest

/**
 * Screenshot tests for the active-workout drawer under a real glass host
 * (STR-162). The solid-host cases in ProgramsLibraryScreenshotTest pin the
 * drawer's presence and legacy geometry; this pair pins the field failure
 * itself: hosted the way MainTabShell hosts it (glass nav, no reserved
 * bottom space), the FULL drawer — name, timer, next lift — must rest above
 * the nav bar, glazed, with content scrolling beneath the glass. The
 * STR-156 regression (drawer fully hidden) and the STR-162 regression
 * (drawer under-lifted, next-lift line behind the bar, sheet slab instead
 * of glaze) both change this golden.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun programsScreenActiveWorkoutGlassTonal() {
    ProgramsActiveWorkoutGlassPreviewContent()
}
