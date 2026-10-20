package com.stratum.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.stratum.R
import com.stratum.app.ShellUiState
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumGoogleSignInButton
import com.stratum.core.ui.component.StratumOutlinedButton
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.legal.LegalUrls
import com.stratum.core.ui.theme.StratumSpacing
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
                    .padding(StratumSpacing.s24),
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
                modifier = Modifier.padding(top = StratumSpacing.s16, bottom = StratumSpacing.s24),
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
                    SignInLegalFooter(modifier = Modifier.padding(top = StratumSpacing.s16))
                }
                is ShellUiState.SignInError -> {
                    // [shellState.message] is already mapped to user-facing copy in MainViewModel
                    // (see toGoogleSignInMessage); never pass raw throwables into ShellUiState.
                    SignInErrorText(
                        text = shellState.message,
                        modifier = Modifier.padding(bottom = StratumSpacing.s16),
                    )
                    StratumGoogleSignInButton(
                        onClick = onSignInWithGoogle,
                        enabled = true,
                    )
                    SignInLegalFooter(modifier = Modifier.padding(top = StratumSpacing.s16))
                }
                is ShellUiState.SessionFailed -> {
                    // Technical session.message stays in logs / state for support; UI is fixed copy.
                    SignInErrorText(
                        text = stringResource(R.string.sign_in_session_failed),
                        modifier = Modifier.padding(bottom = StratumSpacing.s16),
                    )
                    StratumButton(
                        text = stringResource(R.string.sign_in_retry),
                        onClick = onRetrySession,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(StratumSpacing.s12))
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

/**
 * Clickwrap under Google sign-in: continuing implies acceptance of Terms + Privacy.
 * Links open the hosted legal pages.
 */
@Composable
private fun SignInLegalFooter(modifier: Modifier = Modifier) {
    val termsLabel = stringResource(R.string.sign_in_legal_terms)
    val privacyLabel = stringResource(R.string.sign_in_legal_privacy)
    val template = stringResource(R.string.sign_in_legal_agreement, termsLabel, privacyLabel)
    val accent = StratumThemeAccessor.colors.accent
    val linkStyle =
        remember(accent) {
            TextLinkStyles(
                style =
                    SpanStyle(
                        color = accent,
                        textDecoration = TextDecoration.Underline,
                    ),
            )
        }
    // Template uses %1$s / %2$s expanded labels — split by those labels for link spans.
    val termsIndex = template.indexOf(termsLabel)
    val privacyIndex = template.indexOf(privacyLabel)
    val annotated =
        remember(template, termsLabel, privacyLabel, linkStyle) {
            buildAnnotatedString {
                if (termsIndex < 0 || privacyIndex < 0) {
                    append(template)
                    return@buildAnnotatedString
                }
                val firstIsTerms = termsIndex < privacyIndex
                val firstStart = minOf(termsIndex, privacyIndex)
                val firstLabel = if (firstIsTerms) termsLabel else privacyLabel
                val firstUrl = if (firstIsTerms) LegalUrls.TERMS else LegalUrls.PRIVACY
                val secondStart = maxOf(termsIndex, privacyIndex)
                val secondLabel = if (firstIsTerms) privacyLabel else termsLabel
                val secondUrl = if (firstIsTerms) LegalUrls.PRIVACY else LegalUrls.TERMS

                append(template.substring(0, firstStart))
                withLink(LinkAnnotation.Url(firstUrl, linkStyle)) {
                    append(firstLabel)
                }
                append(template.substring(firstStart + firstLabel.length, secondStart))
                withLink(LinkAnnotation.Url(secondUrl, linkStyle)) {
                    append(secondLabel)
                }
                append(template.substring(secondStart + secondLabel.length))
            }
        }
    Text(
        text = annotated,
        modifier = modifier.fillMaxWidth(),
        style =
            StratumThemeAccessor.typography.sectionLabel.copy(
                color = StratumThemeAccessor.colors.textMuted,
                textAlign = TextAlign.Center,
            ),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SignInErrorText(
    text: String,
    modifier: Modifier = Modifier,
) {
    StratumText(
        text = text,
        modifier = modifier,
        color = StratumThemeAccessor.colors.error,
        textAlign = TextAlign.Center,
    )
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
