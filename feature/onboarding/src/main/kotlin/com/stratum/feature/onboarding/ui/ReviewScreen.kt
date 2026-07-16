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
import com.stratum.feature.onboarding.ImportProgramState
import com.stratum.feature.onboarding.OnboardingPathUi
import com.stratum.feature.onboarding.ProgramGenerationProgressUi
import com.stratum.feature.onboarding.R
import com.stratum.feature.onboarding.ReviewDraftUi

/**
 * Which pane the Review Crossfade shows: summary cards, generate/import timelines, or import
 * recovery after a failed/rejected file.
 */
private enum class ReviewBodyMode {
    Summary,
    GenerateTimeline,
    ImportTimeline,
    ImportError,
}

/**
 * The terminal wizard step. Renders a read-only summary of everything the user entered, then the
 * action that finishes onboarding: **Generate** (Generate path) or **Import** (Import path).
 *
 * On the Import path the Schedule/Specialization/Minimum Volume cards are hidden (the user skipped
 * those steps), so only Profile and Gym & Lifts show. While generate or import is loading, the
 * summary Crossfades out and a status timeline takes its place. On import failure/rejection, the
 * summary is replaced by a focused recovery pane (error card + single retry CTA) so the user is
 * not left with a top Import button and an error blob under the cards.
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
    importMaxTimelineIndex: Int = 0,
    modifier: Modifier = Modifier,
) {
    val showGenerateCards = path == OnboardingPathUi.Generate
    val onImportPath = path == OnboardingPathUi.Import
    val generating = generateState == GenerateState.Loading
    // Import load/error chrome is path-scoped so a stale Import failure cannot hide Generate.
    val importLoading = onImportPath && importProgramState is ImportProgramState.Loading
    val importFailed =
        onImportPath &&
            (
                importProgramState is ImportProgramState.Error ||
                    importProgramState is ImportProgramState.Rejected
                )
    val bodyMode =
        when {
            generating -> ReviewBodyMode.GenerateTimeline
            importLoading -> ReviewBodyMode.ImportTimeline
            importFailed -> ReviewBodyMode.ImportError
            else -> ReviewBodyMode.Summary
        }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Primary action pinned to the top so it's always visible without scrolling.
        // Hidden during import load/error — those panes own the CTA (timeline / Choose another file).
        ReviewPrimaryAction(
            path = path,
            generating = generating,
            importLoading = importLoading,
            importFailed = importFailed,
            generateError = generateState == GenerateState.Error,
            onGenerate = onGenerate,
            onImportExisting = onImportExisting,
        )

        // Summary fades out as a timeline or import-error pane fades in — only one body at a time.
        Crossfade(
            targetState = bodyMode,
            animationSpec = tween(durationMillis = 400),
            label = "review-body-crossfade",
        ) { mode ->
            when (mode) {
                ReviewBodyMode.GenerateTimeline ->
                    GenerateLoadingContent(
                        progress = generateProgress,
                        maxActiveIndex = generateMaxTimelineIndex,
                    )
                ReviewBodyMode.ImportTimeline -> {
                    val phase =
                        (importProgramState as? ImportProgramState.Loading)?.phase
                    ImportLoadingContent(
                        phase = phase,
                        maxActiveIndex = importMaxTimelineIndex,
                    )
                }
                ReviewBodyMode.ImportError -> {
                    val rejected = importProgramState is ImportProgramState.Rejected
                    val message =
                        when (importProgramState) {
                            is ImportProgramState.Error -> importProgramState.message
                            is ImportProgramState.Rejected -> importProgramState.message
                            else -> null
                        }
                    ImportProgramErrorContent(
                        rejected = rejected,
                        message = message,
                        onRetry = onImportExisting,
                    )
                }
                ReviewBodyMode.Summary ->
                    ReviewSummary(
                        draft = draft,
                        showGenerateCards = showGenerateCards,
                        generateError = generateState == GenerateState.Error,
                        generateErrorMessage = generateErrorMessage,
                    )
            }
        }
    }
}

/**
 * The primary CTA at the top of Review. Shows Generate (Generate path) or Import (Import path).
 * Hides while generate/import is loading, and while import recovery owns the retry CTA.
 */
@Composable
private fun ReviewPrimaryAction(
    path: OnboardingPathUi,
    generating: Boolean,
    importLoading: Boolean,
    importFailed: Boolean,
    generateError: Boolean,
    onGenerate: () -> Unit,
    onImportExisting: () -> Unit,
) {
    if (generating || importLoading || importFailed) return
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
 * The summary cards + any generate-error note. Rendered inside the Crossfade so it disappears
 * entirely when a timeline or import-error recovery pane takes over.
 */
@Composable
private fun ReviewSummary(
    draft: ReviewDraftUi,
    showGenerateCards: Boolean,
    generateError: Boolean,
    generateErrorMessage: String?,
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

        if (generateError) {
            ReviewGenerateError(
                message = generateErrorMessage,
            )
        }
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
