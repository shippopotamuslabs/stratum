package com.stratum.core.ui.component.workoutlog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.RirTargetOptionRow
import com.stratum.core.ui.theme.StratumThemeAccessor

/** Snapshot of set-log keyboard pad presentation state. */
@Immutable
data class SetLogKeyboardPadState(
    val activeField: SetLogKeyboardField,
    val selectedRir: Int,
    val showWeightInput: Boolean,
    val allowDecimal: Boolean,
    val canComplete: Boolean = false,
)

/** Callbacks for [StratumSetLogKeyboardContent]. */
@Immutable
data class SetLogKeyboardPadActions(
    val onRirSelected: (Int) -> Unit,
    val onDigit: (Char) -> Unit,
    val onDecimal: () -> Unit,
    val onBackspace: () -> Unit,
    val onPrimaryAction: () -> Unit,
    val onDismiss: () -> Unit,
    val onElevatedNavAction: (() -> Unit)? = null,
)

/**
 * Set-log keyboard pad content (RIR row + numeric keys). Hosted by
 * [ProvideSetLogKeyboardHost] as an in-hierarchy bottom dock.
 *
 * Primary (bottom-right) is Check when the set can complete, else a field-nav chevron.
 * When primary is Check and weight↔reps navigation is available, the nav chevron is
 * elevated into the slot above primary (see [setLogKeyboardElevatedNavIcon]).
 */
@Composable
internal fun StratumSetLogKeyboardContent(
    state: SetLogKeyboardPadState,
    actions: SetLogKeyboardPadActions,
    modifier: Modifier = Modifier,
) {
    val primaryIcon =
        setLogKeyboardPrimaryIcon(
            active = state.activeField,
            canComplete = state.canComplete,
            showWeightInput = state.showWeightInput,
        )
    val elevatedNavIcon =
        setLogKeyboardElevatedNavIcon(
            active = state.activeField,
            canComplete = state.canComplete,
            showWeightInput = state.showWeightInput,
        )
    val primaryDescription =
        when (primaryIcon) {
            SetLogKeyboardPrimaryIcon.NextReps ->
                stringResource(CoreUiR.string.set_log_keyboard_next_reps)
            SetLogKeyboardPrimaryIcon.PrevWeight ->
                stringResource(CoreUiR.string.set_log_keyboard_prev_weight)
            SetLogKeyboardPrimaryIcon.Check ->
                stringResource(CoreUiR.string.set_log_keyboard_complete_set)
        }
    val elevatedDescription =
        when (elevatedNavIcon) {
            SetLogKeyboardPrimaryIcon.NextReps ->
                stringResource(CoreUiR.string.set_log_keyboard_next_reps)
            SetLogKeyboardPrimaryIcon.PrevWeight ->
                stringResource(CoreUiR.string.set_log_keyboard_prev_weight)
            SetLogKeyboardPrimaryIcon.Check, null -> null
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RirTargetOptionRow(
            selectedRirGoal = state.selectedRir,
            onRirSelected = actions.onRirSelected,
            chipSize = KeyboardRirChipSize,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(StratumThemeAccessor.colors.borderSubtle),
        )
        StratumNumericPad(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            allowDecimal = state.allowDecimal,
            onDigit = actions.onDigit,
            onDecimal = actions.onDecimal,
            onBackspace = actions.onBackspace,
            onDismiss = actions.onDismiss,
            primaryContentDescription = primaryDescription,
            onPrimaryAction = actions.onPrimaryAction,
            primaryContent = {
                SetLogKeyboardNavIcon(icon = primaryIcon)
            },
            elevatedContentDescription = elevatedDescription,
            onElevatedAction =
                if (elevatedNavIcon != null) {
                    actions.onElevatedNavAction
                } else {
                    null
                },
            elevatedContent =
                elevatedNavIcon?.let { icon ->
                    {
                        SetLogKeyboardNavIcon(icon = icon)
                    }
                },
        )
    }
}

@Composable
private fun SetLogKeyboardNavIcon(icon: SetLogKeyboardPrimaryIcon) {
    when (icon) {
        SetLogKeyboardPrimaryIcon.NextReps ->
            Icon(
                painter = painterResource(CoreUiR.drawable.ic_chevron_right),
                contentDescription = null,
                tint = StratumThemeAccessor.colors.textPrimary,
                modifier = Modifier.size(NumericPadKeyIconSize),
            )
        SetLogKeyboardPrimaryIcon.PrevWeight ->
            Icon(
                painter = painterResource(CoreUiR.drawable.ic_chevron_left),
                contentDescription = null,
                tint = StratumThemeAccessor.colors.textPrimary,
                modifier = Modifier.size(NumericPadKeyIconSize),
            )
        SetLogKeyboardPrimaryIcon.Check ->
            Icon(
                painter = painterResource(CoreUiR.drawable.ic_check),
                contentDescription = null,
                tint = StratumThemeAccessor.colors.textPrimary,
                modifier = Modifier.size(NumericPadKeyIconSize),
            )
    }
}

private val KeyboardRirChipSize = 32.dp
