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
                primaryStyle = if (isMuscles) LegendSwatchStyle.Bar else LegendSwatchStyle.Line,
                secondaryLabel = "Volume",
                secondaryColor = colors.textMuted.copy(alpha = PREVIEW_VOLUME_GHOST_ALPHA),
                caption = if (isMuscles) {
                    "Bars are progress vs early baseline (0)"
                } else {
                    "Session counts · last 13 weeks"
                },
                showStatusLegend = true,
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

@Preview(name = "Dashboard health pager light", showBackground = true)
@Composable
private fun DashboardScreenHealthLightPreview() {
    StratumTheme {
        DashboardScreenPreviewContent(health = previewHealthState())
    }
}

@Preview(name = "Dashboard health pager dark", showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun DashboardScreenHealthDarkPreview() {
    StratumTheme(darkTheme = true) {
        DashboardScreenPreviewContent(health = previewHealthState())
    }
}

@Preview(name = "Dashboard health weight page", showBackground = true)
@Composable
private fun DashboardScreenHealthWeightPreview() {
    StratumTheme {
        DashboardScreenPreviewContent(health = previewHealthState(selectedPage = 1))
    }
}

@Preview(name = "Dashboard health recovery page", showBackground = true)
@Composable
private fun DashboardScreenHealthRecoveryPreview() {
    StratumTheme {
        DashboardScreenPreviewContent(health = previewHealthState(selectedPage = 2))
    }
}

@Preview(name = "Dashboard health empty pages", showBackground = true)
@Composable
private fun DashboardScreenHealthEmptyPreview() {
    StratumTheme {
        DashboardScreen(
            state = DashboardUiState.Empty,
            health = DashboardHealthUiState(visible = true),
            onNavigateToSettings = {},
        )
    }
}

@Composable
private fun DashboardScreenPreviewContent(
    health: DashboardHealthUiState = DashboardHealthUiState(),
) {
    DashboardScreen(
        state = DashboardUiState.Loaded(
            windowWeeks = 13,
            // Ordered by sets/wk descending (matches LoadDashboardUseCase).
            // Progress points are log-index vs baseline (about zero), not e1RM-scale values.
            muscles = listOf(
                MuscleSeriesUi(
                    name = "Chest",
                    muscleKey = "Chest",
                    headlineVolume = "14 sets/wk",
                    badge = TrendBadge.Up,
                    progressPoints = risingIndexSeries(),
                    volumePoints = risingVolumeSeries(),
                    contributingLifts = 2,
                    highlight = CalloutDirectionUi.Progressing,
                ),
                MuscleSeriesUi(
                    name = "Back",
                    muscleKey = "Back",
                    headlineVolume = "10 sets/wk",
                    badge = TrendBadge.Down,
                    progressPoints = fallingIndexSeries(),
                    volumePoints = fallingVolumeSeries(),
                    contributingLifts = 3,
                    highlight = CalloutDirectionUi.Regressing,
                ),
                MuscleSeriesUi(
                    name = "Quad",
                    muscleKey = "Quad",
                    headlineVolume = "8 sets/wk",
                    badge = TrendBadge.Flat,
                    progressPoints = flatIndexSeries(),
                    volumePoints = flatVolumeSeries(),
                    contributingLifts = 2,
                ),
                MuscleSeriesUi(
                    name = "Bicep",
                    muscleKey = "Bicep",
                    headlineVolume = "6 sets/wk",
                    badge = TrendBadge.Unknown,
                    progressPoints = emptyList(),
                    volumePoints = risingVolumeSeries(),
                    contributingLifts = 1,
                ),
            ),
            liftSections = listOf(
                LiftSectionUi(
                    title = SECTION_UPPER,
                    lifts = listOf(
                        LiftSeriesUi(
                            name = "Bench Press",
                            liftId = "bench",
                            e1rmText = "225 lb",
                            badge = TrendBadge.Up,
                            performancePoints = ascendingSeries(),
                            volumePoints = ascendingSeries(),
                            sessions = 22,
                            highlight = CalloutDirectionUi.Progressing,
                        ),
                        LiftSeriesUi(
                            name = "Barbell Row",
                            liftId = "row",
                            e1rmText = "185 lb",
                            badge = TrendBadge.Down,
                            performancePoints = descendingSeries(),
                            volumePoints = flatSeries(),
                            sessions = 18,
                            highlight = CalloutDirectionUi.Regressing,
                        ),
                    ),
                ),
                LiftSectionUi(
                    title = SECTION_LOWER,
                    lifts = listOf(
                        LiftSeriesUi(
                            name = "Hack Squat",
                            liftId = "hack",
                            e1rmText = "315 lb",
                            badge = TrendBadge.Flat,
                            performancePoints = flatSeries(),
                            volumePoints = flatSeries(),
                            sessions = 11,
                        ),
                    ),
                ),
            ),
            runningDailyVolume = listOf(
                MuscleDailyVolumeUi(
                    name = "Chest",
                    muscleKey = "Chest",
                    headlineSets = "8 sets",
                    volumePoints = sevenDayVolumeSeries(peakDay = 5),
                    dayLabels = PREVIEW_DAY_LABELS,
                    contributingLifts = 2,
                ),
                MuscleDailyVolumeUi(
                    name = "Back",
                    muscleKey = "Back",
                    headlineSets = "6 sets",
                    volumePoints = sevenDayVolumeSeries(peakDay = 2),
                    dayLabels = PREVIEW_DAY_LABELS,
                    contributingLifts = 1,
                ),
                MuscleDailyVolumeUi(
                    name = "Quad",
                    muscleKey = "Quad",
                    headlineSets = "4 sets",
                    volumePoints = sevenDayVolumeSeries(peakDay = 0),
                    dayLabels = PREVIEW_DAY_LABELS,
                    contributingLifts = 1,
                ),
            ),
        ),
        health = health,
        onNavigateToSettings = {},
    )
}

/** Seven daily bars; [peakDay] (0..6) gets the largest volume, others sparse. */
private fun sevenDayVolumeSeries(peakDay: Int): List<TrendPointUi> =
    (0..6).map { day ->
        val value = when {
            day == peakDay -> 4f
            day == (peakDay + 3) % 7 -> 2f
            else -> 0f
        }
        TrendPointUi(weekIndex = day, value = value)
    }

/** Sample rolling week ending Sunday for previews. */
private val PREVIEW_DAY_LABELS = listOf("M", "T", "W", "R", "F", "Sa", "Su")

private fun previewHealthState(selectedPage: Int = 0): DashboardHealthUiState =
    DashboardHealthUiState(
        visible = true,
        selectedPage = selectedPage,
        activity = ActivityHealthPageUi(
            todayStepsText = "8,432",
            sevenDayAverageText = "7,210",
            dailyPoints = listOf(5_200f, 6_100f, 4_800f, 7_400f, 8_100f, 9_000f, 8_432f)
                .mapIndexed { index, value -> TrendPointUi(index, value) },
            dayLabels = PREVIEW_DAY_LABELS,
        ),
        weight = WeightHealthPageUi(
            latestText = "176.4 lb",
            latestDateText = "Aug 12",
            sevenDayMeanText = "177.1 lb",
            deltaText = "−0.7 lb",
            trendPoints = listOf(178.2f, 177.8f, 177.4f, 177.0f, 176.6f, 176.5f, 176.4f)
                .mapIndexed { index, value -> TrendPointUi(index, value) },
        ),
        recovery = RecoveryHealthPageUi(
            lastNightDurationText = "7h 12m",
            zones = SleepZonesUi(
                lightFraction = 0.45f,
                deepFraction = 0.22f,
                remFraction = 0.23f,
                awakeFraction = 0.10f,
            ),
            restingHrText = "52 bpm",
            restingHrAverageText = "54 bpm",
            hrvText = "48 ms",
            nightlyPoints = listOf(6.8f, 7.1f, 5.9f, 7.4f, 7.0f, 8.1f, 7.2f)
                .mapIndexed { index, value -> TrendPointUi(index, value) },
            dayLabels = PREVIEW_DAY_LABELS,
        ),
    )

/** Lift e1RM-scale series for strength sparklines. */
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

/** Muscle outcome-index series (log deviation about 0). */
private fun risingIndexSeries(): List<TrendPointUi> =
    listOf(
        TrendPointUi(0, -0.01f), TrendPointUi(1, -0.004f), TrendPointUi(2, 0.002f),
        TrendPointUi(3, 0.008f), TrendPointUi(4, 0.014f),
    )

private fun fallingIndexSeries(): List<TrendPointUi> =
    listOf(
        TrendPointUi(0, 0.012f), TrendPointUi(1, 0.006f), TrendPointUi(2, 0.001f),
        TrendPointUi(3, -0.005f), TrendPointUi(4, -0.01f),
    )

private fun flatIndexSeries(): List<TrendPointUi> =
    listOf(
        TrendPointUi(0, 0.002f), TrendPointUi(1, -0.001f), TrendPointUi(2, 0.003f),
        TrendPointUi(3, 0.0f), TrendPointUi(4, 0.001f),
    )

private fun risingVolumeSeries(): List<TrendPointUi> =
    listOf(
        TrendPointUi(0, 8f), TrendPointUi(1, 10f), TrendPointUi(2, 11f),
        TrendPointUi(3, 13f), TrendPointUi(4, 14f),
    )

private fun fallingVolumeSeries(): List<TrendPointUi> =
    listOf(
        TrendPointUi(0, 14f), TrendPointUi(1, 12f), TrendPointUi(2, 10f),
        TrendPointUi(3, 9f), TrendPointUi(4, 8f),
    )

private fun flatVolumeSeries(): List<TrendPointUi> =
    listOf(
        TrendPointUi(0, 10f), TrendPointUi(1, 9f), TrendPointUi(2, 11f),
        TrendPointUi(3, 10f), TrendPointUi(4, 10f),
    )
