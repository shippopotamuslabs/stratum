package com.stratum.feature.program

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.feature.program.ui.CreateProgramScreen
import com.stratum.feature.program.ui.CreateProgramScreenCallbacks

@Composable
fun CreateProgramRoute(
    onBackClick: () -> Unit,
    onFinished: () -> Unit,
    onSwapLiftClick: (workoutId: String, workoutLiftId: String?, liftId: String?, liftName: String?) -> Unit,
    onReorderLiftsClick: (workoutId: String) -> Unit,
    modifier: Modifier = Modifier,
    startInScratch: Boolean = false,
    finishOnboarding: Boolean = false,
    programId: String? = null,
    viewModel: CreateProgramViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(startInScratch, finishOnboarding, programId) {
        viewModel.configureEntry(
            startInScratch = startInScratch,
            finishOnboarding = finishOnboarding,
            programId = programId,
        )
    }

    BackHandler {
        when (state.step) {
            CreateProgramStep.ChoosePath -> onBackClick()
            CreateProgramStep.Editor -> handleEditorBack(
                state = state,
                startInScratch = startInScratch,
                onBackClick = onBackClick,
                requestDiscard = viewModel::requestDiscard,
            )
            CreateProgramStep.Generating -> Unit
            else -> viewModel.back()
        }
    }

    CreateProgramScreen(
        state = state,
        callbacks =
            CreateProgramScreenCallbacks(
                onBack = {
                    when (state.step) {
                        CreateProgramStep.ChoosePath -> onBackClick()
                        CreateProgramStep.Editor -> handleEditorBack(
                            state = state,
                            startInScratch = startInScratch,
                            onBackClick = onBackClick,
                            requestDiscard = viewModel::requestDiscard,
                        )
                        else -> viewModel.back()
                    }
                },
                onSelectGenerate = viewModel::selectGeneratePath,
                onSelectScratch = viewModel::selectScratchPath,
                onMuscleToggled = viewModel::onMuscleToggled,
                onMinimumVolumeMuscleToggled = viewModel::onMinimumVolumeMuscleToggled,
                onSuggestedMinimumVolumeToggled = viewModel::onSuggestedMinimumVolumeToggled,
                onWeekdayToggled = viewModel::onWeekdayToggled,
                onSessionPresetSelected = viewModel::onSessionPresetSelected,
                onCustomSessionMinutesChange = viewModel::onCustomSessionMinutesChange,
                onNext = viewModel::nextFromPreferences,
                onGenerate = viewModel::generate,
                onSelectDay = viewModel::selectDay,
                onAddDay = viewModel::addDay,
                onReorderDays = viewModel::reorderDays,
                onRemoveDay = viewModel::removeSelectedDay,
                onShowRenameProgram = { viewModel.showRenameProgram(true) },
                onShowRenameWorkout = { viewModel.showRenameWorkout(true) },
                onDismissRenameProgram = { viewModel.showRenameProgram(false) },
                onDismissRenameWorkout = { viewModel.showRenameWorkout(false) },
                onRenameProgram = viewModel::renameProgram,
                onRenameWorkout = viewModel::renameSelectedWorkout,
                onAddExercise = { viewModel.addExerciseToSelectedDay(onSwapLiftClick) },
                onUpdateSelectedWorkout = viewModel::updateSelectedWorkout,
                onReorderLifts = { viewModel.startReorderLifts(onReorderLiftsClick) },
                onSwapLift = onSwapLiftClick,
                onSaveToLibrary = { viewModel.saveToLibrary(onFinished) },
                onSaveChanges = { viewModel.saveChanges(onFinished) },
                onActivate = { viewModel.activate(onFinished) },
                onConfirmDiscard = {
                    viewModel.dismissDiscard()
                    onBackClick()
                },
                onDismissDiscard = viewModel::dismissDiscard,
            ),
        modifier = modifier.fillMaxSize(),
    )
}

/**
 * Editor back: empty onboarding scratch pops immediately; clean library edit pops;
 * dirty drafts/edits show discard confirmation.
 */
private fun handleEditorBack(
    state: CreateProgramUiState,
    startInScratch: Boolean,
    onBackClick: () -> Unit,
    requestDiscard: () -> Boolean,
) {
    when {
        // Onboarding scratch with nothing saveable — leave without a sheet.
        startInScratch && !(state.isDirty && state.canSave) -> onBackClick()
        // requestDiscard returns true when there is nothing to discard (e.g. clean library edit).
        requestDiscard() -> onBackClick()
    }
}
