package com.stratum.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.DashboardScreen
import com.stratum.GymScreen
import com.stratum.R
import com.stratum.core.ui.component.StratumNavigationBar
import com.stratum.core.ui.component.StratumNavigationBarItem
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.program.ProgramsRoute
import com.stratum.feature.program.ui.ProgramsScreenCallbacks
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

internal enum class MainTab(val index: Int) {
    Dashboard(0),
    Program(1),
    Gym(2),
    ;

    companion object {
        fun fromIndex(index: Int): MainTab =
            entries.firstOrNull { tab -> tab.index == index } ?: Program
    }
}

/**
 * Bundles main-tab navigation callbacks to keep [MainTabShell] under the detekt parameter threshold.
 */
data class MainTabShellCallbacks(
    val onDiscardProgramClick: () -> Unit = {},
    val onKeepProgramClick: () -> Unit = {},
    val onNavigateToSwap: (workoutId: String, workoutLiftId: String?, liftId: String?, liftName: String?) -> Unit =
        { _, _, _, _ -> },
    val onNavigateToEditWorkout: (workoutId: String) -> Unit = {},
    val onNavigateToWorkout: (workoutId: String) -> Unit = {},
    val onNavigateToSettings: () -> Unit = {},
    val onNavigateToHistory: () -> Unit = {},
    val onCreateNewLift: () -> Unit = {},
    val onManageEquipment: () -> Unit = {},
    val onNavigateToEquipmentIncrements: (liftId: String, equipmentId: String?) -> Unit = { _, _ -> },
    val onNavigateToLiftInformation: (liftId: String) -> Unit = {},
    val onCreateProgramClick: () -> Unit = {},
    val onEditProgramClick: (programId: String) -> Unit = {},
    val onReorderWorkoutsClick: (programId: String) -> Unit = {},
    val onProgramLibraryClick: () -> Unit = {},
)

@Composable
fun MainTabShell(
    modifier: Modifier = Modifier,
    isPreviewMode: Boolean = false,
    callbacks: MainTabShellCallbacks = MainTabShellCallbacks(),
    onScreenChanged: (String) -> Unit = {},
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(MainTab.Program.index) }
    var hideBottomBar by remember { mutableStateOf(false) }

    // Glass Bars (§12.8): one HazeState shared by the shell's bars and the tab
    // screens' top bars, blurring this shell's content. Preview mode renders the
    // pre-glass layout exactly, so screenshot references stay stable there.
    val hazeState = rememberHazeState()
    val glass = StratumThemeAccessor.haze.enabled && !isPreviewMode
    val screenHazeState = if (glass) hazeState else null

    val currentTab = remember(selectedTabIndex) { MainTab.fromIndex(selectedTabIndex) }
    LaunchedEffect(currentTab) {
        onScreenChanged("MainTab_${currentTab.name}")
    }

    val items =
        listOf(
            StratumNavigationBarItem(
                label = stringResource(R.string.tab_dashboard),
                icon = Icons.Outlined.Dashboard,
                selectedIcon = Icons.Filled.Dashboard,
            ),
            StratumNavigationBarItem(
                label = stringResource(R.string.tab_program),
                icon = Icons.AutoMirrored.Outlined.ListAlt,
                selectedIcon = Icons.AutoMirrored.Filled.ListAlt,
            ),
            StratumNavigationBarItem(
                label = stringResource(R.string.tab_gym),
                icon = Icons.Outlined.FitnessCenter,
                selectedIcon = Icons.Filled.FitnessCenter,
            ),
        )

    StratumScaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (!isPreviewMode && !hideBottomBar) {
                StratumNavigationBar(
                    items = items,
                    selectedIndex = selectedTabIndex,
                    onItemSelected = { index -> selectedTabIndex = index },
                    hazeState = screenHazeState,
                )
            }
        },
    ) { padding ->
        // Tab screens own their top bar + status-bar inset. Under glass (§12.8) the
        // shell reserves NO bottom space: tab content is the hazeSource and scrolls
        // beneath the always-glass nav bar; screens pad their own ends instead.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(if (glass) Modifier.hazeSource(state = hazeState) else Modifier)
                    .padding(
                        PaddingValues(
                            bottom = if (glass) 0.dp else padding.calculateBottomPadding(),
                        ),
                    ),
        ) {
            when (MainTab.fromIndex(selectedTabIndex)) {
                MainTab.Dashboard ->
                    DashboardScreen(
                        onNavigateToSettings = callbacks.onNavigateToSettings,
                        hazeState = screenHazeState,
                    )
                MainTab.Program ->
                    ProgramsRoute(
                        isPreviewMode = isPreviewMode,
                        hazeState = screenHazeState,
                        callbacks =
                            ProgramsScreenCallbacks(
                                onCalendarClick = callbacks.onNavigateToHistory,
                                onKeepProgramClick = callbacks.onKeepProgramClick,
                                onDiscardProgramClick = callbacks.onDiscardProgramClick,
                                onEditWorkoutClick = callbacks.onNavigateToEditWorkout,
                                onCreateProgramClick = callbacks.onCreateProgramClick,
                                onEditProgramClick = callbacks.onEditProgramClick,
                                onReorderWorkoutsClick = callbacks.onReorderWorkoutsClick,
                                onProgramLibraryClick = callbacks.onProgramLibraryClick,
                            ),
                        onSwapLiftClick = callbacks.onNavigateToSwap,
                        onNavigateToWorkout = callbacks.onNavigateToWorkout,
                    )
                MainTab.Gym ->
                    GymScreen(
                        hazeState = screenHazeState,
                        onCreateNewLift = callbacks.onCreateNewLift,
                        onManageEquipment = callbacks.onManageEquipment,
                        onNavigateToIncrements = callbacks.onNavigateToEquipmentIncrements,
                        onNavigateToInformation = callbacks.onNavigateToLiftInformation,
                        onMergeModeChange = { active -> hideBottomBar = active },
                    )
            }
        }
    }
}
