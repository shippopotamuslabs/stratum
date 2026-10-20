package com.stratum.liftconfig

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumTextField
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId

private fun previewEditor(headerContent: (@Composable ColumnScope.() -> Unit)? = null) =
    IncrementsEditorData(
        tiers =
            listOf(
                IncrementTierDraft("0", "100", "5", openEnded = false),
                IncrementTierDraft("100", null, "10", openEnded = true),
            ),
        microStops = listOf("1.25"),
        unitLabel = "lb",
        headerContent = headerContent,
    )

internal val PREVIEW_EQUIPMENT_CHROME =
    IncrementEditorChrome(
        contextText = "Shared by 3 lift(s)",
        onContextClick = {},
        saveText = "Save changes",
        saveConfirm =
            IncrementSaveConfirm(
                title = "Save changes?",
                body = "This updates the shared profile used by 3 lift(s).",
                confirmLabel = "Update 3 lifts",
                dismissLabel = "Cancel",
                onConfirm = {},
                onDismiss = {},
            ),
        forkSuggestion = "Cables (copy)",
    )

/** Equipment name field supplied through the screen's header slot (STR-135). */
internal val PREVIEW_EQUIPMENT_NAME_FIELD: @Composable ColumnScope.() -> Unit = {
    StratumTextField(
        value = "Cables",
        onValueChange = {},
        label = "Name",
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    )
}

private val PREVIEW_OVERRIDE_CHROME =
    IncrementEditorChrome(
        contextText = "Using defaults from Cables. Save to override for this lift only.",
        onContextClick = null,
        saveText = "Save",
        saveConfirm = null,
        forkSuggestion = "Cables (copy)",
    )

private fun previewPicker() = ScanPicker(ScanPickerState(), {}, {})

private val previewBandActions =
    IncrementEditorBandActions(
        onBandClick = {},
        onAddBand = {},
        onBandSheetDismiss = {},
        onBandFieldChanged = { _, _, _ -> },
        onDeleteBand = {},
    )

private val previewStopActions =
    IncrementEditorStopActions(
        onStopsClick = {},
        onStopChanged = { _, _ -> },
        onAddStop = {},
        onRemoveStop = {},
        onStopsDismiss = {},
    )

private val previewSaveActions =
    IncrementEditorSaveActions(
        onSaveClick = {},
        onSaveAsNewClick = {},
        onForkCreate = {},
        onForkDismiss = {},
    )

@Composable
internal fun IncrementEditorPreview(
    chrome: IncrementEditorChrome,
    title: String,
    selection: Int?,
    darkTheme: Boolean? = null,
    family: StratumThemeId = StratumThemeId.Tonal,
    deleteAction: IncrementEditorDeleteAction? = null,
    headerContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    StratumTheme(themeId = family, darkTheme = darkTheme) {
        IncrementEditorScreen(
            editor = previewEditor(headerContent),
            chrome = chrome,
            title = title,
            selection = selection,
            scanPicker = previewPicker(),
            bandActions = previewBandActions,
            stopActions = previewStopActions,
            saveActions = previewSaveActions,
            deleteAction = deleteAction,
            onBackClick = {},
            showSaveConfirm = false,
            showForkSheet = false,
            showStopsSheet = false,
        )
    }
}

@Preview(name = "Increment editor light", showBackground = true)
@Composable
private fun IncrementEditorLightPreview() {
    IncrementEditorPreview(
        chrome = PREVIEW_EQUIPMENT_CHROME,
        title = "Cables",
        selection = 0,
        darkTheme = false,
        headerContent = PREVIEW_EQUIPMENT_NAME_FIELD,
    )
}

@Preview(name = "Increment editor dark", showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun IncrementEditorDarkPreview() {
    IncrementEditorPreview(
        chrome = PREVIEW_OVERRIDE_CHROME,
        title = "Lat pulldown",
        selection = 1,
        darkTheme = true,
    )
}

@Preview(name = "Increment editor delete light", showBackground = true)
@Composable
private fun IncrementEditorDeleteLightPreview() {
    IncrementEditorPreview(
        chrome = PREVIEW_EQUIPMENT_CHROME,
        title = "Cables",
        selection = null,
        darkTheme = false,
        deleteAction = IncrementEditorDeleteAction(text = "Delete equipment", onClick = {}),
        headerContent = PREVIEW_EQUIPMENT_NAME_FIELD,
    )
}

@Preview(name = "Increment editor delete dark", showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun IncrementEditorDeleteDarkPreview() {
    IncrementEditorPreview(
        chrome = PREVIEW_EQUIPMENT_CHROME,
        title = "Cables",
        selection = null,
        darkTheme = true,
        deleteAction = IncrementEditorDeleteAction(text = "Delete equipment", onClick = {}),
        headerContent = PREVIEW_EQUIPMENT_NAME_FIELD,
    )
}
