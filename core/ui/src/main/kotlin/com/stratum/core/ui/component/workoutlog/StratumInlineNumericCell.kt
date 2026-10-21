package com.stratum.core.ui.component.workoutlog

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import android.view.inputmethod.InputMethodManager
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Compact numeric cell for weight/reps in a set-log row.
 *
 * When [onActivate] is non-null, the system IME is suppressed and taps open the custom
 * set-log keyboard via [onActivate]. [isActive] drives the accent border and requests
 * list scroll so the cell sits above the docked keyboard.
 *
 * When active with a custom keyboard, a **drawn** blinking caret is shown ([cursorIndex]);
 * taps place the caret without focusing a platform text field (no system IME). Pad keys
 * insert/delete at that caret on the host. Selection ranges are not supported.
 *
 * [completed] is visual only: keeps the dark sunken chip on finished sets even when
 * [enabled] so lifters can still edit without losing the completed-row look.
 *
 * [onValueChange] is only used on the system-IME path ([onActivate] null). Custom-keyboard
 * hosts update values through the dock session instead.
 */
@Composable
fun StratumInlineNumericCell(
    value: String,
    placeholder: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit = {},
    isActive: Boolean = false,
    onActivate: (() -> Unit)? = null,
    completed: Boolean = false,
    /** When true, border uses error color (takes priority over [isActive] accent). */
    isError: Boolean = false,
    /**
     * Caret index when active with a custom keyboard (`0..value.length`).
     * Callers should pass host cursor when active; default is end of [value].
     */
    cursorIndex: Int = -1,
    /** Caret moved by tap inside the focused custom-keyboard field. Required when active. */
    onCursorChange: ((Int) -> Unit)? = null,
    /** Selection range when active with a custom keyboard. */
    selection: TextRange = if (cursorIndex >= 0) TextRange(cursorIndex) else TextRange(value.length),
) {
    val interactionSource = remember { MutableInteractionSource() }
    val useCustomKeyboard = onActivate != null
    var cellBoundsInWindow by remember { mutableStateOf(Rect.Zero) }
    val resolvedCursor =
        if (cursorIndex < 0) {
            value.length
        } else {
            cursorIndex.coerceIn(0, value.length)
        }

    if (useCustomKeyboard) {
        DockCellRevealEffect(
            isActive = isActive,
            cellBoundsInWindow = cellBoundsInWindow,
        )
    }

    val borderColor =
        when {
            isError -> StratumThemeAccessor.colors.error
            isActive -> StratumThemeAccessor.colors.accent
            else -> StratumThemeAccessor.colors.borderSubtle
        }

    // Completed rows keep sunken chips (dark on setComplete green); incomplete enabled
    // fields use inset; disabled incomplete uses sunken.
    val cellBackground =
        if (completed || !enabled) {
            StratumThemeAccessor.colors.surfaceSunken
        } else {
            StratumThemeAccessor.colors.surfaceInset
        }

    val cellModifier =
        modifier
            .onGloballyPositioned { coords ->
                cellBoundsInWindow = coords.boundsInWindow()
            }
            .height(36.dp)
            .clip(StratumThemeAccessor.shapes.chip)
            .background(cellBackground)
            .border(
                width = StratumThemeAccessor.shapes.borderWidth,
                color = borderColor,
                shape = StratumThemeAccessor.shapes.chip,
            )
            .padding(horizontal = StratumSpacing.s4)

    if (useCustomKeyboard) {
        if (isActive && enabled) {
            CustomKeyboardFocusedCell(
                value = value,
                placeholder = placeholder,
                cursorIndex = resolvedCursor,
                selection = selection,
                onCursorChange = onCursorChange ?: {},
                enabled = enabled,
                modifier = cellModifier,
            )
        } else {
            // useCustomKeyboard guarantees onActivate is non-null.
            val activate = requireNotNull(onActivate)
            Box(
                modifier =
                    cellModifier.then(
                        if (enabled) {
                            Modifier.clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = activate,
                            )
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    value.isEmpty() ->
                        StratumText(text = placeholder, muted = true, maxLines = 1)
                    else ->
                        StratumText(
                            text = value,
                            muted = !enabled,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                        )
                }
            }
        }
    } else {
        SystemImeNumericCell(
            value = value,
            placeholder = placeholder,
            enabled = enabled,
            onValueChange = onValueChange,
            modifier = cellModifier,
            interactionSource = interactionSource,
        )
    }
}

/**
 * Custom-pad focused cell: **no** platform [BasicTextField], so the system IME cannot open.
 *
 * Caret is drawn manually and taps map to character offsets via [TextLayoutResult].
 * Compose's TextFieldValue [BasicTextField] always starts an IME session on tap
 * (`showKeyboardOnFocus` is unsupported on that overload and ignored on from-tap sessions).
 */
