package com.stratum.feature.onboarding.ui

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
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.onboarding.R
import com.stratum.feature.onboarding.ReviewDraftUi

/**
 * Step 6 of the wizard. A read-only, premium summary of everything the user entered before
 * program generation. There are no inputs here — every value comes from [draft], which the
 * wizard ViewModel derives from the same domain streams the editable steps write to. The
 * wizard's shared Back/Next controls handle navigation (Next advances to the Generate step).
 *
 * The summary is laid out as a sequence of numbered [ReviewCard] panels (one per onboarding
 * area). Each card owns its own background, border, and rounded shape so the sections read as
 * distinct units rather than a wall of text. Empty answers render a friendly placeholder
 * rather than a blank line: a skipped specialization reads as "Balanced", and any unanswered
 * field reads as "Not set yet" — so the screen is never blank even mid-flow.
 */
@Composable
fun ReviewScreen(
    draft: ReviewDraftUi,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StratumText(
            text = stringResource(R.string.review_intro),
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

        ReviewCard(order = 5, title = stringResource(R.string.review_section_gym_lifts)) {
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
