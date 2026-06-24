package com.stratum.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.domain.onboarding.OnboardingStep
import com.stratum.feature.onboarding.ui.ProfileScreen
import com.stratum.feature.onboarding.ui.ScheduleScreen
import com.stratum.feature.onboarding.ui.WelcomeScreen

/**
 * Onboarding wizard root. No NavController is passed in (ARCHITECTURE.md §4.4) —
 * completion is signaled via [onComplete].
 */
@Composable
fun OnboardingRoute(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.isComplete) {
        onComplete()
        return
    }

    OnboardingWizard(
        state = state,
        onNext = viewModel::next,
        onBack = viewModel::back,
        onComplete = viewModel::complete,
        onTrainingAgeSelected = viewModel::onTrainingAgeSelected,
        onLastTrainedSelected = viewModel::onLastTrainedSelected,
        onWeekdayToggled = viewModel::onWeekdayToggled,
        onSessionPresetSelected = viewModel::onSessionPresetSelected,
        onCustomSessionChange = viewModel::onCustomSessionChange,
        modifier = modifier,
    )
}

@Composable
private fun OnboardingWizard(
    state: OnboardingUiState,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onComplete: () -> Unit,
    onTrainingAgeSelected: (String?) -> Unit,
    onLastTrainedSelected: (String?) -> Unit,
    onWeekdayToggled: (String) -> Unit,
    onSessionPresetSelected: (String?) -> Unit,
    onCustomSessionChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    StratumScaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            StratumTopBar(title = "Onboarding")
            Stepper(step = state.step)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (state.step) {
                    OnboardingStep.Welcome -> WelcomeScreen()
                    OnboardingStep.Profile -> ProfileScreen(
                        draft = state.profileDraft,
                        onTrainingAgeSelected = onTrainingAgeSelected,
                        onLastTrainedSelected = onLastTrainedSelected,
                    )
                    OnboardingStep.Schedule -> ScheduleScreen(
                        draft = state.scheduleDraft,
                        onWeekdayToggled = onWeekdayToggled,
                        onSessionPresetSelected = onSessionPresetSelected,
                        onCustomSessionChange = onCustomSessionChange,
                    )
                    OnboardingStep.Specialization,
                    OnboardingStep.GymAndLifts,
                    OnboardingStep.Review -> ComingSoonScreen(state.step)
                    OnboardingStep.Generate -> GenerateScreen(onComplete = onComplete)
                }
            }
            WizardControls(
                isFirst = state.isFirst,
                isLast = state.isLast,
                canAdvance = state.canAdvance,
                onBack = onBack,
                onNext = onNext,
            )
        }
    }
}

@Composable
private fun Stepper(step: OnboardingStep) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OnboardingStep.entries.forEach { entry ->
            val active = entry == step
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(
                        color = if (active) {
                            StratumThemeAccessor.colors.accent
                        } else {
                            StratumThemeAccessor.colors.borderSubtle
                        },
                    ),
            )
        }
    }
}

@Composable
private fun ComingSoonScreen(step: OnboardingStep) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StratumText(text = step.name, style = StratumThemeAccessor.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        StratumText(text = "Coming in a later slice.", muted = true)
    }
}

@Composable
private fun GenerateScreen(onComplete: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StratumText(
            text = "Ready to build your program?",
            style = StratumThemeAccessor.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(16.dp))
        StratumButton(text = "Generate", onClick = onComplete)
    }
}

@Composable
private fun WizardControls(
    isFirst: Boolean,
    isLast: Boolean,
    canAdvance: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isFirst) {
            StratumButton(text = "Back", onClick = onBack)
        } else {
            Spacer(modifier = Modifier.height(48.dp))
        }
        if (!isLast && canAdvance) {
            StratumButton(text = "Next", onClick = onNext)
        }
    }
}
