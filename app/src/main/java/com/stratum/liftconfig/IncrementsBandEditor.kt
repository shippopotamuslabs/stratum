package com.stratum.liftconfig

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as UiR
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumOutlinedButton
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTextButton
import com.stratum.core.ui.component.StratumTextField
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Shared band-strip editor components (STR-61 slice 1).
 *
 * The STR-58 equipment editor is the gold standard: these are its components,
 * extracted verbatim so every increments entry renders one editor. Route
 * chrome (titles, context rows, save bars, confirms) stays on the owning
 * route; only the band/strip/sheet building blocks live here. Presentation
 * only — no domain types (ARCHITECTURE.md §4.3), `Stratum*` primitives only.
 */

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
        modifier = rowModifier.padding(horizontal = 14.dp, vertical = 12.dp),
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
        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                modifier = Modifier.weight(bandWeight(tiers, index)),
            )
        }
        BandAddCell(onClick = onAddBand)
    }
}

private fun bandRangeText(tier: IncrementTierDraft, isFirst: Boolean): String {
    val start = if (isFirst) "0" else tier.lowerBound.ifBlank { "?" }
    val end = tier.upperBound?.ifBlank { null }
    return if (end == null) "$start+" else "$start–$end"
}

private fun bandWeight(tiers: List<IncrementTierDraft>, index: Int): Float {
    if (index == tiers.lastIndex) return OPEN_BAND_WEIGHT
    val start = tiers[index].lowerBound.toDoubleOrNull()
    val end = tiers[index].upperBound?.toDoubleOrNull()
    if (start == null || end == null) return MIN_BAND_WEIGHT
    return ((end - start).toFloat() / BAND_WEIGHT_SPAN).coerceAtLeast(MIN_BAND_WEIGHT)
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
                .padding(horizontal = 8.dp, vertical = 12.dp),
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
        modifier = modifier.size(width = 44.dp, height = 64.dp),
        contentColor = colors.accent,
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_add),
            contentDescription = stringResource(UiR.string.equipment_band_add),
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Band editor sheet: derived start context plus exactly Ends-at and Step. */
@Composable
internal fun BandEditorSheet(
    index: Int,
    tier: IncrementTierDraft,
    start: String,
    isLast: Boolean,
    canDelete: Boolean,
    unitLabel: String,
    onEndChanged: (String) -> Unit,
    onStepChanged: (String) -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    StratumBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(UiR.string.equipment_band_editor_title, index + 1),
    ) {
        StratumText(
            text = stringResource(UiR.string.equipment_band_editor_sub),
            style = StratumThemeAccessor.typography.sectionLabel,
            muted = true,
        )
        StratumText(
            text =
                stringResource(
                    UiR.string.equipment_band_starts_at,
                    start,
                    unitLabel,
                ),
            style = StratumThemeAccessor.typography.bodyLarge,
            muted = true,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        color = colors.surfaceSunken,
                        shape = StratumThemeAccessor.shapes.panel,
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
        )
        StratumTextField(
            value = tier.upperBound.orEmpty(),
            onValueChange = onEndChanged,
            label = stringResource(UiR.string.equipment_band_ends_at, unitLabel),
            enabled = !isLast,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
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
    val start = IncrementBandTiling.startAt(tiers, selection)
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

private const val OPEN_BAND_WEIGHT = 4f
private const val MIN_BAND_WEIGHT = 2f
private const val BAND_WEIGHT_SPAN = 25f
