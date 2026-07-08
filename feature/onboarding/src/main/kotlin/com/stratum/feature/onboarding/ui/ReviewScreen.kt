package com.stratum.feature.onboarding.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.onboarding.GenerateState
import com.stratum.feature.onboarding.ImportProgramPhaseUi
import com.stratum.feature.onboarding.ImportProgramState
import com.stratum.feature.onboarding.OnboardingPathUi
import com.stratum.feature.onboarding.ProgramGenerationProgressUi
import com.stratum.feature.onboarding.R
import com.stratum.feature.onboarding.ReviewDraftUi

/**
 * The terminal wizard step. Renders a read-only summary of everything the user entered, then the
 * action that finishes onboarding: **Generate** (Generate path) or **Import** (Import path).
 *
 * On the Import path the Schedule/Specialization/Minimum Volume cards are hidden (the user skipped
 * those steps), so only Profile and Gym & Lifts show. The generate/import loading and error states
 * render inline via [ReviewActionContent].
 *
 * The summary cards are laid out as numbered [ReviewCard] panels. Empty answers render a friendly
 * placeholder rather than a blank line: a skipped specialization reads as "Balanced", and any
 * unanswered field reads as "Not set yet".
 */
@Composable
fun ReviewScreen(
    draft: ReviewDraftUi,
    path: OnboardingPathUi,
    onGenerate: () -> Unit,
    onImportExisting: () -> Unit,
    generateState: GenerateState,
    generateProgress: ProgramGenerationProgressUi?,
    generateMaxTimelineIndex: Int,
    generateErrorMessage: String?,
    importProgramState: ImportProgramState,
    modifier: Modifier = Modifier,
) {
    val showGenerateCards = path == OnboardingPathUi.Generate
    val generating = generateState == GenerateState.Loading
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Primary action pinned to the top so it's always visible without scrolling.
        ReviewPrimaryAction(
            path = path,
            generating = generating,
            importLoading = importProgramState is ImportProgramState.Loading,
            generateError = generateState == GenerateState.Error,
            onGenerate = onGenerate,
            onImportExisting = onImportExisting,
        )

        // Summary fades out as the generation timeline fades in — only one is on screen at a time,
        // so the user never has to scroll past the summary to find the timeline (or vice versa).
        Crossfade(
            targetState = generating,
            animationSpec = tween(durationMillis = 400),
            label = "review-generate-crossfade",
        ) { showTimeline ->
            if (showTimeline) {
                GenerateLoadingContent(
                    progress = generateProgress,
                    maxActiveIndex = generateMaxTimelineIndex,
                )
            } else {
                ReviewSummary(
                    draft = draft,
                    showGenerateCards = showGenerateCards,
                    importProgramState = importProgramState,
                    generateError = generateState == GenerateState.Error,
                    generateErrorMessage = generateErrorMessage,
                    onImportExisting = onImportExisting,
                )
            }
        }
    }
}

/**
 * The primary CTA at the top of Review. Shows Generate (Generate path) or Import (Import path).
 * Hides while a generate is loading (the timeline replaces it) or while an import is in flight.
 */
@Composable
private fun ReviewPrimaryAction(
    path: OnboardingPathUi,
    generating: Boolean,
    importLoading: Boolean,
    generateError: Boolean,
    onGenerate: () -> Unit,
    onImportExisting: () -> Unit,
) {
    if (generating || importLoading) return
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (path == OnboardingPathUi.Generate) {
            StratumButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(
                    if (generateError) R.string.onboarding_generate_error_retry
                    else R.string.onboarding_button_generate,
                ),
                onClick = onGenerate,
            )
        } else {
            StratumButton(
                text = stringResource(R.string.onboarding_import_existing_button),
                onClick = onImportExisting,
            )
            StratumText(
                text = stringResource(R.string.onboarding_import_supported_hint),
                muted = true,
            )
        }
    }
}

