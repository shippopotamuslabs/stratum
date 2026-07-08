package com.stratum

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.TrendPointUi
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeAccessor

private const val PREVIEW_VOLUME_GHOST_ALPHA = 0.5f

@Preview(name = "Dashboard light", showBackground = true)
@Composable
private fun DashboardScreenLightPreview() {
    StratumTheme {
        DashboardScreenPreviewContent()
    }
}

@Preview(name = "Dashboard dark", showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun DashboardScreenDarkPreview() {
    StratumTheme(darkTheme = true) {
        DashboardScreenPreviewContent()
    }
}

@Preview(name = "Dashboard legend — muscles", showBackground = true)
@Composable
private fun DashboardLegendMusclesLightPreview() {
    LegendPreviewContent(isMuscles = true)
}

@Preview(name = "Dashboard legend — lifts", showBackground = true)
@Composable
private fun DashboardLegendLiftsLightPreview() {
    LegendPreviewContent(isMuscles = false)
}

@Preview(name = "Dashboard legend — muscles dark", showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun DashboardLegendMusclesDarkPreview() {
    LegendPreviewContent(isMuscles = true, dark = true)
}

@Preview(name = "Dashboard legend — lifts dark", showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun DashboardLegendLiftsDarkPreview() {
    LegendPreviewContent(isMuscles = false, dark = true)
}

/**
 * Renders [DashboardLegend] for a single tab. The legend is app-local and tab-aware (primary label
 * + swatch color differ per tab), so these previews pin both configurations against light/dark.
 */
@Composable
private fun LegendPreviewContent(isMuscles: Boolean, dark: Boolean = false) {
    StratumTheme(darkTheme = dark) {
        val colors = StratumThemeAccessor.colors
        Box(modifier = Modifier.padding(16.dp)) {
            DashboardLegend(
                primaryLabel = if (isMuscles) "Progress" else "Strength",
                primaryColor = if (isMuscles) colors.setComplete else colors.textData,
                secondaryLabel = "Volume",
                secondaryColor = if (isMuscles) {
                    colors.textMuted
                } else {
                    colors.textMuted.copy(alpha = PREVIEW_VOLUME_GHOST_ALPHA)
                },
            )
        }
    }
}

@Preview(name = "Dashboard empty light", showBackground = true)
@Composable
private fun DashboardScreenEmptyPreview() {
    StratumTheme {
        DashboardScreen(
            state = DashboardUiState.Empty,
            onNavigateToSettings = {},
        )
    }
}

@Preview(name = "Dashboard loading light", showBackground = true)
@Composable
private fun DashboardScreenLoadingPreview() {
    StratumTheme {
        DashboardScreen(
            state = DashboardUiState.Loading,
            onNavigateToSettings = {},
        )
    }
}

@Composable
private fun DashboardScreenPreviewContent() {
    DashboardScreen(
        state = DashboardUiState.Loaded(
            windowWeeks = 13,
            muscles = listOf(
                MuscleSeriesUi(
                    name = "Back",
                    headlineVolume = "10 sets/wk",
                    badge = TrendBadge.Down,
                    progressPoints = descendingSeries(),
                    volumePoints = descendingSeries(),
                    contributingLifts = 3,
                ),
                MuscleSeriesUi(
                    name = "Chest",
                    headlineVolume = "14 sets/wk",
                    badge = TrendBadge.Up,
                    progressPoints = ascendingSeries(),
                    volumePoints = ascendingSeries(),
                    contributingLifts = 2,
                ),
                MuscleSeriesUi(
                    name = "Quad",
                    headlineVolume = "8 sets/wk",
                    badge = TrendBadge.Flat,
                    progressPoints = flatSeries(),
                    volumePoints = flatSeries(),
                    contributingLifts = 2,
                ),
                MuscleSeriesUi(
                    name = "Bicep",
                    headlineVolume = "6 sets/wk",
                    badge = TrendBadge.Unknown,
                    progressPoints = emptyList(),
                    volumePoints = ascendingSeries(),
                    contributingLifts = 1,
                ),
            ),
            lifts = listOf(
                LiftSeriesUi(
                    name = "Barbell Row",
                    e1rmText = "185 lb",
                    badge = TrendBadge.Down,
                    performancePoints = descendingSeries(),
                    volumePoints = flatSeries(),
                    tenureText = "12 wks",
                    sessions = 18,
                ),
                LiftSeriesUi(
                    name = "Bench Press",
                    e1rmText = "225 lb",
                    badge = TrendBadge.Up,
                    performancePoints = ascendingSeries(),
                    volumePoints = ascendingSeries(),
                    tenureText = "16 wks",
                    sessions = 22,
                ),
                LiftSeriesUi(
                    name = "Hack Squat",
                    e1rmText = "315 lb",
                    badge = TrendBadge.Flat,
                    performancePoints = flatSeries(),
                    volumePoints = flatSeries(),
                    tenureText = "8 wks",
                    sessions = 11,
                ),
            ),
        ),
        onNavigateToSettings = {},
    )
}

private fun ascendingSeries(): List<TrendPointUi> =
    listOf(
        TrendPointUi(0, 100f), TrendPointUi(1, 103f), TrendPointUi(2, 105f),
        TrendPointUi(3, 108f), TrendPointUi(4, 112f),
    )

private fun descendingSeries(): List<TrendPointUi> =
    listOf(
        TrendPointUi(0, 112f), TrendPointUi(1, 110f), TrendPointUi(2, 106f),
        TrendPointUi(3, 103f), TrendPointUi(4, 100f),
    )

private fun flatSeries(): List<TrendPointUi> =
    listOf(
        TrendPointUi(0, 105f), TrendPointUi(1, 104f), TrendPointUi(2, 106f),
        TrendPointUi(3, 105f), TrendPointUi(4, 105f),
    )
