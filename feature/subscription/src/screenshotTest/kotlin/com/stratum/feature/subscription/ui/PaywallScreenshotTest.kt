package com.stratum.feature.subscription.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.stratum.core.ui.theme.StratumThemeId

/**
 * Screenshot tests for the paywall (STR-95). Thin `@PreviewTest` wrappers over the shared
 * preview content.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun paywallContentTonal() {
    PaywallPreviewContent(
        family = StratumThemeId.Tonal,
        uiState = previewPaywallContent(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun paywallContentNeo() {
    PaywallPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        uiState = previewPaywallContent(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun paywallErrorTonal() {
    PaywallPreviewContent(
        family = StratumThemeId.Tonal,
        uiState = previewPaywallError(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun paywallErrorNeo() {
    PaywallPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        uiState = previewPaywallError(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun paywallEntitledTonal() {
    PaywallPreviewContent(
        family = StratumThemeId.Tonal,
        uiState = previewPaywallEntitled(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun paywallEntitledNeo() {
    PaywallPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        uiState = previewPaywallEntitled(),
    )
}