/**
 * The summary cards + any inline import/generate-error state. Rendered inside the Crossfade so it
 * disappears entirely when the generation timeline takes over.
 */
@Composable
private fun ReviewSummary(
    draft: ReviewDraftUi,
    showGenerateCards: Boolean,
    importProgramState: ImportProgramState,
    generateError: Boolean,
    generateErrorMessage: String?,
    onImportExisting: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StratumText(
            text = stringResource(
                if (showGenerateCards) R.string.review_intro_generate else R.string.review_intro_import,
            ),
            muted = true,
        )

        ReviewCard(order = 1, title = stringResource(R.string.review_section_profile)) {
            SummaryRow(
                label = stringResource(R.string.review_label_training_age),
                value = draft.trainingAge,
            )
            SummaryRow(
                label = stringResource(R.string.review_label_last_trained),
                value = draft.lastTrained,
            )
        }

        if (showGenerateCards) {
            ReviewCard(order = 2, title = stringResource(R.string.review_section_schedule)) {
                SummaryRow(
                    label = stringResource(R.string.review_label_days),
                    value = draft.weekdays.joinToString(", "),
                )
                SummaryRow(
                    label = stringResource(R.string.review_label_session_length),
                    value = draft.sessionMinutes?.let {
                        stringResource(R.string.review_session_minutes, it)
                    },
                )
            }

            ReviewCard(order = 3, title = stringResource(R.string.review_section_specialization)) {
                val balanced = stringResource(R.string.review_focus_balanced)
                val focus = draft.specialization.joinToString(", ").ifEmpty { balanced }
                SummaryRow(label = stringResource(R.string.review_label_focus), value = focus)
            }

            ReviewCard(order = 4, title = stringResource(R.string.review_section_minimum_volume)) {
                val none = stringResource(R.string.review_minimum_volume_none)
                val mv = draft.minimumVolume.joinToString(", ").ifEmpty { none }
                SummaryRow(label = stringResource(R.string.review_label_minimum_volume), value = mv)
            }
        }

        ReviewCard(order = if (showGenerateCards) 5 else 2, title = stringResource(R.string.review_section_gym_lifts)) {
            SummaryRow(
                label = stringResource(R.string.review_label_gym_preset),
                value = draft.presetLabel,
            )
            val countText = draft.enabledLiftCount.takeIf { it > 0 }?.let { it.toString() }
            if (countText != null) {
                DataColumnRow(label = stringResource(R.string.review_label_lifts_enabled)) {
                    StratumNumber(text = countText)
                }
            } else {
                SummaryRow(label = stringResource(R.string.review_label_lifts_enabled), value = null)
            }
        }

        // Inline import progress / errors render below the summary (import doesn't replace it).
        ReviewImportState(
            importProgramState = importProgramState,
            onImportExisting = onImportExisting,
        )

        // Generate errors render inline beneath the summary with a retry hint.
        if (generateError) {
            ReviewGenerateError(
                message = generateErrorMessage,
            )
        }
    }
}

@Composable
private fun ReviewImportState(
    importProgramState: ImportProgramState,
    onImportExisting: () -> Unit,
) {
    when (importProgramState) {
        is ImportProgramState.Loading -> ImportLoadingInline(phase = importProgramState.phase)
        is ImportProgramState.Error -> ImportErrorInline(
            message = importProgramState.message,
            onRetry = onImportExisting,
        )
        is ImportProgramState.Rejected -> ImportErrorInline(
            message = importProgramState.message,
            rejected = true,
            onRetry = onImportExisting,
        )
        ImportProgramState.Idle -> Unit
    }
}

@Composable
private fun ReviewGenerateError(message: String?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StratumText(text = stringResource(R.string.onboarding_generate_error_title))
        if (!message.isNullOrEmpty()) {
            StratumText(text = message, muted = true)
        }
    }
}

