package com.stratum.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumPreferenceRow
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarBackButton
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.settings.R

@Composable
fun ExportDataScreen(
    onBackClick: () -> Unit,
    onExportLogsClick: () -> Unit,
    onExportProgramsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StratumScaffold(
        modifier = modifier,
        topBar = {
            StratumTopBar(
                title = stringResource(R.string.export_data_title),
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StratumText(
                text = stringResource(R.string.export_data_description),
                muted = true,
                style = StratumThemeAccessor.typography.sectionLabel,
            )
            StratumPreferenceRow(
                title = stringResource(R.string.export_logs_row_title),
                subtitle = stringResource(R.string.export_logs_row_subtitle),
                onClick = onExportLogsClick,
            )
            StratumPreferenceRow(
                title = stringResource(R.string.export_programs_row_title),
                subtitle = stringResource(R.string.export_programs_row_subtitle),
                onClick = onExportProgramsClick,
            )
        }
    }
}

