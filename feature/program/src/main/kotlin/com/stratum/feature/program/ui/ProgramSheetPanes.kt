package com.stratum.feature.program.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.program.SheetPosture

/**
 * Single pane, or sheet | hinge | inspector. The hinge slot is not clickable.
 */
@Composable
internal fun ProgramSheetPanes(
    posture: SheetPosture,
    modifier: Modifier = Modifier,
    inspector: @Composable () -> Unit,
    sheet: @Composable () -> Unit,
) {
    val split = posture as? SheetPosture.TwoPane
    if (split == null) {
        Box(modifier = modifier.fillMaxSize()) { sheet() }
        return
    }
    val hinge = split.hinge
    if (hinge == null) {
        Row(modifier = modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) { sheet() }
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) { inspector() }
        }
        return
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val left = maxWidth * hinge.start
        val gap = maxWidth * (hinge.end - hinge.start)
        val right = maxWidth - left - gap
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.width(left).fillMaxHeight()) { sheet() }
            Spacer(
                modifier =
                    Modifier
                        .width(gap)
                        .fillMaxHeight()
                        .background(StratumThemeAccessor.colors.surfaceSunken),
            )
            Box(modifier = Modifier.width(right).fillMaxHeight()) { inspector() }
        }
    }
}
