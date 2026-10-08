package com.stratum.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.stratum.app.ShellUiState
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId

@PreviewLightDark
@Composable
private fun SignInSignedOutTonalPreview() {
    SignInScreenPreviewContent(family = StratumThemeId.Tonal, shellState = ShellUiState.SignedOut)
}

@PreviewLightDark
@Composable
private fun SignInSignedOutNeoPreview() {
    SignInScreenPreviewContent(family = StratumThemeId.NeoBrutalist, shellState = ShellUiState.SignedOut)
}

@PreviewLightDark
@Composable
private fun SignInErrorTonalPreview() {
    SignInScreenPreviewContent(
        family = StratumThemeId.Tonal,
        shellState = ShellUiState.SignInError("Sign-in was cancelled. Try again."),
    )
}

@PreviewLightDark
@Composable
private fun SignInErrorNeoPreview() {
    SignInScreenPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        shellState = ShellUiState.SignInError("Sign-in was cancelled. Try again."),
    )
}

@PreviewLightDark
@Composable
private fun SignInSessionFailedTonalPreview() {
    SignInScreenPreviewContent(
        family = StratumThemeId.Tonal,
        shellState = ShellUiState.SessionFailed("sqlcipher failure (support detail)"),
    )
}

@PreviewLightDark
@Composable
private fun SignInSessionFailedNeoPreview() {
    SignInScreenPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        shellState = ShellUiState.SessionFailed("sqlcipher failure (support detail)"),
    )
}

@Composable
internal fun SignInScreenPreviewContent(
    family: StratumThemeId,
    shellState: ShellUiState,
) {
    StratumTheme(themeId = family) {
        SignInScreen(
            shellState = shellState,
            onSignInWithGoogle = {},
        )
    }
}
