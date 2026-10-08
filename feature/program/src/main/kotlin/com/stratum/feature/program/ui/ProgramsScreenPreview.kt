package com.stratum.feature.program.ui

import com.stratum.core.ui.component.StratumMuscleVolumeCardState
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.feature.program.ui.ScheduleItemState.RestDay
import com.stratum.feature.program.ui.ScheduleItemState.Workout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(name = "Programs loaded light")
@Composable
fun ProgramsScreenLoadedPreview() {
    ProgramsLoadedPreviewContent(family = StratumThemeId.Tonal)
}

@Preview(name = "Programs loaded dark")
@Composable
fun ProgramsScreenLoadedDarkPreview() {
    ProgramsLoadedPreviewContent(family = StratumThemeId.Tonal, darkTheme = true)
}

@Preview(name = "Programs loaded light neo")
@Composable
private fun ProgramsScreenLoadedNeoPreview() {
    ProgramsLoadedPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@Preview(name = "Programs loaded dark neo")
@Composable
private fun ProgramsScreenLoadedDarkNeoPreview() {
    ProgramsLoadedPreviewContent(family = StratumThemeId.NeoBrutalist, darkTheme = true)
}

@Preview(name = "Programs preview loaded light")
@Composable
fun ProgramsScreenPreviewLoadedPreview() {
    ProgramsLoadedPreviewContent(family = StratumThemeId.Tonal, isPreviewMode = true)
}

@Preview(name = "Programs preview loaded dark")
@Composable
fun ProgramsScreenPreviewLoadedDarkPreview() {
    ProgramsLoadedPreviewContent(family = StratumThemeId.Tonal, darkTheme = true, isPreviewMode = true)
}

@Composable
internal fun ProgramsLoadedPreviewContent(
    family: StratumThemeId,
    darkTheme: Boolean? = null,
    isPreviewMode: Boolean = false,
) {
    StratumTheme(themeId = family, darkTheme = darkTheme) {
        LoadedProgramsScreen(isPreviewMode = isPreviewMode)
    }
}

@Composable
private fun LoadedProgramsScreen(isPreviewMode: Boolean = false) {
    ProgramsScreen(
        state =
            ProgramsUiState.Loaded(
                program = ProgramViewState(programId = "preview-program", name = "Minimal 4 Day"),
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
                        RestDay(slotIndex = 1),
                        Workout(
                            workout =
                                WorkoutViewState(
                                    workoutId = "w-preview-1",
                                    name = "Minimal Lower A",
                                    position = 0,
                                    lifts =
                                        listOf(
                                            WorkoutLiftLineState(
                                                workoutLiftId = "wl-1",
                                                liftId = "barbell-romanian-deadlift",
                                                name = "Barbell Romanian Deadlift",
                                                setCount = 3,
                                                repRange = "8\u201312",
                                                topSetRpe = "@8",
                                                position = 0,
                                            ),
                                            WorkoutLiftLineState(
                                                workoutLiftId = "wl-2",
                                                liftId = "pin-loaded-leg-press-calf-raise",
                                                name = "Pin-Loaded Leg Press Calf Raise",
                                                setCount = 2,
                                                repRange = "10\u201315",
                                                topSetRpe = "@8",
                                                position = 1,
                                            ),
                                            WorkoutLiftLineState(
                                                workoutLiftId = "wl-3",
                                                liftId = "seated-dumbbell-biceps-curl",
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
        callbacks = ProgramsScreenCallbacks(),
        onSwapLiftClick = { _, _ -> },
        isPreviewMode = isPreviewMode,
    )
}

@Preview(name = "Programs empty light")
@Composable
private fun ProgramsScreenEmptyLightPreview() {
    ProgramsEmptyPreviewContent(family = StratumThemeId.Tonal)
}

@Preview(name = "Programs empty dark")
@Composable
fun ProgramsScreenEmptyPreview() {
    ProgramsEmptyPreviewContent(family = StratumThemeId.Tonal, darkTheme = true)
}

@Preview(name = "Programs empty light neo")
@Composable
private fun ProgramsScreenEmptyNeoPreview() {
    ProgramsEmptyPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@Preview(name = "Programs empty dark neo")
@Composable
private fun ProgramsScreenEmptyDarkNeoPreview() {
    ProgramsEmptyPreviewContent(family = StratumThemeId.NeoBrutalist, darkTheme = true)
}

@Composable
internal fun ProgramsEmptyPreviewContent(
    family: StratumThemeId,
    darkTheme: Boolean? = null,
) {
    StratumTheme(themeId = family, darkTheme = darkTheme) {
        ProgramsScreen(
            state = ProgramsUiState.Empty,
            callbacks = ProgramsScreenCallbacks(),
            onSwapLiftClick = { _, _ -> },
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

@Preview(name = "Workout card expanded dark", showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun WorkoutCardExpandedDarkPreview() {
    StratumTheme(darkTheme = true) {
        WorkoutCardPreviewContent(initiallyExpanded = true)
    }
}

@Composable
private fun WorkoutCardPreviewContent(initiallyExpanded: Boolean = false) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WorkoutCard(
            workout =
                WorkoutViewState(
                    workoutId = "w-preview-2",
                    name = "Push Day",
                    position = 0,
                    lifts =
                        listOf(
                            WorkoutLiftLineState(
                                workoutLiftId = "wl-4",
                                liftId = "barbell-bench-press",
                                name = "Barbell Bench Press",
                                setCount = 4,
                                repRange = "5",
                                topSetRpe = "@8",
                                position = 0,
                            ),
                            WorkoutLiftLineState(
                                workoutLiftId = "wl-5",
                                liftId = "seated-dumbbell-overhead-press",
                                name = "Seated Dumbbell Overhead Press",
                                setCount = 3,
                                repRange = "8\u201312",
                                topSetRpe = "@8",
                                position = 1,
                            ),
                            WorkoutLiftLineState(
                                workoutLiftId = "wl-6",
                                liftId = "incline-dumbbell-bench-press",
                                name = "Incline Dumbbell Bench Press",
                                setCount = 3,
                                repRange = "8\u201312",
                                topSetRpe = "@7",
                                position = 2,
                            ),
                            WorkoutLiftLineState(
                                workoutLiftId = "wl-7",
                                liftId = "cable-crossover-fly",
                                name = "Cable Crossover Fly",
                                setCount = 3,
                                repRange = "12\u201315",
                                topSetRpe = "",
                                position = 3,
                            ),
                            WorkoutLiftLineState(
                                workoutLiftId = "wl-8",
                                liftId = "dumbbell-lateral-raise",
                                name = "Dumbbell Lateral Raise",
                                setCount = 4,
                                repRange = "15\u201320",
                                topSetRpe = "",
                                position = 4,
                            ),
                            WorkoutLiftLineState(
                                workoutLiftId = "wl-9",
                                liftId = "tricep-rope-pushdown",
                                name = "Tricep Rope Pushdown",
                                setCount = 3,
                                repRange = "10\u201315",
                                topSetRpe = "",
                                position = 5,
                            ),
                            WorkoutLiftLineState(
                                workoutLiftId = "wl-10",
                                liftId = "overhead-cable-tricep-extension",
                                name = "Overhead Cable Tricep Extension",
                                setCount = 3,
                                repRange = "12\u201315",
                                topSetRpe = "",
                                position = 6,
                            ),
                        ),
                ),
            expanded = expanded,
            onExpandedChange = { expanded = it },
            onStartWorkoutClick = {},
            onEditWorkoutClick = {},
            onSkipWorkoutCheckedChange = { _, _ -> },
            onSwapLiftClick = { _, _ -> },
        )
    }
}
