package com.stratum.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stratum.core.ui.R
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor

internal enum class SegmentUnit { Hours, Minutes }

/**
 * Digital-clock style duration picker with independent hour and minute steppers.
 * Values are expressed as total minutes and clamped to [minMinutes]..[maxMinutes].
 * The minute segment adjusts by [minutesStep] per tap (default 1).
 *
 * Each unit renders as its own bordered value box with a bordered up/down control
 * column beside it. Tapping a value box turns it into an inline numeric field
 * (on-screen numeric keyboard) so the user can type the value directly; the typed
 * text is validated on commit — blank, non-numeric, and out-of-range input is
 * rejected via [onInvalidInput] and the display reverts to the last valid value.
 * Digits use the bundled DSEG7 segment font, sized to fill and centered. Hours and
 * minutes both show two digits (leading zero below 10). UI_DESIGN.md §5 — controls
 * ≥ 48dp.
 */
@Composable
fun StratumDurationStepper(
    totalMinutes: Int,
    onTotalMinutesChange: (Int) -> Unit,
    minMinutes: Int,
    maxMinutes: Int,
    hoursLabel: String,
    minutesLabel: String,
    incrementContentDescription: String,
    decrementContentDescription: String,
    modifier: Modifier = Modifier,
    minutesStep: Int = 1,
    isError: Boolean = false,
    onInvalidInput: ((InvalidDurationInput) -> Unit)? = null,
) {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val borderColor = if (isError) {
        StratumThemeAccessor.colors.accent
    } else {
        StratumThemeAccessor.colors.borderSubtle
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
        verticalAlignment = Alignment.Top,
    ) {
        DurationSegment(
            unit = SegmentUnit.Hours,
            label = hoursLabel,
            hours = hours,
            minutes = minutes,
            minMinutes = minMinutes,
            maxMinutes = maxMinutes,
            controls = StepperControls(
                incrementEnabled = totalMinutes + 60 <= maxMinutes,
                decrementEnabled = totalMinutes - 60 >= minMinutes,
                incrementContentDescription = incrementContentDescription,
                decrementContentDescription = decrementContentDescription,
                onIncrement = { onTotalMinutesChange((totalMinutes + 60).coerceIn(minMinutes, maxMinutes)) },
                onDecrement = { onTotalMinutesChange((totalMinutes - 60).coerceIn(minMinutes, maxMinutes)) },
            ),
            onTotalMinutesChange = onTotalMinutesChange,
            onInvalidInput = onInvalidInput,
            borderColor = borderColor,
            isError = isError,
            modifier = Modifier.weight(1f),
        )
        SegmentColon(
            isError = isError,
            modifier = Modifier.fillMaxHeight().wrapContentHeight(),
        )
        DurationSegment(
            unit = SegmentUnit.Minutes,
            label = minutesLabel,
            hours = hours,
            minutes = minutes,
            minMinutes = minMinutes,
            maxMinutes = maxMinutes,
            controls = StepperControls(
                incrementEnabled = totalMinutes + minutesStep <= maxMinutes,
                decrementEnabled = totalMinutes - minutesStep >= minMinutes,
                incrementContentDescription = incrementContentDescription,
                decrementContentDescription = decrementContentDescription,
                onIncrement = {
                    onTotalMinutesChange((totalMinutes + minutesStep).coerceIn(minMinutes, maxMinutes))
                },
                onDecrement = {
                    onTotalMinutesChange((totalMinutes - minutesStep).coerceIn(minMinutes, maxMinutes))
                },
            ),
            onTotalMinutesChange = onTotalMinutesChange,
            onInvalidInput = onInvalidInput,
            borderColor = borderColor,
            isError = isError,
            modifier = Modifier.weight(1f),
        )
    }
}

private data class StepperControls(
    val incrementEnabled: Boolean,
    val decrementEnabled: Boolean,
    val incrementContentDescription: String,
    val decrementContentDescription: String,
    val onIncrement: () -> Unit,
    val onDecrement: () -> Unit,
)

