package com.stratum.feature.onboarding.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.stratum.feature.onboarding.ImportProgramPhaseUi
import com.stratum.feature.onboarding.R as FeatureR
import com.stratum.feature.onboarding.activeImportTimelineIndex
import com.stratum.feature.onboarding.programImportTimelinePhases

/**
 * Import-specific wrapper around [OnboardingProgressTimeline]. Steps cover upload through catalog
 * matching, including AI conversion for custom spreadsheets.
 */
@Composable
internal fun ProgramImportTimeline(
    phase: ImportProgramPhaseUi?,
    maxActiveIndex: Int,
    modifier: Modifier = Modifier,
) {
    val currentIndex = phase.activeImportTimelineIndex()
    val activeIndex = maxOf(maxActiveIndex, currentIndex)
    val stepLabels =
        programImportTimelinePhases.map { step ->
            stringResource(programImportPhaseStringRes(step))
        }

    OnboardingProgressTimeline(
        stepLabels = stepLabels,
        activeIndex = activeIndex,
        activeDetail = null,
        modifier = modifier,
    )
}

@StringRes
internal fun programImportPhaseStringRes(phase: ImportProgramPhaseUi): Int =
    when (phase) {
        ImportProgramPhaseUi.Uploading -> FeatureR.string.onboarding_import_phase_uploading
        ImportProgramPhaseUi.Sniffing -> FeatureR.string.onboarding_import_phase_sniffing
        ImportProgramPhaseUi.Analyzing -> FeatureR.string.onboarding_import_phase_analyzing
        ImportProgramPhaseUi.Converting -> FeatureR.string.onboarding_import_phase_converting
        ImportProgramPhaseUi.Parsing -> FeatureR.string.onboarding_import_phase_parsing
        ImportProgramPhaseUi.MatchingCatalog -> FeatureR.string.onboarding_import_phase_matching_catalog
        ImportProgramPhaseUi.Complete -> FeatureR.string.onboarding_import_phase_complete
    }
