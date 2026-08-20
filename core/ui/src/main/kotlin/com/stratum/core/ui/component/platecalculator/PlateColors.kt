package com.stratum.core.ui.component.platecalculator

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs

/**
 * Standard gym and Olympic color mappings and geometry for weight plate denominations.
 * Unit-aware for both Olympic KG (25kg Red, 20kg Blue, 15kg Yellow, 10kg Green, 5kg White)
 * and LB (55lb Red, 45lb Blue, 35lb Yellow, 25lb Green, 10lb White, 5lb Red, 2.5lb Dark Slate, 1.25lb Chrome).
 */
object PlateColors {
    val PlateRed = Color(0xFFC53030)
    val PlateBlue = Color(0xFF2B6CB0)
    val PlateYellow = Color(0xFFD69E2E)
    val PlateGreen = Color(0xFF2F855A)
    val PlateWhite = Color(0xFFE2E8F0)
    val PlateDarkSlate = Color(0xFF4A5568)
    val PlateChrome = Color(0xFF718096)

    fun colorFor(weight: Double, isKg: Boolean = false): Color {
        val w = abs(weight)
        return if (isKg) {
            when {
                w >= 24.0 -> PlateRed // 25 kg
                w >= 19.0 -> PlateBlue // 20 kg
                w >= 14.0 -> PlateYellow // 15 kg
                w >= 9.0 -> PlateGreen // 10 kg
                w >= 4.5 -> PlateWhite // 5 kg
                w >= 2.0 -> PlateRed // 2.5 kg
                w >= 1.0 -> PlateDarkSlate // 1.25 kg
                else -> PlateChrome // 0.5 kg
            }
        } else {
            when {
                w >= 54.0 -> PlateRed // 55 lb
                w >= 44.0 -> PlateBlue // 45 lb
                w >= 34.0 -> PlateYellow // 35 lb
                w >= 24.0 -> PlateGreen // 25 lb
                w >= 9.0 -> PlateWhite // 10 lb
                w >= 4.5 -> PlateRed // 5 lb
                w >= 2.0 -> PlateDarkSlate // 2.5 lb
                else -> PlateChrome // 1.25 lb
            }
        }
    }

    fun onColorFor(weight: Double, isKg: Boolean = false): Color {
        val w = abs(weight)
        val isWhitePlate = if (isKg) {
            w in 4.5..5.5 // 5 kg
        } else {
            w in 9.0..11.0 // 10 lb
        }
        return if (isWhitePlate) Color(0xFF1A202C) else Color(0xFFFFFFFF)
    }

    /**
     * Relative height fraction (0.35..1.0) for visual barbell diagram plate sizing.
     */
    fun heightFractionFor(weight: Double, isKg: Boolean = false): Float {
        val w = abs(weight)
        return if (isKg) {
            when {
                w >= 19.0 -> 1.0f // 25kg, 20kg
                w >= 14.0 -> 0.88f // 15kg
                w >= 9.0 -> 0.78f // 10kg
                w >= 4.5 -> 0.65f // 5kg
                w >= 2.0 -> 0.54f // 2.5kg
                w >= 1.0 -> 0.44f // 1.25kg
                else -> 0.36f // 0.5kg
            }
        } else {
            when {
                w >= 44.0 -> 1.0f // 55lb, 45lb
                w >= 34.0 -> 0.88f // 35lb
                w >= 24.0 -> 0.78f // 25lb
                w >= 9.0 -> 0.65f // 10lb
                w >= 4.5 -> 0.54f // 5lb
                w >= 2.0 -> 0.44f // 2.5lb
                else -> 0.36f // 1.25lb
            }
        }
    }

    /**
     * Plate thickness in Dp for the diagram.
     */
    fun widthDpFor(weight: Double, isMini: Boolean, isKg: Boolean = false): Dp {
        val w = abs(weight)
        val baseWidth = if (isKg) {
            when {
                w >= 19.0 -> 16.dp // 25kg, 20kg
                w >= 14.0 -> 14.dp // 15kg
                w >= 9.0 -> 12.dp // 10kg
                w >= 4.5 -> 10.dp // 5kg
                w >= 2.0 -> 8.dp // 2.5kg
                else -> 6.dp
            }
        } else {
            when {
                w >= 44.0 -> 16.dp // 55lb, 45lb
                w >= 34.0 -> 14.dp // 35lb
                w >= 24.0 -> 12.dp // 25lb
                w >= 9.0 -> 10.dp // 10lb
                w >= 4.5 -> 8.dp // 5lb
                else -> 6.dp
            }
        }
        return if (isMini) (baseWidth * 0.7f) else baseWidth
    }

    /**
     * Formats a plate weight denomination without rounding distortion:
     * e.g. 45.0 -> "45", 2.5 -> "2.5", 1.25 -> "1.25", 0.5 -> "0.5".
     */
    fun formatPlateWeight(weight: Double): String {
        val absWeight = abs(weight)
        return if (absWeight % 1.0 == 0.0) {
            absWeight.toInt().toString()
        } else {
            String.format(Locale.US, "%.2f", absWeight).trimEnd('0').trimEnd('.')
        }
    }
}
