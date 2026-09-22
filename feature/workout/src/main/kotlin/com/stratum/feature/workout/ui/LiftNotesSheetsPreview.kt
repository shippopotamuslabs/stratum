package com.stratum.feature.workout.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.stratum.core.ui.theme.StratumTheme

@PreviewLightDark
@Composable
private fun ManageNotesSummaryContentPreview() {
    StratumTheme {
        ManageNotesSummaryContent(
            lift =
                RenderedLiftUi(
                    workoutLiftId = "wl-1",
                    liftId = "lift-1",
                    liftName = "Squat",
                    sets = emptyList(),
                    workoutPinnedNotes = listOf("Keep elbows tucked", "Chest up"),
                    liftPinnedNotes = listOf("Belt on working sets"),
                ),
            initialTab = LiftNoteTab.Workout,
            loadNoteLists = { _, _ ->
                NoteListsUi(
                    session = listOf(NoteDraftUi(id = "n-1", body = "Session cue")),
                    workout = listOf(NoteDraftUi(id = "w-1", body = "Keep elbows tucked")),
                    lift = listOf(NoteDraftUi(id = "c-1", body = "Belt on working sets")),
                )
            },
            onEditNotes = {},
            onDismiss = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun ManageNotesScreenPreview() {
    StratumTheme {
        ManageNotesScreen(
            target =
                EditingNotesTarget(
                    workoutLiftId = "wl-1",
                    liftId = "lift-1",
                    liftName = "Squat",
                    tab = LiftNoteTab.Lift,
                ),
            liftNotes =
                WorkoutLiftNotesActions(
                    onLoadNoteLists = { _, _ ->
                        NoteListsUi(
                            lift =
                                listOf(
                                    NoteDraftUi(id = "c-1", body = "Belt on working sets"),
                                    NoteDraftUi(body = ""),
                                ),
                        )
                    },
                    onSave = {},
                ),
            onClose = {},
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
                    PastSessionNotesGroupUi(
                        dateLabel = "Aug 14, 2026",
                        workoutName = "Lower A",
                        bodies =
                            listOf(
                                "Switched to high-bar this session",
                                "Belt on top set only",
                            ),
                    ),
                ),
            onDismiss = {},
        )
    }
}
