package com.stratum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumTabScaffold
import com.stratum.core.ui.component.StratumText

@Composable
fun DashboardPlaceholder(modifier: Modifier = Modifier) {
    StratumTabScaffold(
        modifier = modifier,
        horizontalPadding = 12.dp,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StratumText(text = stringResource(R.string.dashboard_placeholder_title))
        StratumText(text = stringResource(R.string.dashboard_placeholder_subtitle), muted = true)
    }
}
