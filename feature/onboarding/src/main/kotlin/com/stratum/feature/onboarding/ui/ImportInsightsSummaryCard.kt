package com.stratum.feature.onboarding.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumConfidenceBars
import com.stratum.core.ui.component.StratumEffectivenessBars
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumSegmentedControl
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.domain.importing.LandmarkConfidenceTier
import com.stratum.domain.importing.UserEffectivenessTier
import com.stratum.feature.onboarding.EffectiveLiftHighlightUi
import com.stratum.feature.onboarding.ImportInsightsSummaryUi
import com.stratum.feature.onboarding.MuscleVolumeSnapshotUi
import com.stratum.feature.onboarding.R
import com.stratum.feature.onboarding.VolumeResponseHighlightUi

private enum class ImportInsightsTab {
    Summary,
    DoseResponse,
    Efficacy,
}

@Composable
fun ImportInsightsSummaryCard(
    summary: ImportInsightsSummaryUi,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(ImportInsightsTab.Summary.ordinal) }
    val tabLabels =
        listOf(
            stringResource(R.string.gym_lifts_import_summary_tab_summary),
            stringResource(R.string.gym_lifts_import_summary_tab_landmarks),
            stringResource(R.string.gym_lifts_import_summary_tab_efficacy),
        )

    Column(
        modifier =
            modifier
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
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StratumText(
            text = stringResource(R.string.gym_lifts_import_summary_title),
            style = StratumThemeAccessor.typography.titleLarge,
        )

        StratumSegmentedControl(
            modifier = Modifier.fillMaxWidth(),
            segments = tabLabels,
            selectedIndex = selectedTab,
            onSelectedIndexChange = { selectedTab = it },
        )

        when (ImportInsightsTab.entries[selectedTab]) {
            ImportInsightsTab.Summary -> ImportSummaryTabContent(summary = summary)
            ImportInsightsTab.DoseResponse -> ImportDoseResponseTabContent(summary = summary)
            ImportInsightsTab.Efficacy -> ImportEfficacyTabContent(summary = summary)
        }
    }
}

