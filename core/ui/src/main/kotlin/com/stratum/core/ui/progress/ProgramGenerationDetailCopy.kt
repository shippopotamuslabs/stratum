package com.stratum.core.ui.progress

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.stratum.core.ui.R

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

/** Hides schema/validation jargon from generation progress subtitles. */
fun userFacingGenerationDetail(detail: String): String? =
    detail.takeIf { text -> TECHNICAL_GENERATION_DETAIL_PATTERNS.none { it.containsMatchIn(text) } }

/**
 * Returns [detail] when it adds information beyond [phaseLabel]. Progress messages often
 * repeat the phase headline; model tool intents should still show as subtitle.
 */
fun distinctGenerationDetail(detail: String, phaseLabel: String): String? {
    val cleaned = userFacingGenerationDetail(detail) ?: return null
    if (normalizeProgressCopy(cleaned) == normalizeProgressCopy(phaseLabel)) {
        return null
    }
    return cleaned
}

private fun normalizeProgressCopy(text: String): String =
    text.trim().trimEnd('.', '…').lowercase()

@StringRes
fun programGenerationPhaseStringRes(phase: ProgramGenerationProgressPhaseUi): Int =
    when (phase) {
        ProgramGenerationProgressPhaseUi.PreparingPayload ->
            R.string.program_generate_phase_preparing
        ProgramGenerationProgressPhaseUi.ReviewingProfile ->
            R.string.program_generate_phase_reviewing
        ProgramGenerationProgressPhaseUi.PlanningVolume ->
            R.string.program_generate_phase_planning_volume
        ProgramGenerationProgressPhaseUi.SynthesizingProgram ->
            R.string.program_generate_phase_synthesizing_program
        ProgramGenerationProgressPhaseUi.EvaluatingSplits ->
            R.string.program_generate_phase_evaluating_splits
        ProgramGenerationProgressPhaseUi.SelectingExercises ->
            R.string.program_generate_phase_selecting_exercises
        ProgramGenerationProgressPhaseUi.PolishingProgram ->
            R.string.program_generate_phase_polishing_program
        ProgramGenerationProgressPhaseUi.FinalizingProgram ->
            R.string.program_generate_phase_finalizing
        ProgramGenerationProgressPhaseUi.SavingProgram ->
            R.string.program_generate_phase_saving
        ProgramGenerationProgressPhaseUi.Complete ->
            R.string.program_generate_phase_complete
        ProgramGenerationProgressPhaseUi.Failed ->
            R.string.program_generate_error_title
    }

@Composable
fun programGenerationProgressSubtitle(
    progress: ProgramGenerationProgressUi,
    phaseLabel: String,
): String? {
    if (progress.phase == ProgramGenerationProgressPhaseUi.EvaluatingSplits && progress.detail != null) {
        return stringResource(
            R.string.program_generate_phase_evaluating_splits_detail,
            progress.detail,
        )
    }
    return progress.detail?.let { detail -> distinctGenerationDetail(detail, phaseLabel) }
}
