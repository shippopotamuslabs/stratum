// STR-142 allowlist: dense 2.dp data stacks pinned; card gaps adopted.
@file:Suppress("ForbiddenRawSpacingLiteralInUi")
package com.stratum.feature.workout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumConfirmDialog
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumOutlinedButton
import com.stratum.core.ui.component.StratumDetailScaffold
import com.stratum.core.ui.component.StratumStatCard
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTextButton
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.share.SHARE_CAPTURE_DEFAULT_PIXEL_SIZE
import com.stratum.core.ui.share.ShareCaptureHost
import com.stratum.core.ui.share.WorkoutShareCard
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.R as CoreUiR
import com.stratum.feature.workout.R

@Composable
fun WorkoutCompleteScreen(
    state: WorkoutCompleteUiState.Loaded,
    actions: WorkoutCompleteActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
    isSharing: Boolean = false,
) {
    val shareContent =
        remember(state.workoutName, state.durationFormatted, state.bestSets) {
            state.toShareContent()
        }
    // Local busy covers the on-demand capture phase before the route sets isSharing.
    var captureActive by remember { mutableStateOf(false) }
    val actionsBusy = isSharing || captureActive

    // Glass (§12.8, STR-157): under the route's glass host the top bar overlays
    // and the scrollable column carries the top clearance as its top content
    // padding, so the summary scrolls beneath the glass. Solid hosts keep the
    // in-flow geometry exactly (chrome.top is 0 there).
    Box(modifier = modifier.fillMaxSize()) {
        StratumDetailScaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHostState = snackbarHostState,
            topBar = {
                StratumTopBar(
                    title = state.workoutName,
                    actions = {
                        StratumIconButton(
                            onClick = {
                                if (!actionsBusy) {
                                    captureActive = true
                                }
                            },
                            enabled = !actionsBusy,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription =
                                    stringResource(CoreUiR.string.workout_share_action_cd),
                                tint = StratumThemeAccessor.colors.textPrimary,
                            )
                        }
                        StratumTextButton(
                            text = stringResource(R.string.workout_complete_close),
                            onClick = actions.onClose,
                            enabled = !actionsBusy,
                        )
                    },
                )
            },
        ) { chrome ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = StratumSpacing.s16,
                            end = StratumSpacing.s16,
                            top = StratumSpacing.s16 + chrome.top,
                            bottom = StratumSpacing.s16,
                        ),
                verticalArrangement = Arrangement.spacedBy(StratumSpacing.s16),
            ) {
                StratumText(
                    text = stringResource(R.string.workout_complete_title),
                    style = StratumThemeAccessor.typography.titleLarge,
                )

                SummaryStatCards(state)

                if (state.bestSets.isNotEmpty()) {
                    PerformanceTable(state.bestSets)
                }

                if (state.repRangeProposals.isNotEmpty()) {
                    RepRangeProposalsSection(
                        proposals = state.repRangeProposals,
                        onAccept = actions.onAcceptRepRangeShift,
                        onSkip = actions.onSkipProposal,
                        onNotNow = actions.onNotNowProposal,
                    )
                }

                if (state.equipmentProposals.isNotEmpty()) {
                    EquipmentProposalsSection(
                        proposals = state.equipmentProposals,
                        onAccept = actions.onAcceptProposal,
                        onAcceptAlternative = actions.onAcceptAlternative,
                        onEdit = actions.onEditProposal,
                        onSkip = actions.onSkipProposal,
                        onNotNow = actions.onNotNowProposal,
                    )
                }
            }
        }

        // On-demand capture only — no permanent 1080² tax while proposals are open.
        ShareCaptureHost(
            active = captureActive,
            pixelSize = SHARE_CAPTURE_DEFAULT_PIXEL_SIZE,
            onCaptured = { image ->
                captureActive = false
                actions.onShareImage(image)
            },
            onCaptureFailed = {
                captureActive = false
                actions.onShareCaptureFailed()
            },
        ) {
            WorkoutShareCard(
                content = shareContent,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    state.blastRadiusConfirm?.let { confirm ->
        StratumConfirmDialog(
            title = confirm.title,
            body = confirm.body,
            confirmLabel = confirm.confirmLabel,
            dismissLabel = stringResource(R.string.workout_complete_proposal_cancel),
            onConfirm = actions.onConfirmBlastRadius,
            onDismiss = actions.onDismissBlastRadius,
        )
    }
}

@Composable
private fun RepRangeProposalsSection(
    proposals: List<RepRangeShiftCardUi>,
    onAccept: (String) -> Unit,
    onSkip: (String) -> Unit,
    onNotNow: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(StratumSpacing.s12)) {
        StratumText(
            text = stringResource(R.string.workout_complete_rep_range_section),
            style = StratumThemeAccessor.typography.titleMedium,
        )
        StratumText(
            text = stringResource(R.string.workout_complete_rep_range_section_hint),
            style = StratumThemeAccessor.typography.sectionLabel,
            muted = true,
        )
        proposals.forEach { card ->
            RepRangeShiftCard(
                card = card,
                onAccept = { onAccept(card.id) },
                onSkip = { onSkip(card.id) },
                onNotNow = { onNotNow(card.id) },
            )
        }
    }
}

