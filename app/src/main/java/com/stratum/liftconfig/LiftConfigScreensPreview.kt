package com.stratum.liftconfig

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as UiR
import com.stratum.core.ui.component.StratumMuscleFilterCardState
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumSpacing
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
private fun ManageEquipmentReassigningLongListTonalPreview() {
    ManageEquipmentPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewManageEquipmentReassigningLongList(),
        selectionMode = false,
    )
}

@PreviewLightDark
@Composable
private fun ManageEquipmentReassigningLongListNeoPreview() {
    ManageEquipmentPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = previewManageEquipmentReassigningLongList(),
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

/** STR-335: the reassign state before a replacement is picked (Delete stays disabled). */
internal fun previewManageEquipmentReassigningNoPick(): ManageEquipmentUiState =
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
                selectedReplacementId = null,
            ),
    )

/** STR-134: a candidate list long enough to overflow the dialog unbounded. */
internal fun previewManageEquipmentReassigningLongList(): ManageEquipmentUiState =
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
                        ReplacementOptionUi(id = "eq-cable-y", name = "Cable Y-Raise stack"),
                        ReplacementOptionUi(id = "eq-cable-cross", name = "Cable crossover"),
                        ReplacementOptionUi(id = "eq-lat-stack", name = "Lat pulldown stack"),
                        ReplacementOptionUi(id = "eq-row-stack", name = "Seated row stack"),
                        ReplacementOptionUi(id = "eq-pec-deck", name = "Pec deck"),
                        ReplacementOptionUi(id = "eq-leg-ext", name = "Leg extension"),
                        ReplacementOptionUi(id = "eq-leg-curl", name = "Leg curl"),
                        ReplacementOptionUi(id = "eq-smith", name = "Smith machine"),
                        ReplacementOptionUi(id = "eq-trap-bar", name = "Trap bar"),
                        ReplacementOptionUi(id = "eq-ssb", name = "Safety squat bar"),
                        ReplacementOptionUi(id = "eq-ez-bar", name = "EZ curl bar"),
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

/** STR-334: a cable station with more affected lifts than the preview shows. */
internal fun previewManageEquipmentReassigningManyAffected(): ManageEquipmentUiState =
    previewManageEquipment().copy(
        pendingDelete =
            PendingDeleteUi(
                id = "eq-hoist-low-row",
                name = "Hoist Low-Row",
                affectedLiftNames =
                    listOf(
                        "Bayesian Curl",
                        "Bicep Curl (Cable EZ Bar)",
                        "Bicep Curl (Cable Single-Arm)",
                        "Cable Chest Fly",
                        "Cable Crossover",
                        "Cable Crunch",
                        "Cable Row (Mid Neutral Grip)",
                        "Face Pull",
                    ),
                candidates =
                    listOf(
                        ReplacementOptionUi(id = "eq-barbell", name = "Olympic Barbell"),
                        ReplacementOptionUi(id = "eq-cable-cross", name = "Cable crossover"),
                        ReplacementOptionUi(id = "eq-lat-stack", name = "Lat pulldown stack"),
                    ),
                selectedReplacementId = "eq-barbell",
            ),
    )

/**
 * STR-335: the delete sheet body rendered inline (sheet chrome is
 * framework-owned). The container mirrors StratumBottomSheet's inner Column:
 * surface-inset background, same paddings and spacing, title on top.
 */
@Composable
internal fun ManageEquipmentDeleteSheetPreviewContent(
    family: StratumThemeId,
    state: ManageEquipmentUiState,
) {
    val pending = state.pendingDelete
    StratumTheme(themeId = family) {
        if (pending != null) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(StratumThemeAccessor.colors.surfaceInset)
                        .padding(horizontal = StratumSpacing.s12)
                        .padding(top = StratumSpacing.s4, bottom = StratumSpacing.s32),
                verticalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
            ) {
                StratumText(
                    text = stringResource(UiR.string.equipment_delete_title, pending.name),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = StratumSpacing.s8),
                    style = StratumThemeAccessor.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                EquipmentDeleteSheetContent(
                    name = pending.name,
                    onConfirm = {},
                    onDismiss = {},
                    affectedLiftNames = pending.affectedLiftNames,
                    affectedWorkouts = pending.affectedWorkouts,
                    candidates = pending.candidates,
                    selectedReplacementId = pending.selectedReplacementId,
                    usesBodyweightFallback = pending.usesBodyweightFallback,
                    canConfirmDelete = pending.canConfirmDelete,
                    onOpenPicker = {},
                )
            }
        }
    }
}

/** STR-335: the full-screen replacement picker over the given state's candidate set. */
@Composable
internal fun ManageEquipmentDeletePickerPreviewContent(
    family: StratumThemeId,
    state: ManageEquipmentUiState = previewManageEquipmentReassigningManyAffected(),
) {
    val pending = state.pendingDelete
    StratumTheme(themeId = family) {
        if (pending != null) {
            ReplacementCatalogPickerContent(
                candidates = pending.candidates,
                selectedReplacementId = pending.selectedReplacementId,
                onSelect = {},
                onBack = {},
            )
        }
    }
}

/** STR-334: the Bodyweight fallback with more affected lifts than the preview shows. */
internal fun previewManageEquipmentFallbackManyAffected(): ManageEquipmentUiState =
    previewManageEquipment().copy(
        pendingDelete =
            PendingDeleteUi(
                id = "eq-hoist-low-row",
                name = "Hoist Low-Row",
                affectedLiftNames =
                    listOf(
                        "Bayesian Curl",
                        "Cable Chest Fly",
                        "Cable Crossover",
                        "Cable Row (Mid Neutral Grip)",
                        "Face Pull",
                    ),
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
