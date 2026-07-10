package com.stratum.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.feature.onboarding.navigation.OnboardingKey
import com.stratum.feature.onboarding.navigation.onboardingEntry
import com.stratum.feature.program.navigation.CreateProgramKey
import com.stratum.feature.program.navigation.createProgramEntry
import com.stratum.feature.program.navigation.EditWorkoutKey
import com.stratum.feature.program.navigation.editWorkoutEntry
import com.stratum.feature.program.navigation.LiftSwapKey
import com.stratum.feature.program.navigation.liftSwapEntry
import com.stratum.feature.program.navigation.ProgramLibraryKey
import com.stratum.feature.program.navigation.programLibraryEntry
import com.stratum.feature.program.navigation.ReorderLiftsKey
import com.stratum.feature.program.navigation.reorderLiftsEntry
import com.stratum.feature.history.navigation.EditHistoricalWorkoutKey
import com.stratum.feature.history.navigation.WorkoutHistoryKey
import com.stratum.feature.history.navigation.WorkoutSessionDetailKey
import com.stratum.feature.history.navigation.editHistoricalWorkoutEntry
import com.stratum.feature.history.navigation.workoutHistoryEntry
import com.stratum.feature.history.navigation.workoutSessionDetailEntry
import com.stratum.feature.settings.navigation.SettingsKey
import com.stratum.feature.settings.navigation.settingsEntry
import com.stratum.feature.workout.navigation.WorkoutKey
import com.stratum.feature.workout.navigation.WorkoutCompleteKey
import com.stratum.feature.workout.navigation.workoutEntry
import com.stratum.feature.workout.navigation.workoutCompleteEntry
import com.stratum.liftconfig.CreateLiftKey
import com.stratum.liftconfig.LiftIncrementsKey
import com.stratum.liftconfig.LiftInformationKey
import com.stratum.liftconfig.createLiftEntry
import com.stratum.liftconfig.liftIncrementsEntry
import com.stratum.liftconfig.liftInformationEntry

