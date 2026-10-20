package com.stratum.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Recovery chrome for an explicit [UiState] error branch. Call this from a screen that already
 * mapped a recoverable failure to presentation state — not as a Compose composition catcher.
 */
@Composable
fun StratumFeatureErrorFallback(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.feature_error_title),
    message: String = stringResource(R.string.feature_error_message),
    retryLabel: String = stringResource(R.string.feature_error_retry),
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(StratumSpacing.s24),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StratumText(
            text = title,
            style = StratumThemeAccessor.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(StratumSpacing.s8))
        StratumText(
            text = message,
            muted = true,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(StratumSpacing.s24))
        StratumButton(
            text = retryLabel,
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
