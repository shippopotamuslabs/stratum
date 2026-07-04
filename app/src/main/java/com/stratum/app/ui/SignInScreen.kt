package com.stratum.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.auth.StratumAuthUiState
import com.stratum.core.ui.component.StratumGoogleSignInButton
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor

@Composable
fun SignInScreen(
    authState: StratumAuthUiState,
    onSignInWithGoogle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StratumScaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StratumText(
                text = "Stratum",
                style = StratumThemeAccessor.typography.titleLarge,
            )
            StratumText(
                text = "Sign in to build your training program.",
                muted = true,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
            if (authState is StratumAuthUiState.Error) {
                StratumText(
                    text = authState.message,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
            StratumGoogleSignInButton(
                onClick = onSignInWithGoogle,
                enabled = authState !is StratumAuthUiState.Loading,
            )
        }
    }
}
