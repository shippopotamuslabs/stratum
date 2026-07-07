package com.stratum.feature.workout.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId

@Preview(name = "Workout complete light", showBackground = true)
@Composable
private fun WorkoutCompleteScreenLightPreview() {
    StratumTheme {
        WorkoutCompleteScreenPreviewContent()
    }
}

@Preview(name = "Workout complete dark", showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun WorkoutCompleteScreenDarkPreview() {
    StratumTheme(darkTheme = true) {
        WorkoutCompleteScreenPreviewContent()
    }
}

@Preview(name = "Workout complete light", showBackground = true)
@Composable
private fun WorkoutCompleteScreenLightPreviewNeo() {
    StratumTheme(themeId = StratumThemeId.NeoBrutalist) {
        WorkoutCompleteScreenPreviewContent()
    }
}

@Preview(name = "Workout complete dark", showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun WorkoutCompleteScreenDarkPreviewNeo() {
    StratumTheme(themeId = StratumThemeId.NeoBrutalist, darkTheme = true) {
        WorkoutCompleteScreenPreviewContent()
    }
}

@Composable
private fun WorkoutCompleteScreenPreviewContent() {
    WorkoutCompleteScreen(
        state = WorkoutCompleteUiState.Loaded(
            workoutName = "Push Day — Wk 4",
            durationFormatted = "1h 12m 38s",
            bestSets =
                listOf(
                    LiftBestSetUi(
                        liftName = "Barbell Bench Press",
                        performanceLabel = "185 × 3",
                        rirLabel = "1",
                        estimatedOneRmLabel = "201",
                        isPersonalRecord = true,
                    ),
                    LiftBestSetUi(
                        liftName = "Incline Dumbbell Press",
                        performanceLabel = "80 × 8",
                        rirLabel = "2",
                        estimatedOneRmLabel = "101",
                        isPersonalRecord = false,
                    ),
                    LiftBestSetUi(
                        liftName = "Cable Fly",
                        performanceLabel = "35 × 12",
                        rirLabel = "—",
                        estimatedOneRmLabel = "50",
                        isPersonalRecord = false,
                    ),
                ),
        ),
        onClose = {},
    )
}
