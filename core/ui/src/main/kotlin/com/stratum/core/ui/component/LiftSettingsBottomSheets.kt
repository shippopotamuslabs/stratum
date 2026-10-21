package com.stratum.core.ui.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumSupersetGroupColors
import com.stratum.core.ui.theme.StratumThemeAccessor

enum class SupersetLinkDirection {
    Previous,
    Next,
    Remove,
}

interface SupersetLinkableLift {
    val workoutLiftId: String
    val liftId: String
    val name: String
    val supersetGroupId: String?
    val useRestTimerBetweenLifts: Boolean
}

data class SupersetLinkableLiftState(
    override val workoutLiftId: String,
    override val liftId: String,
    override val name: String,
    override val supersetGroupId: String?,
    override val useRestTimerBetweenLifts: Boolean,
) : SupersetLinkableLift

data class SupersetLinkSheetOption(
    val direction: SupersetLinkDirection,
    @StringRes val labelRes: Int,
    @DrawableRes val trailingIconRes: Int,
)

fun supersetLinkSheetOptions(
    lifts: List<SupersetLinkableLift>,
    workoutLiftId: String,
): List<SupersetLinkSheetOption> {
    val index = lifts.indexOfFirst { lift -> lift.workoutLiftId == workoutLiftId }
    if (index < 0) {
        return emptyList()
    }
    return buildList {
        val current = lifts[index]
        if (isSupersetPairMember(current, lifts)) {
            val groupSize =
                lifts.count { lift -> lift.supersetGroupId == current.supersetGroupId }
            add(
                SupersetLinkSheetOption(
                    direction = SupersetLinkDirection.Remove,
                    labelRes =
                        if (groupSize >= 3) {
                            CoreUiR.string.edit_workout_remove_from_giant_set
                        } else {
                            CoreUiR.string.edit_workout_remove_from_superset
                        },
                    trailingIconRes = CoreUiR.drawable.ic_join,
                ),
            )
        }
        if (index > 0) {
            val previous = lifts[index - 1]
            if (canLinkWithNeighbor(current, previous)) {
                add(
                    SupersetLinkSheetOption(
                        direction = SupersetLinkDirection.Previous,
                        labelRes = linkLabelForNeighbor(current, previous, isPrevious = true),
                        trailingIconRes = CoreUiR.drawable.ic_join_left,
                    ),
                )
            }
        }
        if (index < lifts.lastIndex) {
            val next = lifts[index + 1]
            if (canLinkWithNeighbor(current, next)) {
                add(
                    SupersetLinkSheetOption(
                        direction = SupersetLinkDirection.Next,
                        labelRes = linkLabelForNeighbor(current, next, isPrevious = false),
                        trailingIconRes = CoreUiR.drawable.ic_join_right,
                    ),
                )
            }
        }
    }
}

private fun canLinkWithNeighbor(current: SupersetLinkableLift, neighbor: SupersetLinkableLift): Boolean =
    current.supersetGroupId == null || current.supersetGroupId != neighbor.supersetGroupId

private fun linkLabelForNeighbor(
    current: SupersetLinkableLift,
    neighbor: SupersetLinkableLift,
    isPrevious: Boolean,
): Int {
    val joiningExistingGroup =
        neighbor.supersetGroupId != null && neighbor.supersetGroupId != current.supersetGroupId
    return when {
        joiningExistingGroup && isPrevious -> CoreUiR.string.edit_workout_giant_set_with_previous
        joiningExistingGroup -> CoreUiR.string.edit_workout_giant_set_with_next
        isPrevious -> CoreUiR.string.edit_workout_superset_with_previous
        else -> CoreUiR.string.edit_workout_superset_with_next
    }
}

fun shouldKeepSupersetSheetOpenAfterLink(
    lifts: List<SupersetLinkableLift>,
    workoutLiftId: String,
    direction: SupersetLinkDirection,
): Boolean {
    if (direction == SupersetLinkDirection.Remove) {
        return false
    }
    val updated =
        linkSupersetWithAdjacent(
            lifts = lifts,
            workoutLiftId = workoutLiftId,
            direction = direction,
            nextGroupId = { "preview-group-id" },
        )
    val current = updated.firstOrNull { lift -> lift.workoutLiftId == workoutLiftId } ?: return false
    return isSupersetPairMember(current, updated) && !isLastInSupersetChain(current, updated)
}

fun isSupersetPairMember(
    lift: SupersetLinkableLift,
    allLifts: List<SupersetLinkableLift>,
): Boolean {
    val groupId = lift.supersetGroupId ?: return false
    return allLifts.count { member -> member.supersetGroupId == groupId } >= 2
}

