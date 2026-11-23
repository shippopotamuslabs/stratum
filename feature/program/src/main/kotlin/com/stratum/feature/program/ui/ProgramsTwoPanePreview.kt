package com.stratum.feature.program.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import com.stratum.core.ui.component.StratumMuscleVolumeCardState
import com.stratum.core.ui.posture.FoldPosture
import com.stratum.core.ui.posture.LocalFoldPosture
import com.stratum.core.ui.posture.flatHingeFixture
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId
import com.stratum.domain.onboarding.MuscleGroup

private const val FOLD_PREVIEW_WIDTH_DP = 1200
private const val FOLD_PREVIEW_HEIGHT_DP = 860

@Preview(name = "Light", widthDp = FOLD_PREVIEW_WIDTH_DP, heightDp = FOLD_PREVIEW_HEIGHT_DP)
@Preview(
    name = "Dark",
    widthDp = FOLD_PREVIEW_WIDTH_DP,
    heightDp = FOLD_PREVIEW_HEIGHT_DP,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ProgramsTwoPaneNextDayPreview() {
    ProgramsTwoPaneNextDayPreviewContent()
}

@Preview(name = "Light", widthDp = FOLD_PREVIEW_WIDTH_DP, heightDp = FOLD_PREVIEW_HEIGHT_DP)
@Preview(
    name = "Dark",
    widthDp = FOLD_PREVIEW_WIDTH_DP,
    heightDp = FOLD_PREVIEW_HEIGHT_DP,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ProgramsTwoPaneSelectedDayPreview() {
    ProgramsTwoPaneSelectedDayPreviewContent()
}

@Preview(name = "Light", widthDp = FOLD_PREVIEW_WIDTH_DP, heightDp = FOLD_PREVIEW_HEIGHT_DP)
@Preview(
    name = "Dark",
    widthDp = FOLD_PREVIEW_WIDTH_DP,
    heightDp = FOLD_PREVIEW_HEIGHT_DP,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ProgramsTwoPaneHingePreview() {
    ProgramsTwoPaneHingePreviewContent()
}

/** Default selection: no pick yet, so the pane opens the next workout day. */
@Composable
internal fun ProgramsTwoPaneNextDayPreviewContent() {
    FoldablePrograms(posture = FoldPosture.TwoPane(hinge = null), selectedWorkoutId = null)
}

/** A picked day that is not the next one: accent row on the left, its lifts on the right. */
@Composable
internal fun ProgramsTwoPaneSelectedDayPreviewContent() {
    FoldablePrograms(posture = FoldPosture.TwoPane(hinge = null), selectedWorkoutId = PREVIEW_PULL_ID)
}

/** Flat fold with a hinge band between the panes. */
@Composable
internal fun ProgramsTwoPaneHingePreviewContent() {
    FoldablePrograms(posture = flatHingeFixture(), selectedWorkoutId = null)
}

@Composable
private fun FoldablePrograms(
    posture: FoldPosture,
    selectedWorkoutId: String?,
) {
    StratumTheme(themeId = StratumThemeId.Tonal) {
        CompositionLocalProvider(LocalFoldPosture provides posture) {
            ProgramsScreen(
                state = previewFoldableProgram(selectedWorkoutId),
                callbacks = ProgramsScreenCallbacks(),
                onSwapLiftClick = { _, _ -> },
            )
        }
    }
}

private const val PREVIEW_PULL_ID = "w-fold-pull"

private fun previewFoldableProgram(selectedWorkoutId: String?): ProgramsUiState.Loaded =
    ProgramsUiState.Loaded(
        program = ProgramViewState(programId = "preview-fold-program", name = "Upper Lower Push Pull"),
        upperVolumeByMuscle =
            listOf(
                StratumMuscleVolumeCardState(
                    muscle = MuscleGroup.Back,
                    displayName = "Back",
                    exerciseCount = 4,
                    setCount = 12.0,
                ),
                StratumMuscleVolumeCardState(
                    muscle = MuscleGroup.Chest,
                    displayName = "Chest",
                    exerciseCount = 3,
                    setCount = 10.5,
                ),
                StratumMuscleVolumeCardState(
                    muscle = MuscleGroup.Tricep,
                    displayName = "Triceps",
                    exerciseCount = 2,
                    setCount = 6.0,
                ),
            ),
        lowerVolumeByMuscle =
            listOf(
                StratumMuscleVolumeCardState(
                    muscle = MuscleGroup.Calf,
                    displayName = "Calves",
                    exerciseCount = 2,
                    setCount = 6.0,
                ),
            ),
        scheduleItems =
            listOf(
                ScheduleItemState.Workout(
                    previewDay(
                        id = "w-fold-upper",
                        name = "Upper A",
                        position = 0,
                        completed = true,
                        lifts =
                            listOf(
                                Triple("Barbell Bench Press", 4, "5"),
                                Triple("Chest-Supported Row", 4, "8–12"),
                                Triple("Seated Dumbbell Overhead Press", 3, "8–12"),
                            ),
                    ),
                ),
                ScheduleItemState.Workout(
                    previewDay(
                        id = "w-fold-lower",
                        name = "Lower A",
                        position = 1,
                        lifts =
                            listOf(
                                Triple("Barbell Back Squat", 4, "5"),
                                Triple("Barbell Romanian Deadlift", 3, "8–12"),
                                Triple("Pin-Loaded Leg Press Calf Raise", 3, "10–15"),
                                Triple("Seated Leg Curl", 3, "10–15"),
                            ),
                    ),
                ),
                ScheduleItemState.RestDay(slotIndex = 2),
                ScheduleItemState.Workout(
                    previewDay(
                        id = "w-fold-push",
                        name = "Push",
                        position = 3,
                        lifts =
                            listOf(
                                Triple("Incline Dumbbell Bench Press", 3, "8–12"),
                                Triple("Cable Crossover Fly", 3, "12–15"),
                                Triple("Tricep Rope Pushdown", 3, "10–15"),
                            ),
                    ),
                ),
                ScheduleItemState.Workout(
                    previewDay(
                        id = PREVIEW_PULL_ID,
                        name = "Pull",
                        position = 4,
                        lifts =
                            listOf(
                                Triple("Weighted Pull-Up", 4, "6–8"),
                                Triple("Cable Row", 3, "10–12"),
                                Triple("Seated Dumbbell Biceps Curl With Supinated Grip", 3, "10–15"),
                            ),
                    ),
                ),
                ScheduleItemState.RestDay(slotIndex = 5),
                ScheduleItemState.RestDay(slotIndex = 6),
            ),
        selectedWorkoutId = selectedWorkoutId,
    )

private fun previewDay(
    id: String,
    name: String,
    position: Int,
    lifts: List<Triple<String, Int, String>>,
    completed: Boolean = false,
): WorkoutViewState =
    WorkoutViewState(
        workoutId = id,
        name = name,
        position = position,
        isCompleted = completed,
        lifts =
            lifts.mapIndexed { index, (liftName, sets, reps) ->
                WorkoutLiftLineState(
                    workoutLiftId = "$id-wl-$index",
                    liftId = "$id-lift-$index",
                    name = liftName,
                    setCount = sets,
                    repRange = reps,
                    topSetRpe = if (index == 0) "@8" else "",
                    position = index,
                )
            },
    )
