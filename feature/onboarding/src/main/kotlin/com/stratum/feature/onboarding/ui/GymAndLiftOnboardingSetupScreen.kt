package com.stratum.feature.onboarding.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumSegmentedControl
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.trainingsetup.inScrollTopClearance
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.onboarding.GymLiftsDraftUi
import com.stratum.feature.onboarding.GymPresetOptionUi
import com.stratum.feature.onboarding.ImportProgressPhaseUi
import com.stratum.feature.onboarding.ImportProgressUi
import com.stratum.feature.onboarding.R as FeatureR

/**
 * Step 5 of the wizard. Pick a gym preset or import historical training data (JSON/CSV).
 * [appWeightUnitIsKg] is the app-wide display preference (Settings / live log stamp), not the
 * import-file unit fallback.
 */
@Composable
fun GymAndLiftOnboardingSetupScreen(
    draft: GymLiftsDraftUi,
    presetOptions: List<GymPresetOptionUi>,
    onPresetSelected: (String?) -> Unit,
    onImportClick: () -> Unit,
    onImportCancel: () -> Unit,
    appWeightUnitIsKg: Boolean = false,
    onAppWeightUnitSelected: (useKg: Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    // Glass (§12.8, STR-192): added inside the scroll (padding sits after
    // verticalScroll in the chain), so content scrolls beneath the bar.
    // 0 keeps other hosts unchanged.
    topClearance: Dp = 0.dp,
) {
    val importActive = draft.importInProgress
    val importComplete = draft.importComplete
    val importFailed = draft.importFailed
    var showImportDetails by remember { mutableStateOf(false) }

    if (showImportDetails) {
        ImportDetailsBottomSheet(onDismiss = { showImportDetails = false })
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = StratumSpacing.s16)
                .padding(top = inScrollTopClearance(topClearance), bottom = StratumSpacing.s12),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s16),
    ) {
        StratumText(
            text = stringResource(FeatureR.string.gym_lifts_intro),
            muted = true,
        )

        Column(verticalArrangement = Arrangement.spacedBy(StratumSpacing.s8)) {
            StratumText(
                text = stringResource(FeatureR.string.gym_lifts_weight_unit_title),
                style = StratumThemeAccessor.typography.sectionLabel,
                muted = true,
            )
            StratumText(
                text = stringResource(FeatureR.string.gym_lifts_weight_unit_subtitle),
                muted = true,
            )
            val unitOptions =
                listOf(
                    false to stringResource(FeatureR.string.gym_lifts_weight_unit_lbs),
                    true to stringResource(FeatureR.string.gym_lifts_weight_unit_kg),
                )
            StratumSegmentedControl(
                modifier = Modifier.fillMaxWidth(),
                segments = unitOptions.map { it.second },
                selectedIndex = if (appWeightUnitIsKg) 1 else 0,
                onSelectedIndexChange = { index ->
                    onAppWeightUnitSelected(unitOptions[index].first)
                },
            )
        }

        presetOptions.forEach { preset ->
            AnimatedVisibility(
                visible = !importComplete,
                exit = fadeOut() + shrinkVertically(),
            ) {
                GymPresetCard(
                    option = preset,
                    selected =
                        !importActive &&
                            draft.selectedPresetLabel == preset.label &&
                            !draft.usedHistoricalImport,
                    enabled = !importActive && !importComplete,
                    onClick = { onPresetSelected(preset.label) },
                )
            }
        }

        GymImportCard(
            highlighted = importActive || importComplete,
            showCompleteBadge = importComplete,
            importFailed = importFailed,
            importUnsupportedFile = draft.importUnsupportedFile,
            historicalImportMaxTimelineIndex = draft.historicalImportMaxTimelineIndex,
            importResumeFileIndex = draft.importResumeFileIndex,
            importResumeFileCount = draft.importResumeFileCount,
            progress = draft.importProgress,
            onClick = onImportClick,
            onCancel = onImportCancel,
            onLearnMoreClick = { showImportDetails = true },
        )

        AnimatedImportInsightsSummary(
            visible = importComplete,
            summary = draft.importInsightsSummary,
        )

        draft.importError?.let { error ->
            StratumText(text = error, muted = true)
        }

        draft.presetApplyError?.let { error ->
            StratumText(text = error, muted = true)
        }

        if (draft.showNoPresetError) {
            StratumText(
                text = stringResource(FeatureR.string.gym_lifts_error_no_preset),
                muted = true,
            )
        }
    }
}

@Composable
private fun ImportDetailsBottomSheet(
    onDismiss: () -> Unit,
) {
    StratumBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(FeatureR.string.gym_lifts_import_details_title),
    ) {
        StratumText(
            text = stringResource(FeatureR.string.gym_lifts_import_details_ai),
            muted = true,
        )
        StratumText(
            text = stringResource(FeatureR.string.gym_lifts_import_details_intro),
            muted = true,
        )
        StratumText(
            text = stringResource(FeatureR.string.gym_lifts_import_benefits),
            muted = true,
        )
        StratumText(
            text = stringResource(FeatureR.string.gym_lifts_import_format_hint),
            muted = true,
        )
    }
}

