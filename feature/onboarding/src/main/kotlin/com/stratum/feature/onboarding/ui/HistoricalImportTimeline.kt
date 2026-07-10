package com.stratum.feature.onboarding.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.stratum.feature.onboarding.HistoricalImportTimelineStep
import com.stratum.feature.onboarding.ImportProgressPhaseUi
import com.stratum.feature.onboarding.ImportProgressUi
import com.stratum.feature.onboarding.R as FeatureR
import com.stratum.feature.onboarding.historicalImportTimelineIndex
import com.stratum.feature.onboarding.historicalImportTimelineSteps

/**
 * Historical training-log import timeline for the Gym card. Reuses the shared
 * [OnboardingProgressTimeline] chrome from Review generate/import.
 */
@Composable
internal fun HistoricalImportTimeline(
    progress: ImportProgressUi,
    maxActiveIndex: Int,
    modifier: Modifier = Modifier,
) {
    val phase = progress.phase.takeUnless { it == ImportProgressPhaseUi.Failed }
    val currentIndex = phase.historicalImportTimelineIndex()
    val activeIndex = maxOf(maxActiveIndex, currentIndex)
    val stepLabels =
        historicalImportTimelineSteps.map { step ->
            stringResource(historicalImportTimelineStepStringRes(step))
        }
    val activeDetail = importProgressSubtitle(progress)

    OnboardingProgressTimeline(
        stepLabels = stepLabels,
        activeIndex = activeIndex,
        activeDetail = activeDetail,
        modifier = modifier,
    )
}

@StringRes
internal fun historicalImportTimelineStepStringRes(step: HistoricalImportTimelineStep): Int =
    when (step) {
        HistoricalImportTimelineStep.Upload -> FeatureR.string.gym_lifts_import_timeline_upload
        HistoricalImportTimelineStep.Convert -> FeatureR.string.gym_lifts_import_timeline_convert
        HistoricalImportTimelineStep.Catalog -> FeatureR.string.gym_lifts_import_timeline_catalog
        HistoricalImportTimelineStep.Analyze -> FeatureR.string.gym_lifts_import_timeline_analyze
    }
