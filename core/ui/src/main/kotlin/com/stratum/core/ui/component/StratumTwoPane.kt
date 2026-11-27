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
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Single pane, or primary | hinge | detail. The hinge slot is not clickable. With no hinge
 * band the window splits in half.
 *
 * The primary call site never moves: every posture composes [primary] inside the same
 * Row > Box slot, so state remembered inside the primary content (list scroll, drafts)
 * survives fold and unfold (STR-336). Only the primary width and the trailing detail
 * slot change with posture.
 */
@Composable
fun StratumTwoPane(
    posture: PanePosture,
    modifier: Modifier = Modifier,
    detail: @Composable () -> Unit,
    primary: @Composable () -> Unit,
) {
    val split = posture as? PanePosture.TwoPane
    val hinge = split?.hinge
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val primaryWidth =
            when {
                split == null -> maxWidth
                hinge != null -> maxWidth * hinge.start
                else -> maxWidth / 2
            }
        val gapWidth = if (hinge != null) maxWidth * (hinge.end - hinge.start) else maxWidth * 0
        val detailWidth = maxWidth - primaryWidth - gapWidth
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.width(primaryWidth).fillMaxHeight()) { primary() }
            if (split != null) {
                if (hinge != null) {
                    Spacer(
                        modifier =
                            Modifier
                                .width(gapWidth)
                                .fillMaxHeight()
                                .background(StratumThemeAccessor.colors.surfaceSunken),
                    )
                }
                Box(modifier = Modifier.width(detailWidth).fillMaxHeight()) { detail() }
            }
        }
    }
}