@Composable
private fun ImportSummaryTabContent(
    summary: ImportInsightsSummaryUi,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StratumText(
            text = stringResource(R.string.gym_lifts_import_summary_intro),
            muted = true,
        )

        SummaryStatBlock(
            label = stringResource(R.string.gym_lifts_import_summary_stat_history),
            value = stringResource(
                R.string.gym_lifts_import_summary_stat_history_value,
                summary.totalSessions,
                summary.totalSets,
                summary.analysisWeeks,
            ),
        )
        SummaryStatBlock(
            label = stringResource(R.string.gym_lifts_import_summary_stat_lifts),
            value = stringResource(
                R.string.gym_lifts_import_summary_stat_lifts_value,
                summary.enabledLiftCount,
                summary.provenLiftCount,
                summary.customLiftsCreated,
            ),
        )
        if (summary.liftsWithInsights > 0 || summary.supersetPairs > 0) {
            SummaryStatBlock(
                label = stringResource(R.string.gym_lifts_import_summary_stat_patterns),
                value = stringResource(
                    R.string.gym_lifts_import_summary_stat_patterns_value,
                    summary.liftsWithInsights,
                    summary.supersetPairs,
                ),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StratumText(
                text = stringResource(R.string.gym_lifts_import_summary_extracted_title),
                style = StratumThemeAccessor.typography.titleLarge,
            )
            if (summary.hasVolumeSnapshots) {
                SummaryBullet(
                    text = stringResource(
                        R.string.gym_lifts_import_summary_extracted_landmarks,
                        summary.snapshotCount,
                    ),
                )
            }
            if (summary.hasEffectiveLifts) {
                SummaryBullet(
                    text = stringResource(
                        R.string.gym_lifts_import_summary_extracted_efficacy,
                        summary.effectiveLifts.size,
                    ),
                )
            }
            if (summary.hasVolumeResponses) {
                SummaryBullet(
                    text = stringResource(
                        R.string.gym_lifts_import_summary_extracted_responses,
                        summary.volumeResponses.size,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ImportDoseResponseTabContent(
    summary: ImportInsightsSummaryUi,
) {
    var expandedMuscleLabel by remember { mutableStateOf<String?>(null) }
    var showGuide by remember { mutableStateOf(false) }

    if (showGuide) {
        DoseResponseGuideBottomSheet(onDismiss = { showGuide = false })
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (summary.hasVolumeSnapshots) {
            val learnMoreLabel = stringResource(R.string.gym_lifts_import_summary_landmarks_learn_more)
            StratumText(
                text = stringResource(R.string.gym_lifts_import_summary_landmarks_intro),
                muted = true,
            )
            if (summary.usesMostlyPopulationDefaults) {
                StratumText(
                    text = stringResource(R.string.gym_lifts_import_summary_landmarks_defaults_note),
                    muted = true,
                )
            }
            Row(
                modifier =
                    Modifier
                        .clickable(onClick = { showGuide = true })
                        .semantics { contentDescription = learnMoreLabel },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    painter = painterResource(CoreUiR.drawable.ic_info),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = StratumThemeAccessor.colors.accent,
                )
                StratumText(
                    text = learnMoreLabel,
                    style = StratumThemeAccessor.typography.bodyLarge,
                )
            }
            Spacer(Modifier.height(8.dp))
            DoseResponseTableHeader()
            summary.volumeSnapshots.forEach { snapshot ->
                DoseResponseAccordionRow(
                    snapshot = snapshot,
                    expanded = expandedMuscleLabel == snapshot.muscleLabel,
                    onToggle = {
                        expandedMuscleLabel =
                            if (expandedMuscleLabel == snapshot.muscleLabel) {
                                null
                            } else {
                                snapshot.muscleLabel
                            }
                    },
                )
            }
        } else {
            StratumText(
                text = stringResource(R.string.gym_lifts_import_summary_landmarks_empty),
                muted = true,
            )
        }
    }
}

@Composable
private fun ImportEfficacyTabContent(
    summary: ImportInsightsSummaryUi,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (summary.hasEffectiveLifts) {
            EfficacyTableHeader()
            summary.effectiveLifts.forEach { lift ->
                EfficacyTableRow(highlight = lift)
            }
        } else {
            StratumText(
                text = stringResource(R.string.gym_lifts_import_summary_efficacy_empty),
                muted = true,
            )
        }
    }
}

@Composable
private fun EfficacyTableHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        StratumText(
            text = stringResource(R.string.gym_lifts_import_summary_efficacy_muscle_header),
            modifier = Modifier.weight(EFFICACY_MUSCLE_WEIGHT),
            muted = true,
            style = StratumThemeAccessor.typography.sectionLabel,
        )
        Box(
            modifier = Modifier.weight(EFFICACY_SIGNAL_WEIGHT),
            contentAlignment = Alignment.CenterEnd,
        ) {
            StratumText(
                text = stringResource(R.string.gym_lifts_import_summary_efficacy_level_header),
                muted = true,
                style = StratumThemeAccessor.typography.sectionLabel,
            )
        }
        Box(
            modifier = Modifier.weight(EFFICACY_SIGNAL_WEIGHT),
            contentAlignment = Alignment.CenterEnd,
        ) {
            StratumText(
                text = stringResource(R.string.gym_lifts_import_summary_efficacy_confidence_header),
                muted = true,
                style = StratumThemeAccessor.typography.sectionLabel,
            )
        }
    }
}

@Composable
private fun EfficacyTableRow(
    highlight: EffectiveLiftHighlightUi,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(EFFICACY_MUSCLE_WEIGHT),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            StratumText(text = highlight.muscleLabel)
            StratumText(text = highlight.liftName, muted = true)
        }
        Box(
            modifier = Modifier.weight(EFFICACY_SIGNAL_WEIGHT),
            contentAlignment = Alignment.CenterEnd,
        ) {
            StratumEffectivenessBars(
                tier = highlight.effectivenessTier,
                contentDescription = effectivenessBarsContentDescription(highlight.effectivenessTier),
            )
        }
        Box(
            modifier = Modifier.weight(EFFICACY_SIGNAL_WEIGHT),
            contentAlignment = Alignment.CenterEnd,
        ) {
            StratumConfidenceBars(
                tier = highlight.confidenceTier,
                contentDescription = confidenceBarsContentDescription(highlight.confidenceTier),
            )
        }
    }
}

@Composable
private fun effectivenessBarsContentDescription(tier: UserEffectivenessTier): String =
    when (tier) {
        UserEffectivenessTier.VeryEffective ->
            stringResource(R.string.gym_lifts_import_summary_efficacy_a11y_high)
        UserEffectivenessTier.ModeratelyEffective ->
            stringResource(R.string.gym_lifts_import_summary_efficacy_a11y_medium)
        UserEffectivenessTier.SomewhatEffective ->
            stringResource(R.string.gym_lifts_import_summary_efficacy_a11y_low)
    }

@Composable
private fun confidenceBarsContentDescription(tier: LandmarkConfidenceTier): String =
    when (tier) {
        LandmarkConfidenceTier.High ->
            stringResource(R.string.gym_lifts_import_summary_confidence_a11y_high)
        LandmarkConfidenceTier.Medium ->
            stringResource(R.string.gym_lifts_import_summary_confidence_a11y_medium)
        LandmarkConfidenceTier.Low ->
            stringResource(R.string.gym_lifts_import_summary_confidence_a11y_low)
    }

