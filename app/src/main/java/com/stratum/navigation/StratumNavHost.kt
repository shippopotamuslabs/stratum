package com.stratum.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.feature.onboarding.navigation.OnboardingKey
import com.stratum.feature.onboarding.navigation.onboardingEntry
import com.stratum.feature.program.navigation.EditWorkoutKey
import com.stratum.feature.program.navigation.editWorkoutEntry
import com.stratum.feature.program.navigation.LiftSwapKey
import com.stratum.feature.program.navigation.liftSwapEntry

@Composable
fun StratumNavHost() {
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

    NavDisplay(
        backStack = backStack,
        onBack = onNavigateBack,
        entryProvider = entryProvider {
            onboardingEntry(
                onComplete = {
                    backStack.removeAll { it is OnboardingKey }
                    backStack.add(MainTabKey(isPreviewMode = true))
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
                    onNavigateToSwap = { workoutLiftId, id, name ->
                        backStack.add(LiftSwapKey(workoutLiftId, id, name))
                    },
                    onNavigateToEditWorkout = { workoutId ->
                        backStack.add(EditWorkoutKey(workoutId))
                    },
                    onCreateNewLift = {
                        // Navigate to custom lift screen in future slice
                    },
                )
            }
            liftSwapEntry(
                onBackClick = onNavigateBack,
                onCreateNewLift = {
                    // Navigate to custom lift screen in future slice
                },
            )
            editWorkoutEntry(
                onBackClick = onNavigateBack,
                onSwapLiftClick = { workoutLiftId, liftId, liftName ->
                    backStack.add(LiftSwapKey(workoutLiftId, liftId, liftName))
                },
            )
        },
    )
}
