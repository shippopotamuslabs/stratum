package com.stratum.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.onboarding.GenerateState
import com.stratum.feature.onboarding.ProgramGenerationProgressUi
import com.stratum.feature.onboarding.R

@Composable
internal fun GenerateScreen(
    onGenerate: () -> Unit,
    onBack: () -> Unit,
    generateState: GenerateState,
    generateProgress: ProgramGenerationProgressUi?,
    generateMaxTimelineIndex: Int,
    generateErrorMessage: String?,
    modifier: Modifier = Modifier,
) {
    when (generateState) {
        GenerateState.Loading ->
            GenerateLoadingContent(
                progress = generateProgress,
                maxActiveIndex = generateMaxTimelineIndex,
                modifier = modifier,
            )
        GenerateState.Error ->
            GenerateErrorContent(
                onGenerate = onGenerate,
                onBack = onBack,
                generateErrorMessage = generateErrorMessage,
                modifier = modifier,
            )
        GenerateState.Idle ->
            GenerateIdleContent(
                onGenerate = onGenerate,
                modifier = modifier,
            )
    }
}

@Composable
internal fun GenerateLoadingContent(
    progress: ProgramGenerationProgressUi?,
    maxActiveIndex: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        StratumText(
            text = stringResource(R.string.onboarding_generate_loading),
            style = StratumThemeAccessor.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(24.dp))
        ProgramGenerationTimeline(
            progress = progress,
            maxActiveIndex = maxActiveIndex,
        )
    }
}

@Composable
private fun GenerateIdleContent(
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StratumText(
            text = stringResource(R.string.onboarding_generate_title),
            style = StratumThemeAccessor.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(16.dp))
        StratumButton(
            text = stringResource(R.string.onboarding_button_generate),
            onClick = onGenerate,
        )
    }
}

@Composable
private fun GenerateErrorContent(
    onGenerate: () -> Unit,
    onBack: () -> Unit,
    generateErrorMessage: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StratumText(text = stringResource(R.string.onboarding_generate_error_title))
        generateErrorMessage?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            StratumText(text = message, muted = true)
        }
        Spacer(modifier = Modifier.height(16.dp))
        StratumButton(
            text = stringResource(R.string.onboarding_generate_error_retry),
            onClick = onGenerate,
        )
        Spacer(modifier = Modifier.height(8.dp))
        StratumButton(
            text = stringResource(R.string.onboarding_generate_error_back_to_review),
            onClick = onBack,
        )
    }
}