fun isLastInSupersetChain(
    lift: SupersetLinkableLift,
    allLifts: List<SupersetLinkableLift>,
): Boolean {
    val groupId = lift.supersetGroupId ?: return true
    val chain =
        allLifts
            .filter { member -> member.supersetGroupId == groupId }
    return chain.lastOrNull()?.workoutLiftId == lift.workoutLiftId
}

fun linkSupersetWithAdjacent(
    lifts: List<SupersetLinkableLift>,
    workoutLiftId: String,
    direction: SupersetLinkDirection,
    nextGroupId: () -> String,
): List<SupersetLinkableLiftState> {
    val mapped = lifts.map {
        SupersetLinkableLiftState(
            workoutLiftId = it.workoutLiftId,
            liftId = it.liftId,
            name = it.name,
            supersetGroupId = it.supersetGroupId,
            useRestTimerBetweenLifts = it.useRestTimerBetweenLifts,
        )
    }
    val index = mapped.indexOfFirst { lift -> lift.workoutLiftId == workoutLiftId }
    if (index < 0) {
        return mapped
    }
    val neighborIndex =
        when (direction) {
            SupersetLinkDirection.Previous -> index - 1
            SupersetLinkDirection.Next -> index + 1
            SupersetLinkDirection.Remove -> return mapped
        }
    if (neighborIndex !in mapped.indices) {
        return mapped
    }

    val current = mapped[index]
    val neighbor = mapped[neighborIndex]
    if (current.supersetGroupId != null && current.supersetGroupId == neighbor.supersetGroupId) {
        return mapped
    }

    val targetGroupId =
        neighbor.supersetGroupId
            ?: current.supersetGroupId
            ?: nextGroupId()

    val liftIdsToUpdate =
        buildSet {
            add(current.workoutLiftId)
            if (neighbor.supersetGroupId == null) {
                add(neighbor.workoutLiftId)
            }
        }

    return mapped.map { lift ->
        if (lift.workoutLiftId in liftIdsToUpdate) {
            lift.copy(
                supersetGroupId = targetGroupId,
                useRestTimerBetweenLifts = false,
            )
        } else {
            lift
        }
    }
}

fun unlinkLiftFromSuperset(
    lifts: List<SupersetLinkableLift>,
    workoutLiftId: String,
): List<SupersetLinkableLiftState> {
    val mapped = lifts.map {
        SupersetLinkableLiftState(
            workoutLiftId = it.workoutLiftId,
            liftId = it.liftId,
            name = it.name,
            supersetGroupId = it.supersetGroupId,
            useRestTimerBetweenLifts = it.useRestTimerBetweenLifts,
        )
    }
    val updated =
        mapped.map { lift ->
            if (lift.workoutLiftId == workoutLiftId) {
                lift.copy(
                    supersetGroupId = null,
                    useRestTimerBetweenLifts = false,
                )
            } else {
                lift
            }
        }
    return cleanupSupersetGroups(updated)
}

fun cleanupSupersetGroups(lifts: List<SupersetLinkableLift>): List<SupersetLinkableLiftState> {
    val mapped = lifts.map {
        SupersetLinkableLiftState(
            workoutLiftId = it.workoutLiftId,
            liftId = it.liftId,
            name = it.name,
            supersetGroupId = it.supersetGroupId,
            useRestTimerBetweenLifts = it.useRestTimerBetweenLifts,
        )
    }
    val counts =
        mapped
            .mapNotNull { lift -> lift.supersetGroupId }
            .groupingBy { groupId -> groupId }
            .eachCount()
    return mapped.map { lift ->
        val groupId = lift.supersetGroupId
        if (groupId != null && (counts[groupId] ?: 0) < 2) {
            lift.copy(
                supersetGroupId = null,
                useRestTimerBetweenLifts = false,
            )
        } else {
            lift
        }
    }
}

@Composable
fun LiftActionsBottomSheet(
    onConfigureSuperset: () -> Unit,
    onSwapLift: () -> Unit,
    onRemoveLift: () -> Unit,
    onReorderLifts: () -> Unit,
    onDismiss: () -> Unit,
    onConfigureRestTimes: (() -> Unit)? = null,
    onManageNotes: (() -> Unit)? = null,
    onViewPastSessionNotes: (() -> Unit)? = null,
    onEditEquipment: (() -> Unit)? = null,
) {
    StratumBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(CoreUiR.string.edit_workout_lift_menu),
    ) {
        LiftActionsSheetContent(
            onConfigureSuperset = onConfigureSuperset,
            onSwapLift = onSwapLift,
            onRemoveLift = onRemoveLift,
            onReorderLifts = onReorderLifts,
            onConfigureRestTimes = onConfigureRestTimes,
            onManageNotes = onManageNotes,
            onViewPastSessionNotes = onViewPastSessionNotes,
            onEditEquipment = onEditEquipment,
        )
    }
}

