package com.stratum.feature.program.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumFilterChip
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarBackButton
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.program.R

/**
 * Full-screen two-step coach check-in wizard (recovery → recommendations).
 * Mirrors onboarding chrome: top step indicator + bottom primary action bar.
 */
@Composable
fun CheckInScreen(
    state: CheckInUiState,
    onBack: () -> Unit,
    onOverallRecoverySelected: (RecoverySignalUi) -> Unit,
    onHasLocalizedIssuesSelected: (Boolean) -> Unit,
    onToggleFlaggedMuscle: (String) -> Unit,
    onMuscleSignalSelected: (muscleKey: String, signal: RecoverySignalUi) -> Unit,
    onNext: () -> Unit,
    onToggleAccepted: (id: String, accepted: Boolean) -> Unit,
    onOpenSwapPicker: (id: String) -> Unit,
    onSelectSwapCandidate: (liftId: String, name: String) -> Unit,
    onDismissSwapPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.isOpen) return

    BackHandler(onBack = onBack)

    StratumScaffold(
        modifier = modifier,
        topBar = {
            StratumTopBar(
                title = stringResource(R.string.checkin_sheet_title),
                navigationIcon = {
                    StratumTopBarBackButton(
                        onClick = onBack,
                        contentDescription = stringResource(R.string.checkin_back),
                    )
                },
                bottom = {
                    CheckInStepIndicator(
                        stepCount = state.stepCount,
                        currentIndex = state.stepIndex,
                    )
                },
            )
        },
        bottomBar = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(StratumThemeAccessor.colors.surfaceBase)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                StratumButton(
                    text =
                        when {
                            state.phase == CheckInPhase.Subjective && state.isEvaluating ->
                                stringResource(R.string.checkin_evaluating)
                            state.phase == CheckInPhase.Recommendations && state.isApplying ->
                                stringResource(R.string.checkin_applying)
                            state.phase == CheckInPhase.Recommendations ->
                                stringResource(R.string.checkin_apply)
                            else -> stringResource(R.string.checkin_next)
                        },
                    onClick = onNext,
                    enabled = state.primaryActionEnabled,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when (state.phase) {
                CheckInPhase.Subjective ->
                    SubjectiveStep(
                        state = state,
                        onOverallRecoverySelected = onOverallRecoverySelected,
                        onHasLocalizedIssuesSelected = onHasLocalizedIssuesSelected,
                        onToggleFlaggedMuscle = onToggleFlaggedMuscle,
                        onMuscleSignalSelected = onMuscleSignalSelected,
                    )
                CheckInPhase.Recommendations ->
                    RecommendationsStep(
                        state = state,
                        onToggleAccepted = onToggleAccepted,
                        onOpenSwapPicker = onOpenSwapPicker,
                    )
            }
            state.errorMessage?.let { message ->
                StratumText(
                    text = message,
                    style = StratumThemeAccessor.typography.sectionLabel,
                )
            }
        }
    }

    if (state.swapPickerRecommendationId != null) {
        StratumBottomSheet(
            onDismissRequest = onDismissSwapPicker,
            title = stringResource(R.string.checkin_swap_picker_title),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.swapCandidates.forEach { candidate ->
                    SwapCandidateRow(
                        candidate = candidate,
                        onClick = { onSelectSwapCandidate(candidate.liftId, candidate.name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckInStepIndicator(
    stepCount: Int,
    currentIndex: Int,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(stepCount) { index ->
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(
                            color =
                                if (index == currentIndex) {
                                    StratumThemeAccessor.colors.accent
                                } else {
                                    StratumThemeAccessor.colors.borderSubtle
                                },
                        ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubjectiveStep(
    state: CheckInUiState,
    onOverallRecoverySelected: (RecoverySignalUi) -> Unit,
    onHasLocalizedIssuesSelected: (Boolean) -> Unit,
    onToggleFlaggedMuscle: (String) -> Unit,
    onMuscleSignalSelected: (muscleKey: String, signal: RecoverySignalUi) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SectionHeading(
            title = stringResource(R.string.checkin_overall_recovery_label),
            description = stringResource(R.string.checkin_overall_recovery_hint),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            systemicRecoveryOptions().forEach { signal ->
                StratumFilterChip(
                    label = signal.label(),
                    selected = state.overallRecovery == signal,
                    onSelectedChange = { onOverallRecoverySelected(signal) },
                )
            }
        }

        SectionHeading(
            title = stringResource(R.string.checkin_localized_question),
            description = stringResource(R.string.checkin_localized_question_hint),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StratumFilterChip(
                label = stringResource(R.string.checkin_localized_no),
                selected = state.hasLocalizedIssues == false,
                onSelectedChange = { onHasLocalizedIssuesSelected(false) },
            )
            StratumFilterChip(
                label = stringResource(R.string.checkin_localized_yes),
                selected = state.hasLocalizedIssues == true,
                onSelectedChange = { onHasLocalizedIssuesSelected(true) },
            )
        }

        if (state.hasLocalizedIssues == true) {
            SectionHeading(
                title = stringResource(R.string.checkin_pick_muscles_title),
                description = stringResource(R.string.checkin_pick_muscles_hint),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.availableMuscles.forEach { muscle ->
                    StratumFilterChip(
                        label = muscle.displayName,
                        selected = muscle.muscleKey in state.flaggedMuscleKeys,
                        onSelectedChange = { onToggleFlaggedMuscle(muscle.muscleKey) },
                    )
                }
            }

            state.muscleSignals.forEach { row ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StratumText(
                        text = row.displayName,
                        style = StratumThemeAccessor.typography.titleMedium,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        muscleIssueOptions().forEach { signal ->
                            StratumFilterChip(
                                label = signal.label(),
                                selected = row.signal == signal,
                                onSelectedChange = {
                                    onMuscleSignalSelected(row.muscleKey, signal)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationsStep(
    state: CheckInUiState,
    onToggleAccepted: (id: String, accepted: Boolean) -> Unit,
    onOpenSwapPicker: (id: String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.recommendations.isEmpty()) {
            StratumText(
                text = stringResource(R.string.checkin_no_changes),
                muted = true,
            )
        } else {
            StratumText(
                text = stringResource(R.string.checkin_recommendations_hint),
                muted = true,
                style = StratumThemeAccessor.typography.sectionLabel,
            )
            state.recommendations.forEach { rec ->
                RecommendationCard(
                    recommendation = rec,
                    onToggleAccepted = { onToggleAccepted(rec.id, it) },
                    onOpenSwapPicker = { onOpenSwapPicker(rec.id) },
                )
            }
        }
    }
}

@Composable
private fun RecommendationCard(
    recommendation: CheckInRecommendationUi,
    onToggleAccepted: (Boolean) -> Unit,
    onOpenSwapPicker: () -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val selected = recommendation.accepted
    val isSwap = recommendation is CheckInRecommendationUi.Swap
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(
                    width = shapes.borderWidth,
                    color = if (selected) colors.accent else colors.borderSubtle,
                    shape = shapes.panel,
                )
                .background(
                    color = if (selected) colors.surfaceRaised else colors.surfaceSunken,
                    shape = shapes.panel,
                )
                .then(
                    if (isSwap) {
                        Modifier.clickable(onClick = onOpenSwapPicker)
                    } else {
                        Modifier
                    },
                )
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StratumText(
            text =
                stringResource(
                    recommendation.titleRes,
                    *recommendation.titleArgs.toTypedArray(),
                ),
            style = StratumThemeAccessor.typography.titleMedium,
        )
        StratumText(
            text =
                stringResource(
                    recommendation.rationaleRes,
                    *recommendation.rationaleArgs.toTypedArray(),
                ),
            muted = true,
            style = StratumThemeAccessor.typography.sectionLabel,
        )
        if (isSwap) {
            StratumText(
                text = stringResource(R.string.checkin_tap_to_change_swap),
                style = StratumThemeAccessor.typography.sectionLabel,
            )
        }
        StratumFilterChip(
            label =
                if (selected) {
                    stringResource(R.string.checkin_include_selected)
                } else {
                    stringResource(R.string.checkin_include)
                },
            selected = selected,
            onSelectedChange = { onToggleAccepted(!selected) },
        )
    }
}

@Composable
private fun SectionHeading(
    title: String,
    description: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StratumText(
            text = title,
            style = StratumThemeAccessor.typography.titleMedium,
        )
        StratumText(
            text = description,
            muted = true,
            style = StratumThemeAccessor.typography.sectionLabel,
        )
    }
}

@Composable
private fun SwapCandidateRow(
    candidate: CheckInSwapCandidateUi,
    onClick: () -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(
                    width = shapes.borderWidth,
                    color = if (candidate.selected) colors.accent else colors.borderSubtle,
                    shape = shapes.panel,
                )
                .background(
                    color = if (candidate.selected) colors.accent else colors.surfaceRaised,
                    shape = shapes.panel,
                )
                .clickable(onClick = onClick)
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StratumText(
            text = candidate.name,
            style = StratumThemeAccessor.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RecoverySignalUi.label(): String =
    when (this) {
        RecoverySignalUi.Good -> stringResource(R.string.checkin_recovery_good)
        RecoverySignalUi.MildSoreness -> stringResource(R.string.checkin_recovery_mild)
        RecoverySignalUi.SevereUnderRecovery -> stringResource(R.string.checkin_recovery_severe)
        RecoverySignalUi.JointPain -> stringResource(R.string.checkin_recovery_joint)
    }

private fun systemicRecoveryOptions(): List<RecoverySignalUi> =
    listOf(
        RecoverySignalUi.Good,
        RecoverySignalUi.MildSoreness,
        RecoverySignalUi.SevereUnderRecovery,
    )

private fun muscleIssueOptions(): List<RecoverySignalUi> =
    listOf(
        RecoverySignalUi.MildSoreness,
        RecoverySignalUi.SevereUnderRecovery,
        RecoverySignalUi.JointPain,
    )
