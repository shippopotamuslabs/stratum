package com.stratum.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumActiveBadge
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumCheckbox
import com.stratum.core.ui.component.StratumPreferenceRow
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTextButton
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarBackButton
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.settings.ExportProgramsUiState
import com.stratum.feature.settings.R

@Composable
fun ExportProgramsScreen(
    uiState: ExportProgramsUiState,
    onBackClick: () -> Unit,
    onToggleProgram: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit = {},
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    val selectedCount = uiState.selectedIds.size
    val canExport = !uiState.isExporting && selectedCount > 0

    StratumScaffold(
        modifier = modifier,
        topBar = {
            StratumTopBar(
                title = stringResource(R.string.export_programs_title),
                navigationIcon = {
                    StratumTopBarBackButton(
                        onClick = onBackClick,
                        contentDescription = stringResource(R.string.settings_navigate_back),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StratumText(
                text = stringResource(R.string.export_programs_description),
                muted = true,
                style = StratumThemeAccessor.typography.sectionLabel,
            )

            if (uiState.programs.isEmpty()) {
                StratumText(
                    text = stringResource(R.string.export_programs_empty),
                    muted = true,
                    style = StratumThemeAccessor.typography.bodyLarge,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StratumText(
                        text =
                            stringResource(
                                R.string.export_programs_selection_count,
                                selectedCount,
                                uiState.programs.size,
                            ),
                        style = StratumThemeAccessor.typography.sectionLabel,
                        muted = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        StratumTextButton(
                            text = stringResource(R.string.export_programs_select_all),
                            onClick = onSelectAll,
                            enabled = !uiState.isExporting,
                        )
                        if (selectedCount > 0) {
                            StratumTextButton(
                                text = stringResource(R.string.export_programs_clear),
                                onClick = onClearSelection,
                                enabled = !uiState.isExporting,
                                contentColor = colors.textMuted,
                            )
                        }
                    }
                }

                uiState.programs.forEach { program ->
                    val selected = program.id in uiState.selectedIds
                    StratumPreferenceRow(
                        title = program.name,
                        subtitle =
                            if (program.isActive) {
                                stringResource(R.string.export_programs_active_subtitle)
                            } else {
                                stringResource(R.string.export_programs_library_subtitle)
                            },
                        onClick = { onToggleProgram(program.id) },
                        trailing = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (program.isActive) {
                                    StratumActiveBadge(
                                        label = stringResource(R.string.export_programs_active_badge),
                                        active = true,
                                    )
                                }
                                StratumCheckbox(
                                    checked = selected,
                                    onCheckedChange = { onToggleProgram(program.id) },
                                )
                            }
                        },
                    )
                }
            }

            uiState.errorMessage?.let { message ->
                StratumText(
                    text = message,
                    color = colors.error,
                    style = StratumThemeAccessor.typography.bodyLarge,
                )
            }

            StratumButton(
                text =
                    when {
                        uiState.isExporting -> stringResource(R.string.export_programs_exporting)
                        selectedCount > 0 ->
                            stringResource(R.string.export_programs_export_count, selectedCount)
                        else -> stringResource(R.string.export_programs_export)
                    },
                onClick = onExportClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = canExport,
            )
        }
    }
}