@Composable
private fun SummaryStatBlock(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StratumText(text = label, muted = true)
        StratumText(text = value)
    }
}

@Composable
private fun SummaryBullet(text: String) {
    StratumText(text = "• $text", muted = true)
}

@Composable
private fun DoseResponseTableHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        StratumText(
            text = stringResource(R.string.gym_lifts_import_summary_landmarks_muscle_header),
            modifier = Modifier.weight(DOSE_MUSCLE_WEIGHT),
            muted = true,
            style = StratumThemeAccessor.typography.sectionLabel,
        )
        DoseResponseHeaderLabel(
            label = stringResource(R.string.gym_lifts_import_summary_mev_header),
            modifier = Modifier.weight(DOSE_VALUE_WEIGHT),
        )
        DoseResponseHeaderLabel(
            label = stringResource(R.string.gym_lifts_import_summary_mav_header),
            modifier = Modifier.weight(DOSE_VALUE_WEIGHT),
            emphasized = true,
        )
        DoseResponseHeaderLabel(
            label = stringResource(R.string.gym_lifts_import_summary_mrv_header),
            modifier = Modifier.weight(DOSE_VALUE_WEIGHT),
        )
    }
}

@Composable
private fun DoseResponseHeaderLabel(
    label: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd,
    ) {
        StratumText(
            text = label,
            muted = !emphasized,
            style = StratumThemeAccessor.typography.sectionLabel.copy(textAlign = TextAlign.End),
        )
    }
}

@Composable
private fun DoseResponseGuideBottomSheet(
    onDismiss: () -> Unit,
) {
    StratumBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.gym_lifts_import_summary_landmarks_glossary_title),
    ) {
        StratumText(
            text = stringResource(R.string.gym_lifts_import_summary_mev_tooltip),
            muted = true,
        )
        StratumText(
            text = stringResource(R.string.gym_lifts_import_summary_mav_tooltip),
            muted = true,
        )
        StratumText(
            text = stringResource(R.string.gym_lifts_import_summary_mrv_tooltip),
            muted = true,
        )
        StratumText(
            text = stringResource(R.string.gym_lifts_import_summary_landmarks_fractional_note),
            muted = true,
        )
    }
}

@Composable
private fun DoseResponseAccordionRow(
    snapshot: MuscleVolumeSnapshotUi,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) CHEVRON_ROTATION_EXPANDED else CHEVRON_ROTATION_COLLAPSED,
        label = "landmarkChevronRotation",
    )
    val rowA11y =
        if (expanded) {
            stringResource(
                R.string.gym_lifts_import_summary_landmarks_collapse_a11y,
                snapshot.muscleLabel,
            )
        } else {
            stringResource(
                R.string.gym_lifts_import_summary_landmarks_expand_a11y,
                snapshot.muscleLabel,
            )
        }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = DOSE_ROW_MIN_HEIGHT.dp)
                    .clickable(onClick = onToggle)
                    .semantics { contentDescription = rowA11y }
                    .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(DOSE_MUSCLE_WEIGHT),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    painter = painterResource(CoreUiR.drawable.ic_chevron_down),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(16.dp)
                            .rotate(chevronRotation),
                    tint = StratumThemeAccessor.colors.textMuted,
                )
                StratumText(text = snapshot.muscleLabel)
            }
            DoseResponseCollapsedValue(
                value = snapshot.mvWeeklyHardSets,
                modifier = Modifier.weight(DOSE_VALUE_WEIGHT),
            )
            DoseResponseCollapsedValue(
                value = snapshot.pvWeeklyHardSets,
                modifier = Modifier.weight(DOSE_VALUE_WEIGHT),
                emphasized = true,
            )
            DoseResponseCollapsedValue(
                value = snapshot.rcWeeklyHardSets,
                modifier = Modifier.weight(DOSE_VALUE_WEIGHT),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            DoseResponseExpandedDetail(snapshot = snapshot)
        }
    }
}

@Composable
private fun DoseResponseCollapsedValue(
    value: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd,
    ) {
        if (emphasized) {
            StratumText(
                text = value,
                style = StratumThemeAccessor.typography.dataLarge,
            )
        } else {
            StratumNumber(text = value)
        }
    }
}

