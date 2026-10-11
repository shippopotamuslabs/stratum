package com.stratum.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R
import com.stratum.core.ui.theme.StratumThemeAccessor

/** One selectable equipment row. Ids are opaque strings; host VMs map domain `Equipment`. */
data class EquipmentSwapOption(
    val id: String,
    val name: String,
)

/**
 * Full swap-sheet state, owned by the host ViewModel (workout session or template editor).
 * [recent] is most-recent-first for the target lift; [all] is the searchable catalog.
 */
data class EquipmentSwapSheetState(
    val current: EquipmentSwapOption?,
    val recent: List<EquipmentSwapOption>,
    val all: List<EquipmentSwapOption>,
    val query: String = "",
)

/** Case-insensitive substring filter over the full equipment list. Blank query matches all. */
fun filterEquipmentOptions(query: String, all: List<EquipmentSwapOption>): List<EquipmentSwapOption> {
    if (query.isBlank()) return all
    return all.filter { it.name.contains(query.trim(), ignoreCase = true) }
}

/**
 * Equipment swap bottom sheet, shared by the live workout and edit-workout screens.
 * Section order: current equipment, recently used for this lift, searchable full list,
 * create-new entry point.
 */
@Composable
fun EquipmentSwapSheet(
    state: EquipmentSwapSheetState,
    onQueryChange: (String) -> Unit,
    onSelect: (EquipmentSwapOption) -> Unit,
    onCreateNew: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StratumBottomSheet(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.equipment_swap_title),
        modifier = modifier,
    ) {
        EquipmentSwapSheetContent(
            state = state,
            onQueryChange = onQueryChange,
            onSelect = onSelect,
            onCreateNew = onCreateNew,
        )
    }
}

/** Sheet body without the modal chrome, so previews and tests can render it inline. */
@Composable
internal fun EquipmentSwapSheetContent(
    state: EquipmentSwapSheetState,
    onQueryChange: (String) -> Unit,
    onSelect: (EquipmentSwapOption) -> Unit,
    onCreateNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val searching = state.query.isNotBlank()
    val matches = filterEquipmentOptions(state.query, state.all)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.current?.let { current ->
            EquipmentSwapSectionLabel(text = stringResource(R.string.equipment_swap_current))
            EquipmentSwapOptionRow(
                option = current,
                onClick = { onSelect(current) },
                selected = true,
            )
        }
        if (!searching && state.recent.isNotEmpty()) {
            EquipmentSwapSectionLabel(text = stringResource(R.string.equipment_swap_recent))
            state.recent.forEach { option ->
                EquipmentSwapOptionRow(
                    option = option,
                    onClick = { onSelect(option) },
                    selected = option.id == state.current?.id,
                )
            }
        }
        EquipmentSwapSectionLabel(text = stringResource(R.string.equipment_swap_all))
        StratumSearchField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.equipment_swap_search_placeholder),
        )
        if (matches.isEmpty()) {
            StratumText(
                text = stringResource(R.string.equipment_swap_no_match, state.query.trim()),
                muted = true,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            // Weighted (not greedy): the list scrolls internally within remaining space so
            // a large catalog cannot push the create-new entry off-screen.
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(matches, key = { it.id }) { option ->
                    EquipmentSwapOptionRow(
                        option = option,
                        onClick = { onSelect(option) },
                        selected = option.id == state.current?.id,
                    )
                }
            }
        }
        EquipmentSwapCreateNewRow(onClick = onCreateNew)
    }
}

@Composable
private fun ColumnScope.EquipmentSwapSectionLabel(text: String) {
    StratumText(
        text = text,
        style = StratumThemeAccessor.typography.sectionLabel,
        muted = true,
    )
}

@Composable
private fun EquipmentSwapOptionRow(
    option: EquipmentSwapOption,
    onClick: () -> Unit,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val borderColor = if (selected) colors.accent else colors.borderSubtle
    val borderWidth = if (selected) shapes.borderWidth * 2 else shapes.borderWidth
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(color = colors.surfaceRaised, shape = shapes.panel)
                .border(width = borderWidth, color = borderColor, shape = shapes.panel)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StratumText(
            text = option.name,
            style = StratumThemeAccessor.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun EquipmentSwapCreateNewRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_add),
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(20.dp),
        )
        StratumText(
            text = stringResource(R.string.equipment_swap_create_new),
            style = StratumThemeAccessor.typography.bodyLarge,
            color = colors.accent,
        )
    }
}
