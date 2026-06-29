package com.stratum.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.stratum.DashboardPlaceholder
import com.stratum.GymPlaceholder
import com.stratum.R
import com.stratum.core.ui.component.StratumNavigationBar
import com.stratum.core.ui.component.StratumNavigationBarItem
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.feature.program.ProgramsRoute

private enum class MainTab(val index: Int) {
    Dashboard(0),
    Program(1),
    Gym(2),
    ;

    companion object {
        fun fromIndex(index: Int): MainTab = entries.first { tab -> tab.index == index }
    }
}

@Composable
fun MainTabShell(modifier: Modifier = Modifier) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(MainTab.Program.index) }
    val items =
        listOf(
            StratumNavigationBarItem(
                label = stringResource(R.string.tab_dashboard),
                icon = Icons.Outlined.Dashboard,
                selectedIcon = Icons.Filled.Dashboard,
            ),
            StratumNavigationBarItem(
                label = stringResource(R.string.tab_program),
                icon = Icons.Outlined.ListAlt,
                selectedIcon = Icons.Filled.ListAlt,
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
            StratumNavigationBar(
                items = items,
                selectedIndex = selectedTabIndex,
                onItemSelected = { index -> selectedTabIndex = index },
            )
        },
    ) { padding ->
        // Tab screens own their top bar + status-bar inset; shell only reserves bottom nav space.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(PaddingValues(bottom = padding.calculateBottomPadding())),
        ) {
            when (MainTab.fromIndex(selectedTabIndex)) {
                MainTab.Dashboard -> DashboardPlaceholder()
                MainTab.Program -> ProgramsRoute()
                MainTab.Gym -> GymPlaceholder()
            }
        }
    }
}