@Composable
private fun DoseResponseExpandedDetail(
    snapshot: MuscleVolumeSnapshotUi,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, bottom = 12.dp)
                .background(
                    color = StratumThemeAccessor.colors.surfaceSunken,
                    shape = StratumThemeAccessor.shapes.panel,
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StratumText(
            text = stringResource(R.string.gym_lifts_import_summary_landmarks_logged_volume_title),
            style = StratumThemeAccessor.typography.sectionLabel,
        )
        DoseResponseLoggedVolumeTable(snapshot = snapshot)

        if (snapshot.hasPvInterval) {
            DoseResponsePvIntervalRow(
                intervalLabel = snapshot.pvIntervalLabel,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StratumConfidenceBars(
                tier = snapshot.confidenceTier,
                contentDescription = confidenceBarsContentDescription(snapshot.confidenceTier),
            )
            StratumText(
                text = stringResource(R.string.gym_lifts_import_summary_landmarks_confidence_header),
                muted = true,
                style = StratumThemeAccessor.typography.sectionLabel,
            )
        }
    }
}

@Composable
private fun DoseResponseLoggedVolumeTable(snapshot: MuscleVolumeSnapshotUi) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DoseResponseLoggedVolumeHeaderRow()
        DoseResponseLoggedVolumeDataRow(
            label = stringResource(R.string.gym_lifts_import_summary_landmarks_logged_volume_total),
            low = snapshot.observedMinWeeklyHardSets,
            typical = snapshot.observedMedianWeeklyHardSetsLabel,
            high = snapshot.observedMaxWeeklyHardSets,
        )
    }
}

@Composable
private fun DoseResponseLoggedVolumeHeaderRow() {
    Row(modifier = Modifier.fillMaxWidth()) {
        StratumText(
            text = "",
            modifier = Modifier.weight(DOSE_LOGGED_LABEL_WEIGHT),
        )
        DoseResponseLoggedVolumeHeaderCell(
            text = stringResource(R.string.gym_lifts_import_summary_landmarks_logged_volume_low),
            modifier = Modifier.weight(DOSE_LOGGED_VALUE_WEIGHT),
        )
        DoseResponseLoggedVolumeHeaderCell(
            text = stringResource(R.string.gym_lifts_import_summary_landmarks_logged_volume_typical),
            modifier = Modifier.weight(DOSE_LOGGED_VALUE_WEIGHT),
        )
        DoseResponseLoggedVolumeHeaderCell(
            text = stringResource(R.string.gym_lifts_import_summary_landmarks_logged_volume_high),
            modifier = Modifier.weight(DOSE_LOGGED_VALUE_WEIGHT),
        )
    }
}

@Composable
private fun DoseResponseLoggedVolumeHeaderCell(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        StratumText(
            text = text,
            muted = true,
            style = StratumThemeAccessor.typography.sectionLabel.copy(textAlign = TextAlign.End),
        )
    }
}

@Composable
private fun DoseResponseLoggedVolumeDataRow(
    label: String,
    low: String,
    typical: String,
    high: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StratumText(
            text = label,
            modifier = Modifier.weight(DOSE_LOGGED_LABEL_WEIGHT),
            muted = true,
        )
        DoseResponseLoggedVolumeValueCell(value = low, modifier = Modifier.weight(DOSE_LOGGED_VALUE_WEIGHT))
        DoseResponseLoggedVolumeValueCell(value = typical, modifier = Modifier.weight(DOSE_LOGGED_VALUE_WEIGHT))
        DoseResponseLoggedVolumeValueCell(value = high, modifier = Modifier.weight(DOSE_LOGGED_VALUE_WEIGHT))
    }
}

@Composable
private fun DoseResponseLoggedVolumeValueCell(
    value: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        StratumText(text = value)
    }
}

@Composable
private fun DoseResponsePvIntervalRow(intervalLabel: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StratumText(
            text = stringResource(R.string.gym_lifts_import_summary_landmarks_pv_interval_label),
            muted = true,
            style = StratumThemeAccessor.typography.sectionLabel,
        )
        StratumText(text = intervalLabel)
    }
}

@Composable
fun AnimatedImportInsightsSummary(
    visible: Boolean,
    summary: ImportInsightsSummaryUi?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible && summary != null,
        enter = fadeIn() + expandVertically(),
        modifier = modifier,
    ) {
        summary?.let { ImportInsightsSummaryCard(summary = it) }
    }
}

private const val DOSE_MUSCLE_WEIGHT = 1.8f
private const val DOSE_VALUE_WEIGHT = 0.85f
private const val DOSE_LOGGED_LABEL_WEIGHT = 1.2f
private const val DOSE_LOGGED_VALUE_WEIGHT = 0.7f
private const val EFFICACY_MUSCLE_WEIGHT = 1.4f
private const val EFFICACY_SIGNAL_WEIGHT = 1f
private const val DOSE_ROW_MIN_HEIGHT = 40
private const val CHEVRON_ROTATION_COLLAPSED = 0f
private const val CHEVRON_ROTATION_EXPANDED = 180f
