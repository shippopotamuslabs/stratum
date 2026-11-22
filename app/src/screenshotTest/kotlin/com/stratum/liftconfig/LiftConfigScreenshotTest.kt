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
fun manageEquipmentDeleteSheetManyAffectedTonal() {
    ManageEquipmentDeleteSheetPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewManageEquipmentReassigningManyAffected(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun manageEquipmentDeleteSheetManyAffectedNeo() {
    ManageEquipmentDeleteSheetPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = previewManageEquipmentReassigningManyAffected(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun manageEquipmentDeleteSheetFallbackManyAffectedTonal() {
    ManageEquipmentDeleteSheetPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewManageEquipmentFallbackManyAffected(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun manageEquipmentDeleteSheetFallbackManyAffectedNeo() {
    ManageEquipmentDeleteSheetPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = previewManageEquipmentFallbackManyAffected(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun manageEquipmentDeletePickerTonal() {
    ManageEquipmentDeletePickerPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun manageEquipmentDeletePickerNeo() {
    ManageEquipmentDeletePickerPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun manageEquipmentDeleteSheetSimpleTonal() {
    ManageEquipmentDeleteSheetPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewManageEquipmentDeleting(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun manageEquipmentDeleteSheetSimpleNeo() {
    ManageEquipmentDeleteSheetPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = previewManageEquipmentDeleting(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun manageEquipmentDeleteSheetNoPickTonal() {
    ManageEquipmentDeleteSheetPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewManageEquipmentReassigningNoPick(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun manageEquipmentDeleteSheetNoPickNeo() {
    ManageEquipmentDeleteSheetPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = previewManageEquipmentReassigningNoPick(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun manageEquipmentDeletePickerLongListTonal() {
    ManageEquipmentDeletePickerPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewManageEquipmentReassigningLongList(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun manageEquipmentDeletePickerLongListNeo() {
    ManageEquipmentDeletePickerPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = previewManageEquipmentReassigningLongList(),
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
        headerContent = PREVIEW_EQUIPMENT_NAME_FIELD,
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
        headerContent = PREVIEW_EQUIPMENT_NAME_FIELD,
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
        headerContent = PREVIEW_EQUIPMENT_NAME_FIELD,
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
        headerContent = PREVIEW_EQUIPMENT_NAME_FIELD,
    )
}
