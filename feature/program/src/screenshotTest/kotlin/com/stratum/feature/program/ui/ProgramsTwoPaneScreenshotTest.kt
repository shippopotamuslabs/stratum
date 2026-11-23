package com.stratum.feature.program.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest

private const val FOLD_PREVIEW_WIDTH_DP = 1200
private const val FOLD_PREVIEW_HEIGHT_DP = 860

/** STR-332: unfolded Program screen, week rows | selected day. */
@PreviewTest
@Preview(name = "Light", widthDp = FOLD_PREVIEW_WIDTH_DP, heightDp = FOLD_PREVIEW_HEIGHT_DP)
@Preview(
    name = "Dark",
    widthDp = FOLD_PREVIEW_WIDTH_DP,
    heightDp = FOLD_PREVIEW_HEIGHT_DP,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun programsTwoPaneNextDayTonal() {
    ProgramsTwoPaneNextDayPreviewContent()
}

@PreviewTest
@Preview(name = "Light", widthDp = FOLD_PREVIEW_WIDTH_DP, heightDp = FOLD_PREVIEW_HEIGHT_DP)
@Preview(
    name = "Dark",
    widthDp = FOLD_PREVIEW_WIDTH_DP,
    heightDp = FOLD_PREVIEW_HEIGHT_DP,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun programsTwoPaneSelectedDayTonal() {
    ProgramsTwoPaneSelectedDayPreviewContent()
}

@PreviewTest
@Preview(name = "Light", widthDp = FOLD_PREVIEW_WIDTH_DP, heightDp = FOLD_PREVIEW_HEIGHT_DP)
@Preview(
    name = "Dark",
    widthDp = FOLD_PREVIEW_WIDTH_DP,
    heightDp = FOLD_PREVIEW_HEIGHT_DP,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun programsTwoPaneHingeTonal() {
    ProgramsTwoPaneHingePreviewContent()
}
