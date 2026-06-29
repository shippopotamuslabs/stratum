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
        if (isComplete == true) MainTabKey else OnboardingKey
    }
    val backStack = rememberNavBackStack(startKey)

    NavDisplay(
        backStack = backStack,
        entryProvider = entryProvider {
            onboardingEntry(
                onComplete = {
                    backStack.removeAll { it is OnboardingKey }
                    backStack.add(MainTabKey)
                },
            )
            entry<MainTabKey> {
                MainTabShell()
            }
        },
    )
}
