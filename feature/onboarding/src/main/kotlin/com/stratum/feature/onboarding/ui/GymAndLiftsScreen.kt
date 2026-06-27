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
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.domain.importing.ImportProgress
import com.stratum.domain.importing.ImportProgressPhase
import com.stratum.feature.onboarding.GymLiftsDraftUi
import com.stratum.feature.onboarding.GymPresetOptionUi
import com.stratum.feature.onboarding.R as FeatureR

/**
 * Step 5 of the wizard. Pick a gym preset or import historical training data (JSON/CSV).
 */
@Composable
fun GymAndLiftsScreen(
    draft: GymLiftsDraftUi,
    presetOptions: List<GymPresetOptionUi>,
    onPresetSelected: (String?) -> Unit,
    onImportClick: () -> Unit,
    onImportCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val importActive = draft.importInProgress
    val importComplete = draft.importComplete
    val importFailed = draft.importFailed
    var showImportDetails by remember { mutableStateOf(false) }

    if (showImportDetails) {
        ImportDetailsBottomSheet(onDismiss = { showImportDetails = false })
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StratumText(
            text = stringResource(FeatureR.string.gym_lifts_intro),
            muted = true,
        )

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
    importResumeFileIndex: Int?,
    importResumeFileCount: Int?,
    progress: ImportProgress?,
    onClick: () -> Unit,
    onCancel: () -> Unit,
    onLearnMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val importActive = progress != null &&
        progress.phase != ImportProgressPhase.Complete &&
        progress.phase != ImportProgressPhase.Failed
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
            importFailed -> stringResource(FeatureR.string.gym_lifts_import_phase_failed)
            importActive -> phaseLabel(checkNotNull(progress))
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
                .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .clickable(enabled = !importActive && !showCompleteBadge, onClick = onClick),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            StratumText(
                text = title,
                style = StratumThemeAccessor.typography.titleLarge,
            )
            if (importActive) {
                importProgressSubtitle(checkNotNull(progress))?.let { detail ->
                    StratumText(
                        text = detail,
                        muted = true,
                    )
                }
            } else if (!showCompleteBadge && !importFailed) {
                StratumText(
                    text = stringResource(FeatureR.string.gym_lifts_import_description),
                    muted = true,
                )
                Row(
                    modifier =
                        Modifier
                            .clickable(onClick = onLearnMoreClick)
                            .semantics { contentDescription = learnMoreLabel },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                    if (importResumeFileIndex != null && importResumeFileCount != null && importResumeFileCount > 1) {
                        stringResource(
                            FeatureR.string.gym_lifts_import_resume_hint,
                            importResumeFileIndex,
                            importResumeFileCount,
                        )
                    } else {
                        stringResource(FeatureR.string.gym_lifts_import_retry_hint)
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
private fun phaseLabel(progress: ImportProgress): String =
    when (progress.phase) {
        ImportProgressPhase.Uploading -> stringResource(FeatureR.string.gym_lifts_import_phase_uploading)
        ImportProgressPhase.ReadingData -> stringResource(FeatureR.string.gym_lifts_import_phase_reading)
        ImportProgressPhase.CreatingConverter -> stringResource(FeatureR.string.gym_lifts_import_phase_creating)
        ImportProgressPhase.ValidatingSample -> stringResource(FeatureR.string.gym_lifts_import_phase_validating)
        ImportProgressPhase.ConvertingFull -> stringResource(FeatureR.string.gym_lifts_import_phase_converting)
        ImportProgressPhase.EnrichingCustomLifts -> stringResource(FeatureR.string.gym_lifts_import_phase_enriching)
        ImportProgressPhase.ExtractingInsights -> stringResource(FeatureR.string.gym_lifts_import_phase_extracting)
        ImportProgressPhase.Complete -> stringResource(FeatureR.string.gym_lifts_import_complete)
        ImportProgressPhase.Failed -> stringResource(FeatureR.string.gym_lifts_import_phase_failed)
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
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
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