@Composable
private fun DurationSegment(
    unit: SegmentUnit,
    label: String,
    hours: Int,
    minutes: Int,
    minMinutes: Int,
    maxMinutes: Int,
    controls: StepperControls,
    onTotalMinutesChange: (Int) -> Unit,
    onInvalidInput: ((InvalidDurationInput) -> Unit)?,
    borderColor: Color,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = StratumThemeAccessor.shapes.panel
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(StepperControlRowHeight),
            horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
            verticalAlignment = Alignment.Top,
        ) {
            ValueBox(
                unit = unit,
                hours = hours,
                minutes = minutes,
                minMinutes = minMinutes,
                maxMinutes = maxMinutes,
                borderColor = borderColor,
                shape = shape,
                isError = isError,
                onTotalMinutesChange = onTotalMinutesChange,
                onInvalidInput = onInvalidInput,
                modifier = Modifier.weight(1f),
            )
            StepperControlColumn(
                controls = controls,
                borderColor = borderColor,
                shape = shape,
                modifier = Modifier.weight(1f),
            )
        }
        StratumText(
            text = label,
            muted = true,
            modifier = Modifier.fillMaxWidth(),
            style = StratumThemeAccessor.typography.bodyLarge.copy(textAlign = TextAlign.Center),
        )
    }
}

