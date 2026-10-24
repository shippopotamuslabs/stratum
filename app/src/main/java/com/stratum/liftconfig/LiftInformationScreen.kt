// STR-142 allowlist: dense 2.dp text stacks pinned.
@file:Suppress("ForbiddenRawSpacingLiteralInUi")
package com.stratum.liftconfig

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.stratum.core.ui.component.MinSegmentHeight
import com.stratum.core.ui.component.segmentedSwitcherTopClearance
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.ui.R as UiR
import com.stratum.core.ui.component.RirTargetBadge
import com.stratum.core.ui.component.StratumDetailScaffold
import com.stratum.core.ui.component.StratumLiftImage
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumSegmentedControl
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarBackButton
import com.stratum.core.ui.component.UserMessageEffect
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.rememberGlassHazeState

@Composable
fun LiftInformationRoute(
    liftId: String,
    onBackClick: () -> Unit,
    onChooseEquipment: () -> Unit,
    viewModel: LiftInformationViewModel = hiltViewModel(),
) {
    LaunchedEffect(liftId) {
        viewModel.initialize(liftId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    UserMessageEffect(
        userMessage = userMessage,
        snackbarHostState = snackbarHostState,
        onUserMessageShown = viewModel::userMessageShown,
    )
    LiftInformationScreen(
        state = state,
        onBackClick = onBackClick,
        snackbarHostState = snackbarHostState,
        actions =
            LiftInformationActions(
                onTabSelected = viewModel::onTabSelected,
                onMuscleRoleSelected = viewModel::onMuscleRoleSelected,
                onSessionExpandedToggled = viewModel::onSessionExpandedToggled,
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

/**
 * Glass bottom-slot height (STR-203): under glass the tab switcher lives in
 * the top bar's bottom slot, so the clearance formula must cover the whole
 * bar — the s8 gap below the hairline plus the fixed segment height plus the
 * s12 rhythm below, pinned by construction on the slot composable (STR-159
 * idiom). Solid hosts ignore it (chrome.top is 0 there) and keep the
 * content-side switcher geometry exactly.
 */
private val LIFT_INFO_SWITCHER_SLOT_HEIGHT =
    StratumSpacing.s8 + MinSegmentHeight + StratumSpacing.s12

@Composable
fun LiftInformationScreen(
    state: LiftInformationUiState,
    onBackClick: () -> Unit,
    actions: LiftInformationActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
) {
    val tabs =
        listOf(
            LiftInfoTab.Instructions to stringResource(UiR.string.lift_info_tab_instructions),
            LiftInfoTab.Details to stringResource(UiR.string.lift_info_tab_details),
            LiftInfoTab.History to stringResource(UiR.string.lift_info_tab_history),
        )
    // Glass (§12.8, STR-160/STR-192): under the route's glass host the top bar
    // (with the tab switcher in its bottom slot, STR-203) overlays and each
    // tab list owns the full viewport — the whole top clearance (bar + slot)
    // is the list's top content padding, so rows scroll beneath the glass bar
    // AND the switcher. The slot keeps the switcher outside the hazeSource
    // subtree, so it never renders transparent the way the old in-source
    // glazed strip did (STR-197 surface removed). Solid hosts (chrome.top =
    // 0) reproduce the content-side switcher geometry exactly.
    val selectedTabIndex = tabs.indexOfFirst { it.first == state.selectedTab }.coerceAtLeast(0)
    StratumDetailScaffold(
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        topBarBottomSlotHeight = LIFT_INFO_SWITCHER_SLOT_HEIGHT,
        topBar = {
            StratumTopBar(
                title = state.liftName.ifBlank { stringResource(UiR.string.lift_info_title) },
                navigationIcon = {
                    StratumTopBarBackButton(
                        onClick = onBackClick,
                        contentDescription = stringResource(UiR.string.lift_config_navigate_back),
                    )
                },
                bottom = {
                    // Glass only: the switcher rides the bar's bottom slot with
                    // an s8 gap below the hairline (STR-203) — the off-grid
                    // 2.dp stays on the solid path. The s8 + segment + s12
                    // stack pins LIFT_INFO_SWITCHER_SLOT_HEIGHT by construction.
                    if (rememberGlassHazeState() != null) {
                        StratumSegmentedControl(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = StratumSpacing.s16)
                                    .padding(top = StratumSpacing.s8, bottom = StratumSpacing.s12),
                            segments = tabs.map { it.second },
                            selectedIndex = selectedTabIndex,
                            onSelectedIndexChange = { index -> actions.onTabSelected(tabs[index].first) },
                        )
                    }
                },
            )
        },
    ) { chrome ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Solid only: the content-side switcher, composed first so
            // accessibility traversal reaches it before the list; zIndex keeps
            // it painted above scrolling rows. STR-188: 2.dp gap below the top
            // app bar so the switcher does not sit flush against its bottom
            // border; the content clearance below follows it down so the s12
            // rhythm under the switcher (see segmentedSwitcherTopClearance)
            // is preserved. Pixel-identical to the pre-STR-203 solid layout —
            // the removed strip surface was a no-op without glass.
            if (!chrome.active) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = chrome.top)
                            .zIndex(1f),
                ) {
                    StratumSegmentedControl(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = StratumSpacing.s16)
                                .padding(top = 2.dp, bottom = StratumSpacing.s12),
                        segments = tabs.map { it.second },
                        selectedIndex = selectedTabIndex,
                        onSelectedIndexChange = { index -> actions.onTabSelected(tabs[index].first) },
                    )
                }
            }
            // Glass: chrome.top already covers bar + slot (status + 64 + s8 +
            // segment + s12, STR-203), so the whole clearance is the lists'
            // top padding — first rows land s12 below the switcher, the same
            // rhythm as before, shifted +6dp by the wider s8 gap. Solid keeps
            // the overlay formula with its 2.dp gap.
            val tabTopClearance =
                if (chrome.active) {
                    chrome.top
                } else {
                    segmentedSwitcherTopClearance(chrome.top) + 2.dp
                }
            when (state.selectedTab) {
                LiftInfoTab.Instructions -> InstructionsTab(state, topClearance = tabTopClearance)
                LiftInfoTab.Details ->
                    LiftDetailsEditor(
                        state = state.details,
                        actions =
                            LiftDetailsEditorActions(
                                onMuscleRoleSelected = actions.onMuscleRoleSelected,
                                onShowAddMuscleSheet = actions.onShowAddMuscleSheet,
                                onAddMuscle = actions.onAddMuscle,
                                onRemoveMuscle = actions.onRemoveMuscle,
                                onOpenDetailEditor = actions.onOpenDetailEditor,
                                onDismissDetailEditor = actions.onDismissDetailEditor,
                                onSelectLengthBias = actions.onSelectLengthBias,
                                onSelectFatigue = actions.onSelectFatigue,
                                onSelectLoadType = actions.onSelectLoadType,
                                onToggleDraftMovementPattern = actions.onToggleDraftMovementPattern,
                                onConfirmMovementPatterns = actions.onConfirmMovementPatterns,
                                onAssignBodyweightEquipment = actions.onAssignBodyweightEquipment,
                                onChooseEquipment = actions.onChooseEquipment,
                                onRestDraftChange = actions.onRestDraftChange,
                                onSaveDefaultRest = actions.onSaveDefaultRest,
                            ),
                        topClearance = tabTopClearance,
                        modifier = Modifier.fillMaxSize(),
                    )
                LiftInfoTab.History ->
                    HistoryTab(
                        state = state,
                        onSessionExpandedToggled = actions.onSessionExpandedToggled,
                        topClearance = tabTopClearance,
                    )
            }
        }
    }
}