@Composable
private fun GymImportCard(
    highlighted: Boolean,
    showCompleteBadge: Boolean,
    importFailed: Boolean,
    importUnsupportedFile: Boolean = false,
    historicalImportMaxTimelineIndex: Int = 0,
    importResumeFileIndex: Int?,
    importResumeFileCount: Int?,
    progress: ImportProgressUi?,
    onClick: () -> Unit,
    onCancel: () -> Unit,
    onLearnMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val importActive = progress != null &&
        progress.phase != ImportProgressPhaseUi.Complete &&
        progress.phase != ImportProgressPhaseUi.Failed
    val showTimeline = importActive
    val borderColor =
        when {
            importFailed -> StratumThemeAccessor.colors.error
            highlighted -> StratumThemeAccessor.colors.accent
            else -> StratumThemeAccessor.colors.borderSubtle
        }
    val borderWidth =
        if (importFailed || highlighted) {
            SELECTED_BORDER_WIDTH
        } else {
            StratumThemeAccessor.shapes.borderWidth
        }
    val title =
        when {
            showCompleteBadge -> stringResource(FeatureR.string.gym_lifts_import_complete)
            importFailed && importUnsupportedFile ->
                stringResource(FeatureR.string.gym_lifts_import_phase_unsupported)
            importFailed -> stringResource(FeatureR.string.gym_lifts_import_phase_failed)
            importActive -> stringResource(FeatureR.string.gym_lifts_import_in_progress)
            else -> stringResource(FeatureR.string.gym_lifts_import_title)
        }
    val learnMoreLabel = stringResource(FeatureR.string.gym_lifts_import_learn_more)
    val cancelLabel = stringResource(FeatureR.string.gym_lifts_import_cancel)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    color = StratumThemeAccessor.colors.surfaceRaised,
                    shape = StratumThemeAccessor.shapes.panel,
                )
                .border(
                    width = borderWidth,
                    color = borderColor,
                    shape = StratumThemeAccessor.shapes.panel,
                )
                .padding(StratumSpacing.s12),
        horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .clickable(enabled = !importActive && !showCompleteBadge, onClick = onClick),
            verticalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
        ) {
            StratumText(
                text = title,
                style = StratumThemeAccessor.typography.titleLarge,
            )
            if (showTimeline) {
                HistoricalImportTimeline(
                    progress = checkNotNull(progress),
                    maxActiveIndex = historicalImportMaxTimelineIndex,
                )
            } else if (!showCompleteBadge && !importFailed) {
                StratumText(
                    text = stringResource(FeatureR.string.gym_lifts_import_description),
                    muted = true,
                )
                Row(
                    modifier =
                        Modifier
                            // §12.5: heightIn floors the link row at the 48dp
                            // target height at any font scale; the padding is
                            // the visual breathing room inside that target.
                            .clickable(onClick = onLearnMoreClick)
                            .heightIn(min = 48.dp)
                            .padding(vertical = StratumSpacing.s12)
                            .semantics { contentDescription = learnMoreLabel },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_info),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = StratumThemeAccessor.colors.accent,
                    )
                    StratumText(
                        text = learnMoreLabel,
                        style = StratumThemeAccessor.typography.bodyLarge,
                    )
                }
            } else if (importFailed) {
                val retryHint =
                    when {
                        importResumeFileIndex != null &&
                            importResumeFileCount != null &&
                            importResumeFileCount > 1 ->
                            stringResource(
                                FeatureR.string.gym_lifts_import_resume_hint,
                                importResumeFileIndex,
                                importResumeFileCount,
                            )
                        importUnsupportedFile ->
                            stringResource(FeatureR.string.gym_lifts_import_unsupported_hint)
                        else -> stringResource(FeatureR.string.gym_lifts_import_retry_hint)
                    }
                StratumText(
                    text = retryHint,
                    muted = true,
                )
            }
        }
        when {
            showCompleteBadge -> ImportCompleteBadge()
            importActive || importFailed ->
                StratumIconButton(
                    onClick = onCancel,
                    containerColor = StratumThemeAccessor.colors.borderSubtle,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_stop),
                        contentDescription = cancelLabel,
                        modifier = Modifier.size(20.dp),
                        tint = StratumThemeAccessor.colors.textPrimary,
                    )
                }
        }
    }
}

@Composable
private fun ImportCompleteBadge(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(32.dp)
                .background(StratumThemeAccessor.colors.accent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = StratumThemeAccessor.colors.onAccent,
        )
    }
}

@Composable
private fun GymPresetCard(
    option: GymPresetOptionUi,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    val borderColor =
        when {
            !enabled -> colors.borderSubtle
            selected -> colors.accent
            else -> colors.borderSubtle
        }
    val borderWidth =
        if (enabled && selected) {
            SELECTED_BORDER_WIDTH
        } else {
            StratumThemeAccessor.shapes.borderWidth
        }
    val cardAlpha = if (enabled) 1f else 0.42f

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .alpha(cardAlpha)
                .clickable(enabled = enabled, onClick = onClick)
                .background(
                    color = colors.surfaceRaised,
                    shape = StratumThemeAccessor.shapes.panel,
                )
                .border(
                    width = borderWidth,
                    color = borderColor,
                    shape = StratumThemeAccessor.shapes.panel,
                )
                .padding(StratumSpacing.s12),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
    ) {
        StratumText(
            text = option.label,
            style = StratumThemeAccessor.typography.titleLarge,
            muted = !enabled,
        )
        StratumText(
            text = option.description,
            muted = true,
        )
    }
}

private val SELECTED_BORDER_WIDTH = 2.dp
