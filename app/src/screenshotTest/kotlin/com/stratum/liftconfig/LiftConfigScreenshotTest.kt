package com.stratum.liftconfig

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.stratum.core.ui.theme.StratumThemeId

/**
 * Screenshot tests for liftconfig (STR-93). Thin `@PreviewTest` wrappers over the shared preview
 * content in `LiftConfigScreensPreview.kt` and `IncrementEditorScreenPreview.kt`.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun createLiftBlankTonal() {
    CreateLiftPreviewContent(family = StratumThemeId.Tonal, state = previewCreateLiftBlank())
}

@PreviewTest
@PreviewLightDark
@Composable
fun createLiftBlankNeo() {
    CreateLiftPreviewContent(family = StratumThemeId.NeoBrutalist, state = previewCreateLiftBlank())
}

@PreviewTest
@PreviewLightDark
@Composable
fun createLiftFilledTonal() {
    CreateLiftPreviewContent(family = StratumThemeId.Tonal, state = previewCreateLiftFilled())
}

@PreviewTest
@PreviewLightDark
@Composable
fun createLiftFilledNeo() {
    CreateLiftPreviewContent(family = StratumThemeId.NeoBrutalist, state = previewCreateLiftFilled())
}

@PreviewTest
@PreviewLightDark
@Composable
fun manageEquipmentLoadedTonal() {
    ManageEquipmentPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewManageEquipment(),
        selectionMode = false,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun manageEquipmentLoadedNeo() {
    ManageEquipmentPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = previewManageEquipment(),
        selectionMode = false,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun manageEquipmentSelectionTonal() {
    ManageEquipmentPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewManageEquipment(),
        selectionMode = true,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun manageEquipmentSelectionNeo() {
    ManageEquipmentPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = previewManageEquipment(),
        selectionMode = true,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun manageEquipmentDeletingTonal() {
    ManageEquipmentPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewManageEquipmentDeleting(),
        selectionMode = false,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun manageEquipmentDeletingNeo() {
    ManageEquipmentPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = previewManageEquipmentDeleting(),
        selectionMode = false,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun manageEquipmentRevealedTonal() {
    ManageEquipmentRevealedPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun manageEquipmentRevealedNeo() {
    ManageEquipmentRevealedPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun manageEquipmentReassigningTonal() {
    ManageEquipmentPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewManageEquipmentReassigning(),
        selectionMode = false,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun manageEquipmentReassigningNeo() {
    ManageEquipmentPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = previewManageEquipmentReassigning(),
        selectionMode = false,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun manageEquipmentFallbackTonal() {
    ManageEquipmentPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewManageEquipmentFallback(),
        selectionMode = false,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun manageEquipmentFallbackNeo() {
    ManageEquipmentPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = previewManageEquipmentFallback(),
        selectionMode = false,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun liftInformationInstructionsTonal() {
    LiftInformationPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewLiftInformation(LiftInfoTab.Instructions),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun liftInformationInstructionsNeo() {
    LiftInformationPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = previewLiftInformation(LiftInfoTab.Instructions),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun liftInformationDetailsTonal() {
    LiftInformationPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewLiftInformation(LiftInfoTab.Details),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun liftInformationDetailsNeo() {
    LiftInformationPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = previewLiftInformation(LiftInfoTab.Details),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun liftInformationHistoryTonal() {
    LiftInformationPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewLiftInformation(LiftInfoTab.History),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun liftInformationHistoryNeo() {
    LiftInformationPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = previewLiftInformation(LiftInfoTab.History),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun equipmentIncrementsTonal() {
    // selection = null: an open band sheet is a dialog window and blanks the golden.
    IncrementEditorPreview(
        chrome = PREVIEW_EQUIPMENT_CHROME,
        title = "Cables",
        selection = null,
        family = StratumThemeId.Tonal,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun equipmentIncrementsNeo() {
    // selection = null: an open band sheet is a dialog window and blanks the golden.
    IncrementEditorPreview(
        chrome = PREVIEW_EQUIPMENT_CHROME,
        title = "Cables",
        selection = null,
        family = StratumThemeId.NeoBrutalist,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun equipmentIncrementsDeleteTonal() {
    IncrementEditorPreview(
        chrome = PREVIEW_EQUIPMENT_CHROME,
        title = "Cables",
        selection = null,
        family = StratumThemeId.Tonal,
        deleteAction = IncrementEditorDeleteAction(text = "Delete equipment", onClick = {}),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun equipmentIncrementsDeleteNeo() {
    IncrementEditorPreview(
        chrome = PREVIEW_EQUIPMENT_CHROME,
        title = "Cables",
        selection = null,
        family = StratumThemeId.NeoBrutalist,
        deleteAction = IncrementEditorDeleteAction(text = "Delete equipment", onClick = {}),
    )
}
