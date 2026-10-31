package com.stratum.core.infrastructure.inference

import com.stratum.domain.lift.ScannedTierRow
import com.stratum.domain.lift.ScannedTiers
import com.stratum.domain.lift.Weight

/**
 * Wire parser for the `inferEquipmentIncrements` callable response (`unit` + `tiers` +
 * optional `microStops`).
 *
 * Parsing is tolerant: an unknown unit falls back to Lb, malformed tier rows and micro
 * stops are dropped, but a structurally invalid payload throws (the adapter converts it
 * to a failed [Result], never throwing past it).
 */
internal object IncrementScanResponseParser {
    private const val KEY_UNIT = "unit"
    private const val KEY_TIERS = "tiers"
    private const val KEY_MICRO_STOPS = "microStops"
    private const val KEY_LOWER = "lower"
    private const val KEY_UPPER = "upper"
    private const val KEY_STEP = "step"

    fun parseResponse(data: Any?): ScannedTiers {
        val map = data as? Map<*, *> ?: error("Invalid inferEquipmentIncrements response.")
        val tiersRaw =
            map[KEY_TIERS] as? List<*>
                ?: error("Missing tiers in inferEquipmentIncrements response.")
        return ScannedTiers(
            unit = parseUnit(map[KEY_UNIT]),
            tiers = tiersRaw.mapNotNull { entry -> parseTierRow(entry as? Map<*, *>) },
            microStops = parseMicroStops(map[KEY_MICRO_STOPS]),
        )
    }

    private fun parseUnit(raw: Any?): Weight.WeightUnit =
        (raw as? String)?.let(Weight.WeightUnit::parseOrNull) ?: Weight.WeightUnit.Lb

    private fun parseTierRow(entry: Map<*, *>?): ScannedTierRow? {
        if (entry == null) return null
        val lower = parseLower(entry[KEY_LOWER])
        val step = parsePositive(entry[KEY_STEP])
        // A missing upper key fails the row; an explicit null upper means open-ended.
        if (lower == null || step == null || !entry.containsKey(KEY_UPPER)) return null
        val upperRaw = entry[KEY_UPPER]
        val upper: Double? =
            if (upperRaw == null) {
                null
            } else {
                parseFiniteDouble(upperRaw, min = lower, inclusive = false) ?: return null
            }
        return ScannedTierRow(lower = lower, upper = upper, step = step)
    }

    private fun parseLower(raw: Any?): Double? = parseFiniteDouble(raw, min = 0.0, inclusive = true)

    private fun parsePositive(raw: Any?): Double? = parseFiniteDouble(raw, min = 0.0, inclusive = false)

    private fun parseMicroStops(raw: Any?): List<Double> {
        val stops = raw as? List<*> ?: return emptyList()
        return stops.mapNotNull { entry -> parseFiniteDouble(entry, min = 0.0, inclusive = false) }
    }

    private fun parseFiniteDouble(raw: Any?, min: Double, inclusive: Boolean): Double? {
        val value = parseNumber(raw)
        if (value == null || !value.isFinite()) return null
        val inRange = if (inclusive) value >= min else value > min
        return if (inRange) value else null
    }

    private fun parseNumber(raw: Any?): Double? =
        when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.toDoubleOrNull()
            else -> null
        }
}
