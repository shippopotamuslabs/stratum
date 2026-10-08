package com.stratum

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId

/**
 * Screenshot tests for the dashboard (STR-93). Thin `@PreviewTest` wrappers over the shared
 * preview content in `DashboardScreenPreview.kt` — fake state lives in main sources so Studio
 * `@Preview`s and goldens can never drift apart. The activity/weight pages pin the STR-87
 * steps/weight y-axis work.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun dashboardLoadedTonal() {
    StratumTheme(themeId = StratumThemeId.Tonal) {
        DashboardScreenPreviewContent(health = previewHealthState())
    }
}

@PreviewTest
@PreviewLightDark
@Composable
fun dashboardLoadedNeo() {
    StratumTheme(themeId = StratumThemeId.NeoBrutalist) {
        DashboardScreenPreviewContent(health = previewHealthState())
    }
}

@PreviewTest
@PreviewLightDark
@Composable
fun dashboardLoadedWeightTonal() {
    StratumTheme(themeId = StratumThemeId.Tonal) {
        DashboardScreenPreviewContent(health = previewHealthState(selectedPage = 1))
    }
}

@PreviewTest
@PreviewLightDark
@Composable
fun dashboardLoadedWeightNeo() {
    StratumTheme(themeId = StratumThemeId.NeoBrutalist) {
        DashboardScreenPreviewContent(health = previewHealthState(selectedPage = 1))
    }
}

@PreviewTest
@PreviewLightDark
@Composable
fun dashboardLoadedRecoveryTonal() {
    StratumTheme(themeId = StratumThemeId.Tonal) {
        DashboardScreenPreviewContent(health = previewHealthState(selectedPage = 2))
    }
}

@PreviewTest
@PreviewLightDark
@Composable
fun dashboardLoadedRecoveryNeo() {
    StratumTheme(themeId = StratumThemeId.NeoBrutalist) {
        DashboardScreenPreviewContent(health = previewHealthState(selectedPage = 2))
    }
}

@PreviewTest
@PreviewLightDark
@Composable
fun dashboardEmptyTonal() {
    StratumTheme(themeId = StratumThemeId.Tonal) {
        DashboardScreen(
            state = DashboardUiState.Empty,
            health = previewEmptyHealthState(),
            isRefreshing = false,
            onRefresh = {},
            onNavigateToSettings = {},
        )
    }
}

@PreviewTest
@PreviewLightDark
@Composable
fun dashboardEmptyNeo() {
    StratumTheme(themeId = StratumThemeId.NeoBrutalist) {
        DashboardScreen(
            state = DashboardUiState.Empty,
            health = previewEmptyHealthState(),
            isRefreshing = false,
            onRefresh = {},
            onNavigateToSettings = {},
        )
    }
}

@PreviewTest
@PreviewLightDark
@Composable
fun dashboardLoadingTonal() {
    StratumTheme(themeId = StratumThemeId.Tonal) {
        DashboardScreen(
            state = DashboardUiState.Loading,
            onNavigateToSettings = {},
        )
    }
}

@PreviewTest
@PreviewLightDark
@Composable
fun dashboardLoadingNeo() {
    StratumTheme(themeId = StratumThemeId.NeoBrutalist) {
        DashboardScreen(
            state = DashboardUiState.Loading,
            onNavigateToSettings = {},
        )
    }
}

@PreviewTest
@PreviewLightDark
@Composable
fun healthDisabledPromptTonal() {
    StratumTheme(themeId = StratumThemeId.Tonal) {
        DashboardHealthDisabledPrompt(onNavigateToSettings = {})
    }
}

@PreviewTest
@PreviewLightDark
@Composable
fun healthDisabledPromptNeo() {
    StratumTheme(themeId = StratumThemeId.NeoBrutalist) {
        DashboardHealthDisabledPrompt(onNavigateToSettings = {})
    }
}
