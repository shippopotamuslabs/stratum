package com.stratum.core.ui.share

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId

@Preview(name = "Tonal Light", group = "WorkoutShareCard", showBackground = true)
@Composable
private fun WorkoutShareCardLightPreview() {
    StratumTheme(themeId = StratumThemeId.Tonal, darkTheme = false) {
        WorkoutShareCard(
            content = previewShareContent(),
            modifier =
                Modifier
                    .fillMaxSize()
                    .aspectRatio(1f)
                    .padding(8.dp),
        )
    }
}

@Preview(name = "Tonal Dark", group = "WorkoutShareCard", showBackground = true)
@Composable
private fun WorkoutShareCardDarkPreview() {
    StratumTheme(themeId = StratumThemeId.Tonal, darkTheme = true) {
        WorkoutShareCard(
            content = previewShareContent(),
            modifier =
                Modifier
                    .fillMaxSize()
                    .aspectRatio(1f)
                    .padding(8.dp),
        )
    }
}

@Preview(name = "Neo Light", group = "WorkoutShareCard", showBackground = true)
@Composable
private fun WorkoutShareCardNeoLightPreview() {
    StratumTheme(themeId = StratumThemeId.NeoBrutalist, darkTheme = false) {
        WorkoutShareCard(
            content = previewShareContent(),
            modifier =
                Modifier
                    .fillMaxSize()
                    .aspectRatio(1f)
                    .padding(8.dp),
        )
    }
}

@Preview(name = "Neo Dark", group = "WorkoutShareCard", showBackground = true)
@Composable
private fun WorkoutShareCardNeoDarkPreview() {
    StratumTheme(themeId = StratumThemeId.NeoBrutalist, darkTheme = true) {
        WorkoutShareCard(
            content = previewShareContent(),
            modifier =
                Modifier
                    .fillMaxSize()
                    .aspectRatio(1f)
                    .padding(8.dp),
        )
    }
}

@Preview(name = "Tonal Overflow", group = "WorkoutShareCard", showBackground = true)
@Composable
private fun WorkoutShareCardOverflowPreview() {
    StratumTheme(themeId = StratumThemeId.Tonal, darkTheme = true) {
        WorkoutShareCard(
            content = previewShareContentOverflow(),
            modifier =
                Modifier
                    .fillMaxSize()
                    .aspectRatio(1f)
                    .padding(8.dp),
        )
    }
}

private fun previewShareContent(): WorkoutShareContent =
    buildWorkoutShareContent(
        workoutName = "Upper Body A",
        durationFormatted = "1h 12m",
        liftRows =
            listOf(
                ShareLiftRowUi(
                    liftName = "Barbell Bench Press",
                    performanceLabel = "225 lb × 8",
                    isPersonalRecord = true,
                ),
                ShareLiftRowUi(
                    liftName = "Incline Dumbbell Press",
                    performanceLabel = "80 lb × 10",
                    isPersonalRecord = false,
                ),
                ShareLiftRowUi(
                    liftName = "Chest-Supported Row",
                    performanceLabel = "70 lb × 12",
                    isPersonalRecord = true,
                ),
                ShareLiftRowUi(
                    liftName = "Cable Lateral Raise",
                    performanceLabel = "25 lb × 15",
                    isPersonalRecord = false,
                ),
            ),
    )

private fun previewShareContentOverflow(): WorkoutShareContent =
    buildWorkoutShareContent(
        workoutName = "Full Body Comprehensive",
        durationFormatted = "1h 45m",
        liftRows =
            listOf(
                ShareLiftRowUi("Barbell Squat", "315 lb × 5", true),
                ShareLiftRowUi("Romanian Deadlift", "275 lb × 8", false),
                ShareLiftRowUi("Barbell Bench Press", "225 lb × 8", true),
                ShareLiftRowUi("Pull-Up", "BW+45 lb × 6", false),
                ShareLiftRowUi("Overhead Press", "135 lb × 8", false),
                ShareLiftRowUi("Barbell Curl", "85 lb × 10", false),
                ShareLiftRowUi("Skull Crusher", "75 lb × 12", false),
                ShareLiftRowUi("Lateral Raise", "30 lb × 15", false),
                ShareLiftRowUi("Calf Raise", "200 lb × 20", false),
            ),
    )
