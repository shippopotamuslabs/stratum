package com.stratum.feature.program.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.stratum.core.ui.theme.StratumTheme

@Preview(name = "Programs loaded light")
@Composable
fun ProgramsScreenLoadedPreview() {
    StratumTheme {
        ProgramsScreen(
            state =
                ProgramsUiState.Loaded(
                    program =
                        ProgramViewState(
                            name = "4-Day Specialization - Back, Tricep, Calf",
                            workouts =
                                listOf(
                                    WorkoutViewState(
                                        name = "Push A (Chest/Tricep Focus)",
                                        weekdayLabel = "Wednesday",
                                        position = 0,
                                        workoutLifts =
                                            listOf(
                                                WorkoutLiftViewState(
                                                    summaryLine = "Barbell Bench Press - 3 x 8\u201312",
                                                    position = 0,
                                                ),
                                                WorkoutLiftViewState(
                                                    summaryLine = "Cable One Arm Tricep Extension - 2 x 10\u201315",
                                                    position = 1,
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                ),
        )
    }
}

@Preview(name = "Programs empty dark")
@Composable
fun ProgramsScreenEmptyPreview() {
    StratumTheme(darkTheme = true) {
        ProgramsScreen(state = ProgramsUiState.Empty)
    }
}
