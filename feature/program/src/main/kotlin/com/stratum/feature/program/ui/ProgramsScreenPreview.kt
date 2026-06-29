package com.stratum.feature.program.ui

import com.stratum.core.ui.component.StratumMuscleVolumeCardState
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.feature.program.ui.ScheduleItemState.RestDay
import com.stratum.feature.program.ui.ScheduleItemState.Workout
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "Programs loaded light")
@Composable
fun ProgramsScreenLoadedPreview() {
    StratumTheme {
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
                                                    summaryLine = "Barbell Romanian Deadlift - 3 x 8\u201312@8",
                                                    position = 0,
                                                ),
                                                WorkoutLiftLineState(
                                                    summaryLine = "Pin-Loaded Leg Press Calf Raise - 2 x 10\u201315@8",
                                                    position = 1,
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
