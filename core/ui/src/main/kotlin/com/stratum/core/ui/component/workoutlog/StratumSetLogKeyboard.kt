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
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.RirTargetOptionRow
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Presentation-only set-log keyboard composable.
 *
 * Renders the RIR chip row above a divider, then [StratumNumericPad]. Contains no
 * domain/workout business logic: callers supply [SetLogKeyboardPadState] and
 * [SetLogKeyboardPadActions].
 */
@Immutable
data class SetLogKeyboardPadState(
    val activeField: SetLogKeyboardField,
    val selectedRir: Int?,
    val showWeightInput: Boolean,
    val allowDecimal: Boolean,
    val canComplete: Boolean,
    val showPlateCalculator: Boolean = false,
)

@Immutable
data class SetLogKeyboardPadActions(
    val onRirSelected: (Int) -> Unit,
    val onDigit: (Char) -> Unit,
    val onDecimal: () -> Unit,
    val onBackspace: () -> Unit,
    val onPrimaryAction: () -> Unit,
    val onDismiss: () -> Unit,
    val onElevatedNavAction: (() -> Unit)? = null,
    val onPlateCalculatorClick: (() -> Unit)? = null,
)

@Composable
fun StratumSetLogKeyboardContent(
    state: SetLogKeyboardPadState,
    actions: SetLogKeyboardPadActions,
    modifier: Modifier = Modifier,
) {
    val primaryIcon =
        setLogKeyboardPrimaryIcon(canComplete = state.canComplete)
    val primaryDescription =
        when (primaryIcon) {
            SetLogKeyboardPrimaryIcon.Check ->
                stringResource(CoreUiR.string.set_log_keyboard_action_complete)
            SetLogKeyboardPrimaryIcon.NextReps,
            SetLogKeyboardPrimaryIcon.PrevWeight,
            null,
            -> null
        }
    val elevatedNavIcon =
        setLogKeyboardElevatedNavIcon(
            active = state.activeField,
            showWeightInput = state.showWeightInput,
        )
    val elevatedDescription =
        when (elevatedNavIcon) {
            SetLogKeyboardPrimaryIcon.NextReps ->
                stringResource(CoreUiR.string.set_log_keyboard_action_next_reps)
            SetLogKeyboardPrimaryIcon.PrevWeight ->
                stringResource(CoreUiR.string.set_log_keyboard_action_prev_weight)
            SetLogKeyboardPrimaryIcon.Check, null -> null
        }

    Column(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RirTargetOptionRow(
            selectedRirGoal = state.selectedRir ?: -1,
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
            onPrimaryAction = if (primaryIcon != null) actions.onPrimaryAction else null,
            primaryContent =
                if (primaryIcon != null) {
                    { SetLogKeyboardNavIcon(icon = primaryIcon) }
                } else {
                    null
                },
            elevatedActionKey =
                if (elevatedNavIcon != null && actions.onElevatedNavAction != null && elevatedDescription != null) {
                    NumericPadActionKey(
                        contentDescription = elevatedDescription,
                        onAction = actions.onElevatedNavAction,
                        content = { SetLogKeyboardNavIcon(icon = elevatedNavIcon) },
                    )
                } else {
                    null
                },
            auxiliaryActionKey =
                if (state.showPlateCalculator && actions.onPlateCalculatorClick != null) {
                    NumericPadActionKey(
                        contentDescription = stringResource(CoreUiR.string.set_log_keyboard_plate_calculator),
                        onAction = actions.onPlateCalculatorClick,
                        content = {
                            Icon(
                                painter = painterResource(CoreUiR.drawable.ic_barbell),
                                contentDescription = null,
                                tint = LocalContentColor.current,
                                modifier = Modifier.size(NumericPadKeyIconSize),
                            )
                        },
                    )
                } else {
                    null
                },
        )
    }
}

@Composable
private fun SetLogKeyboardNavIcon(icon: SetLogKeyboardPrimaryIcon) {
    val tint = LocalContentColor.current
    when (icon) {
        SetLogKeyboardPrimaryIcon.NextReps ->
            Icon(
                painter = painterResource(CoreUiR.drawable.ic_chevron_right),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(NumericPadKeyIconSize),
            )
        SetLogKeyboardPrimaryIcon.PrevWeight ->
            Icon(
                painter = painterResource(CoreUiR.drawable.ic_chevron_left),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(NumericPadKeyIconSize),
            )
        SetLogKeyboardPrimaryIcon.Check ->
            Icon(
                painter = painterResource(CoreUiR.drawable.ic_check),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(NumericPadKeyIconSize),
            )
    }
}

private val KeyboardRirChipSize = 32.dp
