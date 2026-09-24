package com.stratum.core.data.sync.codecs

/**
 * Wire conversion for adder-stop columns (`micro_options_json`).
 *
 * Firestore carries the stops as a number list under `micro_options` (null when
 * absent). The entity form is a JSON array string (see `LiftMapper`); these helpers
 * translate between the two without extra dependencies. Malformed input decodes to
 * null so a bad document never wedges the row — reads fail open (inherit / no adder).
 */

/** Entity JSON column → wire list. Null stays null so inherit survives the round-trip. */
internal fun microWireList(raw: String?): List<Double>? {
    if (raw == null) return null
    val trimmed = raw.trim()
    if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return null
    val inner = trimmed.substring(1, trimmed.length - 1).trim()
    if (inner.isEmpty()) return emptyList()
    return inner.split(",").map { it.trim().toDoubleOrNull() ?: return null }
}

/**
 * Wire value → entity JSON column. Missing/null and wrong-type values decode to null
 * (inherit / no adder) so a bad document fails open instead of wedging the row.
 * An empty list decodes to [emptyJson] (`"[]"` preserves an explicit lift no-adder
 * override; null normalizes empty equipment stops to no-adder).
 */
internal fun microJsonFromWire(raw: Any?, emptyJson: String?): String? {
    if (raw == null) return null
    if (raw !is List<*>) return null
    val amounts = raw.map { (it as? Number)?.toDouble() ?: return null }
    if (amounts.isEmpty()) return emptyJson
    return "[${amounts.joinToString(",")}]"
}
