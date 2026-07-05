package com.stratum.core.ui.theme

import kotlin.math.absoluteValue
import androidx.compose.ui.graphics.Color

/**
 * Distinct chip backgrounds for linked superset groups. Same [groupId] maps to the same color
 * on every lift in the group (UI_DESIGN.md §3 — colors live in theme, not features).
 */
object StratumSupersetGroupColors {
    fun backgroundFor(groupId: String): Color = Palette[groupId.hashCode().mod(Palette.size).absoluteValue]

    val contentColor: Color = Color(0xFF1A1A1A)

    private val Palette =
        listOf(
            Color(0xFF90CAF9),
            Color(0xFFCE93D8),
            Color(0xFFA5D6A7),
            Color(0xFFFFCC80),
            Color(0xFF80DEEA),
            Color(0xFFF48FB1),
            Color(0xFFB39DDB),
            Color(0xFFC5E1A5),
        )
}