@Composable
fun StratumNavHost(
    pendingWorkoutId: String? = null,
    onPendingWorkoutConsumed: () -> Unit = {},
) {
    val statusViewModel: OnboardingStatusViewModel = hiltViewModel()
    val isComplete by statusViewModel.isComplete.collectAsState()
    if (isComplete == null) {
        StratumScaffold { padding ->
            Box(Modifier.fillMaxSize().padding(padding))
        }
        return
    }
    val startKey: NavKey = remember(isComplete) {
        if (isComplete == true) MainTabKey(isPreviewMode = false) else OnboardingKey
    }
    val backStack = rememberNavBackStack(startKey)
    val onNavigateBack =
        remember(backStack) {
            {
                if (backStack.size > 1) {
                    backStack.removeLast()
                }
            }
        }

    LaunchedEffect(pendingWorkoutId, isComplete) {
        val workoutId = pendingWorkoutId ?: return@LaunchedEffect
        if (isComplete != true) return@LaunchedEffect
        val top = backStack.lastOrNull()
        if (top is WorkoutKey && top.workoutId == workoutId) {
            onPendingWorkoutConsumed()
            return@LaunchedEffect
        }
        // Avoid stacking duplicate workout routes for the same session.
        backStack.removeAll { it is WorkoutKey && it.workoutId == workoutId }
        backStack.add(WorkoutKey(workoutId))
        onPendingWorkoutConsumed()
    }

    NavDisplay(
        backStack = backStack,
        onBack = onNavigateBack,
        // Scope ViewModels (and saveable state) to each NavEntry. Without the ViewModelStore
        // decorator, hiltViewModel() resolves to the Activity and CreateProgram retains
        // showDiscardConfirm / entryConfigured across visits — discard sheet on library edit open.
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider = entryProvider {
            onboardingEntry(
                onComplete = {
                    backStack.removeAll { it is OnboardingKey || it is CreateProgramKey }
                    if (backStack.none { it is MainTabKey }) {
                        backStack.add(MainTabKey(isPreviewMode = true))
                    }
                },
                onBuildFromScratch = {
                    backStack.add(
                        CreateProgramKey(
                            startInScratch = true,
                            finishOnboarding = true,
                        ),
                    )
                },
            )
            entry<MainTabKey> { key ->
                MainTabShell(
                    isPreviewMode = key.isPreviewMode,
                    onDiscardProgramClick = {
                        statusViewModel.reopenOnboarding()
                        backStack.removeAll { it is MainTabKey }
                        backStack.add(OnboardingKey)
                    },
                    onKeepProgramClick = {
                        backStack.removeAll { it is MainTabKey }
                        backStack.add(MainTabKey(isPreviewMode = false))
                    },
                    onNavigateToSwap = { workoutId, workoutLiftId, id, name ->
                        backStack.add(LiftSwapKey(workoutId, workoutLiftId, id, name))
                    },
                    onNavigateToEditWorkout = { workoutId ->
                        backStack.add(EditWorkoutKey(workoutId))
                    },
                    onNavigateToWorkout = { workoutId ->
                        backStack.add(WorkoutKey(workoutId))
                    },
                    onNavigateToSettings = {
                        backStack.add(SettingsKey)
                    },
                    onNavigateToHistory = {
                        backStack.add(WorkoutHistoryKey)
                    },
                    onCreateNewLift = {
                        backStack.add(CreateLiftKey)
                    },
                    onNavigateToLiftIncrements = { liftId ->
                        backStack.add(LiftIncrementsKey(liftId))
                    },
                    onNavigateToLiftInformation = { liftId ->
                        backStack.add(LiftInformationKey(liftId))
                    },
                    onCreateProgramClick = {
                        backStack.add(CreateProgramKey())
                    },
                    onProgramLibraryClick = {
                        backStack.add(ProgramLibraryKey)
                    },
                )
            }
            liftIncrementsEntry(onBackClick = onNavigateBack)
            liftInformationEntry(onBackClick = onNavigateBack)
            createLiftEntry(onBackClick = onNavigateBack)
            settingsEntry(
                onBackClick = onNavigateBack,
            )
            workoutHistoryEntry(
                onBackClick = onNavigateBack,
                onSessionClick = { sessionId ->
                    backStack.add(WorkoutSessionDetailKey(sessionId))
                },
            )
            workoutSessionDetailEntry(
                onBackClick = onNavigateBack,
                onEditWorkoutClick = { sessionId ->
                    backStack.add(EditHistoricalWorkoutKey(sessionId))
                },
            )
            editHistoricalWorkoutEntry(
                onBackClick = onNavigateBack,
            )
            programLibraryEntry(
                onBackClick = onNavigateBack,
                onProgramClick = { programId ->
                    backStack.add(CreateProgramKey(programId = programId))
                },
                onWorkoutClick = { programId, workoutId ->
                    backStack.add(EditWorkoutKey(workoutId = workoutId, programId = programId))
                },
            )
            createProgramEntry(
                onBackClick = onNavigateBack,
                onFinished = {
                    // Onboarding scratch: CompleteOnboarding already ran in the VM; land on
                    // preview main tabs (same as generate). Guard against double-push if
                    // OnboardingRoute.onComplete also fires when isComplete flips.
                    val key = backStack.lastOrNull() as? CreateProgramKey
                    if (key?.finishOnboarding == true) {
                        backStack.removeAll { it is OnboardingKey || it is CreateProgramKey }
                        if (backStack.none { it is MainTabKey }) {
                            backStack.add(MainTabKey(isPreviewMode = true))
                        }
                    } else {
                        onNavigateBack()
                    }
                },
                onSwapLiftClick = { workoutId, workoutLiftId, liftId, liftName ->
                    backStack.add(LiftSwapKey(workoutId, workoutLiftId, liftId, liftName))
                },
                onReorderLiftsClick = { workoutId ->
                    backStack.add(ReorderLiftsKey(workoutId))
                },
            )
            liftSwapEntry(
                onBackClick = onNavigateBack,
                onCreateNewLift = {
                    backStack.add(CreateLiftKey)
                },
            )
            editWorkoutEntry(
                onBackClick = onNavigateBack,
                onSwapLiftClick = { workoutId, workoutLiftId, liftId, liftName ->
                    backStack.add(LiftSwapKey(workoutId, workoutLiftId, liftId, liftName))
                },
                onReorderLiftsClick = { workoutId ->
                    backStack.add(ReorderLiftsKey(workoutId))
                },
            )
            workoutEntry(
                onNavigateBack = onNavigateBack,
                onNavigateToFinalize = { sessionId ->
                    backStack.removeAll { it is WorkoutKey }
                    backStack.add(WorkoutCompleteKey(sessionId))
                },
                onSwapLiftClick = { workoutId, workoutLiftId, liftId, liftName ->
                    backStack.add(LiftSwapKey(workoutId, workoutLiftId, liftId, liftName))
                },
                onReorderLiftsClick = { workoutId ->
                    backStack.add(ReorderLiftsKey(workoutId))
                },
            )
            workoutCompleteEntry(
                onCloseClick = onNavigateBack,
            )
            reorderLiftsEntry(
                onBackClick = onNavigateBack,
            )
        },
    )
}
