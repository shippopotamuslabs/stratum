package com.stratum.feature.workout.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.stratum.core.ui.theme.StratumThemeId

private const val PANE_PREVIEW_WIDTH_DP = 560
private const val PANE_PREVIEW_HEIGHT_DP = 700
private const val TWO_PANE_PREVIEW_WIDTH_DP = 1200
private const val TWO_PANE_PREVIEW_HEIGHT_DP = 860

@PreviewTest
@Preview(name = "Light", widthDp = PANE_PREVIEW_WIDTH_DP, heightDp = PANE_PREVIEW_HEIGHT_DP)
@Preview(
    name = "Dark",
    widthDp = PANE_PREVIEW_WIDTH_DP,
    heightDp = PANE_PREVIEW_HEIGHT_DP,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun workoutCoachPaneCoachTonal() {
    WorkoutCoachPanePreviewContent(family = StratumThemeId.Tonal, tab = WorkoutCoachTab.Coach)
}

@PreviewTest
@Preview(name = "Light", widthDp = PANE_PREVIEW_WIDTH_DP, heightDp = PANE_PREVIEW_HEIGHT_DP)
@Preview(
    name = "Dark",
    widthDp = PANE_PREVIEW_WIDTH_DP,
    heightDp = PANE_PREVIEW_HEIGHT_DP,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun workoutCoachPaneOverviewTonal() {
    WorkoutCoachPanePreviewContent(family = StratumThemeId.Tonal, tab = WorkoutCoachTab.Overview)
}

@PreviewTest
@Preview(name = "Light", widthDp = PANE_PREVIEW_WIDTH_DP, heightDp = PANE_PREVIEW_HEIGHT_DP)
@Preview(
    name = "Dark",
    widthDp = PANE_PREVIEW_WIDTH_DP,
    heightDp = PANE_PREVIEW_HEIGHT_DP,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun workoutCoachPaneRestTonal() {
    WorkoutCoachPanePreviewContent(family = StratumThemeId.Tonal, tab = WorkoutCoachTab.Rest)
}

@PreviewTest
@Preview(name = "Light", widthDp = TWO_PANE_PREVIEW_WIDTH_DP, heightDp = TWO_PANE_PREVIEW_HEIGHT_DP)
@Preview(
    name = "Dark",
    widthDp = TWO_PANE_PREVIEW_WIDTH_DP,
    heightDp = TWO_PANE_PREVIEW_HEIGHT_DP,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun workoutCoachTwoPaneTonal() {
    WorkoutCoachTwoPanePreviewContent(family = StratumThemeId.Tonal)
}