@Composable
private fun RepRangeShiftCard(
    card: RepRangeShiftCardUi,
    onAccept: () -> Unit,
    onSkip: () -> Unit,
    onNotNow: () -> Unit,
) {
    val panelShape = StratumThemeAccessor.shapes.panel
    val borderWidth = StratumThemeAccessor.shapes.borderWidth
    val borderColor = StratumThemeAccessor.colors.borderSubtle
    val cardColor = StratumThemeAccessor.colors.surfaceRaised

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = cardColor, shape = panelShape)
            .border(width = borderWidth, color = borderColor, shape = panelShape)
            .padding(StratumSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
    ) {
        StratumText(
            text = stringResource(
                R.string.workout_complete_rep_range_title,
                card.liftName,
                card.proposedBand,
            ),
            style = StratumThemeAccessor.typography.bodyLarge.copy(
                fontWeight = FontWeight.SemiBold,
            ),
        )
        StratumText(
            text = stringResource(
                R.string.workout_complete_rep_range_subtitle,
                card.currentBand,
                card.lastSetFloor,
            ),
            style = StratumThemeAccessor.typography.sectionLabel,
            muted = true,
        )
        StratumText(
            text = stringResource(R.string.workout_complete_rep_range_band, card.bandSummary),
            style = StratumThemeAccessor.typography.sectionLabel,
            muted = true,
        )

        val actionsEnabled = !card.isApplying
        StratumButton(
            text = stringResource(R.string.workout_complete_rep_range_accept),
            onClick = onAccept,
            enabled = actionsEnabled,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8, Alignment.End),
        ) {
            StratumTextButton(
                text = stringResource(R.string.workout_complete_proposal_skip),
                onClick = onSkip,
                enabled = actionsEnabled,
            )
            StratumTextButton(
                text = stringResource(R.string.workout_complete_proposal_not_now),
                onClick = onNotNow,
                enabled = actionsEnabled,
            )
        }
    }
}

@Composable
private fun EquipmentProposalsSection(
    proposals: List<EquipmentProposalCardUi>,
    onAccept: (String) -> Unit,
    onAcceptAlternative: (String) -> Unit,
    onEdit: (String) -> Unit,
    onSkip: (String) -> Unit,
    onNotNow: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(StratumSpacing.s12)) {
        StratumText(
            text = stringResource(R.string.workout_complete_equipment_section),
            style = StratumThemeAccessor.typography.titleMedium,
        )
        StratumText(
            text = stringResource(R.string.workout_complete_equipment_section_hint),
            style = StratumThemeAccessor.typography.sectionLabel,
            muted = true,
        )
        proposals.forEach { card ->
            EquipmentProposalCard(
                card = card,
                onAccept = { onAccept(card.id) },
                onAcceptAlternative = { onAcceptAlternative(card.id) },
                onEdit = { onEdit(card.id) },
                onSkip = { onSkip(card.id) },
                onNotNow = { onNotNow(card.id) },
            )
        }
    }
}

