package com.stratum.feature.program.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId
import com.stratum.core.ui.component.PanePosture
import com.stratum.core.ui.component.paneFlatHingeFixture
import com.stratum.core.ui.component.LocalPanePosture

private const val FOLD_PREVIEW_WIDTH_DP = 1200
private const val FOLD_PREVIEW_HEIGHT_DP = 860

@Preview(name = "Light", widthDp = FOLD_PREVIEW_WIDTH_DP, heightDp = FOLD_PREVIEW_HEIGHT_DP)
@Preview(
    name = "Dark",
    widthDp = FOLD_PREVIEW_WIDTH_DP,
    heightDp = FOLD_PREVIEW_HEIGHT_DP,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ProgramSheetTwoPaneDashboardPreview() {
    ProgramSheetTwoPaneDashboardPreviewContent()
}

@Preview(name = "Light", widthDp = FOLD_PREVIEW_WIDTH_DP, heightDp = FOLD_PREVIEW_HEIGHT_DP)
@Preview(
    name = "Dark",
    widthDp = FOLD_PREVIEW_WIDTH_DP,
    heightDp = FOLD_PREVIEW_HEIGHT_DP,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ProgramSheetTwoPaneLiftPreview() {
    ProgramSheetTwoPaneLiftPreviewContent()
}

@Preview(name = "Light", widthDp = FOLD_PREVIEW_WIDTH_DP, heightDp = FOLD_PREVIEW_HEIGHT_DP)
@Preview(
    name = "Dark",
    widthDp = FOLD_PREVIEW_WIDTH_DP,
    heightDp = FOLD_PREVIEW_HEIGHT_DP,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ProgramSheetSinglePanePreview() {
    ProgramSheetSinglePanePreviewContent()
}

@Preview(name = "Light", widthDp = FOLD_PREVIEW_WIDTH_DP, heightDp = FOLD_PREVIEW_HEIGHT_DP)
@Preview(
    name = "Dark",
    widthDp = FOLD_PREVIEW_WIDTH_DP,
    heightDp = FOLD_PREVIEW_HEIGHT_DP,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ProgramSheetHingePreview() {
    ProgramSheetHingePreviewContent()
}

@Composable
internal fun ProgramSheetTwoPaneDashboardPreviewContent() {
    FoldableSheetPreview(posture = PanePosture.TwoPane(hinge = null), expandedLift = false)
}

@Composable
internal fun ProgramSheetTwoPaneLiftPreviewContent() {
    FoldableSheetPreview(posture = PanePosture.TwoPane(hinge = null), expandedLift = true)
}

@Composable
internal fun ProgramSheetSinglePanePreviewContent() {
    FoldableSheetPreview(posture = PanePosture.SinglePane, expandedLift = true)
}

@Composable
internal fun ProgramSheetHingePreviewContent() {
    FoldableSheetPreview(posture = paneFlatHingeFixture(), expandedLift = false)
}

@Composable
private fun FoldableSheetPreview(
    posture: PanePosture,
    expandedLift: Boolean,
) {
    StratumTheme(themeId = StratumThemeId.Tonal) {
        CompositionLocalProvider(LocalPanePosture provides posture) {
            CreateProgramStepPreviewContent(
                family = StratumThemeId.Tonal,
                state = previewProgramSheetState(),
                initialExpandedLift = if (expandedLift) previewSheetExpandedLift() else null,
            )
        }
    }
}
