package com.stratum.feature.subscription.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest

/**
 * Screenshot test for the glazed paywall (STR-161, §12.8). A thin
 * `@PreviewTest` wrapper over the glass-host preview content — the bar
 * renders glazed (floor + blur + hairline) with content beneath, where the
 * unhosted previews in PaywallScreenshotTest pin the solid fallback and
 * the Neo carve-out.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun paywallGlass() {
    PaywallGlassPreviewContent()
}
