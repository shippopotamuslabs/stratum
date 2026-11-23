package com.stratum.core.ui.component

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
import com.stratum.core.ui.posture.FoldPosture
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Single pane, or primary | hinge | detail. With no hinge band the window
 * splits in half. The hinge slot is not clickable.
 */
@Composable
fun StratumTwoPane(
    posture: FoldPosture,
    modifier: Modifier = Modifier,
    detail: @Composable () -> Unit,
    primary: @Composable () -> Unit,
) {
    val split = posture as? FoldPosture.TwoPane
    if (split == null) {
        Box(modifier = modifier.fillMaxSize()) { primary() }
        return
    }
    val hinge = split.hinge
    if (hinge == null) {
        Row(modifier = modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) { primary() }
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) { detail() }
        }
        return
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val left = maxWidth * hinge.start
        val gap = maxWidth * (hinge.end - hinge.start)
        val right = maxWidth - left - gap
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.width(left).fillMaxHeight()) { primary() }
            Spacer(
                modifier =
                    Modifier
                        .width(gap)
                        .fillMaxHeight()
                        .background(StratumThemeAccessor.colors.surfaceSunken),
            )
            Box(modifier = Modifier.width(right).fillMaxHeight()) { detail() }
        }
    }
}
