// STR-142 allowlist: dense tier-cell micro-stack (2.dp) pinned.
@file:Suppress("ForbiddenRawSpacingLiteralInUi")
package com.stratum.liftconfig

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stratum.core.ui.R as UiR
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumConfirmDialog
import com.stratum.core.ui.component.StratumDetailScaffold
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumOutlinedButton
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTextButton
import com.stratum.core.ui.component.StratumTextField
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarBackButton
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Shared band-strip editor components (STR-61 slice 1).
 *
 * The STR-58 equipment editor is the gold standard: these are its components,
 * extracted verbatim so every increments entry renders one editor. Route
 * chrome (titles, context rows, save bars, confirms) stays on the owning
 * route; only the band/strip/sheet building blocks live here. Presentation
 * only — no domain types (ARCHITECTURE.md §4.3), `Stratum*` primitives only.
 *
 * STR-73: the full editor screen also lives here as [IncrementEditorScreen].
 * Every increments entry renders that one screen; routes only map their
 * UiState to [IncrementEditorChrome] plus callbacks.
 */

/**
 * Per-entry chrome for the unified band-strip editor screen (STR-73).
 *
 * Everything else (bands, stops, scan, validation) renders identically for
 * every entry — only these values differ. Plain data: routes resolve strings
 * and pass them in, so the screen stays dumb.
 */
data class IncrementSaveConfirm(
    val title: String,
    val body: String,
    val confirmLabel: String,
    val dismissLabel: String,
    val onConfirm: () -> Unit,
    val onDismiss: () -> Unit,
)

data class IncrementEditorChrome(
    /** Null hides the context row (e.g. a lift carrying its own overrides). */
    val contextText: String?,
    val onContextClick: (() -> Unit)?,
    val saveText: String,
    /** Non-null for shared-defaults entries (blast-radius confirm); null saves directly. */
    val saveConfirm: IncrementSaveConfirm?,
    /** Null = no save-as-new and no fork sheet (nothing to fork from). */
    val forkSuggestion: String?,
)

/** Which draft field of a band the user edited; one entry point for all band field edits. */
enum class BandField {
    START,
    END,
    STEP,
}

/** Band-strip callbacks, grouped so the screen signature stays lean. */
data class IncrementEditorBandActions(
    val onBandClick: (Int) -> Unit,
    val onAddBand: () -> Unit,
    val onBandSheetDismiss: () -> Unit,
    /**
     * Single entry point for band draft-field edits. Was three parallel
     * (Int, String) -> Unit callbacks (start/end/step) that differed only by
     * field; the enum keeps the boundary from growing with every new field.
     */
    val onBandFieldChanged: (Int, BandField, String) -> Unit,
    val onDeleteBand: (Int) -> Unit,
)

/** Adder-stop callbacks, grouped so the screen signature stays lean. */
data class IncrementEditorStopActions(
    val onStopsClick: () -> Unit,
    val onStopChanged: (Int, String) -> Unit,
    val onAddStop: () -> Unit,
    val onRemoveStop: (Int) -> Unit,
    val onStopsDismiss: () -> Unit,
)

/** Save/fork callbacks, grouped so the screen signature stays lean. */
data class IncrementEditorSaveActions(
    val onSaveClick: () -> Unit,
    val onSaveAsNewClick: () -> Unit,
    val onForkCreate: (String) -> Unit,
    val onForkDismiss: () -> Unit,
)

/** Optional destructive action rendered below the save buttons (equipment delete). */
data class IncrementEditorDeleteAction(
    val text: String,
    val onClick: () -> Unit,
)

/**
 * The single band-strip increments editor screen (STR-73).
 *
 * Verbatim unification of the former equipment and lift screens: same strip,
 * same bottom-sheet band editor, same stops sheet, same AI scan entry.
 */
