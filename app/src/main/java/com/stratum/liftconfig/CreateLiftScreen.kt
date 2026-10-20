package com.stratum.liftconfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.ui.R as UiR
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumTextField
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarBackButton
import com.stratum.core.ui.component.StratumTopBarTextButton
import com.stratum.core.ui.component.UserMessageEffect
import com.stratum.core.ui.theme.StratumSpacing


@Composable
fun CreateLiftRoute(
    onBackClick: () -> Unit,
    onChooseEquipment: () -> Unit,
    viewModel: CreateLiftViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    UserMessageEffect(
        userMessage = state.errorMessage,
        snackbarHostState = snackbarHostState,
        onUserMessageShown = viewModel::errorMessageShown,
    )
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                CreateLiftEvent.NavigateBack -> onBackClick()
            }
        }
    }
    CreateLiftScreen(
        state = state,
        onBackClick = onBackClick,
        onConfirm = viewModel::onConfirm,
        onNameChange = viewModel::onNameChange,
        snackbarHostState = snackbarHostState,
        actions =
            LiftDetailsEditorActions(
                onMuscleRoleSelected = viewModel::onMuscleRoleSelected,
                onShowAddMuscleSheet = viewModel::onShowAddMuscleSheet,
                onAddMuscle = viewModel::onAddMuscle,
                onRemoveMuscle = viewModel::onRemoveMuscle,
                onOpenDetailEditor = viewModel::onOpenDetailEditor,
                onDismissDetailEditor = viewModel::onDismissDetailEditor,
                onSelectLengthBias = viewModel::onSelectLengthBias,
                onSelectFatigue = viewModel::onSelectFatigue,
                onSelectLoadType = viewModel::onSelectLoadType,
                onToggleDraftMovementPattern = viewModel::onToggleDraftMovementPattern,
                onConfirmMovementPatterns = viewModel::onConfirmMovementPatterns,
                onAssignBodyweightEquipment = viewModel::onAssignBodyweightEquipment,
                onChooseEquipment = {
                    viewModel.onDismissDetailEditor()
                    viewModel.onBeginChooseEquipment()
                    onChooseEquipment()
                },
                onRestDraftChange = viewModel::onRestDraftChange,
                onSaveDefaultRest = viewModel::onSaveDefaultRest,
            ),
    )
}

@Composable
fun CreateLiftScreen(
    state: CreateLiftUiState,
    onBackClick: () -> Unit,
    onConfirm: () -> Unit,
    onNameChange: (String) -> Unit,
    actions: LiftDetailsEditorActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
) {
    StratumScaffold(
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        topBar = {
            StratumTopBar(
                title = stringResource(UiR.string.lift_swap_create_new_lift),
                navigationIcon = {
                    StratumTopBarBackButton(
                        onClick = onBackClick,
                        contentDescription = stringResource(UiR.string.lift_config_navigate_back),
                    )
                },
                actions = {
                    StratumTopBarTextButton(
                        text = stringResource(UiR.string.create_lift_confirm),
                        onClick = onConfirm,
                        enabled = state.canConfirm,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = StratumSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
        ) {
            StratumTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = stringResource(UiR.string.create_lift_name_label),
                placeholder = stringResource(UiR.string.create_lift_name_placeholder),
                modifier = Modifier.fillMaxWidth(),
            )
            LiftDetailsEditor(
                state = state.details,
                actions = actions,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