@Composable
private fun ImportLoadingInline(phase: ImportProgramPhaseUi) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StratumText(
            text = stringResource(R.string.onboarding_import_loading),
            style = StratumThemeAccessor.typography.titleLarge,
        )
        StratumText(text = stringResource(phase.labelRes()), muted = true)
    }
}

@Composable
private fun ImportErrorInline(
    message: String?,
    rejected: Boolean = false,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StratumText(text = stringResource(R.string.onboarding_import_error_title))
        StratumText(
            text = when {
                !message.isNullOrEmpty() -> message
                rejected -> stringResource(R.string.onboarding_import_error_rejected)
                else -> stringResource(R.string.onboarding_import_error_generic)
            },
            muted = true,
        )
        StratumButton(
            text = stringResource(R.string.onboarding_import_error_retry),
            onClick = onRetry,
        )
    }
}

private fun ImportProgramPhaseUi.labelRes(): Int = when (this) {
    ImportProgramPhaseUi.Uploading -> R.string.onboarding_import_phase_uploading
    ImportProgramPhaseUi.Sniffing -> R.string.onboarding_import_phase_sniffing
    ImportProgramPhaseUi.Parsing -> R.string.onboarding_import_phase_parsing
    ImportProgramPhaseUi.MatchingCatalog -> R.string.onboarding_import_phase_matching_catalog
    ImportProgramPhaseUi.Complete -> R.string.onboarding_import_phase_complete
}

/**
 * One numbered summary card. The card is a [surfaceRaised] panel with a subtle border and the
 * theme's [panel][StratumThemeAccessor.shapes] shape — the same idiom the Gym & Lifts step
 * uses for its tappable cards, so summary cards and selection cards feel like one system.
 *
 * @param order 1-based ordinal rendered as a small accent badge to the left of the title.
 * @param title Section heading, rendered in title-large typography.
 * @param content The section's [SummaryRow]s.
 */
@Composable
private fun ReviewCard(
    order: Int,
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = StratumThemeAccessor.colors.surfaceRaised,
                shape = StratumThemeAccessor.shapes.panel,
            )
            .border(
                width = StratumThemeAccessor.shapes.borderWidth,
                color = StratumThemeAccessor.colors.borderSubtle,
                shape = StratumThemeAccessor.shapes.panel,
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OrderBadge(order = order)
            Spacer(modifier = Modifier.width(12.dp))
            StratumText(text = title, style = StratumThemeAccessor.typography.titleLarge)
        }
        content()
    }
}

/**
 * A small accent-filled square holding the section ordinal. A fixed-size badge keeps the
 * section titles aligned even as title lengths vary.
 */
@Composable
private fun OrderBadge(order: Int) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(
                color = StratumThemeAccessor.colors.accent,
                shape = StratumThemeAccessor.shapes.chip,
            ),
        contentAlignment = Alignment.Center,
    ) {
        StratumText(
            text = order.toString(),
            // Light text on the accent fill.
            style = StratumThemeAccessor.typography.titleLarge.copy(
                color = StratumThemeAccessor.colors.onAccent,
                fontSize = 14.sp,
            ),
        )
    }
}

/**
 * One label/value line. A null [value] (an unanswered field) renders muted as "Not set yet"
 * so the summary is never blank mid-flow, while keeping the omission legible. Label sits
 * above value for a clean, scannable two-line rhythm inside each card.
 */
@Composable
private fun SummaryRow(label: String, value: String?) {
    val notSet = stringResource(R.string.review_not_set)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        StratumText(text = label, muted = true)
        StratumText(
            text = value ?: notSet,
            muted = value == null,
        )
    }
}

/**
 * A label/value line whose value slot is a custom composable (e.g. [StratumNumber]). Used for
 * the lift count so it can render in tabular monospace rather than a plain string.
 */
@Composable
private fun DataColumnRow(
    label: String,
    value: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StratumText(text = label, muted = true)
        value()
    }
}
