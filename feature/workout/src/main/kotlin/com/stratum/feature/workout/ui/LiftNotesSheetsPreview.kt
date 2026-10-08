package com.stratum.feature.workout.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId

@PreviewLightDark
@Composable
private fun ManageNotesSummaryContentTonalPreview() {
    ManageNotesSummaryPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun ManageNotesSummaryContentNeoPreview() {
    ManageNotesSummaryPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewLightDark
@Composable
private fun ManageNotesScreenTonalPreview() {
    ManageNotesScreenPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun ManageNotesScreenNeoPreview() {
    ManageNotesScreenPreviewContent(family = StratumThemeId.NeoBrutalist)
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

@Composable
internal fun ManageNotesSummaryPreviewContent(family: StratumThemeId) {
    SheetContentPreview(family = family) {
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
            loadNoteLists = { _, _ -> previewSummaryNoteLists() },
            onEditNotes = {},
            onDismiss = {},
            initialLists = previewSummaryNoteLists(),
        )
    }
}

private fun previewSummaryNoteLists(): NoteListsUi =
    NoteListsUi(
        session = listOf(NoteDraftUi(id = "n-1", body = "Session cue")),
        workout = listOf(NoteDraftUi(id = "w-1", body = "Keep elbows tucked")),
        lift = listOf(NoteDraftUi(id = "c-1", body = "Belt on working sets")),
    )

private fun previewScreenNoteLists(): NoteListsUi =
    NoteListsUi(
        lift =
            listOf(
                NoteDraftUi(id = "c-1", body = "Belt on working sets"),
                NoteDraftUi(body = ""),
            ),
    )

@Composable
internal fun ManageNotesScreenPreviewContent(family: StratumThemeId) {
    StratumTheme(themeId = family) {
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
                    onLoadNoteLists = { _, _ -> previewScreenNoteLists() },
                    onSave = {},
                ),
            onClose = {},
            initialLists = previewScreenNoteLists(),
        )
    }
}
