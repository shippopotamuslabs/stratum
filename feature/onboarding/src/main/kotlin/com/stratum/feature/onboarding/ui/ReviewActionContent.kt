package com.stratum.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.ProgramGenerationLoadingContent
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.onboarding.ImportProgramPhaseUi
import com.stratum.feature.onboarding.ProgramGenerationProgressUi
import com.stratum.feature.onboarding.R

/**
 * Loading timelines and import-error recovery rendered inside ReviewScreen's Crossfade.
 * No vertical scroll — they live inside ReviewScreen's scrolling column. Nesting two
 * verticalScrolls crashes with infinite-height constraints.
 */
@Composable
internal fun GenerateLoadingContent(
    progress: ProgramGenerationProgressUi?,
    maxActiveIndex: Int,
    modifier: Modifier = Modifier,
) {
    ProgramGenerationLoadingContent(
        progress = progress,
        maxActiveIndex = maxActiveIndex,
        modifier = modifier,
    )
}

@Composable
internal fun ImportLoadingContent(
    phase: ImportProgramPhaseUi?,
    maxActiveIndex: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StratumText(
            text = stringResource(R.string.onboarding_import_loading),
            style = StratumThemeAccessor.typography.titleLarge,
        )
        ProgramImportTimeline(
            phase = phase,
            maxActiveIndex = maxActiveIndex,
        )
    }
}

/**
 * Focused recovery pane when program-template import fails or is rejected. Replaces the review
 * summary so the error is not buried under cards with a duplicate Import CTA at the top.
 *
 * @param rejected Distinct title/body for server "not a program" rejections.
 * @param message Optional user-safe body (e.g. file-too-large). Null falls back to generic strings.
 */
@Composable
internal fun ImportProgramErrorContent(
    rejected: Boolean,
    message: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val body = when {
        !message.isNullOrEmpty() -> message
        rejected -> stringResource(R.string.onboarding_import_error_rejected)
        else -> stringResource(R.string.onboarding_import_error_generic)
    }
    val title = if (rejected) {
        stringResource(R.string.onboarding_import_error_rejected_title)
    } else {
        stringResource(R.string.onboarding_import_error_title)
    }
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes

    Column(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = colors.surfaceRaised, shape = shapes.panel)
                .border(
                    width = ErrorBorderWidth,
                    color = colors.error,
                    shape = shapes.panel,
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StratumText(
                text = title,
                style = StratumThemeAccessor.typography.titleLarge.copy(color = colors.error),
            )
            StratumText(text = body, muted = true)
        }

        StratumButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.onboarding_import_error_retry),
            onClick = onRetry,
        )
        StratumText(
            text = stringResource(R.string.onboarding_import_supported_hint),
            muted = true,
        )
    }
}

private val ErrorBorderWidth = 2.dp
