package com.stratum.feature.program.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.feature.program.R

@Preview(name = "Check-in subjective light")
@Composable
fun CheckInSubjectivePreview() {
    StratumTheme {
        PreviewCheckIn(subjectiveBaseState())
    }
}

@Preview(name = "Check-in subjective dark")
@Composable
fun CheckInSubjectiveDarkPreview() {
    StratumTheme(darkTheme = true) {
        PreviewCheckIn(subjectiveBaseState())
    }
}

@Preview(name = "Check-in localized muscles light")
@Composable
fun CheckInLocalizedMusclesPreview() {
    StratumTheme {
        PreviewCheckIn(subjectiveLocalizedState())
    }
}

@Preview(name = "Check-in localized muscles dark")
@Composable
fun CheckInLocalizedMusclesDarkPreview() {
    StratumTheme(darkTheme = true) {
        PreviewCheckIn(subjectiveLocalizedState())
    }
}

@Preview(name = "Check-in recommendations light")
@Composable
fun CheckInRecommendationsPreview() {
    StratumTheme {
        PreviewCheckIn(recommendationsMixedState())
    }
}

@Preview(name = "Check-in recommendations dark")
@Composable
fun CheckInRecommendationsDarkPreview() {
    StratumTheme(darkTheme = true) {
        PreviewCheckIn(recommendationsMixedState())
    }
}

@Preview(name = "Check-in recommendations empty light")
@Composable
fun CheckInRecommendationsEmptyPreview() {
    StratumTheme {
        PreviewCheckIn(recommendationsEmptyState())
    }
}

@Preview(name = "Check-in recommendations empty dark")
@Composable
fun CheckInRecommendationsEmptyDarkPreview() {
    StratumTheme(darkTheme = true) {
        PreviewCheckIn(recommendationsEmptyState())
    }
}

@Preview(name = "Check-in swap picker dark")
@Composable
fun CheckInSwapPickerDarkPreview() {
    StratumTheme(darkTheme = true) {
        PreviewCheckIn(recommendationsWithSwapPickerState())
    }
}

@Composable
private fun PreviewCheckIn(state: CheckInUiState) {
    CheckInScreen(
        state = state,
        onBack = {},
        onOverallRecoverySelected = {},
        onHasLocalizedIssuesSelected = {},
        onToggleFlaggedMuscle = {},
        onMuscleSignalSelected = { _, _ -> },
        onNext = {},
        onToggleAccepted = { _, _ -> },
        onOpenSwapPicker = {},
        onSelectSwapCandidate = { _, _ -> },
        onDismissSwapPicker = {},
    )
}

private fun subjectiveBaseState(): CheckInUiState =
    CheckInUiState(
        isOpen = true,
        phase = CheckInPhase.Subjective,
        overallRecovery = RecoverySignalUi.Good,
        hasLocalizedIssues = null,
        availableMuscles = previewMuscles(),
    )

private fun subjectiveLocalizedState(): CheckInUiState =
    CheckInUiState(
        isOpen = true,
        phase = CheckInPhase.Subjective,
        overallRecovery = RecoverySignalUi.MildSoreness,
        hasLocalizedIssues = true,
        availableMuscles = previewMuscles(),
        flaggedMuscleKeys = setOf("chest", "shoulder"),
        muscleSignals =
            listOf(
                CheckInMuscleSignalUi(
                    muscleKey = "chest",
                    displayName = "Chest",
                    signal = RecoverySignalUi.MildSoreness,
                ),
                CheckInMuscleSignalUi(
                    muscleKey = "shoulder",
                    displayName = "Shoulder",
                    signal = RecoverySignalUi.JointPain,
                ),
            ),
    )

private fun recommendationsMixedState(): CheckInUiState =
    CheckInUiState(
        isOpen = true,
        phase = CheckInPhase.Recommendations,
        overallRecovery = RecoverySignalUi.MildSoreness,
        hasLocalizedIssues = false,
        recommendations =
            listOf(
                CheckInRecommendationUi.Swap(
                    id = "swap-1",
                    titleRes = R.string.checkin_rec_title_swap,
                    titleArgs = listOf("Barbell Bench Press", "DB Bench Press"),
                    rationaleRes = R.string.checkin_rationale_soft_stale_swap_flat,
                    rationaleArgs = listOf(8),
                    accepted = true,
                    liftName = "Barbell Bench Press",
                    currentLiftId = "lift-bench",
                    currentSetCount = 3,
                    suggestedLiftId = "lift-db-bench",
                    suggestedLiftName = "DB Bench Press",
                ),
                CheckInRecommendationUi.IncreaseVolume(
                    id = "inc-1",
                    titleRes = R.string.checkin_rec_title_increase,
                    titleArgs = listOf(1, "Lat Pulldown"),
                    rationaleRes = R.string.checkin_rationale_soft_fresh_increase_flat,
                    rationaleArgs = listOf(3),
                    accepted = true,
                    liftName = "Lat Pulldown",
                    currentLiftId = "lift-pulldown",
                    currentSetCount = 3,
                    addSets = 1,
                ),
                CheckInRecommendationUi.CutVolume(
                    id = "cut-1",
                    titleRes = R.string.checkin_rec_title_cut,
                    titleArgs = listOf(1, "Overhead Press"),
                    rationaleRes = R.string.checkin_rationale_joint_cut,
                    rationaleArgs = listOf(25),
                    accepted = false,
                    liftName = "Overhead Press",
                    currentLiftId = "lift-ohp",
                    currentSetCount = 3,
                    removeSets = 1,
                ),
            ),
    )

private fun recommendationsEmptyState(): CheckInUiState =
    CheckInUiState(
        isOpen = true,
        phase = CheckInPhase.Recommendations,
        overallRecovery = RecoverySignalUi.Good,
        hasLocalizedIssues = false,
        recommendations = emptyList(),
    )

private fun recommendationsWithSwapPickerState(): CheckInUiState =
    recommendationsMixedState().copy(
        swapPickerRecommendationId = "swap-1",
        swapCandidates =
            listOf(
                CheckInSwapCandidateUi(
                    liftId = "lift-db-bench",
                    name = "DB Bench Press",
                    selected = true,
                ),
                CheckInSwapCandidateUi(
                    liftId = "lift-incline",
                    name = "Incline DB Press",
                    selected = false,
                ),
                CheckInSwapCandidateUi(
                    liftId = "lift-machine-press",
                    name = "Machine Chest Press",
                    selected = false,
                ),
            ),
    )

private fun previewMuscles(): List<CheckInMuscleOptionUi> =
    listOf(
        CheckInMuscleOptionUi(muscleKey = "chest", displayName = "Chest"),
        CheckInMuscleOptionUi(muscleKey = "back", displayName = "Back"),
        CheckInMuscleOptionUi(muscleKey = "shoulder", displayName = "Shoulder"),
        CheckInMuscleOptionUi(muscleKey = "quad", displayName = "Quads"),
        CheckInMuscleOptionUi(muscleKey = "hamstring", displayName = "Hamstrings"),
    )
