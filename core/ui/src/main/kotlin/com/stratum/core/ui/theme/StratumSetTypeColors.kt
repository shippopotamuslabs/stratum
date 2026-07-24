package com.stratum.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Solid circular badge colors for set-type indicators (S / M / MM / D).
 * Lives in `:core:ui` so feature modules avoid hard-coded hex (UI_DESIGN.md §3).
 * Content is dark-on-light like [StratumRirTargetColors].
 */
object StratumSetTypeColors {
    private val DarkOnLight = Color(0xFF1A1A1A)
    private val BlueGray = Color(0xFF90A4AE)
    private val Teal = Color(0xFF4DB6AC)
    private val Indigo = Color(0xFF7986CB)
    private val DeepOrange = Color(0xFFFF8A65)

    val standard: Color = BlueGray
    val myoRep: Color = Teal
    val myoRepMatch: Color = Indigo
    val dropSet: Color = DeepOrange

    val contentColor: Color = DarkOnLight
}
