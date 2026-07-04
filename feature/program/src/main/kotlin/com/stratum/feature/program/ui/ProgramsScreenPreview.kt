package com.stratum.feature.program.ui

import com.stratum.core.ui.component.StratumMuscleVolumeCardState
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.feature.program.ui.ScheduleItemState.RestDay
import com.stratum.feature.program.ui.ScheduleItemState.Workout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(name = "Programs loaded light")
@Composable
fun ProgramsScreenLoadedPreview() {
    StratumTheme {
        LoadedProgramsScreen()
    }
}

@Preview(name = "Programs loaded dark")
@Composable
fun ProgramsScreenLoadedDarkPreview() {
    StratumTheme(darkTheme = true) {
        LoadedProgramsScreen()
    }
}

@Composable
private fun LoadedProgramsScreen() {
    ProgramsScreen(
        state =
            ProgramsUiState.Loaded(
                program = ProgramViewState(name = "Minimal 4 Day"),
                upperVolumeByMuscle =
                    listOf(
                        StratumMuscleVolumeCardState(
                            muscle = MuscleGroup.Back,
                            displayName = "Back",
                            exerciseCount = 4,
                            setCount = 10.1,
                        ),
                        StratumMuscleVolumeCardState(
                            muscle = MuscleGroup.Tricep,
                            displayName = "Triceps",
                            exerciseCount = 2,
                            setCount = 8.0,
                        ),
                    ),
                lowerVolumeByMuscle =
                    listOf(
                        StratumMuscleVolumeCardState(
                            muscle = MuscleGroup.Calf,
                            displayName = "Calves",
                            exerciseCount = 3,
                            setCount = 9.7,
                        ),
                    ),
                scheduleItems =
                    listOf(
                        RestDay(weekdayLabel = "Tuesday"),
                        Workout(
                            workout =
                                WorkoutViewState(
                                    name = "Minimal Lower A",
                                    weekdayLabel = "Monday",
                                    position = 0,
                                    lifts =
                                        listOf(
                                            WorkoutLiftLineState(
                                                name = "Barbell Romanian Deadlift",
                                                setCount = 3,
                                                repRange = "8\u201312",
                                                topSetRpe = "@8",
                                                position = 0,
                                            ),
                                            WorkoutLiftLineState(
                                                name = "Pin-Loaded Leg Press Calf Raise",
                                                setCount = 2,
                                                repRange = "10\u201315",
                                                topSetRpe = "@8",
                                                position = 1,
                                            ),
                                            WorkoutLiftLineState(
                                                name = "Seated Dumbbell Biceps Curl With Supinated Grip",
                                                setCount = 4,
                                                repRange = "10\u201315",
                                                topSetRpe = "",
                                                position = 2,
                                            ),
                                        ),
                                ),
                        ),
                    ),
            ),
        onCalendarClick = {},
        onCreateProgramClick = {},
        onGenerateProgramClick = {},
        onProgramLibraryClick = {},
        onStartNextWorkoutClick = {},
        onStartEmptyWorkoutClick = {},
        onStartWorkoutClick = {},
        onSkipWorkoutCheckedChange = { _, _ -> },
    )
}

@Preview(name = "Programs empty dark")
@Composable
fun ProgramsScreenEmptyPreview() {
    StratumTheme(darkTheme = true) {
        ProgramsScreen(
            state = ProgramsUiState.Empty,
            onCalendarClick = {},
            onCreateProgramClick = {},
            onGenerateProgramClick = {},
            onProgramLibraryClick = {},
            onStartNextWorkoutClick = {},
            onStartEmptyWorkoutClick = {},
            onStartWorkoutClick = {},
            onSkipWorkoutCheckedChange = { _, _ -> },
        )
    }
}

@Preview(name = "Workout card light", showBackground = true)
@Composable
fun WorkoutCardPreview() {
    StratumTheme {
        WorkoutCardPreviewContent()
    }
}

@Preview(name = "Workout card dark", showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun WorkoutCardDarkPreview() {
    StratumTheme(darkTheme = true) {
        WorkoutCardPreviewContent()
    }
}

@Composable
private fun WorkoutCardPreviewContent() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WorkoutCard(
            workout =
                WorkoutViewState(
                    name = "Push Day",
                    weekdayLabel = "Monday",
                    position = 0,
                    lifts =
                        listOf(
                            WorkoutLiftLineState(
                                name = "Barbell Bench Press",
                                setCount = 4,
                                repRange = "5",
                                topSetRpe = "@8",
                                position = 0,
                            ),
                            WorkoutLiftLineState(
                                name = "Seated Dumbbell Overhead Press",
                                setCount = 3,
                                repRange = "8\u201312",
                                topSetRpe = "@8",
                                position = 1,
                            ),
                            WorkoutLiftLineState(
                                name = "Incline Dumbbell Bench Press",
                                setCount = 3,
                                repRange = "8\u201312",
                                topSetRpe = "@7",
                                position = 2,
                            ),
                            WorkoutLiftLineState(
                                name = "Cable Crossover Fly",
                                setCount = 3,
                                repRange = "12\u201315",
                                topSetRpe = "",
                                position = 3,
                            ),
                            WorkoutLiftLineState(
                                name = "Dumbbell Lateral Raise",
                                setCount = 4,
                                repRange = "15\u201320",
                                topSetRpe = "",
                                position = 4,
                            ),
                            WorkoutLiftLineState(
                                name = "Tricep Rope Pushdown",
                                setCount = 3,
                                repRange = "10\u201315",
                                topSetRpe = "",
                                position = 5,
                            ),
                            WorkoutLiftLineState(
                                name = "Overhead Cable Tricep Extension",
                                setCount = 3,
                                repRange = "12\u201315",
                                topSetRpe = "",
                                position = 6,
                            ),
                        ),
                ),
            onStartWorkoutClick = {},
            onSkipWorkoutCheckedChange = { _, _ -> },
        )
    }
}
