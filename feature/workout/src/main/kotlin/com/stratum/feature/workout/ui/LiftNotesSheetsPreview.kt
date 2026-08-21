package com.stratum.feature.workout.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.stratum.core.ui.theme.StratumTheme

@PreviewLightDark
@Composable
private fun ManageLiftNotesContentPreview() {
    StratumTheme {
        ManageLiftNotesContent(
            lift =
                RenderedLiftUi(
                    workoutLiftId = "wl-1",
                    liftId = "lift-1",
                    liftName = "Squat",
                    sets = emptyList(),
                    workoutPinnedNote = "Keep elbows tucked",
                    liftPinnedNote = "Belt on working sets",
                ),
            initialTab = LiftNoteTab.Workout,
            loadSessionNote = { "" },
            onSave = {},
            onDismiss = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PastSessionNotesSheetPreview() {
    StratumTheme {
        PastSessionNotesSheet(
            notes =
                listOf(
                    PastSessionNoteUi(
                        dateLabel = "Aug 14, 2026",
                        workoutName = "Lower A",
                        body = "Switched to high-bar this session",
                    ),
                ),
            onDismiss = {},
        )
    }
}
