package com.stratum.liftconfig

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as UiR
import com.stratum.core.ui.component.StratumMuscleFilterCardState
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.StratumThemeId
import com.stratum.domain.onboarding.MuscleGroup

@PreviewLightDark
@Composable
private fun CreateLiftBlankTonalPreview() {
    CreateLiftPreviewContent(family = StratumThemeId.Tonal, state = previewCreateLiftBlank())
}

@PreviewLightDark
@Composable
private fun CreateLiftBlankNeoPreview() {
    CreateLiftPreviewContent(family = StratumThemeId.NeoBrutalist, state = previewCreateLiftBlank())
}

@PreviewLightDark
@Composable
private fun CreateLiftFilledTonalPreview() {
    CreateLiftPreviewContent(family = StratumThemeId.Tonal, state = previewCreateLiftFilled())
}

@PreviewLightDark
@Composable
private fun CreateLiftFilledNeoPreview() {
    CreateLiftPreviewContent(family = StratumThemeId.NeoBrutalist, state = previewCreateLiftFilled())
}

@PreviewLightDark
@Composable
private fun ManageEquipmentLoadedTonalPreview() {
    ManageEquipmentPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewManageEquipment(),
        selectionMode = false,
    )
}

@PreviewLightDark
@Composable
private fun ManageEquipmentLoadedNeoPreview() {
    ManageEquipmentPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = previewManageEquipment(),
        selectionMode = false,
    )
}

@PreviewLightDark
@Composable
private fun ManageEquipmentSelectionTonalPreview() {
    ManageEquipmentPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewManageEquipment(),
        selectionMode = true,
    )
}

@PreviewLightDark
@Composable
private fun ManageEquipmentSelectionNeoPreview() {
    ManageEquipmentPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = previewManageEquipment(),
        selectionMode = true,
    )
}

@PreviewLightDark
@Composable
private fun ManageEquipmentDeletingTonalPreview() {
    ManageEquipmentPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewManageEquipmentDeleting(),
        selectionMode = false,
    )
}

@PreviewLightDark
@Composable
private fun ManageEquipmentDeletingNeoPreview() {
    ManageEquipmentPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = previewManageEquipmentDeleting(),
        selectionMode = false,
    )
}

@PreviewLightDark
@Composable
private fun LiftInformationInstructionsTonalPreview() {
    LiftInformationPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewLiftInformation(LiftInfoTab.Instructions),
    )
}

@PreviewLightDark
@Composable
private fun LiftInformationInstructionsNeoPreview() {
    LiftInformationPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = previewLiftInformation(LiftInfoTab.Instructions),
    )
}

@PreviewLightDark
@Composable
private fun LiftInformationDetailsTonalPreview() {
    LiftInformationPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewLiftInformation(LiftInfoTab.Details),
    )
}

@PreviewLightDark
@Composable
private fun LiftInformationDetailsNeoPreview() {
    LiftInformationPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = previewLiftInformation(LiftInfoTab.Details),
    )
}

@PreviewLightDark
@Composable
private fun LiftInformationHistoryTonalPreview() {
    LiftInformationPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewLiftInformation(LiftInfoTab.History),
    )
}

@PreviewLightDark
@Composable
private fun LiftInformationHistoryNeoPreview() {
    LiftInformationPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = previewLiftInformation(LiftInfoTab.History),
    )
}

@Composable
internal fun CreateLiftPreviewContent(
    family: StratumThemeId,
    state: CreateLiftUiState,
) {
    StratumTheme(themeId = family) {
        CreateLiftScreen(
            state = state,
            onBackClick = {},
            onConfirm = {},
            onNameChange = {},
            actions = previewDetailsActions(),
        )
    }
}

@Composable
internal fun ManageEquipmentPreviewContent(
    family: StratumThemeId,
    state: ManageEquipmentUiState,
    selectionMode: Boolean,
) {
    StratumTheme(themeId = family) {
        ManageEquipmentScreen(
            state = state,
            selectionMode = selectionMode,
            actions =
                ManageEquipmentActions(
                    onBackClick = {},
                    onOpenIncrements = {},
                    onEquipmentSelected = {},
                    onOpenCreate = {},
                    onDismissCreate = {},
                    onCreateEquipment = {},
                    onNameDraftChange = {},
                ),
        )
    }
}

