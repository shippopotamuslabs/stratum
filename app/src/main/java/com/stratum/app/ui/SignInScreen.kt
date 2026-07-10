package com.stratum.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.R
import com.stratum.core.ui.auth.StratumAuthUiState
import com.stratum.core.ui.component.StratumGoogleSignInButton
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.R as CoreUiR

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
