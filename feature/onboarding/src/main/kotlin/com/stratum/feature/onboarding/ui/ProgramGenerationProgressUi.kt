package com.stratum.feature.onboarding.ui

import androidx.annotation.StringRes
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

@StringRes
internal fun programGenerationPhaseStringRes(phase: ProgramGenerationProgressPhaseUi): Int =
    when (phase) {
        ProgramGenerationProgressPhaseUi.PreparingPayload ->
            FeatureR.string.onboarding_generate_phase_preparing
        ProgramGenerationProgressPhaseUi.ReviewingProfile ->
            FeatureR.string.onboarding_generate_phase_reviewing
        ProgramGenerationProgressPhaseUi.PlanningVolume ->
            FeatureR.string.onboarding_generate_phase_planning_volume
        ProgramGenerationProgressPhaseUi.SynthesizingProgram ->
            FeatureR.string.onboarding_generate_phase_synthesizing_program
        ProgramGenerationProgressPhaseUi.EvaluatingSplits ->
            FeatureR.string.onboarding_generate_phase_evaluating_splits
        ProgramGenerationProgressPhaseUi.SelectingExercises ->
            FeatureR.string.onboarding_generate_phase_selecting_exercises
        ProgramGenerationProgressPhaseUi.PolishingProgram ->
            FeatureR.string.onboarding_generate_phase_polishing_program
        ProgramGenerationProgressPhaseUi.FinalizingProgram ->
            FeatureR.string.onboarding_generate_phase_finalizing
        ProgramGenerationProgressPhaseUi.SavingProgram ->
            FeatureR.string.onboarding_generate_phase_saving
        ProgramGenerationProgressPhaseUi.Complete ->
            FeatureR.string.onboarding_generate_phase_complete
        ProgramGenerationProgressPhaseUi.Failed ->
            FeatureR.string.onboarding_generate_error_title
    }

@Composable
internal fun programGenerationPhaseLabel(progress: ProgramGenerationProgressUi): String =
    stringResource(programGenerationPhaseStringRes(progress.phase))

@Composable
internal fun programGenerationProgressSubtitle(
    progress: ProgramGenerationProgressUi,
    phaseLabel: String,
): String? {
    if (progress.phase == ProgramGenerationProgressPhaseUi.EvaluatingSplits && progress.detail != null) {
        return stringResource(
            FeatureR.string.onboarding_generate_phase_evaluating_splits_detail,
            progress.detail,
        )
    }
    return progress.detail?.let { detail -> distinctGenerationDetail(detail, phaseLabel) }
}
