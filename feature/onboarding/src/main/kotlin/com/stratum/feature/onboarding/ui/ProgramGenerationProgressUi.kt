package com.stratum.feature.onboarding.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.stratum.feature.onboarding.ProgramGenerationProgressPhaseUi
import com.stratum.feature.onboarding.ProgramGenerationProgressUi
import com.stratum.feature.onboarding.R as FeatureR

private val TECHNICAL_GENERATION_DETAIL_PATTERNS =
    listOf(
        Regex("(?i)traceback"),
        Regex("(?i)KeyError"),
        Regex("(?i)workoutLiftSets"),
        Regex("(?i)progressionScheme"),
        Regex("(?i)validate_"),
        Regex("(?i)json_schema"),
        Regex("(?i)must be a JSON object"),
    )

/** Hides schema/validation jargon from onboarding progress subtitles. */
internal fun userFacingGenerationDetail(detail: String): String? =
    detail.takeIf { text -> TECHNICAL_GENERATION_DETAIL_PATTERNS.none { it.containsMatchIn(text) } }

/**
 * Returns [detail] when it adds information beyond [phaseLabel]. Server progress messages often
 * repeat the phase headline (e.g. refining_program); model tool intents should still show as subtitle.
 */
internal fun distinctGenerationDetail(detail: String, phaseLabel: String): String? {
    val cleaned = userFacingGenerationDetail(detail) ?: return null
    if (normalizeProgressCopy(cleaned) == normalizeProgressCopy(phaseLabel)) {
        return null
    }
    return cleaned
}

private fun normalizeProgressCopy(text: String): String =
    text.trim().trimEnd('.', '…').lowercase()

@Composable
internal fun programGenerationPhaseLabel(progress: ProgramGenerationProgressUi): String =
    when (progress.phase) {
        ProgramGenerationProgressPhaseUi.PreparingPayload ->
            stringResource(FeatureR.string.onboarding_generate_phase_preparing)
        ProgramGenerationProgressPhaseUi.ReviewingProfile ->
            stringResource(FeatureR.string.onboarding_generate_phase_reviewing)
        ProgramGenerationProgressPhaseUi.ValidatingProgram ->
            stringResource(FeatureR.string.onboarding_generate_phase_validating)
        ProgramGenerationProgressPhaseUi.DraftingProgram ->
            stringResource(FeatureR.string.onboarding_generate_phase_drafting)
        ProgramGenerationProgressPhaseUi.RefiningProgram ->
            stringResource(FeatureR.string.onboarding_generate_phase_refining)
        ProgramGenerationProgressPhaseUi.SplitPlanning ->
            stringResource(FeatureR.string.onboarding_generate_phase_split_planning)
        ProgramGenerationProgressPhaseUi.BuildingWorkouts ->
            stringResource(FeatureR.string.onboarding_generate_phase_building_workouts)
        ProgramGenerationProgressPhaseUi.Integrating ->
            stringResource(FeatureR.string.onboarding_generate_phase_integrating)
        ProgramGenerationProgressPhaseUi.SavingProgram ->
            stringResource(FeatureR.string.onboarding_generate_phase_saving)
        ProgramGenerationProgressPhaseUi.Complete ->
            stringResource(FeatureR.string.onboarding_generate_phase_complete)
        ProgramGenerationProgressPhaseUi.Failed ->
            stringResource(FeatureR.string.onboarding_generate_error_title)
    }

@Composable
internal fun programGenerationProgressSubtitle(
    progress: ProgramGenerationProgressUi,
    phaseLabel: String,
): String? =
    progress.detail?.let { detail -> distinctGenerationDetail(detail, phaseLabel) }
