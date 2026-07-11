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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Full-bleed raised panel: square corners, no border, spans the horizontal viewport.
 *
 * Use for lift / set-log cards on live workout, edit workout, and create-program so panels
 * sit flush with the screen edges (contrast with inset [StratumStatCard]-style bordered cards).
 *
 * Callers typically keep list [contentPadding] horizontal at 0.dp for these items and apply
 * their own horizontal padding inside for titles / actions.
 */
@Composable
fun StratumEdgeToEdgeCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = 16.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    color = StratumThemeAccessor.colors.surfaceRaised,
                    shape = RectangleShape,
                )
                .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}
