package com.stratum.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Color ramp for RIR target pickers: red (hard) through green (easy).
 * Lives in `:core:ui` so feature modules avoid hard-coded hex (UI_DESIGN.md §3).
 */
object StratumRirTargetColors {
    fun backgroundFor(rirGoal: Int): Color =
        when (rirGoal.coerceAtLeast(0)) {
            0 -> Red
            1 -> RedOrange
            2 -> Orange
            3 -> Yellow
            4 -> YellowGreen
            5 -> Green
            else -> GreenDeep
        }

    private val DarkOnLight = Color(0xFF1A1A1A)
    val contentColor: Color = DarkOnLight

    private val Red = Color(0xFFE53935)
    private val RedOrange = Color(0xFFEF6C00)
    private val Orange = Color(0xFFFB8C00)
    private val Yellow = Color(0xFFFDD835)
    private val YellowGreen = Color(0xFFC0CA33)
    private val Green = Color(0xFF7CB342)
    private val GreenDeep = Color(0xFF43A047)
}
