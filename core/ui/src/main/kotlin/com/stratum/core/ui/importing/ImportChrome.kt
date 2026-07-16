package com.stratum.core.ui.importing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumProgressTimeline
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Shared import-surface chrome used by Settings and available to onboarding.
 * Callers pass already-resolved strings so feature modules own copy/i18n.
 */

@Composable
fun StratumImportInfoCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    note: String? = null,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(color = colors.surfaceRaised, shape = shapes.panel)
                .border(
                    width = shapes.borderWidth,
                    color = colors.borderSubtle,
                    shape = shapes.panel,
                )
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StratumText(
            text = title,
            style = StratumThemeAccessor.typography.titleMedium,
        )
        StratumText(
            text = body,
            muted = true,
            style = StratumThemeAccessor.typography.sectionLabel,
        )
        if (!note.isNullOrBlank()) {
            StratumText(
                text = note,
                muted = true,
                style = StratumThemeAccessor.typography.sectionLabel,
            )
        }
    }
}

@Composable
fun StratumImportStatusBanner(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(color = colors.surfaceSunken, shape = shapes.panel)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StratumText(
            text = title,
            style = StratumThemeAccessor.typography.titleMedium,
        )
        StratumText(
            text = body,
            muted = true,
            style = StratumThemeAccessor.typography.sectionLabel,
        )
    }
}

enum class StratumImportResultTone {
    Success,
    Error,
}

@Composable
fun StratumImportResultCard(
    title: String,
    body: String,
    tone: StratumImportResultTone,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val borderColor: Color =
        when (tone) {
            StratumImportResultTone.Success -> colors.setComplete
            StratumImportResultTone.Error -> colors.error
        }
    val titleColor: Color? =
        when (tone) {
            StratumImportResultTone.Success -> null
            StratumImportResultTone.Error -> colors.error
        }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(color = colors.surfaceRaised, shape = shapes.panel)
                .border(
                    width = shapes.borderWidth,
                    color = borderColor,
                    shape = shapes.panel,
                )
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StratumText(
            text = title,
            style = StratumThemeAccessor.typography.titleMedium,
            color = titleColor,
        )
        StratumText(
            text = body,
            style = StratumThemeAccessor.typography.bodyLarge,
        )
        if (!detail.isNullOrBlank()) {
            StratumText(
                text = detail,
                muted = true,
                style = StratumThemeAccessor.typography.sectionLabel,
            )
        }
    }
}

@Composable
fun StratumImportProgressSection(
    title: String,
    stepLabels: List<String>,
    activeIndex: Int,
    modifier: Modifier = Modifier,
    activeDetail: String? = null,
    fileProgressLabel: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StratumText(
            text = title,
            style = StratumThemeAccessor.typography.titleMedium,
        )
        if (!fileProgressLabel.isNullOrBlank()) {
            StratumText(
                text = fileProgressLabel,
                muted = true,
                style = StratumThemeAccessor.typography.sectionLabel,
            )
        }
        StratumProgressTimeline(
            stepLabels = stepLabels,
            activeIndex = activeIndex,
            activeDetail = activeDetail,
        )
    }
}
