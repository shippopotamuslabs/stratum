package com.stratum.feature.program.ui

import com.stratum.core.ui.component.StratumMuscleVolumeCardState
import com.stratum.core.ui.component.StratumNavigationBar
import com.stratum.core.ui.component.StratumNavigationBarItem
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.theme.LocalStratumHazeState
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.feature.program.ui.ScheduleItemState.RestDay
import com.stratum.feature.program.ui.ScheduleItemState.Workout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.rememberHazeState

@PreviewLightDark
@Composable
private fun ProgramsScreenLoadedTonalPreview() {
    ProgramsLoadedPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun ProgramsScreenLoadedNeoPreview() {
    ProgramsLoadedPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewLightDark
@Composable
private fun ProgramsScreenPreviewLoadedTonalPreview() {
    ProgramsLoadedPreviewContent(family = StratumThemeId.Tonal, isPreviewMode = true)
}

@PreviewLightDark
@Composable
private fun ProgramsScreenPreviewLoadedNeoPreview() {
    ProgramsLoadedPreviewContent(family = StratumThemeId.NeoBrutalist, isPreviewMode = true)
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

/**
 * STR-156 regression pin: the minimized-workout drawer had NO screenshot
 * coverage, which is how the glass shell hid it behind the nav bar unnoticed.
 * Solid host here (no glass state in previews) — this pins the drawer's
 * presence and legacy geometry; the glazed treatment is pinned by
 * [ProgramsActiveWorkoutGlassPreviewContent] below.
 */
@Composable
internal fun ProgramsActiveWorkoutPreviewContent(
    family: StratumThemeId,
    darkTheme: Boolean? = null,
) {
    StratumTheme(themeId = family, darkTheme = darkTheme) {
        val startedAt = remember { System.currentTimeMillis() }
        LoadedProgramsScreen(
            activeWorkoutBar =
                MinimizedWorkoutState(
                    workoutId = "w-preview-active",
                    workoutName = "Minimal Lower A",
                    nextLiftName = "Barbell Romanian Deadlift",
                    startedAtEpochMilli = startedAt,
                ),
        )
    }
}

/**
 * STR-162 screenshot pin: the active-workout drawer under a REAL glass host,
 * replicating `MainTabShell`'s hosting (shared HazeState provided, glass nav
 * bar in the shell scaffold's bottom slot, NO bottom space reserved for the
 * content). This is the composition that hid the drawer (STR-156) and then
 * under-lifted it (STR-162): the golden must show the FULL drawer — name,
 * timer, next lift — resting above the nav bar, glazed, with the loaded
 * program scrolling beneath both glass surfaces.
 */
@Composable
internal fun ProgramsActiveWorkoutGlassPreviewContent(darkTheme: Boolean? = null) {
    StratumTheme(themeId = StratumThemeId.Tonal, darkTheme = darkTheme) {
        val hazeState = rememberHazeState()
        CompositionLocalProvider(LocalStratumHazeState provides hazeState) {
            StratumScaffold(
                bottomBar = {
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
                    )
                },
            ) { _ ->
                // Glass shell rule (§12.8): no bottom space reserved — content
                // scrolls beneath the nav glass and pads its own ends.
                Box(modifier = Modifier.fillMaxSize()) {
                    val startedAt = remember { System.currentTimeMillis() }
                    LoadedProgramsScreen(
                        activeWorkoutBar =
                            MinimizedWorkoutState(
                                workoutId = "w-preview-active",
                                workoutName = "Minimal Lower A",
                                nextLiftName = "Barbell Romanian Deadlift",
                                startedAtEpochMilli = startedAt,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadedProgramsScreen(
    isPreviewMode: Boolean = false,
    activeWorkoutBar: MinimizedWorkoutState? = null,
) {
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
        activeWorkoutBar = activeWorkoutBar,
    )
}

@PreviewLightDark
@Composable
private fun ProgramsScreenEmptyTonalPreview() {
    ProgramsEmptyPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun ProgramsScreenEmptyNeoPreview() {
    ProgramsEmptyPreviewContent(family = StratumThemeId.NeoBrutalist)
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
private fun WorkoutCardPreview() {
    StratumTheme {
        WorkoutCardPreviewContent()
    }
}

@Preview(name = "Workout card dark", showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun WorkoutCardDarkPreview() {
    StratumTheme(darkTheme = true) {
        WorkoutCardPreviewContent()
    }
}

@Preview(name = "Workout card expanded dark", showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun WorkoutCardExpandedDarkPreview() {
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
