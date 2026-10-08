package com.stratum.feature.program.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.stratum.core.ui.component.ProgramLibraryCardState
import com.stratum.core.ui.component.ProgramLibraryDayState
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId

/**
 * Screenshot-test previews for [ProgramLibraryScreen] (STR-92). `darkTheme` is left null
 * so `@PreviewLightDark` genuinely varies light/dark.
 */
@PreviewLightDark
@Composable
private fun ProgramLibraryLoadedTonalPreview() {
    ProgramLibraryLoadedPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun ProgramLibraryLoadedNeoPreview() {
    ProgramLibraryLoadedPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewLightDark
@Composable
private fun ProgramLibraryEmptyTonalPreview() {
    ProgramLibraryEmptyPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun ProgramLibraryEmptyNeoPreview() {
    ProgramLibraryEmptyPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@Composable
internal fun ProgramLibraryLoadedPreviewContent(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        ProgramLibraryScreen(
            state =
                ProgramLibraryUiState(
                    programs =
                        listOf(
                            ProgramLibraryCardState(
                                programId = "p-1",
                                name = "Minimal 4 Day",
                                workoutCount = 4,
                                isActive = true,
                                days =
                                    listOf(
                                        ProgramLibraryDayState(
                                            id = "d-1",
                                            name = "Lower A",
                                            isRest = false,
                                            liftNamesSummary = "Squat, Romanian Deadlift",
                                            muscleLabels = listOf("Quads", "Glutes"),
                                            workoutId = "w-1",
                                        ),
                                        ProgramLibraryDayState(
                                            id = "d-2",
                                            name = "Rest",
                                            isRest = true,
                                        ),
                                    ),
                            ),
                            ProgramLibraryCardState(
                                programId = "p-2",
                                name = "Push / Pull",
                                workoutCount = 2,
                                isActive = false,
                                days =
                                    listOf(
                                        ProgramLibraryDayState(
                                            id = "d-3",
                                            name = "Push Day",
                                            isRest = false,
                                            liftNamesSummary = "Bench Press, Overhead Press",
                                            muscleLabels = listOf("Chest", "Triceps"),
                                            workoutId = "w-2",
                                        ),
                                    ),
                            ),
                        ),
                    expandedProgramIds = setOf("p-1"),
                ),
            onBackClick = {},
            onProgramClick = {},
            onWorkoutClick = { _, _ -> },
            onActivateClick = {},
            onExpandToggle = {},
            onDeleteRequest = {},
            onDeleteConfirm = {},
            onDeleteDismiss = {},
            onActivateConfirm = {},
            onActivateDismiss = {},
            onUserMessageShown = {},
        )
    }
}

@Composable
internal fun ProgramLibraryEmptyPreviewContent(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        ProgramLibraryScreen(
            state = ProgramLibraryUiState(),
            onBackClick = {},
            onProgramClick = {},
            onWorkoutClick = { _, _ -> },
            onActivateClick = {},
            onExpandToggle = {},
            onDeleteRequest = {},
            onDeleteConfirm = {},
            onDeleteDismiss = {},
            onActivateConfirm = {},
            onActivateDismiss = {},
            onUserMessageShown = {},
        )
    }
}