@Composable
fun LiftActionsSheetContent(
    onConfigureSuperset: () -> Unit,
    onSwapLift: () -> Unit,
    onRemoveLift: () -> Unit,
    onReorderLifts: () -> Unit,
    onConfigureRestTimes: (() -> Unit)? = null,
    onManageNotes: (() -> Unit)? = null,
    onViewPastSessionNotes: (() -> Unit)? = null,
    onEditEquipment: (() -> Unit)? = null,
) {
    val groups =
        visibleLiftActionGroups(
            showEditEquipment = onEditEquipment != null,
            showRestTimes = onConfigureRestTimes != null,
            showManageNotes = onManageNotes != null,
            showPastNotes = onViewPastSessionNotes != null,
        )
    groups.forEachIndexed { index, group ->
        if (index > 0) {
            LiftActionsGroupDivider()
        }
        group.forEach { item ->
            when (item) {
                LiftActionItem.Swap ->
                    LiftActionRow(
                        text = stringResource(CoreUiR.string.edit_workout_swap_lift),
                        leadingIconRes = CoreUiR.drawable.ic_swap_horiz,
                        onClick = onSwapLift,
                    )
                LiftActionItem.EditEquipment ->
                    LiftActionRow(
                        text = stringResource(CoreUiR.string.edit_workout_edit_equipment),
                        leadingIconRes = CoreUiR.drawable.ic_barbell,
                        onClick = onEditEquipment ?: {},
                    )
                LiftActionItem.Reorder ->
                    LiftActionRow(
                        text = stringResource(CoreUiR.string.edit_workout_reorder_lifts),
                        leadingIconRes = CoreUiR.drawable.ic_swap_vert,
                        onClick = onReorderLifts,
                    )
                LiftActionItem.RestTimes ->
                    LiftActionRow(
                        text = stringResource(CoreUiR.string.edit_workout_configure_rest_times),
                        leadingIconRes = CoreUiR.drawable.ic_settings,
                        onClick = onConfigureRestTimes ?: {},
                    )
                LiftActionItem.Superset ->
                    LiftActionRow(
                        text = stringResource(CoreUiR.string.edit_workout_create_superset),
                        leadingIconRes = CoreUiR.drawable.ic_join,
                        onClick = onConfigureSuperset,
                    )
                LiftActionItem.ManageNotes ->
                    LiftActionRow(
                        text = stringResource(CoreUiR.string.lift_notes_manage),
                        leadingIconRes = CoreUiR.drawable.ic_note,
                        onClick = onManageNotes ?: {},
                    )
                LiftActionItem.PastNotes ->
                    LiftActionRow(
                        text = stringResource(CoreUiR.string.lift_notes_view_past),
                        leadingIconRes = CoreUiR.drawable.ic_history,
                        onClick = onViewPastSessionNotes ?: {},
                    )
                LiftActionItem.Remove ->
                    LiftActionRow(
                        text = stringResource(CoreUiR.string.edit_workout_remove_lift),
                        leadingIcon = Icons.Outlined.Delete,
                        isDestructive = true,
                        onClick = onRemoveLift,
                    )
            }
        }
    }
}

/** Sheet rows in display order: Swap / Edit / Reorder — Rest / Superset — Notes — Remove. */
internal enum class LiftActionItem {
    Swap,
    EditEquipment,
    Reorder,
    RestTimes,
    Superset,
    ManageNotes,
    PastNotes,
    Remove,
}

/**
 * Visible sheet groups in display order. The destructive [LiftActionItem.Remove] is always
 * the last group alone so it stays visually separated; the notes group is dropped when
 * both notes entries are absent.
 *
 * Internal: a single-sheet layout detail. [LiftActionGroupsTest] locks the order so the
 * enum, builder, and renderer cannot drift apart.
 */
internal fun visibleLiftActionGroups(
    showEditEquipment: Boolean,
    showRestTimes: Boolean,
    showManageNotes: Boolean,
    showPastNotes: Boolean,
): List<List<LiftActionItem>> {
    val primary =
        buildList {
            add(LiftActionItem.Swap)
            if (showEditEquipment) {
                add(LiftActionItem.EditEquipment)
            }
            add(LiftActionItem.Reorder)
        }
    val configure =
        buildList {
            if (showRestTimes) {
                add(LiftActionItem.RestTimes)
            }
            add(LiftActionItem.Superset)
        }
    val notes =
        buildList {
            if (showManageNotes) {
                add(LiftActionItem.ManageNotes)
            }
            if (showPastNotes) {
                add(LiftActionItem.PastNotes)
            }
        }
    return buildList {
        add(primary)
        add(configure)
        if (notes.isNotEmpty()) {
            add(notes)
        }
        add(listOf(LiftActionItem.Remove))
    }
}

@Composable
private fun LiftActionsGroupDivider(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(StratumThemeAccessor.colors.borderSubtle),
    )
}