@Composable
private fun EquipmentProposalCard(
    card: EquipmentProposalCardUi,
    onAccept: () -> Unit,
    onAcceptAlternative: () -> Unit,
    onEdit: () -> Unit,
    onSkip: () -> Unit,
    onNotNow: () -> Unit,
) {
    val panelShape = StratumThemeAccessor.shapes.panel
    val borderWidth = StratumThemeAccessor.shapes.borderWidth
    val borderColor = StratumThemeAccessor.colors.borderSubtle
    val cardColor = StratumThemeAccessor.colors.surfaceRaised

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = cardColor, shape = panelShape)
            .border(width = borderWidth, color = borderColor, shape = panelShape)
            .padding(StratumSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
    ) {
        StratumText(
            text = card.title,
            style = StratumThemeAccessor.typography.bodyLarge.copy(
                fontWeight = FontWeight.SemiBold,
            ),
        )
        StratumText(
            text = card.subtitle,
            style = StratumThemeAccessor.typography.sectionLabel,
            muted = true,
        )
        StratumText(
            text = stringResource(R.string.workout_complete_proposal_tiers, card.tierSummary),
            style = StratumThemeAccessor.typography.sectionLabel,
            muted = true,
        )
        if (card.liftNames.isNotBlank()) {
            StratumText(
                text = stringResource(R.string.workout_complete_proposal_lifts, card.liftNames),
                style = StratumThemeAccessor.typography.sectionLabel,
                muted = true,
            )
        }
        if (card.requiresConfirm && card.blastRadiusCount > 0) {
            StratumText(
                text = stringResource(
                    R.string.workout_complete_proposal_blast_hint,
                    card.blastRadiusCount,
                ),
                style = StratumThemeAccessor.typography.sectionLabel,
                muted = true,
            )
        }

        val actionsEnabled = !card.isApplying
        StratumButton(
            text = card.primaryButtonLabel,
            onClick = onAccept,
            enabled = actionsEnabled,
            modifier = Modifier.fillMaxWidth(),
        )
        if (card.alternativeButtonLabel != null) {
            StratumOutlinedButton(
                text = card.alternativeButtonLabel,
                onClick = onAcceptAlternative,
                enabled = actionsEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StratumTextButton(
                text = stringResource(R.string.workout_complete_proposal_edit),
                onClick = onEdit,
                enabled = actionsEnabled,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8, Alignment.End),
            ) {
                StratumTextButton(
                    text = stringResource(R.string.workout_complete_proposal_skip),
                    onClick = onSkip,
                    enabled = actionsEnabled,
                )
                StratumTextButton(
                    text = stringResource(R.string.workout_complete_proposal_not_now),
                    onClick = onNotNow,
                    enabled = actionsEnabled,
                )
            }
        }
    }
}

@Composable
private fun SummaryStatCards(state: WorkoutCompleteUiState.Loaded) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
    ) {
        StratumStatCard(
            label = stringResource(R.string.workout_complete_duration),
            value = state.durationFormatted,
            leading = {
                Icon(
                    imageVector = Icons.Filled.Timer,
                    contentDescription = null,
                    tint = StratumThemeAccessor.colors.textMuted,
                )
            },
            modifier = Modifier.weight(1f),
        )
        val prCount = state.bestSets.count { it.isPersonalRecord }
        StratumStatCard(
            label = stringResource(R.string.workout_complete_personal_records),
            value = prCount.toString(),
            leading = {
                Icon(
                    painter = painterResource(CoreUiR.drawable.ic_personal_record),
                    contentDescription = null,
                    tint = StratumThemeAccessor.colors.prHighlight,
                )
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PerformanceTable(bestSets: List<LiftBestSetUi>) {
    val panelShape = StratumThemeAccessor.shapes.panel
    val borderWidth = StratumThemeAccessor.shapes.borderWidth
    val borderColor = StratumThemeAccessor.colors.borderSubtle
    val cardColor = StratumThemeAccessor.colors.surfaceRaised

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = cardColor, shape = panelShape)
            .border(width = borderWidth, color = borderColor, shape = panelShape)
    ) {
        bestSets.forEachIndexed { index, bestSet ->
            PerformanceItem(
                bestSet = bestSet,
                modifier = Modifier.fillMaxWidth()
            )

            if (index < bestSets.lastIndex) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(borderWidth)
                        .background(color = borderColor)
                )
            }
        }
    }
}

@Composable
private fun PerformanceItem(
    bestSet: LiftBestSetUi,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(StratumSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (bestSet.isPersonalRecord) {
                Icon(
                    painter = painterResource(CoreUiR.drawable.ic_personal_record),
                    contentDescription = stringResource(R.string.workout_complete_pr_cd),
                    tint = StratumThemeAccessor.colors.prHighlight,
                    modifier = Modifier.size(18.dp),
                )
            }
            StratumText(
                text = bestSet.liftName,
                style = StratumThemeAccessor.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val labelStyle = StratumThemeAccessor.typography.sectionLabel
            val valueStyle = StratumThemeAccessor.typography.dataLarge

            Column(
                modifier = Modifier.weight(1.2f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                StratumText(
                    text = stringResource(R.string.workout_complete_table_performance),
                    style = labelStyle,
                    muted = true,
                )
                StratumNumber(
                    text = bestSet.performanceLabel,
                    style = valueStyle,
                )
            }

            Column(
                modifier = Modifier.weight(0.8f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                StratumText(
                    text = stringResource(R.string.workout_complete_table_rir),
                    style = labelStyle,
                    muted = true,
                )
                StratumNumber(
                    text = bestSet.rirLabel,
                    style = valueStyle,
                )
            }

            Column(
                modifier = Modifier.weight(1.0f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                StratumText(
                    text = stringResource(R.string.workout_complete_table_est_1rm),
                    style = labelStyle,
                    muted = true,
                )
                StratumNumber(
                    text = bestSet.estimatedOneRmLabel,
                    style = valueStyle,
                )
            }
        }
    }
}
