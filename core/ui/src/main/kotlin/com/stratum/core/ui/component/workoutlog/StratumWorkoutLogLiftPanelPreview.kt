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
                                previousLabel = "225 lb × 5",
                                weightPlaceholder = "225",
                                repsPlaceholder = "5",
                            ),
                        ),
                    workoutPinnedNotes = listOf("Keep elbows tucked", "Chest up"),
                    liftPinnedNotes = listOf("Belt on working sets"),
                ),
            setRowActions = { WorkoutLogSetRowActions() },
        )
    }
}
