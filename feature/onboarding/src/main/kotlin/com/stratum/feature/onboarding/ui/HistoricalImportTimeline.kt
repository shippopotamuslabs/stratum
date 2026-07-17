package com.stratum.feature.onboarding.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.StratumProgressTimeline
import com.stratum.core.ui.importing.StratumImportProgressSection
import com.stratum.feature.onboarding.ImportProgressPhaseUi
import com.stratum.feature.onboarding.ImportProgressUi
import com.stratum.feature.onboarding.historicalImportTimelineIndex

/**
 * Historical training-log import timeline for the Gym card. Uses shared import progress chrome
 * and the shared 4-step labels from `:core:ui`.
 */
@Composable
internal fun HistoricalImportTimeline(
    progress: ImportProgressUi,
    maxActiveIndex: Int,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    val phase = progress.phase.takeUnless { it == ImportProgressPhaseUi.Failed }
    val currentIndex = phase.historicalImportTimelineIndex()
    val activeIndex = maxOf(maxActiveIndex, currentIndex)
    val stepLabels =
        listOf(
            stringResource(CoreUiR.string.import_timeline_upload),
            stringResource(CoreUiR.string.import_timeline_convert),
            stringResource(CoreUiR.string.import_timeline_catalog),
            stringResource(CoreUiR.string.import_timeline_analyze),
        )
    val activeDetail = importProgressSubtitle(progress)

    if (title != null) {
        StratumImportProgressSection(
            title = title,
            stepLabels = stepLabels,
            activeIndex = activeIndex,
            activeDetail = activeDetail,
            modifier = modifier,
        )
    } else {
        // Gym card embeds timeline without a section title (parent provides context).
        StratumProgressTimeline(
            stepLabels = stepLabels,
            activeIndex = activeIndex,
            activeDetail = activeDetail,
            modifier = modifier,
        )
    }
}
