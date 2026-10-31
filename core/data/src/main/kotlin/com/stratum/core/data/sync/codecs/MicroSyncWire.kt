package com.stratum.core.data.sync.codecs

import com.stratum.core.infrastructure.mapper.parseMicroJson
import com.stratum.core.infrastructure.mapper.toMicroJsonArray

/**
 * Wire conversion for adder-stop columns (`micro_options_json`).
 *
 * Firestore carries the stops as a number list under `micro_options` (null when
 * absent). The entity form is a JSON array string whose grammar is owned by the
 * mapper ([parseMicroJson] / [toMicroJsonArray]); these helpers only translate
 * between the two shapes plus the layer-specific empty contract. Malformed input
 * decodes to null so a bad document never wedges the row — reads fail open
 * (inherit / no adder).
 */

/** Entity JSON column → wire list. Null stays null so inherit survives the round-trip. */
internal fun microWireList(raw: String?): List<Double>? = raw?.parseMicroJson()

/** Wire value → storage-lb doubles. Missing/null and wrong-type values decode to null. */
private fun amountsFromWire(raw: Any?): List<Double>? {
    if (raw == null) return null
    if (raw !is List<*>) return null
    return raw.map { (it as? Number)?.toDouble() ?: return null }
}

/**
 * Equipment stops: an empty list decodes to null (no adder), matching the column
 * contract where null means no adder.
 */
internal fun equipmentMicroJsonFromWire(raw: Any?): String? =
    amountsFromWire(raw)?.takeIf { it.isNotEmpty() }?.toMicroJsonArray()

/**
 * Lift micro override: an empty list decodes to `"[]"` (explicit no-adder override);
 * missing/null stays null (inherit equipment stops).
 */
internal fun liftMicroJsonFromWire(raw: Any?): String? =
    when (val amounts = amountsFromWire(raw)) {
        null -> null
        else -> if (amounts.isEmpty()) "[]" else amounts.toMicroJsonArray()
    }