@Composable
private fun InstructionsTab(
    state: LiftInformationUiState,
    topClearance: Dp = 0.dp,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
        contentPadding =
            PaddingValues(
                start = StratumSpacing.s16,
                end = StratumSpacing.s16,
                top = topClearance,
                bottom = StratumSpacing.s24,
            ),
    ) {
        if (state.imageUrls.isEmpty()) {
            item {
                StratumLiftImage(name = state.liftName, imageUrl = null)
            }
        } else {
            items(state.imageUrls, key = { it }) { url ->
                StratumLiftImage(name = state.liftName, imageUrl = url)
            }
        }
        item {
            val description = state.description
            if (description.isNullOrBlank()) {
                StratumText(
                    text = stringResource(UiR.string.lift_info_no_description),
                    muted = true,
                )
            } else {
                FormattedDescription(description)
            }
        }
    }
}

@Composable
private fun FormattedDescription(text: String) {
    val annotated =
        buildAnnotatedString {
            val parts = text.split(Regex("(?=Tip:)"))
            parts.forEachIndexed { index, part ->
                if (part.startsWith("Tip:")) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("Tip:")
                    }
                    append(part.removePrefix("Tip:"))
                } else {
                    append(part)
                }
                if (index != parts.lastIndex) append(" ")
            }
        }
    StratumText(text = annotated.text)
}

