package com.stratum.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Stratum modal bottom sheet chrome. Feature modules use this instead of raw M3 bottom sheets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StratumBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
        // §12.3: sheets sit on surface-3.
        containerColor = StratumThemeAccessor.colors.surfaceInset,
        contentColor = StratumThemeAccessor.colors.textPrimary,
        dragHandle = { StratumBottomSheetHandle() },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = StratumSpacing.s12)
                    .padding(top = StratumSpacing.s4, bottom = StratumSpacing.s32),
            verticalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
        ) {
            title?.let {
                StratumText(
                    text = it,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = StratumSpacing.s8),
                    style = StratumThemeAccessor.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
            }
            content()
        }
    }
}

/**
 * Full-bleed keyboard-style sheet: square corners, no drag handle, light scrim so the
 * focused set row stays readable while typing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StratumKeyboardBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
        shape = RectangleShape,
        containerColor = StratumThemeAccessor.colors.surfaceRaised,
        contentColor = StratumThemeAccessor.colors.textPrimary,
        scrimColor = Color.Black.copy(alpha = KEYBOARD_SHEET_SCRIM_ALPHA),
        dragHandle = null,
        content = {
            // No extra chrome padding — the keyboard content owns its height (~33% screen).
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content,
            )
        },
    )
}

/**
 * The one interactive surface for a bottom sheet option row: raised panel,
 * its border, then a clip to the panel shape ahead of the clickable, so the
 * press highlight follows the row's rounded corners instead of spilling past
 * them as a sharp rectangle. Every sheet option row builds on this chain, so
 * the row shape and its press behavior cannot drift per sheet.
 */
@Composable
fun Modifier.stratumSheetOptionRow(
    onClick: () -> Unit,
    backgroundColor: Color = StratumThemeAccessor.colors.surfaceRaised,
    borderColor: Color = StratumThemeAccessor.colors.borderSubtle,
    borderWidth: Dp = StratumThemeAccessor.shapes.borderWidth,
): Modifier {
    val shapes = StratumThemeAccessor.shapes
    return this
        .background(color = backgroundColor, shape = shapes.panel)
        .border(width = borderWidth, color = borderColor, shape = shapes.panel)
        .clip(shapes.panel)
        .clickable(onClick = onClick)
}

private const val KEYBOARD_SHEET_SCRIM_ALPHA = 0.18f

@Composable
private fun StratumBottomSheetHandle(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = StratumSpacing.s12, bottom = StratumSpacing.s4),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    // §12.3: drag handle is 32x4dp in text-faint (the palette
                    // grey named for incidental UI; borderSubtle was nearly
                    // invisible on the sheet surface — STR-175).
                    .size(width = 32.dp, height = 4.dp)
                    .background(
                        color = StratumThemeAccessor.colors.textFaint,
                        shape = RoundedCornerShape(2.dp),
                    ),
        )
    }
}
