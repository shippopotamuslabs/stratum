package com.stratum.core.ui.component

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class TopOverflowClipShapeTest {

    private val density = Density(2f)
    private val size = Size(100f, 50f)

    private fun boundsFor(topOverflow: Dp): Rect {
        val outline =
            rowClipWithTopOverflow(topOverflow).createOutline(
                size,
                LayoutDirection.Ltr,
                density,
            ) as Outline.Rectangle
        return outline.bounds
    }

    @Test
    @DisplayName("zero overflow clips to the exact row bounds")
    fun zeroOverflowMatchesRowBounds() {
        assertEquals(Rect(0f, 0f, 100f, 50f), boundsFor(0.dp))
    }

    @Test
    @DisplayName("positive overflow extends only the top edge")
    fun positiveOverflowExtendsTopOnly() {
        assertEquals(Rect(0f, -24f, 100f, 50f), boundsFor(12.dp))
    }
}
