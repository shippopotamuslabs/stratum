package com.stratum.core.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shippopotamus Compose Gospel spacing tokens (contracts/UI_DESIGN.md §12.2).
 *
 * 4dp base unit, 8dp major grid. These are the ONLY spacing values for new UI work —
 * never 5, 7, 10, 15. Added by STR-137 alongside the gospel contract embed; existing
 * screens adopt incrementally, nothing here changes current layouts by itself.
 */
object StratumSpacing {
    val s4: Dp = 4.dp
    val s8: Dp = 8.dp
    val s12: Dp = 12.dp
    val s16: Dp = 16.dp
    val s24: Dp = 24.dp
    val s32: Dp = 32.dp
    val s48: Dp = 48.dp
    val s64: Dp = 64.dp
}
