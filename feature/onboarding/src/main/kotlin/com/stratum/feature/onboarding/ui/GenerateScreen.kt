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
import com.stratum.core.ui.component.StratumOutlinedButton
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.onboarding.GenerateState
import com.stratum.feature.onboarding.ImportProgramPhaseUi
import com.stratum.feature.onboarding.ImportProgramState
import com.stratum.feature.onboarding.ProgramGenerationProgressUi
import com.stratum.feature.onboarding.R

@Composable
internal fun GenerateScreen(
    onGenerate: () -> Unit,
    onImportExisting: () -> Unit,
    onBack: () -> Unit,
    generateState: GenerateState,
    generateProgress: ProgramGenerationProgressUi?,
    generateMaxTimelineIndex: Int,
    generateErrorMessage: String?,
    importProgramState: ImportProgramState,
    modifier: Modifier = Modifier,
) {
    // Import-in-progress takes precedence over generate states: once the user has chosen to import,
    // the screen shows import progress until it succeeds or fails.
    val importLoading = importProgramState as? ImportProgramState.Loading
    when {
        importLoading != null ->
            ImportLoadingContent(phase = importLoading.phase, modifier = modifier)
        importProgramState is ImportProgramState.Error ->
            ImportErrorContent(
                message = importProgramState.message,
                onRetry = onImportExisting,
                onBack = onBack,
                modifier = modifier,
            )
        importProgramState is ImportProgramState.Rejected ->
            ImportErrorContent(
                message = importProgramState.message,
                rejected = true,
                onRetry = onImportExisting,
                onBack = onBack,
                modifier = modifier,
            )
        generateState == GenerateState.Loading ->
            GenerateLoadingContent(
                progress = generateProgress,
                maxActiveIndex = generateMaxTimelineIndex,
                modifier = modifier,
            )
        generateState == GenerateState.Error ->
            GenerateErrorContent(
                onGenerate = onGenerate,
                onBack = onBack,
                generateErrorMessage = generateErrorMessage,
                modifier = modifier,
            )
        else ->
            GenerateIdleContent(
                onGenerate = onGenerate,
                onImportExisting = onImportExisting,
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
                .padding(horizontal = 12.dp, vertical = 16.dp),
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
    onImportExisting: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(12.dp),
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
        Spacer(modifier = Modifier.height(8.dp))
        StratumOutlinedButton(
            text = stringResource(R.string.onboarding_import_existing_button),
            onClick = onImportExisting,
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
        modifier = modifier.fillMaxSize().padding(12.dp),
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

@Composable
private fun ImportLoadingContent(
    phase: ImportProgramPhaseUi,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StratumText(
            text = stringResource(R.string.onboarding_import_loading),
            style = StratumThemeAccessor.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(12.dp))
        StratumText(text = stringResource(phase.label()), muted = true)
    }
}

@Composable
private fun ImportErrorContent(
    message: String?,
    rejected: Boolean = false,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StratumText(text = stringResource(R.string.onboarding_import_error_title))
        Spacer(modifier = Modifier.height(8.dp))
        StratumText(
            text = when {
                !message.isNullOrEmpty() -> message
                rejected -> stringResource(R.string.onboarding_import_error_rejected)
                else -> stringResource(R.string.onboarding_import_error_generic)
            },
            muted = true,
        )
        Spacer(modifier = Modifier.height(16.dp))
        StratumButton(
            text = stringResource(R.string.onboarding_import_error_retry),
            onClick = onRetry,
        )
        Spacer(modifier = Modifier.height(8.dp))
        StratumOutlinedButton(
            text = stringResource(R.string.onboarding_import_error_back),
            onClick = onBack,
        )
    }
}

private fun ImportProgramPhaseUi.label(): Int = when (this) {
    ImportProgramPhaseUi.Uploading -> R.string.onboarding_import_phase_uploading
    ImportProgramPhaseUi.Sniffing -> R.string.onboarding_import_phase_sniffing
    ImportProgramPhaseUi.Parsing -> R.string.onboarding_import_phase_parsing
    ImportProgramPhaseUi.ScriptGenerating -> R.string.onboarding_import_phase_script_generating
    ImportProgramPhaseUi.ScriptTesting -> R.string.onboarding_import_phase_script_testing
    ImportProgramPhaseUi.MatchingCatalog -> R.string.onboarding_import_phase_matching_catalog
    ImportProgramPhaseUi.Complete -> R.string.onboarding_import_phase_complete
}
