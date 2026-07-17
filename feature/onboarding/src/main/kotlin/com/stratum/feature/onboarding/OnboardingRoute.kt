package com.stratum.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarBackButton
import com.stratum.core.ui.component.UserMessageEffect
import com.stratum.core.ui.importing.loadImportFile
import com.stratum.core.ui.importing.loadImportFiles
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.domain.onboarding.OnboardingPath
import com.stratum.domain.program.importing.ProgramTemplateImportLimits
import com.stratum.feature.onboarding.OnboardingPathUi
import com.stratum.feature.onboarding.ui.ChoosePathScreen
import com.stratum.feature.onboarding.ui.GymAndLiftOnboardingSetupScreen
import com.stratum.feature.onboarding.ui.ProfileScreen
import com.stratum.feature.onboarding.ui.ReviewScreen
import com.stratum.feature.onboarding.ui.ScheduleScreen
import com.stratum.feature.onboarding.ui.MinimumVolumeScreen
import com.stratum.feature.onboarding.ui.SpecializationScreen


/**
 * Onboarding wizard root. No NavController is passed in (ARCHITECTURE.md §4.4) —
 * completion is signaled via [onComplete].
 */
@Composable
fun OnboardingRoute(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
    onBuildFromScratch: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    UserMessageEffect(
        userMessage = state.navigationError,
        snackbarHostState = snackbarHostState,
        onUserMessageShown = viewModel::navigationErrorShown,
    )

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onComplete()
    }

    if (state.isComplete) return

    BackHandler(enabled = state.canGoBack) { viewModel.back() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        // SAF result callbacks run on the main thread; permission take + file reads are blocking I/O.
        // Reading historically caused ANRs ("Input dispatching timed out") on the import step.
        scope.launch {
            loadImportFiles(context, uris)
                .onSuccess { files -> viewModel.onImportFilesSelected(files) }
            // Empty/all-failed stays silent on onboarding multi-file (prior behavior).
        }
    }

    // Single-file picker for the "Import Existing" program flow (CSV/XLSX).
    val programImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        // SAF result callbacks run on the main thread; permission take + file reads are blocking I/O.
        scope.launch {
            loadImportFile(context, uri)
                .onSuccess { file ->
                    // Reject oversized files before upload — program templates are tiny, so even 1 MB
                    // is generous. Saves bandwidth and protects the server-side LLM budget.
                    if (file.bytes.size > ProgramTemplateImportLimits.MAX_FILE_BYTES) {
                        viewModel.showImportProgramError(
                            userFacingMessage =
                                context.getString(
                                    R.string.onboarding_import_error_too_large,
                                    ProgramTemplateImportLimits.MAX_FILE_SIZE_MB,
                                ),
                        )
                    } else {
                        viewModel.importExistingProgram(file)
                    }
                }
                .onFailure { error ->
                    // Raw IO messages stay in logs only — UI uses generic copy.
                    viewModel.showImportProgramError(logDetail = error.message)
                }
        }
    }

    OnboardingWizard(
        state = state,
        onNext = viewModel::next,
        onBack = viewModel::back,
        onGenerate = viewModel::generate,
        onImportExisting = { programImportLauncher.launch(PROGRAM_IMPORT_MIME_TYPES) },
        pathHandlers = ChoosePathHandlers(
            onSelectGenerate = { viewModel.selectPath(OnboardingPath.Generate) },
            onSelectScratch = onBuildFromScratch,
            onSelectImport = { viewModel.selectPath(OnboardingPath.Import) },
        ),
        profileSchedule = ProfileScheduleHandlers(
            onTrainingAgeSelected = viewModel::onTrainingAgeSelected,
            onLastTrainedSelected = viewModel::onLastTrainedSelected,
            onTrainingDayCountChanged = viewModel::onTrainingDayCountChanged,
            onSessionPresetSelected = viewModel::onSessionPresetSelected,
            onCustomSessionMinutesChange = viewModel::onCustomSessionMinutesChange,
            onSixDayCycleLengthSelected = viewModel::onSixDayCycleLengthSelected,
            onTrainingSlotToggled = viewModel::onTrainingSlotToggled,
        ),
        onMuscleToggled = viewModel::onMuscleToggled,
        minimumVolume = MinimumVolumeHandlers(
            onMuscleToggled = viewModel::onMinimumVolumeMuscleToggled,
            onSuggestedMuscleToggled = viewModel::onSuggestedMinimumVolumeToggled,
        ),
        gymLifts = GymLiftsHandlers(
            onPresetSelected = viewModel::onPresetSelected,
        ),
        onImportClick = {
            if (state.gymLiftsDraft.importResumable) {
                viewModel.onRetryImportFromFailedFile()
            } else {
                importLauncher.launch(HISTORICAL_IMPORT_MIME_TYPES)
            }
        },
        onImportCancel = viewModel::onCancelImport,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@Composable
private fun OnboardingWizard(
    state: OnboardingUiState,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onGenerate: () -> Unit,
    onImportExisting: () -> Unit,
    pathHandlers: ChoosePathHandlers,
    profileSchedule: ProfileScheduleHandlers,
    onMuscleToggled: (String) -> Unit,
    minimumVolume: MinimumVolumeHandlers,
    gymLifts: GymLiftsHandlers,
    onImportClick: () -> Unit,
    onImportCancel: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
) {
    StratumScaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHostState = snackbarHostState,
        topBar = {
            StratumTopBar(
                title = state.step.displayName,
                navigationIcon = if (state.canGoBack) {
                    {
                        StratumTopBarBackButton(
                            onClick = onBack,
                            contentDescription = stringResource(R.string.onboarding_content_description_back),
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
                    WIZARD_STEP_PROFILE -> ProfileScreen(
                        draft = state.profileDraft,
                        onTrainingAgeSelected = profileSchedule.onTrainingAgeSelected,
                        onLastTrainedSelected = profileSchedule.onLastTrainedSelected,
                    )
                    WIZARD_STEP_SCHEDULE -> ScheduleScreen(
                        draft = state.scheduleDraft,
                        onTrainingDayCountChanged = profileSchedule.onTrainingDayCountChanged,
                        onSessionPresetSelected = profileSchedule.onSessionPresetSelected,
                        onCustomSessionMinutesChange = profileSchedule.onCustomSessionMinutesChange,
                        onSixDayCycleLengthSelected = profileSchedule.onSixDayCycleLengthSelected,
                        onTrainingSlotToggled = profileSchedule.onTrainingSlotToggled,
                    )
                    WIZARD_STEP_SPECIALIZATION -> SpecializationScreen(
                        draft = state.specializationDraft,
                        onMuscleToggled = onMuscleToggled,
                    )
                    WIZARD_STEP_MINIMUM_VOLUME -> MinimumVolumeScreen(
                        draft = state.minimumVolumeDraft,
                        onMuscleToggled = minimumVolume.onMuscleToggled,
                        onSuggestedMuscleToggled = minimumVolume.onSuggestedMuscleToggled,
                    )
                    WIZARD_STEP_GYM_AND_LIFTS -> GymAndLiftOnboardingSetupScreen(
                        draft = state.gymLiftsDraft,
                        presetOptions = state.presetOptions,
                        onPresetSelected = gymLifts.onPresetSelected,
                        onImportClick = onImportClick,
                        onImportCancel = onImportCancel,
                    )
                    WIZARD_STEP_REVIEW -> ReviewScreen(
                        draft = state.reviewDraft,
                        path = state.onboardingPath ?: OnboardingPathUi.Import,
                        onGenerate = onGenerate,
                        onImportExisting = onImportExisting,
                        generateState = state.generateState,
                        generateProgress = state.generateProgress,
                        generateMaxTimelineIndex = state.generateMaxTimelineIndex,
                        generateErrorMessage = state.generateErrorMessage,
                        importProgramState = state.importProgramState,
                        importMaxTimelineIndex = state.importMaxTimelineIndex,
                    )
                    WIZARD_STEP_CHOOSE_PATH -> ChoosePathScreen(
                        onSelectGenerate = pathHandlers.onSelectGenerate,
                        onSelectScratch = pathHandlers.onSelectScratch,
                        onSelectImport = pathHandlers.onSelectImport,
                    )
                }
            }
            val stepDrivesOwnAction = state.isLast || state.step.id == WIZARD_STEP_CHOOSE_PATH
            val actionInProgress =
                state.generateState == GenerateState.Loading ||
                    state.importProgramState is ImportProgramState.Loading
            if (!stepDrivesOwnAction && !actionInProgress) {
                WizardControls(
                    canAdvance = state.canAdvance,
                    onNext = onNext,
                )
            }
        }
    }
}

@Composable
private fun Stepper(steps: List<WizardStepUi>, currentStepId: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
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
private fun WizardControls(
    canAdvance: Boolean,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canAdvance) {
            StratumButton(
                text = stringResource(R.string.onboarding_button_next),
                onClick = onNext,
            )
        }
    }
}

private const val WIZARD_STEP_PROFILE = "Profile"
private const val WIZARD_STEP_SCHEDULE = "Schedule"
private const val WIZARD_STEP_SPECIALIZATION = "Specialization"
private const val WIZARD_STEP_MINIMUM_VOLUME = "MinimumVolume"
private const val WIZARD_STEP_GYM_AND_LIFTS = "GymAndLifts"
private const val WIZARD_STEP_REVIEW = "Review"
private const val WIZARD_STEP_CHOOSE_PATH = "ChoosePath"

/**
 * MIME types accepted by the Gym & Lifts historical-import picker (CSV/JSON/text + XLSX).
 * The backend deterministic parsers accept MacroFactor history as either `.xlsx` (default
 * in-app export) or `.csv`; without the spreadsheet MIME types, SAF greys out Excel files.
 * Providers that mis-report `.xlsx` as `application/octet-stream` will still grey those out —
 * download locally first.
 */
private val HISTORICAL_IMPORT_MIME_TYPES =
    com.stratum.core.ui.importing.ImportMimeTypes.HISTORICAL

/**
 * MIME types accepted by the program-template import picker (CSV + XLSX + JSON). Shared with
 * Settings via [com.stratum.core.ui.importing.ImportMimeTypes].
 */
private val PROGRAM_IMPORT_MIME_TYPES =
    com.stratum.core.ui.importing.ImportMimeTypes.PROGRAM
