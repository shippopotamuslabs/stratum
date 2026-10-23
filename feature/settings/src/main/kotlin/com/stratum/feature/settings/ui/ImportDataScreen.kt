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
import com.stratum.core.ui.component.StratumDetailScaffold
import com.stratum.core.ui.component.StratumPreferenceRow
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarBackButton
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.settings.R

@Composable
fun ImportDataScreen(
    onBackClick: () -> Unit,
    onImportLogsClick: () -> Unit,
    onImportProgramClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Glass (§12.8, STR-161): under the route's glass host the top bar overlays
    // and the content column carries the top clearance inside its scroll, so
    // content scrolls beneath the glass. Solid hosts keep the in-flow geometry.
    StratumDetailScaffold(
        modifier = modifier,
        topBar = {
            StratumTopBar(
                title = stringResource(R.string.import_data_title),
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
            verticalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
        ) {
            StratumText(
                text = stringResource(R.string.import_data_description),
                muted = true,
                style = StratumThemeAccessor.typography.sectionLabel,
            )
            StratumPreferenceRow(
                title = stringResource(R.string.import_logs_row_title),
                subtitle = stringResource(R.string.import_logs_row_subtitle),
                onClick = onImportLogsClick,
            )
            StratumPreferenceRow(
                title = stringResource(R.string.import_program_row_title),
                subtitle = stringResource(R.string.import_program_row_subtitle),
                onClick = onImportProgramClick,
            )
        }
    }
}

