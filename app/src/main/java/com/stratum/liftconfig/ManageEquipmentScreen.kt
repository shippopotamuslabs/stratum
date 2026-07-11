package com.stratum.liftconfig

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.ui.R as UiR
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTextField
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarBackButton
import com.stratum.core.ui.component.UserMessageEffect
import com.stratum.core.ui.theme.StratumThemeAccessor

@Composable
fun ManageEquipmentRoute(
    selectionMode: Boolean,
    onBackClick: () -> Unit,
    onOpenIncrements: (equipmentId: String) -> Unit,
    viewModel: ManageEquipmentViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    UserMessageEffect(
        userMessage = state.userMessage,
        snackbarHostState = snackbarHostState,
        onUserMessageShown = viewModel::userMessageShown,
    )
    ManageEquipmentScreen(
        state = state,
        selectionMode = selectionMode,
        onBackClick = onBackClick,
        onOpenIncrements = onOpenIncrements,
        onEquipmentSelected = { equipmentId ->
            viewModel.selectEquipment(equipmentId)
            onBackClick()
        },
        onCreateEquipment = { onCreated ->
            viewModel.onCreateEquipment { createdId ->
                if (createdId != null) {
                    onCreated()
                    if (selectionMode) {
                        viewModel.selectEquipment(createdId)
                        onBackClick()
                    }
                }
            }
        },
        onNameDraftChange = viewModel::onNameDraftChange,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
fun ManageEquipmentScreen(
    state: ManageEquipmentUiState,
    selectionMode: Boolean,
    onBackClick: () -> Unit,
    onOpenIncrements: (String) -> Unit,
    onEquipmentSelected: (String) -> Unit,
    onCreateEquipment: (onCreated: () -> Unit) -> Unit,
    onNameDraftChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
) {
    var showCreate by remember { mutableStateOf(false) }
    val title =
        if (selectionMode) {
            stringResource(UiR.string.manage_equipment_choose_title)
        } else {
            stringResource(UiR.string.manage_equipment_title)
        }
    StratumScaffold(
        modifier = modifier,
        snackbarHostState = snackbarHostState,
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
                    StratumIconButton(onClick = { showCreate = true }) {
                        Icon(
                            painter = painterResource(UiR.drawable.ic_add),
                            contentDescription = stringResource(UiR.string.manage_equipment_add),
                            tint = StratumThemeAccessor.colors.textPrimary,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        ) {
            item(key = "equipment-help") {
                EquipmentHelpCard()
            }
            if (state.rows.isEmpty()) {
                item {
                    StratumText(
                        text = stringResource(UiR.string.manage_equipment_empty),
                        muted = true,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(state.rows, key = { it.id }) { row ->
                EquipmentRow(
                    row = row,
                    selectionMode = selectionMode,
                    onClick = {
                        if (selectionMode) {
                            onEquipmentSelected(row.id)
                        } else {
                            onOpenIncrements(row.id)
                        }
                    },
                    onEditIncrements = { onOpenIncrements(row.id) },
                )
            }
        }
    }

    if (showCreate) {
        StratumBottomSheet(
            onDismissRequest = {
                if (!state.isCreating) showCreate = false
            },
            title = stringResource(UiR.string.manage_equipment_add),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StratumTextField(
                    value = state.nameDraft,
                    onValueChange = onNameDraftChange,
                    label = stringResource(UiR.string.manage_equipment_name_placeholder),
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = stringResource(UiR.string.manage_equipment_name_placeholder),
                    enabled = !state.isCreating,
                )
                StratumButton(
                    text = stringResource(UiR.string.manage_equipment_create),
                    onClick = {
                        onCreateEquipment {
                            showCreate = false
                        }
                    },
                    enabled = state.nameDraft.trim().isNotEmpty() && !state.isCreating,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun EquipmentHelpCard() {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color = colors.surfaceRaised, shape = shapes.panel)
                .border(
                    width = shapes.borderWidth,
                    color = colors.borderSubtle,
                    shape = shapes.panel,
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_info),
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(18.dp),
        )
        StratumText(
            text = stringResource(UiR.string.manage_equipment_help),
            style = StratumThemeAccessor.typography.sectionLabel,
            muted = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EquipmentRow(
    row: EquipmentRowUi,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onEditIncrements: () -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceRaised, shapes.panel)
                .border(shapes.borderWidth, colors.borderSubtle, shapes.panel)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StratumText(
                text = row.name,
                style = StratumThemeAccessor.typography.titleMedium,
            )
            if (row.tierChips.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    row.tierChips.forEach { chip ->
                        EquipmentTierChip(chip = chip)
                    }
                }
            }
        }
        if (selectionMode) {
            StratumIconButton(
                onClick = onEditIncrements,
                containerColor = colors.borderSubtle,
            ) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_chevron_right),
                    contentDescription = stringResource(UiR.string.manage_equipment_edit_increments),
                    tint = colors.textMuted,
                )
            }
        } else {
            Icon(
                painter = painterResource(UiR.drawable.ic_chevron_right),
                contentDescription = stringResource(UiR.string.manage_equipment_open_increments),
                tint = colors.textMuted,
            )
        }
    }
}

/**
 * Read-only weight-band chip. Not interactive — the whole equipment row is the hit target.
 */
@Composable
private fun EquipmentTierChip(
    chip: EquipmentTierChipUi,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val label =
        if (chip.upperBound != null) {
            stringResource(
                UiR.string.manage_equipment_tier_chip_range,
                chip.lowerBound,
                chip.upperBound,
                chip.increment,
            )
        } else {
            stringResource(
                UiR.string.manage_equipment_tier_chip_open,
                chip.lowerBound,
                chip.increment,
            )
        }
    Box(
        modifier =
            modifier
                .background(colors.surfaceInset, shapes.chip)
                .border(shapes.borderWidth, colors.borderSubtle, shapes.chip)
                .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        StratumText(
            text = label,
            style = StratumThemeAccessor.typography.sectionLabel,
            muted = true,
        )
    }
}
