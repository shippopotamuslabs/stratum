package com.stratum.feature.onboarding.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.stratum.core.ui.R as CoreUiR
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

private enum class LandmarkHeaderTooltip {
    Mev,
    Mav,
    Mrv,
    Pv,
}

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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (summary.hasVolumeLandmarks) {
            LandmarksTableHeader()
            summary.volumeLandmarks.forEach { landmark ->
                LandmarkTableRow(landmark = landmark)
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
        )
        Box(
            modifier = Modifier.weight(EFFICACY_SIGNAL_WEIGHT),
            contentAlignment = Alignment.CenterEnd,
        ) {
            StratumText(
                text = stringResource(R.string.gym_lifts_import_summary_efficacy_level_header),
                muted = true,
            )
        }
        Box(
            modifier = Modifier.weight(EFFICACY_SIGNAL_WEIGHT),
            contentAlignment = Alignment.CenterEnd,
        ) {
            StratumText(
                text = stringResource(R.string.gym_lifts_import_summary_efficacy_confidence_header),
                muted = true,
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
    var activeTooltip by remember { mutableStateOf<LandmarkHeaderTooltip?>(null) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        StratumText(
            text = stringResource(R.string.gym_lifts_import_summary_landmarks_muscle_header),
            modifier = Modifier.weight(LANDMARK_MUSCLE_WEIGHT),
            muted = true,
        )
        LandmarkHeaderWithTooltip(
            label = stringResource(R.string.gym_lifts_import_summary_mev_header),
            tooltipText = stringResource(R.string.gym_lifts_import_summary_mev_tooltip),
            expanded = activeTooltip == LandmarkHeaderTooltip.Mev,
            onExpandedChange = { expanded ->
                activeTooltip = if (expanded) LandmarkHeaderTooltip.Mev else null
            },
            modifier = Modifier.weight(LANDMARK_VALUE_WEIGHT),
        )
        LandmarkHeaderWithTooltip(
            label = stringResource(R.string.gym_lifts_import_summary_mav_header),
            tooltipText = stringResource(R.string.gym_lifts_import_summary_mav_tooltip),
            expanded = activeTooltip == LandmarkHeaderTooltip.Mav,
            onExpandedChange = { expanded ->
                activeTooltip = if (expanded) LandmarkHeaderTooltip.Mav else null
            },
            modifier = Modifier.weight(LANDMARK_VALUE_WEIGHT),
        )
        LandmarkHeaderWithTooltip(
            label = stringResource(R.string.gym_lifts_import_summary_mrv_header),
            tooltipText = stringResource(R.string.gym_lifts_import_summary_mrv_tooltip),
            expanded = activeTooltip == LandmarkHeaderTooltip.Mrv,
            onExpandedChange = { expanded ->
                activeTooltip = if (expanded) LandmarkHeaderTooltip.Mrv else null
            },
            modifier = Modifier.weight(LANDMARK_VALUE_WEIGHT),
        )
        LandmarkHeaderWithTooltip(
            label = stringResource(R.string.gym_lifts_import_summary_pv_header),
            tooltipText = stringResource(R.string.gym_lifts_import_summary_pv_tooltip),
            expanded = activeTooltip == LandmarkHeaderTooltip.Pv,
            onExpandedChange = { expanded ->
                activeTooltip = if (expanded) LandmarkHeaderTooltip.Pv else null
            },
            modifier = Modifier.weight(LANDMARK_VALUE_WEIGHT),
        )
    }
}

@Composable
private fun LandmarkHeaderWithTooltip(
    label: String,
    tooltipText: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val tooltipOffsetY = with(density) { TOOLTIP_OFFSET_BELOW.dp.roundToPx() }
    val learnAboutLabel = stringResource(R.string.gym_lifts_import_summary_header_tooltip_a11y, label)

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Row(
            modifier =
                Modifier
                    .clickable { onExpandedChange(!expanded) }
                    .semantics { contentDescription = learnAboutLabel },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            StratumText(
                text = label,
                muted = true,
                style = StratumThemeAccessor.typography.bodyLarge.copy(textAlign = TextAlign.End),
            )
            Icon(
                painter = painterResource(CoreUiR.drawable.ic_info),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = StratumThemeAccessor.colors.accent,
            )
        }

        if (expanded) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(x = 0, y = tooltipOffsetY),
                onDismissRequest = { onExpandedChange(false) },
                properties =
                    PopupProperties(
                        focusable = true,
                        dismissOnClickOutside = true,
                        dismissOnBackPress = true,
                    ),
            ) {
                LandmarkTooltipPopup(text = tooltipText)
            }
        }
    }
}

