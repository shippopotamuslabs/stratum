package com.stratum.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.onboarding.ui.GymAndLiftsScreen
import com.stratum.feature.onboarding.ui.ProfileScreen
import com.stratum.feature.onboarding.ui.ReviewScreen
import com.stratum.feature.onboarding.ui.ScheduleScreen
import com.stratum.feature.onboarding.ui.SpecializationScreen
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

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onComplete()
    }

    if (state.isComplete) return

    BackHandler(enabled = state.canGoBack) { viewModel.back() }

    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        val name = uri.lastPathSegment ?: "import.csv"
        viewModel.onImportFileSelected(bytes, name)
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
        onCustomSessionMinutesChange = viewModel::onCustomSessionMinutesChange,
        onMuscleToggled = viewModel::onMuscleToggled,
        gymLifts = GymLiftsHandlers(
            onPresetSelected = viewModel::onPresetSelected,
        ),
        onImportClick = { importLauncher.launch(arrayOf("text/*", "application/json", "text/csv")) },
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
    onCustomSessionMinutesChange: (Int) -> Unit,
    onMuscleToggled: (String) -> Unit,
    gymLifts: GymLiftsHandlers,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StratumScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            StratumTopBar(
                title = state.step.displayName,
                navigationIcon = if (!state.isFirst) {
                    {
                        StratumText(
                            text = stringResource(R.string.onboarding_button_back),
                            modifier = Modifier
                                .clickable(onClick = onBack)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            style = StratumThemeAccessor.typography.bodyLarge,
                        )
                    }
                } else {
                    null
                },
                bottom = { Stepper(steps = state.steps, currentStepId = state.step.id) },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (state.step.id) {
                    WIZARD_STEP_WELCOME -> WelcomeScreen()
                    WIZARD_STEP_PROFILE -> ProfileScreen(
                        draft = state.profileDraft,
                        onTrainingAgeSelected = onTrainingAgeSelected,
                        onLastTrainedSelected = onLastTrainedSelected,
                    )
                    WIZARD_STEP_SCHEDULE -> ScheduleScreen(
                        draft = state.scheduleDraft,
                        onWeekdayToggled = onWeekdayToggled,
                        onSessionPresetSelected = onSessionPresetSelected,
                        onCustomSessionMinutesChange = onCustomSessionMinutesChange,
                    )
                    WIZARD_STEP_SPECIALIZATION -> SpecializationScreen(
                        draft = state.specializationDraft,
                        onMuscleToggled = onMuscleToggled,
                    )
                    WIZARD_STEP_GYM_AND_LIFTS -> GymAndLiftsScreen(
                        draft = state.gymLiftsDraft,
                        presetOptions = state.presetOptions,
                        onPresetSelected = gymLifts.onPresetSelected,
                        onImportClick = onImportClick,
                    )
                    WIZARD_STEP_REVIEW -> ReviewScreen(draft = state.reviewDraft)
                    WIZARD_STEP_GENERATE -> GenerateScreen(onComplete = onComplete)
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
private fun Stepper(steps: List<WizardStepUi>, currentStepId: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        steps.forEach { entry ->
            val active = entry.id == currentStepId
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
private fun GenerateScreen(onComplete: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
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
            onClick = onComplete,
        )
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
            StratumButton(
                text = stringResource(R.string.onboarding_button_back),
                onClick = onBack,
            )
        } else {
            Spacer(modifier = Modifier.height(48.dp))
        }
        if (!isLast && canAdvance) {
            StratumButton(
                text = stringResource(R.string.onboarding_button_next),
                onClick = onNext,
            )
        }
    }
}

private const val WIZARD_STEP_WELCOME = "Welcome"
private const val WIZARD_STEP_PROFILE = "Profile"
private const val WIZARD_STEP_SCHEDULE = "Schedule"
private const val WIZARD_STEP_SPECIALIZATION = "Specialization"
private const val WIZARD_STEP_GYM_AND_LIFTS = "GymAndLifts"
private const val WIZARD_STEP_REVIEW = "Review"
private const val WIZARD_STEP_GENERATE = "Generate"