/**
 * Swiped-open equipment row for screenshot goldens (STR-130). The real swipe
 * state needs a gesture, so this fixture reproduces the revealed layout
 * statically: the same panel clip + transparent foreground the production
 * call site passes to StratumSwipeToRevealRow, with the real row content
 * shifted by the component's 25% reveal fraction.
 */
@Composable
internal fun ManageEquipmentRevealedPreviewContent(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        val colors = StratumThemeAccessor.colors
        val shapes = StratumThemeAccessor.shapes
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(colors.surfaceBase)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            // STR-105 preview rows are deletable; mirror that here.
            val row = previewManageEquipment().rows[1].copy(canDelete = true)
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(shapes.panel),
            ) {
                val revealWidth = maxWidth * 0.25f
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .background(colors.error),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.CenterEnd)
                                .width(revealWidth)
                                .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(UiR.drawable.ic_remove),
                                contentDescription = null,
                                tint = colors.error,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }
                Box(modifier = Modifier.offset(x = -revealWidth)) {
                    EquipmentRowContent(
                        row = row,
                        selectionMode = false,
                        onClick = {},
                        onEditIncrements = {},
                        onCopyAsCustom = {},
                    )
                }
            }
        }
    }
}

@Composable
internal fun LiftInformationPreviewContent(
    family: StratumThemeId,
    state: LiftInformationUiState,
) {
    StratumTheme(themeId = family) {
        LiftInformationScreen(
            state = state,
            onBackClick = {},
            actions =
                LiftInformationActions(
                    onTabSelected = {},
                    onMuscleRoleSelected = {},
                    onSessionExpandedToggled = {},
                    onShowAddMuscleSheet = {},
                    onAddMuscle = {},
                    onRemoveMuscle = {},
                    onOpenDetailEditor = {},
                    onDismissDetailEditor = {},
                    onSelectLengthBias = {},
                    onSelectFatigue = {},
                    onSelectLoadType = {},
                    onToggleDraftMovementPattern = {},
                    onConfirmMovementPatterns = {},
                    onAssignBodyweightEquipment = {},
                    onChooseEquipment = {},
                    onRestDraftChange = {},
                    onSaveDefaultRest = {},
                ),
        )
    }
}

internal fun previewCreateLiftBlank(): CreateLiftUiState =
    CreateLiftUiState(
        details = previewDetailsState(categoryLabel = null),
    )

internal fun previewCreateLiftFilled(): CreateLiftUiState =
    CreateLiftUiState(
        name = "Zercher Squat",
        details = previewDetailsState(categoryLabel = null),
        canConfirm = true,
    )

internal fun previewManageEquipment(): ManageEquipmentUiState =
    ManageEquipmentUiState(
        rows =
            listOf(
                EquipmentRowUi(
                    id = "eq-barbell",
                    name = "Olympic Barbell",
                    tierChips =
                        listOf(
                            EquipmentTierChipUi(
                                lowerBound = "45 lb",
                                upperBound = "135 lb",
                                increment = "5 lb",
                            ),
                            EquipmentTierChipUi(
                                lowerBound = "135 lb",
                                upperBound = null,
                                increment = "10 lb",
                            ),
                        ),
                    isCustom = false,
                ),
                EquipmentRowUi(
                    id = "eq-dumbbell",
                    name = "Adjustable Dumbbell",
                    tierChips =
                        listOf(
                            EquipmentTierChipUi(
                                lowerBound = "10 lb",
                                upperBound = null,
                                increment = "5 lb",
                            ),
                        ),
                    isCustom = true,
                ),
            ),
    )

internal fun previewManageEquipmentDeleting(): ManageEquipmentUiState =
    previewManageEquipment().copy(
        pendingDelete = PendingDeleteUi(id = "eq-dumbbell", name = "Adjustable Dumbbell"),
    )