@Composable
private fun ValueBox(
    unit: SegmentUnit,
    hours: Int,
    minutes: Int,
    minMinutes: Int,
    maxMinutes: Int,
    borderColor: Color,
    shape: Shape,
    isError: Boolean,
    onTotalMinutesChange: (Int) -> Unit,
    onInvalidInput: ((InvalidDurationInput) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val displayValue = if (unit == SegmentUnit.Hours) hours else minutes
    val fontFamily = remember { FontFamily(Font(R.font.dseg7_classic_regular)) }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current
    // Raw text while editing. Seeded with the current (unpadded) unit value on focus gain;
    // reverted to the committed display value on focus loss.
    var text by remember { mutableStateOf(displayValue.toString()) }

    // Commit + revert precisely once when focus transitions focused -> unfocused.
    LaunchedEffect(isFocused) {
        if (!isFocused) {
            commitSegmentInput(
                unit = unit,
                raw = text,
                hours = hours,
                minutes = minutes,
                minMinutes = minMinutes,
                maxMinutes = maxMinutes,
                onTotalMinutesChange = onTotalMinutesChange,
                onInvalidInput = onInvalidInput,
            )
            text = if (unit == SegmentUnit.Hours) hours.toString() else minutes.toString()
        } else {
            text = displayValue.toString()
        }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .border(
                width = StratumThemeAccessor.shapes.borderWidth,
                color = borderColor,
                shape = shape,
            )
            .background(
                color = StratumThemeAccessor.colors.surfaceRaised,
                shape = shape,
            )
            .clip(shape)
            .padding(SegmentValuePadding)
            .semantics { this.contentDescription = unit.name },
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints {
            val sizeByHeight = maxHeight * SEGMENT_FONT_HEIGHT_RATIO
            val sizeByWidth = maxWidth / maxOf(1, displayValue.toString().padStart(2, '0').length) *
                SEGMENT_FONT_WIDTH_RATIO
            val fontSize = minOf(sizeByHeight, sizeByWidth).value.sp
            val textColor = if (isError) {
                StratumThemeAccessor.colors.accent
            } else {
                StratumThemeAccessor.colors.textData
            }
            // Show the padded display value when not focused, the raw edit buffer when
            // focused. Keeping only ASCII digits guards against paste / physical-keyboard
            // entry of symbols or text; the numeric IME already filters soft input.
            val visibleText = if (isFocused) text.filter(Char::isDigit) else displayValue.toString().padStart(2, '0')
            BasicTextField(
                value = TextFieldValue(text = visibleText, selection = TextRange(visibleText.length)),
                onValueChange = { text = it.text.filter(Char::isDigit) },
                modifier = Modifier
                    .widthIn(min = 48.dp)
                    .semantics { this.contentDescription = unit.name },
                enabled = true,
                readOnly = false,
                textStyle = TextStyle(
                    fontFamily = fontFamily,
                    fontSize = fontSize,
                    color = textColor,
                    textAlign = TextAlign.Center,
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                cursorBrush = SolidColor(StratumThemeAccessor.colors.accent),
                singleLine = true,
                interactionSource = interactionSource,
            )
        }
    }
}

/**
 * Parses the locally-edited segment text and commits it via [onTotalMinutesChange] when
 * it yields a total within [minMinutes]..[maxMinutes]. Otherwise reports the rejection
 * reason to [onInvalidInput] and leaves the persisted value untouched (the caller's
 * display reverts to the last valid value).
 */
/**
 * Outcome of validating a typed segment value. [Valid] carries the resulting total
 * minutes to commit; [Invalid] carries the rejection reason. Pure (no Android/Compose
 * dependencies) so it can be unit-tested on the JVM.
 */
sealed interface SegmentInputResult {
    data class Valid(val totalMinutes: Int) : SegmentInputResult
    data class Invalid(val reason: InvalidDurationInput) : SegmentInputResult
}

/**
 * Parses and validates the locally-edited segment text against the duration bounds.
 * Returns [SegmentInputResult.Valid] with the new total minutes when the typed value
 * is a number whose resulting total lies within [minMinutes]..[maxMinutes]; otherwise
 * [SegmentInputResult.Invalid] with the specific rejection reason.
 */
internal fun resolveSegmentInput(
    unit: SegmentUnit,
    raw: String,
    hours: Int,
    minutes: Int,
    minMinutes: Int,
    maxMinutes: Int,
): SegmentInputResult {
    if (raw.isBlank()) return SegmentInputResult.Invalid(InvalidDurationInput.Blank)
    val entered = raw.toIntOrNull()
        ?: return SegmentInputResult.Invalid(InvalidDurationInput.NotANumber)
    val candidateTotal = if (unit == SegmentUnit.Hours) {
        entered * 60 + minutes
    } else {
        hours * 60 + entered
    }
    return when {
        candidateTotal < minMinutes -> SegmentInputResult.Invalid(InvalidDurationInput.TooShort)
        candidateTotal > maxMinutes -> SegmentInputResult.Invalid(InvalidDurationInput.TooLong)
        else -> SegmentInputResult.Valid(candidateTotal)
    }
}

private fun commitSegmentInput(
    unit: SegmentUnit,
    raw: String,
    hours: Int,
    minutes: Int,
    minMinutes: Int,
    maxMinutes: Int,
    onTotalMinutesChange: (Int) -> Unit,
    onInvalidInput: ((InvalidDurationInput) -> Unit)?,
) {
    when (val result = resolveSegmentInput(unit, raw, hours, minutes, minMinutes, maxMinutes)) {
        is SegmentInputResult.Valid -> onTotalMinutesChange(result.totalMinutes)
        is SegmentInputResult.Invalid -> onInvalidInput?.invoke(result.reason)
    }
}

@Composable
private fun SegmentColon(
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        // Colon rendered in the same segment font so it reads as part of the display.
        StratumSegmentNumber(
            text = ":",
            fontSize = SegmentColonSize,
            isError = isError,
        )
    }
}

@Composable
private fun StepperControlColumn(
    controls: StepperControls,
    borderColor: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .border(
                width = StratumThemeAccessor.shapes.borderWidth,
                color = borderColor,
                shape = shape,
            )
            .background(
                color = StratumThemeAccessor.colors.surfaceSunken,
                shape = shape,
            )
            .clip(shape),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        StepperButton(
            glyph = "▲",
            enabled = controls.incrementEnabled,
            contentDescription = controls.incrementContentDescription,
            onClick = controls.onIncrement,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(StratumThemeAccessor.shapes.borderWidth)
                .background(StratumThemeAccessor.colors.borderSubtle),
        )
        StepperButton(
            glyph = "▼",
            enabled = controls.decrementEnabled,
            contentDescription = controls.decrementContentDescription,
            onClick = controls.onDecrement,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}

@Composable
private fun StepperButton(
    glyph: String,
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (enabled) {
        StratumThemeAccessor.colors.accent
    } else {
        StratumThemeAccessor.colors.textMuted
    }
    Box(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = StratumThemeAccessor.typography.bodyLarge,
            color = color,
        )
    }
}

// §12.5 derivation: the two stepper buttons split the control row evenly, so
// the row is fixed at 2 x 48dp + the 1dp divider = 97dp. The segment label
// below takes its intrinsic height, so the stepper's total height is not
// fixed: it is this row plus the 4dp gap plus the label. At the old fixed
// 96dp total the buttons were ~33dp.
private val StepperControlRowHeight = 97.dp
private val SegmentValuePadding = 4.dp
private const val SEGMENT_FONT_HEIGHT_RATIO = 0.6f
private const val SEGMENT_FONT_WIDTH_RATIO = 1.2f
private val SegmentColonSize = 28.sp
