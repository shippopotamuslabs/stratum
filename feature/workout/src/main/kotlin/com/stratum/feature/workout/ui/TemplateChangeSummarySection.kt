package com.stratum.feature.workout.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.workout.R

/**
 * Expandable template change summary for the SaveChanges complete sheet (STR-125).
 *
 * One-off in feature: only this sheet needs a grouped old → new diff (UI_DESIGN §7.2
 * step 4). Follows the [StratumProgramLibraryCard][com.stratum.core.ui.component.StratumProgramLibraryCard]
 * chevron + [AnimatedVisibility] pattern. Collapsed by default so the sheet stays
 * compact; the expanded body scrolls under a max height so Update/Discard stay reachable.
 */
@Composable
internal fun TemplateChangeSummarySection(
    summary: TemplateChangeSummaryUi,
    modifier: Modifier = Modifier,
    /** Preview/golden seam — production always starts collapsed. */
    initiallyExpanded: Boolean = false,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "changeSummaryChevronRotation",
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(StratumThemeAccessor.shapes.chip)
                    .clickable(role = Role.Button, onClick = { expanded = !expanded })
                    .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StratumText(
                text = stringResource(R.string.workout_sheet_what_changed, summary.rowCount),
                style = StratumThemeAccessor.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(CoreUiR.drawable.ic_chevron_down),
                contentDescription =
                    stringResource(
                        if (expanded) {
                            R.string.workout_sheet_what_changed_collapse_cd
                        } else {
                            R.string.workout_sheet_what_changed_expand_cd
                        },
                    ),
                modifier =
                    Modifier
                        .size(20.dp)
                        .rotate(chevronRotation),
                tint = StratumThemeAccessor.colors.textMuted,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = MAX_EXPANDED_HEIGHT_DP.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                summary.groups.forEachIndexed { index, group ->
                    if (index > 0) ChangeSummaryDivider()
                    StratumText(
                        text = categoryTitle(group.category),
                        style = StratumThemeAccessor.typography.sectionLabel,
                        muted = true,
                    )
                    group.rows.forEach { row -> ChangeSummaryRow(row) }
                }
            }
        }
    }
}