@Composable
private fun LandmarkTooltipPopup(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .widthIn(max = TOOLTIP_MAX_WIDTH.dp)
                .background(
                    color = StratumThemeAccessor.colors.surfaceRaised,
                    shape = StratumThemeAccessor.shapes.panel,
                )
                .border(
                    width = StratumThemeAccessor.shapes.borderWidth,
                    color = StratumThemeAccessor.colors.borderSubtle,
                    shape = StratumThemeAccessor.shapes.panel,
                )
                .padding(12.dp),
    ) {
        StratumText(text = text, muted = true)
    }
}

@Composable
private fun LandmarkTableRow(
    landmark: MuscleVolumeLandmarkUi,
) {
    val mevA11y = landmark.mevIntervalLabel.intervalA11y("MEV")
    val mavA11y = landmark.mavIntervalLabel.intervalA11y("MAV")
    val mrvA11y = landmark.mrvIntervalLabel.intervalA11y("MRV")
    val pvA11y = landmark.pvIntervalLabel.intervalA11y("PV")
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StratumText(
                text = landmark.muscleLabel,
                modifier = Modifier.weight(LANDMARK_MUSCLE_WEIGHT),
            )
            LandmarkValueCell(
                value = landmark.mevWeeklyHardSets,
                intervalLabel = landmark.mevIntervalLabel,
                intervalA11y = mevA11y,
                modifier = Modifier.weight(LANDMARK_VALUE_WEIGHT),
            )
            LandmarkValueCell(
                value = landmark.mavWeeklyHardSets,
                intervalLabel = landmark.mavIntervalLabel,
                intervalA11y = mavA11y,
                modifier = Modifier.weight(LANDMARK_VALUE_WEIGHT),
            )
            LandmarkValueCell(
                value = landmark.mrvWeeklyHardSets,
                intervalLabel = landmark.mrvIntervalLabel,
                intervalA11y = mrvA11y,
                modifier = Modifier.weight(LANDMARK_VALUE_WEIGHT),
            )
            LandmarkValueCell(
                value = landmark.pvWeeklyHardSets,
                intervalLabel = landmark.pvIntervalLabel,
                intervalA11y = pvA11y,
                modifier = Modifier.weight(LANDMARK_VALUE_WEIGHT),
            )
        }
        StratumText(
            text = stringResource(
                R.string.gym_lifts_import_summary_landmark_confidence,
                confidenceLabel(landmark.confidenceTier),
            ),
            muted = true,
        )
    }
}

/**
 * One landmark value column: the point estimate (e.g. "10") with a tiny muted confidence-interval
 * subheader beneath it (e.g. "6–10"). The subheader is omitted when [intervalLabel] is blank
 * (prior-only fallback with no statistical bound). The cell is right-aligned to match the header.
 */
@Composable
private fun LandmarkValueCell(
    value: String,
    intervalLabel: String,
    intervalA11y: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        StratumNumber(text = value)
        if (intervalLabel.isNotEmpty()) {
            StratumText(
                text = intervalLabel,
                muted = true,
                style = StratumThemeAccessor.typography.bodyLarge.copy(
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.End,
                ),
                modifier =
                    Modifier.semantics {
                        if (intervalA11y.isNotEmpty()) {
                            contentDescription = intervalA11y
                        }
                    },
            )
        }
    }
}

/** Build an a11y content description for a CI label, or "" when there is no interval. */
private fun String.intervalA11y(landmarkName: String): String =
    if (isEmpty()) "" else "$landmarkName confidence interval: $this sets"

@Composable
private fun confidenceLabel(tier: LandmarkConfidenceTier): String =
    when (tier) {
        LandmarkConfidenceTier.High -> stringResource(R.string.gym_lifts_import_summary_confidence_high)
        LandmarkConfidenceTier.Medium -> stringResource(R.string.gym_lifts_import_summary_confidence_medium)
        LandmarkConfidenceTier.Low -> stringResource(R.string.gym_lifts_import_summary_confidence_low)
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

private const val LANDMARK_MUSCLE_WEIGHT = 1.4f
private const val LANDMARK_VALUE_WEIGHT = 1f
private const val EFFICACY_MUSCLE_WEIGHT = 1.4f
private const val EFFICACY_SIGNAL_WEIGHT = 1f
private const val TOOLTIP_MAX_WIDTH = 280
private const val TOOLTIP_OFFSET_BELOW = 8