@Composable
fun SupersetLinkBottomSheet(
    options: List<SupersetLinkSheetOption>,
    showRestBetweenLiftsToggle: Boolean,
    useRestTimerBetweenLifts: Boolean,
    onUseRestTimerBetweenLiftsChange: (Boolean) -> Unit,
    onOptionSelected: (SupersetLinkDirection) -> Unit,
    onDismiss: () -> Unit,
) {
    StratumBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        SupersetLinkSheetContent(
            options = options,
            showRestBetweenLiftsToggle = showRestBetweenLiftsToggle,
            useRestTimerBetweenLifts = useRestTimerBetweenLifts,
            onUseRestTimerBetweenLiftsChange = onUseRestTimerBetweenLiftsChange,
            onOptionSelected = onOptionSelected,
        )
    }
}

/**
 * Superset-link sheet body without the dialog window. Screenshot tests cannot capture
 * dialog windows (an open sheet blanks the golden), so goldens pin this content inline
 * (see docs/screenshot-tests.md). The title lives here (not the sheet wrapper) so the
 * golden covers it; production pixels are unchanged (same text, style, and position).
 */
@Composable
fun SupersetLinkSheetContent(
    options: List<SupersetLinkSheetOption>,
    showRestBetweenLiftsToggle: Boolean,
    useRestTimerBetweenLifts: Boolean,
    onUseRestTimerBetweenLiftsChange: (Boolean) -> Unit,
    onOptionSelected: (SupersetLinkDirection) -> Unit,
) {
    StratumText(
        text = stringResource(CoreUiR.string.edit_workout_create_superset),
        modifier = Modifier.fillMaxWidth().padding(bottom = StratumSpacing.s8),
        style = StratumThemeAccessor.typography.titleLarge,
        textAlign = TextAlign.Center,
    )
    options.forEach { option ->
        SupersetLinkSheetRow(
            label = stringResource(option.labelRes),
            trailingIconRes = option.trailingIconRes,
            onClick = { onOptionSelected(option.direction) },
        )
    }
    if (showRestBetweenLiftsToggle) {
        SupersetRestBetweenLiftsRow(
            checked = useRestTimerBetweenLifts,
            onCheckedChange = onUseRestTimerBetweenLiftsChange,
        )
    }
}

@Composable
private fun SupersetRestBetweenLiftsRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color = colors.surfaceRaised, shape = shapes.panel)
                .border(width = shapes.borderWidth, color = colors.borderSubtle, shape = shapes.panel)
                .clickable { onCheckedChange(!checked) }
                .padding(start = StratumSpacing.s16, end = StratumSpacing.s4, top = StratumSpacing.s8, bottom = StratumSpacing.s8),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(CoreUiR.string.edit_workout_use_rest_timer_between_lifts),
            style = StratumThemeAccessor.typography.bodyLarge,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        StratumCheckbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun SupersetLinkSheetRow(
    label: String,
    @DrawableRes trailingIconRes: Int,
    onClick: () -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color = colors.surfaceRaised, shape = shapes.panel)
                .border(width = shapes.borderWidth, color = colors.borderSubtle, shape = shapes.panel)
                .clickable(onClick = onClick)
                .padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s16),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = StratumThemeAccessor.typography.bodyLarge,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(trailingIconRes),
            contentDescription = null,
            tint = colors.textPrimary,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun LiftActionRow(
    text: String,
    onClick: () -> Unit,
    @DrawableRes leadingIconRes: Int? = null,
    leadingIcon: ImageVector? = null,
    isDestructive: Boolean = false,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val iconTint = if (isDestructive) colors.error else colors.textPrimary
    val textColor = if (isDestructive) colors.error else colors.textPrimary
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color = colors.surfaceRaised, shape = shapes.panel)
                .border(width = shapes.borderWidth, color = colors.borderSubtle, shape = shapes.panel)
                .clickable(onClick = onClick)
                .padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s16),
        horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            leadingIcon != null ->
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp),
                )
            leadingIconRes != null ->
                Icon(
                    painter = painterResource(leadingIconRes),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp),
                )
        }
        Text(
            text = text,
            style = StratumThemeAccessor.typography.bodyLarge,
            color = textColor,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun SupersetGroupChip(
    groupId: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(CoreUiR.string.edit_workout_superset_chip).uppercase(),
        style =
            StratumThemeAccessor.typography.sectionLabel.copy(
                color = StratumSupersetGroupColors.contentColor,
            ),
        modifier =
            modifier
                .background(
                    color = StratumSupersetGroupColors.backgroundFor(groupId),
                    shape = StratumThemeAccessor.shapes.chip,
                )
                .padding(horizontal = StratumSpacing.s8, vertical = StratumSpacing.s4),
    )
}
