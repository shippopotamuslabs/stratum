package com.stratum.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Recovery chrome when a feature cannot render. Used by [StratumRenderBoundary] so an
 * opted-in render failure shows a retry prompt instead of a blank screen.
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
                .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StratumText(
            text = title,
            style = StratumThemeAccessor.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        StratumText(
            text = message,
            muted = true,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        StratumButton(
            text = retryLabel,
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Feature-level recovery boundary. Jetpack Compose cannot catch unhandled composition
 * exceptions; children must catch a failure and call [LocalUiRenderRecovery] to swap in
 * [StratumFeatureErrorFallback], or [LocalUiRenderErrorReporter] to log while keeping a local
 * placeholder. Uncaught composition exceptions still become process fatals.
 */
@Composable
fun StratumRenderBoundary(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var error by remember { mutableStateOf<Throwable?>(null) }
    val reporter = LocalUiRenderErrorReporter.current
    if (error != null) {
        StratumFeatureErrorFallback(
            onRetry = { error = null },
            modifier = modifier,
        )
        return
    }
    CompositionLocalProvider(
        LocalUiRenderRecovery provides { throwable ->
            reporter.report(throwable)
            error = throwable
        },
    ) {
        content()
    }
}
