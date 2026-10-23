// STR-142 allowlist: section rhythm (20.dp) pinned; screen margins adopted.
@file:Suppress("ForbiddenRawSpacingLiteralInUi")
package com.stratum.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumDateField
import com.stratum.core.ui.component.StratumDetailScaffold
import com.stratum.core.ui.component.StratumFilterChip
import com.stratum.core.ui.component.StratumStatCard
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarBackButton
import com.stratum.core.ui.component.formatMediumDate
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.settings.ExportLogPreviewUi
import com.stratum.feature.settings.ExportLogsErrorUi
import com.stratum.feature.settings.ExportLogsUiState
import com.stratum.feature.settings.ExportRangePresetUi
import com.stratum.feature.settings.R
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportLogsScreen(
    uiState: ExportLogsUiState,
    onBackClick: () -> Unit,
    onRangePresetSelected: (ExportRangePresetUi) -> Unit,
    onCustomDatesChanged: (LocalDate?, LocalDate?) -> Unit,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val presets =
        listOf(
            ExportRangePresetUi.AllTime to stringResource(R.string.export_logs_range_all_time),
            ExportRangePresetUi.Last30 to stringResource(R.string.export_logs_range_last_30),
            ExportRangePresetUi.Last90 to stringResource(R.string.export_logs_range_last_90),
            ExportRangePresetUi.YearToDate to stringResource(R.string.export_logs_range_ytd),
            ExportRangePresetUi.Custom to stringResource(R.string.export_logs_range_custom),
        )
    val sessionCount = uiState.preview?.sessionCount ?: 0
    val canExport = !uiState.isExporting && sessionCount > 0
    val customStart = uiState.customStart
    val customEnd = uiState.customEnd
    val rangeOrderError =
        uiState.rangePreset == ExportRangePresetUi.Custom &&
            customStart != null &&
            customEnd != null &&
            customStart > customEnd
    val errorText = uiState.error?.let { exportLogsErrorText(it) }

    // Glass (§12.8, STR-161): under the route's glass host the top bar overlays
    // and the content column carries the top clearance inside its scroll, so
    // content scrolls beneath the glass. Solid hosts keep the in-flow geometry.
    StratumDetailScaffold(
        modifier = modifier,
        topBar = {
            StratumTopBar(
                title = stringResource(R.string.export_logs_title),
                navigationIcon = {
                    StratumTopBarBackButton(
                        onClick = onBackClick,
                        contentDescription = stringResource(R.string.settings_navigate_back),
                    )
                },
            )
        },
    ) { chrome ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = StratumSpacing.s16)
                    .padding(top = StratumSpacing.s16 + chrome.top, bottom = StratumSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            StratumText(
                text = stringResource(R.string.export_logs_description),
                muted = true,
                style = StratumThemeAccessor.typography.sectionLabel,
            )

            Column(verticalArrangement = Arrangement.spacedBy(StratumSpacing.s12)) {
                StratumText(
                    text = stringResource(R.string.export_logs_range_section_title),
                    style = StratumThemeAccessor.typography.titleMedium,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
                    verticalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
                ) {
                    presets.forEach { (preset, label) ->
                        StratumFilterChip(
                            label = label,
                            selected = uiState.rangePreset == preset,
                            onSelectedChange = { selected ->
                                if (selected) onRangePresetSelected(preset)
                            },
                        )
                    }
                }
            }

            if (uiState.rangePreset == ExportRangePresetUi.Custom) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(color = colors.surfaceRaised, shape = shapes.panel)
                            .border(
                                width = shapes.borderWidth,
                                color = colors.borderSubtle,
                                shape = shapes.panel,
                            )
                            .padding(StratumSpacing.s16),
                    verticalArrangement = Arrangement.spacedBy(StratumSpacing.s16),
                ) {
                    StratumText(
                        text = stringResource(R.string.export_logs_custom_section_title),
                        style = StratumThemeAccessor.typography.titleMedium,
                    )
                    StratumText(
                        text = stringResource(R.string.export_logs_custom_section_hint),
                        muted = true,
                        style = StratumThemeAccessor.typography.sectionLabel,
                    )
                    StratumDateField(
                        label = stringResource(R.string.export_logs_custom_start_label),
                        date = customStart,
                        onDateChange = { start ->
                            onCustomDatesChanged(start, customEnd)
                        },
                        placeholder = stringResource(R.string.export_logs_pick_date),
                        maxDate = customEnd ?: LocalDate.now(),
                        dialogTitle = stringResource(R.string.export_logs_custom_start_label),
                        isError = rangeOrderError,
                    )
                    StratumDateField(
                        label = stringResource(R.string.export_logs_custom_end_label),
                        date = customEnd,
                        onDateChange = { end ->
                            onCustomDatesChanged(customStart, end)
                        },
                        placeholder = stringResource(R.string.export_logs_pick_date),
                        minDate = customStart,
                        maxDate = LocalDate.now(),
                        dialogTitle = stringResource(R.string.export_logs_custom_end_label),
                        isError = rangeOrderError,
                        errorText =
                            if (rangeOrderError) {
                                stringResource(R.string.export_logs_custom_date_order)
                            } else {
                                null
                            },
                    )
                }
            }

            uiState.preview?.let { preview ->
                ExportLogsPreviewCard(
                    preview = preview,
                    rangeLabel = rangeSummaryLabel(uiState),
                )
            }

            errorText?.let { message ->
                StratumText(
                    text = message,
                    color = colors.error,
                    style = StratumThemeAccessor.typography.bodyLarge,
                )
            }

            if (sessionCount == 0 && uiState.preview != null && uiState.error == null) {
                StratumText(
                    text = stringResource(R.string.export_logs_empty_range),
                    muted = true,
                    style = StratumThemeAccessor.typography.sectionLabel,
                )
            }

            StratumButton(
                text =
                    stringResource(
                        if (uiState.isExporting) {
                            R.string.export_logs_exporting
                        } else {
                            R.string.export_logs_export
                        },
                    ),
                onClick = onExportClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = canExport && !rangeOrderError,
            )
        }
    }
}

