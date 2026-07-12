package com.stratum.feature.workout.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId

@Preview(name = "Workout complete light", showBackground = true)
@Composable
private fun WorkoutCompleteScreenLightPreview() {
    StratumTheme {
        WorkoutCompleteScreenPreviewContent()
    }
}

@Preview(name = "Workout complete dark", showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun WorkoutCompleteScreenDarkPreview() {
    StratumTheme(darkTheme = true) {
        WorkoutCompleteScreenPreviewContent()
    }
}

@Preview(name = "Workout complete light", showBackground = true)
@Composable
private fun WorkoutCompleteScreenLightPreviewNeo() {
    StratumTheme(themeId = StratumThemeId.NeoBrutalist) {
        WorkoutCompleteScreenPreviewContent()
    }
}

@Preview(name = "Workout complete dark", showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun WorkoutCompleteScreenDarkPreviewNeo() {
    StratumTheme(themeId = StratumThemeId.NeoBrutalist, darkTheme = true) {
        WorkoutCompleteScreenPreviewContent()
    }
}

@Preview(name = "Workout complete proposals light", showBackground = true)
@Composable
private fun WorkoutCompleteProposalsLightPreview() {
    StratumTheme {
        WorkoutCompleteScreen(
            state = previewLoadedWithProposals(),
            onClose = {},
        )
    }
}

@Preview(
    name = "Workout complete proposals dark",
    showBackground = true,
    backgroundColor = 0xFF121212,
)
@Composable
private fun WorkoutCompleteProposalsDarkPreview() {
    StratumTheme(darkTheme = true) {
        WorkoutCompleteScreen(
            state = previewLoadedWithProposals(),
            onClose = {},
        )
    }
}

@Composable
private fun WorkoutCompleteScreenPreviewContent() {
    WorkoutCompleteScreen(
        state = previewLoadedBase(),
        onClose = {},
    )
}

private fun previewLoadedBase(): WorkoutCompleteUiState.Loaded =
    WorkoutCompleteUiState.Loaded(
        workoutName = "Push Day — Wk 4",
        durationFormatted = "1h 12m 38s",
        bestSets =
            listOf(
                LiftBestSetUi(
                    liftName = "Barbell Bench Press",
                    performanceLabel = "185 × 3",
                    rirLabel = "1",
                    estimatedOneRmLabel = "201",
                    isPersonalRecord = true,
                ),
                LiftBestSetUi(
                    liftName = "Incline Dumbbell Press",
                    performanceLabel = "80 × 8",
                    rirLabel = "2",
                    estimatedOneRmLabel = "101",
                    isPersonalRecord = false,
                ),
                LiftBestSetUi(
                    liftName = "Cable Fly",
                    performanceLabel = "35 × 12",
                    rirLabel = "—",
                    estimatedOneRmLabel = "50",
                    isPersonalRecord = false,
                ),
            ),
    )

private fun previewLoadedWithProposals(): WorkoutCompleteUiState.Loaded =
    previewLoadedBase().copy(
        repRangeProposals =
            listOf(
                RepRangeShiftCardUi(
                    id = "rrs:wl-bench:8-10->14-16",
                    liftName = "Barbell Bench Press",
                    currentBand = "8–10",
                    proposedBand = "14–16",
                    lastSetFloor = 5,
                    bandSummary = "8–10 → 14–16",
                ),
            ),
        equipmentProposals =
            listOf(
                EquipmentProposalCardUi(
                    id = "cable|lat|2.5",
                    title = "Weight steps look different",
                    subtitle = "Create \"Lat pulldown stack\" with matching steps (instead of Cables).",
                    tierSummary = "0+ → 2.5",
                    liftNames = "Lat pulldown",
                    primaryButtonLabel = "Accept",
                    requiresConfirm = false,
                    blastRadiusCount = 12,
                    alternativeButtonLabel = "Update shared instead",
                ),
                EquipmentProposalCardUi(
                    id = "custom|row|5",
                    title = "Update your equipment steps",
                    subtitle = "Adjust \"My stack\" defaults to match this session.",
                    tierSummary = "0–100 → 5 · 100+ → 10",
                    liftNames = "Seated row",
                    primaryButtonLabel = "Accept",
                    requiresConfirm = false,
                    blastRadiusCount = 1,
                    alternativeButtonLabel = null,
                ),
            ),
    )
