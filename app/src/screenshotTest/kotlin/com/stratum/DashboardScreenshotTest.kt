package com.stratum

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.stratum.core.ui.component.PanePosture
import com.stratum.core.ui.component.paneFlatHingeFixture
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
            state = DashboardBoardUiState.Empty,
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
            state = DashboardBoardUiState.Empty,
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
            state = DashboardBoardUiState.Loading,
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
            state = DashboardBoardUiState.Loading,
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

@PreviewTest
@PreviewLightDark
@Composable
fun healthUnavailableTonal() {
    StratumTheme(themeId = StratumThemeId.Tonal) {
        DashboardHealthUnavailable()
    }
}

@PreviewTest
@PreviewLightDark
@Composable
fun healthUnavailableNeo() {
    StratumTheme(themeId = StratumThemeId.NeoBrutalist) {
        DashboardHealthUnavailable()
    }
}

private const val FOLD_PREVIEW_WIDTH_DP = 1200
private const val FOLD_PREVIEW_HEIGHT_DP = 860

/** STR-331: unfolded two-pane Dashboard, Muscles view with Chest selected. */
@PreviewTest
@Preview(name = "Light", widthDp = FOLD_PREVIEW_WIDTH_DP, heightDp = FOLD_PREVIEW_HEIGHT_DP)
@Preview(
    name = "Dark",
    widthDp = FOLD_PREVIEW_WIDTH_DP,
    heightDp = FOLD_PREVIEW_HEIGHT_DP,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun dashboardTwoPaneMuscleTonal() {
    StratumTheme(themeId = StratumThemeId.Tonal) {
        DashboardScreenPreviewContent(
            health = previewHealthState(),
            posture = PanePosture.TwoPane(hinge = null),
            pane = previewMusclePaneState(),
        )
    }
}

/** STR-331: unfolded two-pane Dashboard, Lifts view with Bench Press open in the pane. */
@PreviewTest
@Preview(name = "Light", widthDp = FOLD_PREVIEW_WIDTH_DP, heightDp = FOLD_PREVIEW_HEIGHT_DP)
@Preview(
    name = "Dark",
    widthDp = FOLD_PREVIEW_WIDTH_DP,
    heightDp = FOLD_PREVIEW_HEIGHT_DP,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun dashboardTwoPaneLiftTonal() {
    StratumTheme(themeId = StratumThemeId.Tonal) {
        DashboardScreenPreviewContent(
            health = previewHealthState(),
            selectedTab = DashboardBoardTabUi.Lifts,
            posture = PanePosture.TwoPane(hinge = null),
            pane = previewLiftPaneSelection(),
        )
    }
}

/** STR-331: flat fold with a reported hinge band between the panes. */
@PreviewTest
@Preview(name = "Light", widthDp = FOLD_PREVIEW_WIDTH_DP, heightDp = FOLD_PREVIEW_HEIGHT_DP)
@Preview(
    name = "Dark",
    widthDp = FOLD_PREVIEW_WIDTH_DP,
    heightDp = FOLD_PREVIEW_HEIGHT_DP,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun dashboardTwoPaneHingeTonal() {
    StratumTheme(themeId = StratumThemeId.Tonal) {
        DashboardScreenPreviewContent(
            health = previewHealthState(),
            posture = paneFlatHingeFixture(),
            pane = previewMusclePaneState(),
        )
    }
}
