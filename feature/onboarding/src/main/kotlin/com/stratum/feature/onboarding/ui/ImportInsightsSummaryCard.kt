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
import com.stratum.feature.onboarding.MuscleVolumeLandmarkUi
import com.stratum.feature.onboarding.R
import com.stratum.feature.onboarding.VolumeResponseHighlightUi

private enum class ImportInsightsTab {
    Summary,
    Landmarks,
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
            segments = tabLabels,
            selectedIndex = selectedTab,
            onSelectedIndexChange = { selectedTab = it },
        )

        when (ImportInsightsTab.entries[selectedTab]) {
            ImportInsightsTab.Summary -> ImportSummaryTabContent(summary = summary)
            ImportInsightsTab.Landmarks -> ImportLandmarksTabContent(summary = summary)
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
            if (summary.hasVolumeLandmarks) {
                SummaryBullet(
                    text = stringResource(
                        R.string.gym_lifts_import_summary_extracted_landmarks,
                        summary.landmarkCount,
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
private fun ImportLandmarksTabContent(
    summary: ImportInsightsSummaryUi,
) {
    var expandedMuscleLabel by remember { mutableStateOf<String?>(null) }
    var showGlossary by remember { mutableStateOf(false) }

    if (showGlossary) {
        LandmarksGlossaryBottomSheet(onDismiss = { showGlossary = false })
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (summary.hasVolumeLandmarks) {
            val learnMoreLabel = stringResource(R.string.gym_lifts_import_summary_landmarks_learn_more)
            StratumText(
                text = stringResource(R.string.gym_lifts_import_summary_landmarks_intro),
                muted = true,
            )
            Row(
                modifier =
                    Modifier
                        .clickable(onClick = { showGlossary = true })
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
            LandmarksTableHeader()
            summary.volumeLandmarks.forEach { landmark ->
                LandmarkAccordionRow(
                    landmark = landmark,
                    expanded = expandedMuscleLabel == landmark.muscleLabel,
                    onToggle = {
                        expandedMuscleLabel =
                            if (expandedMuscleLabel == landmark.muscleLabel) {
                                null
                            } else {
                                landmark.muscleLabel
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
private fun LandmarksTableHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        StratumText(
            text = stringResource(R.string.gym_lifts_import_summary_landmarks_muscle_header),
            modifier = Modifier.weight(LANDMARK_MUSCLE_WEIGHT),
            muted = true,
            style = StratumThemeAccessor.typography.sectionLabel,
        )
        LandmarkHeaderLabel(
            label = stringResource(R.string.gym_lifts_import_summary_mev_header),
            modifier = Modifier.weight(LANDMARK_VALUE_WEIGHT),
        )
        LandmarkHeaderLabel(
            label = stringResource(R.string.gym_lifts_import_summary_mav_header),
            modifier = Modifier.weight(LANDMARK_VALUE_WEIGHT),
        )
        LandmarkHeaderLabel(
            label = stringResource(R.string.gym_lifts_import_summary_mrv_header),
            modifier = Modifier.weight(LANDMARK_VALUE_WEIGHT),
        )
        LandmarkHeaderLabel(
            label = stringResource(R.string.gym_lifts_import_summary_pv_header),
            modifier = Modifier.weight(LANDMARK_VALUE_WEIGHT),
            emphasized = true,
        )
    }
}

@Composable
private fun LandmarkHeaderLabel(
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
private fun LandmarksGlossaryBottomSheet(
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
            text = stringResource(R.string.gym_lifts_import_summary_pv_tooltip),
            muted = true,
        )
    }
}

@Composable
private fun LandmarkAccordionRow(
    landmark: MuscleVolumeLandmarkUi,
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
                landmark.muscleLabel,
            )
        } else {
            stringResource(
                R.string.gym_lifts_import_summary_landmarks_expand_a11y,
                landmark.muscleLabel,
            )
        }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = LANDMARK_ROW_MIN_HEIGHT.dp)
                    .clickable(onClick = onToggle)
                    .semantics { contentDescription = rowA11y }
                    .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(LANDMARK_MUSCLE_WEIGHT),
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
                StratumText(text = landmark.muscleLabel)
            }
            LandmarkCollapsedValue(
                value = landmark.mevWeeklyHardSets,
                modifier = Modifier.weight(LANDMARK_VALUE_WEIGHT),
            )
            LandmarkCollapsedValue(
                value = landmark.mavWeeklyHardSets,
                modifier = Modifier.weight(LANDMARK_VALUE_WEIGHT),
                emphasized = true,
            )
            LandmarkCollapsedValue(
                value = landmark.mrvWeeklyHardSets,
                modifier = Modifier.weight(LANDMARK_VALUE_WEIGHT),
            )
            LandmarkCollapsedValue(
                value = landmark.pvWeeklyHardSets,
                modifier = Modifier.weight(LANDMARK_VALUE_WEIGHT),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            LandmarkExpandedDetail(landmark = landmark)
        }
    }
}

@Composable
private fun LandmarkCollapsedValue(
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
private fun LandmarkExpandedDetail(
    landmark: MuscleVolumeLandmarkUi,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .background(
                    color = StratumThemeAccessor.colors.surfaceSunken,
                    shape = StratumThemeAccessor.shapes.panel,
                )
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LandmarkDetailHeaderRow()
        LandmarkDetailMetricRow(
            metricLabel = stringResource(R.string.gym_lifts_import_summary_mev_header),
            target = landmark.mevWeeklyHardSets,
            rangeLabel = landmark.mevIntervalLabel,
        )
        LandmarkDetailMetricRow(
            metricLabel = stringResource(R.string.gym_lifts_import_summary_mav_header),
            target = landmark.mavWeeklyHardSets,
            rangeLabel = landmark.mavIntervalLabel,
        )
        LandmarkDetailMetricRow(
            metricLabel = stringResource(R.string.gym_lifts_import_summary_mrv_header),
            target = landmark.mrvWeeklyHardSets,
            rangeLabel = landmark.mrvIntervalLabel,
        )
        LandmarkDetailMetricRow(
            metricLabel = stringResource(R.string.gym_lifts_import_summary_pv_header),
            target = landmark.pvWeeklyHardSets,
            rangeLabel = landmark.pvIntervalLabel,
        )

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StratumConfidenceBars(
                tier = landmark.confidenceTier,
                contentDescription = confidenceBarsContentDescription(landmark.confidenceTier),
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
private fun LandmarkDetailHeaderRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        StratumText(
            text = stringResource(R.string.gym_lifts_import_summary_landmarks_detail_metric_header),
            modifier = Modifier.weight(LANDMARK_DETAIL_METRIC_WEIGHT),
            muted = true,
            style = StratumThemeAccessor.typography.sectionLabel,
        )
        StratumText(
            text = stringResource(R.string.gym_lifts_import_summary_landmarks_detail_target_header),
            modifier = Modifier.weight(LANDMARK_DETAIL_VALUE_WEIGHT),
            muted = true,
            style = StratumThemeAccessor.typography.sectionLabel.copy(textAlign = TextAlign.End),
        )
        StratumText(
            text = stringResource(R.string.gym_lifts_import_summary_landmarks_detail_range_header),
            modifier = Modifier.weight(LANDMARK_DETAIL_RANGE_WEIGHT),
            muted = true,
            style = StratumThemeAccessor.typography.sectionLabel.copy(textAlign = TextAlign.End),
        )
    }
}

@Composable
private fun LandmarkDetailMetricRow(
    metricLabel: String,
    target: String,
    rangeLabel: String,
) {
    val rangeA11y = landmarkIntervalA11y(rangeLabel, metricLabel)
    val displayRange =
        rangeLabel.ifEmpty {
            stringResource(R.string.gym_lifts_import_summary_landmarks_detail_range_unavailable)
        }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StratumText(
            text = metricLabel,
            modifier = Modifier.weight(LANDMARK_DETAIL_METRIC_WEIGHT),
        )
        Box(
            modifier = Modifier.weight(LANDMARK_DETAIL_VALUE_WEIGHT),
            contentAlignment = Alignment.CenterEnd,
        ) {
            StratumNumber(text = target)
        }
        Box(
            modifier =
                Modifier
                    .weight(LANDMARK_DETAIL_RANGE_WEIGHT)
                    .semantics {
                        if (rangeA11y.isNotEmpty()) {
                            contentDescription = rangeA11y
                        }
                    },
            contentAlignment = Alignment.CenterEnd,
        ) {
            StratumText(
                text = displayRange,
                muted = true,
                style = StratumThemeAccessor.typography.bodyLarge.copy(textAlign = TextAlign.End),
            )
        }
    }
}

/** Build an a11y content description for an estimated-range label, or "" when there is no range. */
@Composable
private fun landmarkIntervalA11y(
    intervalLabel: String,
    landmarkName: String,
): String =
    if (intervalLabel.isEmpty()) {
        ""
    } else {
        stringResource(R.string.gym_lifts_import_summary_landmark_interval_a11y, landmarkName, intervalLabel)
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

private const val LANDMARK_MUSCLE_WEIGHT = 2f
private const val LANDMARK_VALUE_WEIGHT = 1f
private const val LANDMARK_DETAIL_METRIC_WEIGHT = 0.8f
private const val LANDMARK_DETAIL_VALUE_WEIGHT = 1f
private const val LANDMARK_DETAIL_RANGE_WEIGHT = 1.8f
private const val EFFICACY_MUSCLE_WEIGHT = 1.4f
private const val EFFICACY_SIGNAL_WEIGHT = 1f
private const val LANDMARK_ROW_MIN_HEIGHT = 40
private const val CHEVRON_ROTATION_COLLAPSED = 0f
private const val CHEVRON_ROTATION_EXPANDED = 180f
