package com.stratum.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.R
import com.stratum.app.ShellUiState
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumGoogleSignInButton
import com.stratum.core.ui.component.StratumOutlinedButton
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.R as CoreUiR
import kotlinx.coroutines.delay

@Composable
fun SignInScreen(
    shellState: ShellUiState,
    onSignInWithGoogle: () -> Unit,
    modifier: Modifier = Modifier,
    onRetrySession: () -> Unit = {},
    onSignOut: () -> Unit = {},
) {
    StratumScaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(CoreUiR.drawable.stratum_logo),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.fillMaxWidth(0.75f),
                contentScale = ContentScale.FillWidth,
                colorFilter = ColorFilter.tint(StratumThemeAccessor.colors.textPrimary),
            )
            StratumText(
                text = stringResource(R.string.sign_in_message),
                muted = true,
                modifier = Modifier.padding(top = 20.dp, bottom = 24.dp),
            )
            when (shellState) {
                ShellUiState.CheckingAuth -> {
                    AnimatedStatusText(base = stringResource(R.string.sign_in_checking))
                }
                ShellUiState.PreparingSession -> {
                    AnimatedStatusText(base = stringResource(R.string.sign_in_preparing_session))
                }
                ShellUiState.SignedOut -> {
                    StratumGoogleSignInButton(
                        onClick = onSignInWithGoogle,
                        enabled = true,
                    )
                }
                is ShellUiState.SignInError -> {
                    StratumText(
                        text = shellState.message,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                    StratumGoogleSignInButton(
                        onClick = onSignInWithGoogle,
                        enabled = true,
                    )
                }
                is ShellUiState.SessionFailed -> {
                    StratumText(
                        text = shellState.message,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                    StratumButton(
                        text = stringResource(R.string.sign_in_retry),
                        onClick = onRetrySession,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    StratumOutlinedButton(
                        text = stringResource(R.string.sign_in_sign_out),
                        onClick = onSignOut,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                ShellUiState.Ready -> {
                    // Host should not show SignIn when Ready; no-op fallback.
                }
            }
        }
    }
}

@Composable
private fun AnimatedStatusText(base: String) {
    var dotCount by remember { mutableIntStateOf(1) }
    LaunchedEffect(base) {
        while (true) {
            delay(DOT_TICK_MS)
            dotCount = if (dotCount >= 3) 1 else dotCount + 1
        }
    }
    val dots = ".".repeat(dotCount)
    StratumText(
        text = "$base$dots",
        style = StratumThemeAccessor.typography.titleMedium,
    )
}

private const val DOT_TICK_MS = 450L