internal fun previewManageEquipmentReassigning(): ManageEquipmentUiState =
    previewManageEquipment().copy(
        pendingDelete =
            PendingDeleteUi(
                id = "eq-dumbbell",
                name = "Adjustable Dumbbell",
                affectedLiftNames = listOf("Dumbbell Press"),
                affectedWorkouts = listOf(AffectedWorkoutUi(name = "Push Day", slotCount = 2)),
                candidates =
                    listOf(
                        ReplacementOptionUi(id = "eq-barbell", name = "Olympic Barbell"),
                    ),
                selectedReplacementId = "eq-barbell",
            ),
    )

internal fun previewManageEquipmentFallback(): ManageEquipmentUiState =
    previewManageEquipment().copy(
        pendingDelete =
            PendingDeleteUi(
                id = "eq-dumbbell",
                name = "Adjustable Dumbbell",
                affectedLiftNames = listOf("Dumbbell Press"),
                usesBodyweightFallback = true,
            ),
    )

internal fun previewLiftInformation(tab: LiftInfoTab): LiftInformationUiState =
    LiftInformationUiState(
        liftId = "lift-bench",
        liftName = "Barbell Bench Press",
        isCustom = false,
        selectedTab = tab,
        description = "Lie on a flat bench, unrack the bar, lower to mid-chest, and press back up.",
        personalRecord =
            LiftPrUi(
                dateText = "Oct 5",
                weightRepsText = "225 lb × 5",
                rir = 1,
                e1rmText = "256 lb",
            ),
        historySessions =
            listOf(
                LiftHistorySessionUi(
                    sessionId = "session-1",
                    dateText = "Oct 5",
                    workoutLabel = "Push Day",
                    bestSetText = "225 lb × 5",
                    sets =
                        listOf(
                            LiftHistorySetUi(setIndex = 1, weightRepsText = "225 lb × 5", rir = 2),
                            LiftHistorySetUi(setIndex = 2, weightRepsText = "225 lb × 4", rir = 1),
                        ),
                    expanded = true,
                ),
                LiftHistorySessionUi(
                    sessionId = "session-2",
                    dateText = "Oct 1",
                    workoutLabel = "Push Day",
                    bestSetText = "220 lb × 5",
                    sets =
                        listOf(
                            LiftHistorySetUi(setIndex = 1, weightRepsText = "220 lb × 5", rir = 2),
                        ),
                    expanded = false,
                ),
            ),
        details = previewDetailsState(categoryLabel = "Compound Upper"),
    )

private fun previewDetailsState(categoryLabel: String?): LiftDetailsEditorState =
    LiftDetailsEditorState(
        canEdit = true,
        primaryMuscles =
            listOf(
                StratumMuscleFilterCardState(MuscleGroup.Chest, MuscleGroup.Chest.displayLabel),
                StratumMuscleFilterCardState(MuscleGroup.Tricep, MuscleGroup.Tricep.displayLabel),
            ),
        secondaryMuscles =
            listOf(
                StratumMuscleFilterCardState(
                    MuscleGroup.AnteriorDeltoid,
                    MuscleGroup.AnteriorDeltoid.displayLabel,
                ),
            ),
        lengthBiasLabel = "Mid",
        movementPatternLabels = listOf("Horizontal Press"),
        fatigueLabel = "Low",
        loadTypeLabel = "Weighted",
        equipment = "Olympic Barbell",
        categoryLabel = categoryLabel,
        defaultRestLabel = "2:00",
    )

private fun previewDetailsActions(): LiftDetailsEditorActions =
    LiftDetailsEditorActions(
        onMuscleRoleSelected = {},
        onShowAddMuscleSheet = {},
        onAddMuscle = {},
        onRemoveMuscle = {},
        onOpenDetailEditor = {},
        onDismissDetailEditor = {},
        onSelectLengthBias = {},
        onSelectFatigue = {},
        onSelectLoadType = {},
        onToggleDraftMovementPattern = {},
        onConfirmMovementPatterns = {},
        onAssignBodyweightEquipment = {},
        onChooseEquipment = {},
        onRestDraftChange = {},
        onSaveDefaultRest = {},
    )
