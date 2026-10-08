package com.stratum.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.stratum.app.ShellUiState
import com.stratum.core.ui.theme.StratumThemeId

/**
 * Screenshot tests for sign-in (STR-93). Thin `@PreviewTest` wrappers over the shared preview
 * content in `SignInScreenPreview.kt`. The animated checking/preparing states are skipped.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun signInSignedOutTonal() {
    SignInScreenPreviewContent(family = StratumThemeId.Tonal, shellState = ShellUiState.SignedOut)
}

@PreviewTest
@PreviewLightDark
@Composable
fun signInSignedOutNeo() {
    SignInScreenPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        shellState = ShellUiState.SignedOut,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun signInErrorTonal() {
    SignInScreenPreviewContent(
        family = StratumThemeId.Tonal,
        shellState = ShellUiState.SignInError("Sign-in was cancelled. Try again."),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun signInErrorNeo() {
    SignInScreenPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        shellState = ShellUiState.SignInError("Sign-in was cancelled. Try again."),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun signInSessionFailedTonal() {
    SignInScreenPreviewContent(
        family = StratumThemeId.Tonal,
        shellState = ShellUiState.SessionFailed("sqlcipher failure (support detail)"),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun signInSessionFailedNeo() {
    SignInScreenPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        shellState = ShellUiState.SessionFailed("sqlcipher failure (support detail)"),
    )
}
