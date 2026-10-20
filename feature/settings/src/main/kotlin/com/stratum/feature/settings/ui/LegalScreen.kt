package com.stratum.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumPreferenceRow
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarBackButton
import com.stratum.core.ui.legal.LegalUrls
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.feature.settings.R

@Composable
fun LegalScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current

    StratumScaffold(
        modifier = modifier,
        topBar = {
            StratumTopBar(
                title = stringResource(R.string.legal_title),
                navigationIcon = {
                    StratumTopBarBackButton(
                        onClick = onBackClick,
                        contentDescription = stringResource(R.string.legal_navigate_back),
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
                    .padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s8),
            verticalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
        ) {
            StratumPreferenceRow(
                title = stringResource(R.string.legal_terms_title),
                subtitle = stringResource(R.string.legal_terms_subtitle),
                onClick = { uriHandler.openUri(LegalUrls.TERMS) },
            )
            StratumPreferenceRow(
                title = stringResource(R.string.legal_privacy_title),
                subtitle = stringResource(R.string.legal_privacy_subtitle),
                onClick = { uriHandler.openUri(LegalUrls.PRIVACY) },
            )
            StratumPreferenceRow(
                title = stringResource(R.string.legal_oss_title),
                subtitle = stringResource(R.string.legal_oss_subtitle),
                onClick = { uriHandler.openUri(LegalUrls.OPEN_SOURCE) },
            )
        }
    }
}
