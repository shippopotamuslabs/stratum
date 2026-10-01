package com.stratum.core.ui.component.workoutlog

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId

@PreviewLightDark
@Composable
private fun StratumWorkoutLogLiftPanelMultiNotePreview() {
    StratumTheme(themeId = StratumThemeId.Tonal) {
        StratumWorkoutLogLiftPanel(
            lift =
                WorkoutLogLiftUi(
                    key = "wl-1",
                    liftName = "Squat",
                    sets =
                        listOf(
                            WorkoutLogSetUi(
                                setKey = "s-1",
                                positionLabel = "1",
                                setTypeLabel = "S",
                                previousLabel = "225 × 5",
                                weightPlaceholder = "225",
                                repsPlaceholder = "5",
                                weightText = "225",
                                repsText = "5",
                                rir = 2,
                                isComplete = true,
                            ),
                            WorkoutLogSetUi(
                                setKey = "s-2",
                                positionLabel = "2",
                                setTypeLabel = "S",
                                // Long decimal weight exercises the Prev shrink-to-fit path.
                                previousLabel = "127.5 × 20",
                                previousRirLabel = "2 RIR",
                                weightPlaceholder = "127.5",
                                repsPlaceholder = "20",
                                weightText = "127.5",
                                repsText = "20",
                                rir = 0,
                                isComplete = true,
                            ),
                            WorkoutLogSetUi(
                                setKey = "s-3",
                                positionLabel = "3",
                                setTypeLabel = "S",
                                previousLabel = "225 × 5",
                                weightPlaceholder = "225",
                                repsPlaceholder = "5",
                                rir = 7,
                            ),
                        ),
                    workoutPinnedNotes = listOf("Keep elbows tucked", "Chest up"),
                    liftPinnedNotes = listOf("Belt on working sets"),
                ),
            setRowActions = { WorkoutLogSetRowActions() },
        )
    }
}