@Composable
private fun ChangeSummaryRow(row: TemplateChangeRowUi) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        StratumText(
            text = rowLabel(row),
            style = StratumThemeAccessor.typography.bodyLarge,
        )
        StratumText(
            text = formatValues(rowOldText(row), rowNewText(row)),
            style = StratumThemeAccessor.typography.bodyLarge,
            muted = true,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun rowLabel(row: TemplateChangeRowUi): String =
    when (row) {
        is LiftAddedUi -> row.liftName
        is LiftRemovedUi -> row.liftName
        is LiftSwappedUi -> stringResource(R.string.workout_sheet_change_swapped_label)
        is LiftMovedUi -> row.liftName
        is SetCountChangedUi -> row.liftName
        is SetEditedUi -> stringResource(R.string.workout_sheet_change_lift_set_label, row.liftName, row.setPosition)
        is RestEditedUi -> stringResource(R.string.workout_sheet_change_lift_set_label, row.liftName, row.setPosition)
        is SupersetGroupingChangedUi -> row.liftName
        is RestBetweenLiftsChangedUi ->
            stringResource(R.string.workout_sheet_change_rest_between_label, row.liftName)
        is PinnedNotesChangedUi -> stringResource(R.string.workout_sheet_change_notes_label, row.liftName)
    }

@Composable
private fun rowOldText(row: TemplateChangeRowUi): String? =
    when (row) {
        is LiftAddedUi -> null
        is LiftRemovedUi -> setCountText(row.setCount)
        is LiftSwappedUi -> row.oldName
        is LiftMovedUi -> stringResource(R.string.workout_sheet_change_position, row.oldPosition)
        is SetCountChangedUi -> setCountText(row.oldCount)
        is SetEditedUi -> formatShape(row.oldShape)
        is RestEditedUi -> formatRestDisplay(row.oldSeconds)
        is SupersetGroupingChangedUi -> formatGrouping(row.oldPartners)
        is RestBetweenLiftsChangedUi -> formatToggle(row.oldEnabled)
        is PinnedNotesChangedUi -> formatNotes(row.oldNotes)
    }

@Composable
private fun rowNewText(row: TemplateChangeRowUi): String? =
    when (row) {
        is LiftAddedUi -> setCountText(row.setCount)
        is LiftRemovedUi -> null
        is LiftSwappedUi -> row.newName
        is LiftMovedUi -> stringResource(R.string.workout_sheet_change_position, row.newPosition)
        is SetCountChangedUi -> formatNewCount(row.newCount, row.replacedCount)
        is SetEditedUi -> formatShape(row.newShape)
        is RestEditedUi -> formatRestDisplay(row.newSeconds)
        is SupersetGroupingChangedUi -> formatGrouping(row.newPartners)
        is RestBetweenLiftsChangedUi -> formatToggle(row.newEnabled)
        is PinnedNotesChangedUi -> formatNotes(row.newNotes)
    }

@Composable
private fun setCountText(count: Int): String =
    pluralStringResource(R.plurals.workout_sheet_change_set_count, count, count)

/** New total with churn detail (suffix only when sets were both added and removed). */
@Composable
private fun formatNewCount(newCount: Int, replacedCount: Int): String {
    val label = setCountText(newCount)
    return if (replacedCount > 0) {
        stringResource(R.string.workout_sheet_change_set_count_replaced, label, replacedCount)
    } else {
        label
    }
}

@Composable
private fun formatToggle(enabled: Boolean): String =
    stringResource(
        if (enabled) {
            R.string.workout_sheet_change_toggle_on
        } else {
            R.string.workout_sheet_change_toggle_off
        },
    )

@Composable
private fun formatGrouping(partners: List<String>): String =
    if (partners.isEmpty()) {
        stringResource(R.string.workout_sheet_change_not_grouped)
    } else {
        stringResource(R.string.workout_sheet_change_grouped_with, partners.joinToString(", "))
    }

@Composable
private fun formatNotes(notes: List<String>): String =
    if (notes.isEmpty()) {
        stringResource(R.string.workout_sheet_change_no_notes)
    } else {
        notes.joinToString("; ")
    }

/**
 * Compact set shape, e.g. `Standard 6–10 · RIR 2 · Top`. RIR-only back-offs
 * (`0/0` band) omit the band. Set-type names reuse the set-type picker strings.
 */
@Composable
private fun formatShape(shape: TemplateSetShapeUi): String {
    val prescription =
        buildString {
            append(stringResource(shape.setKind.displayNameRes()))
            if (shape.repMin != 0 || shape.repMax != 0) {
                append(" ${formatBand(shape.repMin, shape.repMax)}")
            }
        }
    return buildList {
        add(prescription)
        add(stringResource(R.string.workout_sheet_change_rir, shape.rirGoal))
        if (shape.isTopSet) add(stringResource(R.string.workout_sheet_change_top_set))
    }.joinToString(" · ")
}

private fun formatBand(repMin: Int, repMax: Int): String =
    if (repMin == repMax) "$repMin" else "$repMin–$repMax"

/** `old → new`; `+ new` for additions; `− old` for removals. */
@Composable
private fun formatValues(oldText: String?, newText: String?): String =
    when {
        oldText != null && newText != null ->
            stringResource(R.string.workout_sheet_change_old_to_new, oldText, newText)
        oldText != null -> stringResource(R.string.workout_sheet_change_removed, oldText)
        else -> stringResource(R.string.workout_sheet_change_added, newText.orEmpty())
    }

@Composable
private fun categoryTitle(category: TemplateChangeCategoryUi): String =
    stringResource(
        when (category) {
            TemplateChangeCategoryUi.Lifts -> R.string.workout_sheet_change_category_lifts
            TemplateChangeCategoryUi.Order -> R.string.workout_sheet_change_category_order
            TemplateChangeCategoryUi.Sets -> R.string.workout_sheet_change_category_sets
            TemplateChangeCategoryUi.Rest -> R.string.workout_sheet_change_category_rest
            TemplateChangeCategoryUi.Supersets -> R.string.workout_sheet_change_category_supersets
            TemplateChangeCategoryUi.Notes -> R.string.workout_sheet_change_category_notes
        },
    )

@Composable
private fun ChangeSummaryDivider() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(StratumThemeAccessor.colors.borderSubtle),
    )
}

/** Max expanded height before the group list scrolls (keeps sheet CTAs reachable). */
private const val MAX_EXPANDED_HEIGHT_DP = 320
