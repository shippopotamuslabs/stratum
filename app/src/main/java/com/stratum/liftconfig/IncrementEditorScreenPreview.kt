package com.stratum.liftconfig

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId

private val PREVIEW_EDITOR =
    IncrementsEditorData(
        tiers =
            listOf(
                IncrementTierDraft("0", "100", "5", openEnded = false),
                IncrementTierDraft("100", null, "10", openEnded = true),
            ),
        microStops = listOf("1.25"),
        unitLabel = "lb",
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
) {
    StratumTheme(themeId = family, darkTheme = darkTheme) {
        IncrementEditorScreen(
            editor = PREVIEW_EDITOR,
            chrome = chrome,
            title = title,
            selection = selection,
            scanPicker = previewPicker(),
            bandActions = previewBandActions,
            stopActions = previewStopActions,
            saveActions = previewSaveActions,
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