@Composable
private fun ExportLogsPreviewCard(
    preview: ExportLogPreviewUi,
    rangeLabel: String,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color = colors.surfaceRaised, shape = shapes.panel)
                .border(
                    width = shapes.borderWidth,
                    color = colors.borderSubtle,
                    shape = shapes.panel,
                )
                .padding(StratumSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
    ) {
        StratumText(
            text = stringResource(R.string.export_logs_preview_title),
            style = StratumThemeAccessor.typography.titleMedium,
        )
        StratumText(
            text = rangeLabel,
            muted = true,
            style = StratumThemeAccessor.typography.sectionLabel,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
        ) {
            StratumStatCard(
                label = stringResource(R.string.export_logs_preview_sessions),
                value = preview.sessionCount.toString(),
                modifier = Modifier.weight(1f),
            )
            StratumStatCard(
                label = stringResource(R.string.export_logs_preview_sets),
                value = preview.setCount.toString(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun exportLogsErrorText(error: ExportLogsErrorUi): String =
    when (error) {
        ExportLogsErrorUi.InvalidDateOrder ->
            stringResource(R.string.export_logs_custom_date_order)
        ExportLogsErrorUi.EmptyRange ->
            stringResource(R.string.export_logs_empty_export)
        is ExportLogsErrorUi.Generic -> error.message
    }

@Composable
private fun rangeSummaryLabel(uiState: ExportLogsUiState): String =
    when (uiState.rangePreset) {
        ExportRangePresetUi.AllTime -> stringResource(R.string.export_logs_range_all_time)
        ExportRangePresetUi.Last30 -> stringResource(R.string.export_logs_range_last_30)
        ExportRangePresetUi.Last90 -> stringResource(R.string.export_logs_range_last_90)
        ExportRangePresetUi.YearToDate -> stringResource(R.string.export_logs_range_ytd)
        ExportRangePresetUi.Custom -> {
            val start = uiState.customStart
            val end = uiState.customEnd
            if (start != null && end != null) {
                stringResource(
                    R.string.export_logs_custom_range_label,
                    formatMediumDate(start),
                    formatMediumDate(end),
                )
            } else {
                stringResource(R.string.export_logs_range_custom)
            }
        }
    }
