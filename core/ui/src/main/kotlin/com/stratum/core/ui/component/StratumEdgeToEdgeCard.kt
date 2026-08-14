package com.stratum.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Full-bleed raised panel: square corners, spans the horizontal viewport.
 *
 * Use for lift / set-log cards on live workout, edit workout, and create-program so panels
 * sit flush with the screen edges (contrast with inset [StratumStatCard]-style bordered cards).
 *
 * Tonal keeps a flat raised slab. Neo ([com.stratum.core.ui.theme.StratumElevations.useHardShadow])
 * paints strong top/bottom iron bands — banded instrument panels, not floating drop-shadow cards.
 *
 * Callers typically keep list [contentPadding] horizontal at 0.dp for these items and apply
 * their own horizontal padding inside for titles / actions.
 *
 * Set [drawEdgeBands] to false when this slab should merge with adjacent raised chrome
 * (no Neo top/bottom iron bands).
 */
@Composable
fun StratumEdgeToEdgeCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = 16.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    drawEdgeBands: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val elevations = StratumThemeAccessor.elevations
    val paintBands = drawEdgeBands && elevations.useHardShadow
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    color = colors.surfaceRaised,
                    shape = RectangleShape,
                )
                .then(
                    if (paintBands) {
                        Modifier.drawWithContent {
                            drawContent()
                            val stroke = shapes.borderWidth.toPx()
                            val half = stroke / 2f
                            val bandColor = colors.borderStrong
                            drawLine(
                                color = bandColor,
                                start = Offset(0f, half),
                                end = Offset(size.width, half),
                                strokeWidth = stroke,
                            )
                            drawLine(
                                color = bandColor,
                                start = Offset(0f, size.height - half),
                                end = Offset(size.width, size.height - half),
                                strokeWidth = stroke,
                            )
                        }
                    } else {
                        Modifier
                    },
                )
                .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}
