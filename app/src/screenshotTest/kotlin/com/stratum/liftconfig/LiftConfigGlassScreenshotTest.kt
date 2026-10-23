package com.stratum.liftconfig

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest

/**
 * Screenshot tests for the glazed lift-config surfaces (STR-160, §12.8).
 * Thin `@PreviewTest` wrappers over the glass-host preview contents — the
 * bars render glazed (floor + blur + hairline) with content beneath, where
 * the unhosted previews in LiftConfigScreenshotTest pin the solid fallback
 * and the Neo carve-out.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun createLiftGlass() {
    CreateLiftGlassPreviewContent()
}

@PreviewTest
@PreviewLightDark
@Composable
fun manageEquipmentGlass() {
    ManageEquipmentGlassPreviewContent()
}

@PreviewTest
@PreviewLightDark
@Composable
fun liftInformationGlass() {
    LiftInformationGlassPreviewContent()
}

@PreviewTest
@PreviewLightDark
@Composable
fun incrementEditorGlass() {
    IncrementEditorGlassPreviewContent()
}