@Composable
private fun CustomKeyboardFocusedCell(
    value: String,
    placeholder: String,
    cursorIndex: Int,
    selection: TextRange,
    onCursorChange: (Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val density = LocalDensity.current
    val clampedCursor = cursorIndex.coerceIn(0, value.length)
    val hasSelection = selection.min < selection.max && value.isNotEmpty()
    val caretColor = StratumThemeAccessor.colors.accent
    // Start-aligned style: caret geometry from getCursorRect is relative to glyph origin.
    // Do not use TextAlign.Center here — that inflates layout width and shifts caret coords.
    val textStyle =
        StratumThemeAccessor.typography.bodyLarge.copy(
            color =
                if (enabled) {
                    StratumThemeAccessor.colors.textPrimary
                } else {
                    StratumThemeAccessor.colors.textMuted
                },
            textAlign = TextAlign.Start,
        )

    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var textOriginInCell by remember { mutableStateOf(Offset.Zero) }
    var caretVisible by remember { mutableStateOf(true) }

    // Ensure any previously open system IME is dismissed when the custom pad takes over.
    LaunchedEffect(Unit) {
        keyboardController?.hide()
        val imm =
            view.context.getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    // Blink like a platform caret; reset on caret/text change. Pause blink while selection is active.
    LaunchedEffect(clampedCursor, value, hasSelection) {
        if (hasSelection) {
            caretVisible = false
            return@LaunchedEffect
        }
        caretVisible = true
        while (true) {
            delay(CARET_BLINK_HALF_PERIOD_MS)
            caretVisible = !caretVisible
        }
    }

    Box(
        modifier =
            modifier.pointerInput(value, textLayout, textOriginInCell) {
                detectTapGestures { tap ->
                    val layout =
                        textLayout?.takeIf { measured ->
                            measured.layoutInput.text.text == value
                        }
                    if (layout == null || value.isEmpty()) {
                        onCursorChange(if (value.isEmpty()) 0 else value.length)
                        return@detectTapGestures
                    }
                    val localInText =
                        Offset(
                            x = tap.x - textOriginInCell.x,
                            y = tap.y - textOriginInCell.y,
                        )
                    val clamped =
                        Offset(
                            x = localInText.x.coerceIn(0f, layout.size.width.toFloat()),
                            y = (layout.size.height / 2f),
                        )
                    val index =
                        layout
                            .getOffsetForPosition(clamped)
                            .coerceIn(0, value.length)
                    onCursorChange(index)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (value.isEmpty()) {
            // Blank + placeholder: hide caret (it blinks over the suggestion and looks noisy).
            // Blank without placeholder: centered caret so the empty field still feels focused.
            if (placeholder.isNotEmpty()) {
                StratumText(text = placeholder, muted = true, maxLines = 1)
            } else if (caretVisible) {
                Box(
                    modifier =
                        Modifier
                            .width(caretWidth)
                            .fillMaxHeight(0.55f)
                            .background(caretColor),
                )
            }
        } else {
            // TopStart box: BasicText at (0,0) and caret offset from getCursorRect share one
            // origin. Do NOT center-align children here — that double-shifts the caret right.
            val selectionModifier =
                if (hasSelection) {
                    Modifier.drawBehind {
                        val layout =
                            textLayout?.takeIf { measured ->
                                measured.layoutInput.text.text == value
                            }
                        if (layout != null) {
                            val safeStart = selection.min.coerceIn(0, value.length)
                            val safeEnd = selection.max.coerceIn(0, value.length)
                            if (safeStart < safeEnd) {
                                val selectionPath = layout.getPathForRange(safeStart, safeEnd)
                                drawPath(
                                    path = selectionPath,
                                    color = caretColor.copy(alpha = 0.35f),
                                )
                            }
                        }
                    }
                } else {
                    Modifier
                }
            Box(
                modifier =
                    Modifier
                        .then(selectionModifier)
                        .onGloballyPositioned { coords ->
                            textOriginInCell = coords.positionInParent()
                        },
            ) {
                BasicText(
                    text = value,
                    style = textStyle,
                    maxLines = 1,
                    softWrap = false,
                    onTextLayout = { textLayout = it },
                )
                // textLayout can lag one frame behind [value] after pad edits; never call
                // getCursorRect with an index past the *layout* length (crashes).
                val layout =
                    textLayout?.takeIf { measured ->
                        measured.layoutInput.text.text == value
                    }
                if (layout != null && caretVisible && !hasSelection) {
                    val layoutLength = layout.layoutInput.text.length
                    val safeCursor = clampedCursor.coerceIn(0, layoutLength)
                    val cursorRect = layout.getCursorRect(safeCursor)
                    val caretWidthPx = with(density) { caretWidth.toPx() }.coerceAtLeast(1f)
                    // Nudge half a pixel left so the stroke sits between glyphs, not on them.
                    val caretX = (cursorRect.left - caretWidthPx / 2f).coerceAtLeast(0f)
                    Box(
                        modifier =
                            Modifier
                                .offset {
                                    IntOffset(
                                        x = caretX.roundToInt(),
                                        y = cursorRect.top.roundToInt(),
                                    )
                                }
                                .width(with(density) { caretWidthPx.toDp() })
                                .height(with(density) { cursorRect.height.toDp() })
                                .background(caretColor),
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemImeNumericCell(
    value: String,
    placeholder: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    interactionSource: MutableInteractionSource,
) {
    var fieldValue by remember(value) {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }

    LaunchedEffect(value) {
        fieldValue = TextFieldValue(text = value, selection = TextRange(value.length))
    }

    BasicTextField(
        value = fieldValue,
        onValueChange = { updated ->
            val cleaned = updated.text.filter { char -> char.isDigit() || char == '.' }
            fieldValue = updated.copy(text = cleaned, selection = TextRange(cleaned.length))
            onValueChange(cleaned)
        },
        enabled = enabled,
        modifier = modifier,
        textStyle =
            StratumThemeAccessor.typography.bodyLarge.copy(
                color =
                    if (enabled) {
                        StratumThemeAccessor.colors.textPrimary
                    } else {
                        StratumThemeAccessor.colors.textMuted
                    },
                textAlign = TextAlign.Center,
            ),
        singleLine = true,
        cursorBrush = SolidColor(StratumThemeAccessor.colors.accent),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.Center) {
                if (fieldValue.text.isEmpty()) {
                    StratumText(text = placeholder, muted = true, maxLines = 1)
                }
                innerTextField()
            }
        },
    )
}

private val caretWidth = 2.dp
private const val CARET_BLINK_HALF_PERIOD_MS = 530L