@Composable
internal fun IncrementEditorScreen(
    editor: IncrementsEditorData,
    chrome: IncrementEditorChrome,
    title: String,
    selection: Int?,
    scanPicker: ScanPicker,
    bandActions: IncrementEditorBandActions,
    stopActions: IncrementEditorStopActions,
    saveActions: IncrementEditorSaveActions,
    deleteAction: IncrementEditorDeleteAction? = null,
    onBackClick: () -> Unit,
    showSaveConfirm: Boolean,
    showForkSheet: Boolean,
    showStopsSheet: Boolean,
    modifier: Modifier = Modifier,
) {
    // Glass (§12.8, STR-160): under the route's glass host the top bar overlays
    // and the editor column carries the top clearance inside its scroll, so
    // band content scrolls beneath the glass; the save dock below stays a
    // scaffold bottom bar. Solid hosts keep the in-flow geometry exactly.
    StratumDetailScaffold(
        modifier = modifier,
        topBar = {
            StratumTopBar(
                title = title,
                navigationIcon = {
                    StratumTopBarBackButton(
                        onClick = onBackClick,
                        contentDescription = stringResource(UiR.string.lift_config_navigate_back),
                    )
                },
                actions = {
                    if (editor.scanAvailable) {
                        ScanIconEntry(picker = scanPicker, scanUi = editor.scanUi)
                    }
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
                        .padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s12),
                verticalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
            ) {
                StratumButton(
                    text = chrome.saveText,
                    onClick = saveActions.onSaveClick,
                    enabled = !editor.isSaving && editor.validationMessage == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (chrome.forkSuggestion != null) {
                    StratumOutlinedButton(
                        text = stringResource(UiR.string.equipment_increments_save_as_new),
                        onClick = saveActions.onSaveAsNewClick,
                        enabled = !editor.isSaving && editor.validationMessage == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (deleteAction != null) {
                    StratumTextButton(
                        text = deleteAction.text,
                        onClick = deleteAction.onClick,
                        enabled = !editor.isSaving,
                        contentColor = StratumThemeAccessor.colors.error,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) { glass ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = StratumSpacing.s16)
                    .padding(bottom = StratumSpacing.s8)
                    .verticalScroll(rememberScrollState())
                    .padding(top = glass.top),
        ) {
            if (chrome.contextText != null) {
                EditorContextRow(
                    text = chrome.contextText,
                    onClick = chrome.onContextClick,
                    modifier = Modifier.padding(top = StratumSpacing.s4),
                )
            }
            editor.headerContent?.invoke(this)
            StratumText(
                text = stringResource(UiR.string.equipment_band_section),
                style = StratumThemeAccessor.typography.sectionLabel,
                muted = true,
                modifier = Modifier.padding(start = StratumSpacing.s4, top = StratumSpacing.s12, bottom = StratumSpacing.s8),
            )
            StratumText(
                text = stringResource(UiR.string.equipment_band_hint),
                style = StratumThemeAccessor.typography.sectionLabel.copy(fontSize = 12.sp),
                muted = true,
                modifier = Modifier.padding(start = StratumSpacing.s4, bottom = StratumSpacing.s12),
            )
            BandStrip(
                tiers = editor.tiers,
                unitLabel = editor.unitLabel,
                selection = selection,
                onBandClick = bandActions.onBandClick,
                onAddBand = bandActions.onAddBand,
            )
            if (selection != null && editor.tiers.indices.contains(selection)) {
                BandCaption(
                    tiers = editor.tiers,
                    selection = selection,
                    unitLabel = editor.unitLabel,
                    modifier = Modifier.padding(start = StratumSpacing.s4, top = StratumSpacing.s8),
                )
            }
            if (editor.scanAvailable) {
                ScanStatusLines(
                    scanUi = editor.scanUi,
                    picker = scanPicker,
                    modifier = Modifier.padding(top = StratumSpacing.s12),
                )
            }
            if (editor.validationMessage != null) {
                EditorErrorBanner(
                    message = editor.validationMessage,
                    modifier = Modifier.padding(top = StratumSpacing.s12),
                )
            }
            if (editor.warnedTierIndexes.isNotEmpty()) {
                StratumText(
                    text = stringResource(UiR.string.lift_increments_sample_warning),
                    style = StratumThemeAccessor.typography.sectionLabel,
                    muted = true,
                    modifier = Modifier.padding(start = StratumSpacing.s4, top = StratumSpacing.s12),
                )
            }
            StratumText(
                text = stringResource(UiR.string.lift_increments_micro_title),
                style = StratumThemeAccessor.typography.sectionLabel,
                muted = true,
                modifier = Modifier.padding(start = StratumSpacing.s4, top = StratumSpacing.s16, bottom = StratumSpacing.s8),
            )
            MicroStopsRow(
                stops = editor.microStops,
                unitLabel = editor.unitLabel,
                onClick = stopActions.onStopsClick,
            )
        }
    }
    if (selection != null && editor.tiers.indices.contains(selection)) {
        BandEditorSheet(
            index = selection,
            tiers = editor.tiers,
            tier = editor.tiers[selection],
            start =
                if (selection == 0) {
                    IncrementBandTiling.firstBandStartOrZero(editor.tiers)
                } else {
                    IncrementBandTiling.startAt(editor.tiers, selection)
                },
            isLast = selection == editor.tiers.lastIndex,
            canDelete = editor.tiers.size > 1,
            unitLabel = editor.unitLabel,
            onStartChanged = { bandActions.onBandFieldChanged(selection, BandField.START, it) },
            onEndChanged = { bandActions.onBandFieldChanged(selection, BandField.END, it) },
            onStepChanged = { bandActions.onBandFieldChanged(selection, BandField.STEP, it) },
            onDelete = { bandActions.onDeleteBand(selection) },
            onDone = bandActions.onBandSheetDismiss,
            onDismiss = bandActions.onBandSheetDismiss,
        )
    }
    if (showForkSheet && chrome.forkSuggestion != null) {
        ForkNameSheet(
            suggestion = chrome.forkSuggestion,
            onCreate = saveActions.onForkCreate,
            onDismiss = saveActions.onForkDismiss,
        )
    }
    if (showStopsSheet) {
        StopsSheet(
            stops = editor.microStops,
            unitLabel = editor.unitLabel,
            onStopChanged = stopActions.onStopChanged,
            onAddStop = stopActions.onAddStop,
            onRemoveStop = stopActions.onRemoveStop,
            onDismiss = stopActions.onStopsDismiss,
        )
    }
    if (showSaveConfirm && chrome.saveConfirm != null) {
        StratumConfirmDialog(
            title = chrome.saveConfirm.title,
            body = chrome.saveConfirm.body,
            confirmLabel = chrome.saveConfirm.confirmLabel,
            dismissLabel = chrome.saveConfirm.dismissLabel,
            onConfirm = chrome.saveConfirm.onConfirm,
            onDismiss = chrome.saveConfirm.onDismiss,
        )
    }
}

/**
 * Single home for the bordered panel-row shell (background, border, padding,
 * optional chevron) shared by the context, stops, and error rows, so theme
 * changes land in one place.
 */
@Composable
private fun PanelRow(
    onClick: (() -> Unit)?,
    borderColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    var rowModifier =
        modifier
            .fillMaxWidth()
            .background(color = colors.surfaceRaised, shape = shapes.panel)
            .border(
                width = shapes.borderWidth,
                color = borderColor,
                shape = shapes.panel,
            )
    if (onClick != null) {
        rowModifier = rowModifier.clickable(onClick = onClick)
    }
    Row(
        modifier = rowModifier.padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s12),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
        if (onClick != null) {
            Icon(
                painter = painterResource(UiR.drawable.ic_chevron_right),
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** Tappable context row; display-only when [onClick] is null (no chevron). */
@Composable
internal fun EditorContextRow(
    text: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    PanelRow(
        onClick = onClick,
        borderColor = StratumThemeAccessor.colors.borderSubtle,
        modifier = modifier,
    ) {
        StratumText(
            text = text,
            style = StratumThemeAccessor.typography.bodyLarge,
            muted = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun BandStrip(
    tiers: List<IncrementTierDraft>,
    unitLabel: String,
    selection: Int?,
    onBandClick: (Int) -> Unit,
    onAddBand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
        ) {
            tiers.forEachIndexed { index, tier ->
                val isOpen = index == tiers.lastIndex
                val selected = selection == index
                BandCell(
                    rangeText = bandRangeText(tier, index == 0),
                    stepText = "+${tier.increment.ifBlank { "–" }} $unitLabel",
                    selected = selected,
                    openEnded = isOpen,
                    onClick = { onBandClick(index) },
                    modifier = Modifier.width(BAND_CELL_WIDTH),
                )
            }
        }
        BandAddCell(onClick = onAddBand)
    }
}

private fun bandRangeText(tier: IncrementTierDraft, isFirst: Boolean): String {
    val start = if (isFirst) tier.lowerBound.ifBlank { "0" } else tier.lowerBound.ifBlank { "?" }
    val end = tier.upperBound?.ifBlank { null }
    return if (end == null) "$start+" else "$start–$end"
}

@Composable
private fun BandCell(
    rangeText: String,
    stepText: String,
    selected: Boolean,
    openEnded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Column(
        modifier =
            modifier
                .background(
                    color = if (openEnded) colors.surfaceSunken else colors.surfaceRaised,
                    shape = shapes.panel,
                )
                .border(
                    width = shapes.borderWidth,
                    color = if (selected) colors.accent else colors.borderSubtle,
                    shape = shapes.panel,
                )
                .clickable(onClick = onClick)
                .padding(horizontal = StratumSpacing.s8, vertical = StratumSpacing.s12),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        StratumNumber(
            text = rangeText,
            style = StratumThemeAccessor.typography.bodyLarge,
            maxLines = 1,
        )
        StratumText(
            text = stepText,
            style = StratumThemeAccessor.typography.sectionLabel,
            color = colors.accent,
            maxLines = 1,
        )
    }
}

@Composable
private fun BandAddCell(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    StratumIconButton(
        onClick = onClick,
        // §12.5: icon-button targets floor at 48 wide (was 44).
        modifier = modifier.size(width = 48.dp, height = 64.dp),
        contentColor = colors.accent,
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_add),
            contentDescription = stringResource(UiR.string.equipment_band_add),
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * Half-typed decimal field (STR-103, STR-104): owns its in-progress text so
 * the field can always be cleared mid-typing. Only valid numeric input is
 * committed via [onCommitValid]; anything else stays local, flags the error
 * border, and is dropped on dismiss. [initial] re-syncs the field whenever
 * [key] or the committed value changes (e.g. a committed edit re-tiles).
 */
@Composable
private fun ValidatedDecimalField(
    key: Any,
    initial: String,
    isInvalid: (String) -> Boolean,
    onCommitValid: (String) -> Unit,
    label: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var text by remember(key, initial) { mutableStateOf(initial) }
    StratumTextField(
        value = text,
        onValueChange = { raw ->
            val filtered = IncrementTierDraftOps.filterNumeric(raw)
            text = filtered
            if (!isInvalid(filtered)) onCommitValid(filtered)
        },
        label = label,
        enabled = enabled,
        isError = isInvalid(text),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

/** Band editor sheet (STR-101, STR-102): every band's start is editable.
 * The first band stores its own start; editing a later band's start moves the
 * previous band's end to match via re-tiling, so the new band is fully set up
 * in one place. Then Ends-at and Step. (STR-103, STR-104: Ends-at and later
 * bands' Starts-at are [ValidatedDecimalField]s — half-typed UI-local state,
 * so a field can be cleared mid-typing and shows exactly what was typed;
 * only a valid numeric value is committed to the draft, so a blank or
 * non-numeric value shows the error border and Done simply keeps the old
 * value.) */
@Composable
internal fun BandEditorSheet(
    index: Int,
    tiers: List<IncrementTierDraft>,
    tier: IncrementTierDraft,
    start: String,
    isLast: Boolean,
    canDelete: Boolean,
    unitLabel: String,
    onStartChanged: (String) -> Unit,
    onEndChanged: (String) -> Unit,
    onStepChanged: (String) -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isFirst = index == 0
    StratumBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(UiR.string.equipment_band_editor_title, index + 1),
    ) {
        StratumText(
            text = stringResource(UiR.string.equipment_band_editor_sub),
            style = StratumThemeAccessor.typography.sectionLabel,
            muted = true,
        )
        if (isFirst) {
            // STR-101: the first band's start is stored state and commits
            // every keystroke (blank reads as zero).
            StratumTextField(
                value = tier.lowerBound,
                onValueChange = onStartChanged,
                label = stringResource(UiR.string.equipment_band_starts_at_label, unitLabel),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            // STR-104: a later band's start is half-typed local state — only
            // a valid numeric start is committed, moving the previous band's
            // end via re-tiling; anything else flags the error border and is
            // dropped on Done, leaving the derived start untouched.
            ValidatedDecimalField(
                key = index,
                initial = start,
                isInvalid = { candidate ->
                    IncrementBandTiling.isStartInvalid(tiers, index, candidate)
                },
                onCommitValid = onStartChanged,
                label = stringResource(UiR.string.equipment_band_starts_at_label, unitLabel),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // STR-103: Ends-at is half-typed local state too — only a valid
        // numeric end is committed; anything else flags the error border
        // and is dropped on Done, keeping the old end.
        ValidatedDecimalField(
            key = index,
            initial = tier.upperBound.orEmpty(),
            isInvalid = { candidate ->
                IncrementBandTiling.isEndInvalid(tiers, index, candidate)
            },
            onCommitValid = onEndChanged,
            label = stringResource(UiR.string.equipment_band_ends_at, unitLabel),
            enabled = !isLast,
            modifier = Modifier.fillMaxWidth(),
        )
        StratumTextField(
            value = tier.increment,
            onValueChange = onStepChanged,
            label = stringResource(UiR.string.equipment_band_step, unitLabel),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        StratumButton(
            text = stringResource(UiR.string.equipment_band_done),
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        )
        if (canDelete) {
            StratumTextButton(
                text = stringResource(UiR.string.equipment_band_delete),
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun MicroStopsRow(
    stops: List<String>,
    unitLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    PanelRow(
        onClick = onClick,
        borderColor = colors.borderSubtle,
        modifier = modifier,
    ) {
        StratumText(
            text = stringResource(UiR.string.equipment_micro_row),
            style = StratumThemeAccessor.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        StratumText(
            text =
                if (stops.isEmpty()) {
                    stringResource(UiR.string.equipment_micro_none)
                } else {
                    stops.joinToString(separator = " / ") + " $unitLabel"
                },
            style = StratumThemeAccessor.typography.bodyLarge,
            color = colors.accent,
        )
    }
}

@Composable
internal fun StopsSheet(
    stops: List<String>,
    unitLabel: String,
    onStopChanged: (Int, String) -> Unit,
    onAddStop: () -> Unit,
    onRemoveStop: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    StratumBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(UiR.string.lift_increments_micro_title),
    ) {
        StratumText(
            text = stringResource(UiR.string.lift_increments_micro_help),
            style = StratumThemeAccessor.typography.sectionLabel,
            muted = true,
        )
        stops.forEachIndexed { index, stop ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StratumTextField(
                    value = stop,
                    onValueChange = { onStopChanged(index, it) },
                    label = stringResource(UiR.string.lift_increments_micro_stop, unitLabel),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                StratumIconButton(onClick = { onRemoveStop(index) }) {
                    Icon(
                        painter = painterResource(UiR.drawable.ic_close),
                        contentDescription =
                            stringResource(UiR.string.lift_increments_micro_remove),
                        tint = colors.textMuted,
                    )
                }
            }
        }
        StratumTextButton(
            text = stringResource(UiR.string.lift_increments_micro_add),
            onClick = onAddStop,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun ForkNameSheet(
    suggestion: String,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(suggestion) { mutableStateOf(suggestion) }
    StratumBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(UiR.string.equipment_fork_title),
    ) {
        StratumText(
            text = stringResource(UiR.string.equipment_fork_body),
            style = StratumThemeAccessor.typography.sectionLabel,
            muted = true,
        )
        StratumTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(UiR.string.equipment_fork_name_label),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s12, Alignment.End),
        ) {
            StratumOutlinedButton(
                text = stringResource(UiR.string.equipment_dialog_cancel),
                onClick = onDismiss,
            )
            StratumButton(
                text = stringResource(UiR.string.manage_equipment_create),
                onClick = { onCreate(name) },
                enabled = name.isNotBlank(),
            )
        }
    }
}

@Composable
internal fun EditorErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    PanelRow(
        onClick = null,
        borderColor = colors.error,
        modifier = modifier,
    ) {
        StratumText(
            text = message,
            style =
                StratumThemeAccessor.typography.bodyLarge.copy(
                    color = colors.error,
                ),
            modifier = Modifier.weight(1f),
        )
    }
}

/** Caption under the strip: `Band N of M · starts at X lb` (`· open-ended` for last). */
@Composable
internal fun BandCaption(
    tiers: List<IncrementTierDraft>,
    selection: Int,
    unitLabel: String,
    modifier: Modifier = Modifier,
) {
    val start =
        if (selection == 0) {
            IncrementBandTiling.firstBandStartOrZero(tiers)
        } else {
            IncrementBandTiling.startAt(tiers, selection)
        }
    val isOpen = selection == tiers.lastIndex
    StratumText(
        text =
            if (isOpen) {
                stringResource(
                    UiR.string.equipment_band_caption_open,
                    selection + 1,
                    tiers.size,
                    start,
                    unitLabel,
                )
            } else {
                stringResource(
                    UiR.string.equipment_band_caption,
                    selection + 1,
                    tiers.size,
                    start,
                    unitLabel,
                )
            },
        style = StratumThemeAccessor.typography.sectionLabel,
        muted = true,
        modifier = modifier,
    )
}

/** Fixed cell width so every band stays legible; overflow scrolls instead of shrinking. */
private val BAND_CELL_WIDTH = 96.dp