@Composable
private fun HistoryTab(
    state: LiftInformationUiState,
    onSessionExpandedToggled: (String) -> Unit,
    topClearance: Dp = 0.dp,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s16),
        contentPadding =
            PaddingValues(
                start = StratumSpacing.s16,
                end = StratumSpacing.s16,
                top = topClearance,
                bottom = StratumSpacing.s24,
            ),
    ) {
        item {
            HistoryPersonalRecordCard(personalRecord = state.personalRecord)
        }
        if (state.historySessions.isEmpty()) {
            item {
                StratumText(
                    text = stringResource(UiR.string.lift_info_no_history),
                    muted = true,
                )
            }
        } else {
            items(state.historySessions, key = { it.sessionId }) { session ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    HistorySessionRow(
                        session = session,
                        onToggle = { onSessionExpandedToggled(session.sessionId) },
                    )
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = StratumSpacing.s4)
                                .height(shapes.borderWidth)
                                .background(colors.borderSubtle),
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryPersonalRecordCard(
    personalRecord: LiftPrUi?,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color = colors.surfaceRaised, shape = shapes.panel)
                .border(
                    width = shapes.borderWidth,
                    color = colors.borderSubtle,
                    shape = shapes.panel,
                )
                .padding(StratumSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StratumText(
                text = stringResource(UiR.string.lift_info_personal_record),
                style = StratumThemeAccessor.typography.sectionLabel,
                muted = true,
            )
            Icon(
                painter = painterResource(UiR.drawable.ic_personal_record),
                contentDescription = stringResource(UiR.string.lift_info_personal_record),
                tint = colors.prHighlight,
                modifier = Modifier.size(20.dp),
            )
        }
        if (personalRecord == null) {
            StratumText(
                text = stringResource(UiR.string.lift_info_no_pr),
                muted = true,
            )
        } else {
            StratumText(text = personalRecord.dateText, muted = true)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
            ) {
                StratumText(text = personalRecord.weightRepsText)
                if (personalRecord.rir != null) {
                    RirTargetBadge(
                        rirGoal = personalRecord.rir,
                        onClick = {},
                        enabled = false,
                        size = HistoryPrRirBadgeSize,
                    )
                }
            }
            Column(
                modifier = Modifier.padding(top = StratumSpacing.s4),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                StratumText(
                    text = stringResource(UiR.string.lift_info_estimated_one_rep_max),
                    style = StratumThemeAccessor.typography.sectionLabel,
                    muted = true,
                )
                StratumNumber(
                    text = personalRecord.e1rmText,
                    style =
                        StratumThemeAccessor.typography.titleLarge.copy(
                            color = colors.prHighlight,
                        ),
                )
            }
        }
    }
}

private val HistoryPrRirBadgeSize = 20.dp

@Composable
private fun HistorySessionRow(
    session: LiftHistorySessionUi,
    onToggle: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(vertical = StratumSpacing.s8),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                StratumText(text = session.dateText, muted = true)
                StratumText(text = session.workoutLabel)
                StratumText(text = session.bestSetText, muted = true)
            }
            Icon(
                painter =
                    painterResource(
                        if (session.expanded) {
                            UiR.drawable.ic_chevron_down
                        } else {
                            UiR.drawable.ic_chevron_right
                        },
                    ),
                contentDescription =
                    stringResource(
                        if (session.expanded) {
                            UiR.string.lift_info_collapse_session
                        } else {
                            UiR.string.lift_info_expand_session
                        },
                    ),
                tint = StratumThemeAccessor.colors.textMuted,
            )
        }
        AnimatedVisibility(visible = session.expanded) {
            Column(
                modifier = Modifier.padding(start = StratumSpacing.s8, bottom = StratumSpacing.s8),
                verticalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
            ) {
                session.sets.forEach { set ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StratumText(
                            text =
                                stringResource(
                                    UiR.string.lift_info_set_row,
                                    set.setIndex,
                                ) + "  " + set.weightRepsText,
                        )
                        if (set.rir != null) {
                            RirTargetBadge(
                                rirGoal = set.rir,
                                onClick = {},
                                enabled = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

