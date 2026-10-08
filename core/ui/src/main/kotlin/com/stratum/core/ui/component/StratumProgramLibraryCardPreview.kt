package com.stratum.core.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId

@PreviewLightDark
@Composable
private fun StratumProgramLibraryCardExpandedPreview() {
    ProgramLibraryCardPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewProgramLibraryCardActiveState,
        expanded = true,
    )
}

@PreviewLightDark
@Composable
private fun StratumProgramLibraryCardCollapsedPreview() {
    ProgramLibraryCardPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewProgramLibraryCardInactiveState,
        expanded = false,
    )
}

@Composable
internal fun ProgramLibraryCardPreviewContent(
    family: StratumThemeId,
    state: ProgramLibraryCardState,
    expanded: Boolean,
) {
    StratumTheme(themeId = family) {
        StratumProgramLibraryCard(
            state = state,
            expanded = expanded,
            activeLabel = "Active",
            workoutCountLabel = "5 workouts",
            onExpandedChange = {},
            onProgramClick = {},
            onActivateClick = {},
            onWorkoutClick = {},
            expandContentDescription = "Expand",
            collapseContentDescription = "Collapse",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
        )
    }
}

internal val previewProgramLibraryCardActiveState =
    ProgramLibraryCardState(
        programId = "preview-program",
        name = "Generated Program",
        workoutCount = 5,
        isActive = true,
        days =
            listOf(
                ProgramLibraryDayState(
                    id = "s1",
                    name = "Session 1",
                    isRest = false,
                    liftNamesSummary =
                        "V-Bar Pullup, Katana Triceps Extension, Lying T-Bar Row",
                    muscleLabels = listOf("Back", "Hamstrings", "Side Delts", "Triceps"),
                    workoutId = "w1",
                ),
                ProgramLibraryDayState(
                    id = "s2",
                    name = "Session 2",
                    isRest = false,
                    liftNamesSummary =
                        "Cable Chest Press, Cable Y-Raise, Cable V-Bar Triceps Pushdown",
                    muscleLabels = listOf("Chest", "Quads", "Side Delts", "Triceps", "Calves"),
                    workoutId = "w2",
                ),
                ProgramLibraryDayState(
                    id = "rest1",
                    name = "Rest Day",
                    isRest = true,
                ),
            ),
    )

internal val previewProgramLibraryCardInactiveState =
    previewProgramLibraryCardActiveState.copy(isActive = false)
