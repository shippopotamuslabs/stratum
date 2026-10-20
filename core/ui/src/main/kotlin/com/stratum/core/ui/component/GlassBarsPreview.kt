package com.stratum.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * Preview / golden content for the Glass Bars shell (§12.8, STR-153): a tab screen's
 * worth of content with the top bar and bottom nav bar overlaid, mirroring how
 * `MainTabShell` hosts them. With [glass] the content is the shared state's
 * `hazeSource` and both bars take their glass treatments (top bar in its scrolled
 * state); without it both bars render their pre-glass solid treatments — the pair
 * is the before/after record for the migration.
 */
@Composable
fun GlassBarsPreviewContent(glass: Boolean) {
    val colors = StratumThemeAccessor.colors
    val typography = StratumThemeAccessor.typography
    val hazeState = rememberHazeState()
    val barState = if (glass) hazeState else null

    val cardColors =
        listOf(
            colors.accent,
            colors.surfaceRaised,
            colors.surfaceInset,
            colors.surfaceRaised,
            colors.accentMuted,
            colors.surfaceRaised,
            colors.surfaceInset,
            colors.surfaceRaised,
        )

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colors.surfaceBase),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(if (glass) Modifier.hazeSource(state = hazeState) else Modifier)
                    .padding(horizontal = StratumSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
        ) {
            cardColors.forEachIndexed { index, cardColor ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(StratumSpacing.s12))
                            .background(cardColor)
                            .padding(StratumSpacing.s16),
                ) {
                    Text(
                        text = "Content card ${index + 1}",
                        style = typography.titleMedium,
                        color = colors.textPrimary,
                    )
                }
            }
        }

        StratumTopBar(
            title = "Programs",
            hazeState = barState,
            scrolled = glass,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        StratumNavigationBar(
            items =
                listOf(
                    StratumNavigationBarItem(
                        label = "Dashboard",
                        icon = Icons.Outlined.Dashboard,
                        selectedIcon = Icons.Filled.Dashboard,
                    ),
                    StratumNavigationBarItem(
                        label = "Program",
                        icon = Icons.AutoMirrored.Outlined.ListAlt,
                        selectedIcon = Icons.AutoMirrored.Filled.ListAlt,
                    ),
                    StratumNavigationBarItem(
                        label = "Gym",
                        icon = Icons.Outlined.FitnessCenter,
                        selectedIcon = Icons.Filled.FitnessCenter,
                    ),
                ),
            selectedIndex = 1,
            onItemSelected = {},
            hazeState = barState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
