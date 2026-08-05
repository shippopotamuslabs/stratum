package com.stratum.feature.workout.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId

@Preview(name = "Share card light", showBackground = true, widthDp = 360, heightDp = 360)
@Composable
private fun WorkoutShareCardLightPreview() {
    StratumTheme {
        WorkoutShareCard(
            content = previewShareContent(),
            modifier = Modifier.size(360.dp),
        )
    }
}

@Preview(
    name = "Share card dark",
    showBackground = true,
    backgroundColor = 0xFF121212,
    widthDp = 360,
    heightDp = 360,
)
@Composable
private fun WorkoutShareCardDarkPreview() {
    StratumTheme(darkTheme = true) {
        WorkoutShareCard(
            content = previewShareContent(),
            modifier = Modifier.size(360.dp),
        )
    }
}

@Preview(name = "Share card neo light", showBackground = true, widthDp = 360, heightDp = 360)
@Composable
private fun WorkoutShareCardNeoLightPreview() {
    StratumTheme(themeId = StratumThemeId.NeoBrutalist) {
        WorkoutShareCard(
            content = previewShareContent(),
            modifier = Modifier.size(360.dp),
        )
    }
}

@Preview(
    name = "Share card neo dark",
    showBackground = true,
    backgroundColor = 0xFF121212,
    widthDp = 360,
    heightDp = 360,
)
@Composable
private fun WorkoutShareCardNeoDarkPreview() {
    StratumTheme(themeId = StratumThemeId.NeoBrutalist, darkTheme = true) {
        WorkoutShareCard(
            content = previewShareContent(),
            modifier = Modifier.size(360.dp),
        )
    }
}

@Preview(name = "Share card overflow", showBackground = true, widthDp = 360, heightDp = 360)
@Composable
private fun WorkoutShareCardOverflowPreview() {
    StratumTheme(darkTheme = true) {
        WorkoutShareCard(
            content = previewShareContentOverflow(),
            modifier = Modifier.size(360.dp),
        )
    }
}

private fun previewShareContent(): WorkoutShareContent =
    buildWorkoutShareContent(
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
    )

private fun previewShareContentOverflow(): WorkoutShareContent =
    buildWorkoutShareContent(
        workoutName = "Full Body — Long Session",
        durationFormatted = "2h 05m",
        bestSets =
            List(12) { index ->
                LiftBestSetUi(
                    liftName = "Lift number ${index + 1} with a long name",
                    performanceLabel = "${100 + index * 5} × ${8 - index % 4}",
                    rirLabel = "2",
                    estimatedOneRmLabel = "120",
                    isPersonalRecord = index == 0,
                )
            },
    )
