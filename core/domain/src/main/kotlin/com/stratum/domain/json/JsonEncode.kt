package com.stratum.domain.json

/** Escapes a string for inclusion inside JSON double quotes. */
fun escapeJsonString(value: String): String {
    val sb = StringBuilder(value.length + 8)
    for (c in value) {
        when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\b' -> sb.append("\\b")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> {
                if (c.code < 0x20) {
                    sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                } else {
                    sb.append(c)
                }
            }
        }
    }
    return sb.toString()
}

fun jsonString(value: String): String = "\"${escapeJsonString(value)}\""

fun jsonStringOrNull(value: String?): String =
    if (value == null) "null" else jsonString(value)

fun jsonNumber(value: Number): String =
    when (value) {
        is Double -> if (value.isFinite()) value.toString() else "0.0"
        is Float -> if (value.isFinite()) value.toString() else "0.0"
        else -> value.toString()
    }

fun jsonNumberOrNull(value: Number?): String =
    if (value == null) "null" else jsonNumber(value)

fun jsonBoolean(value: Boolean): String = if (value) "true" else "false"

fun jsonBooleanOrNull(value: Boolean?): String =
    if (value == null) "null" else jsonBoolean(value)

/**
 * Central JSON encoding for a typed extra value (STR-28): callers pass raw
 * values and this picks the encoding, so a missed `jsonString()` can never
 * emit invalid JSON and a number is never accidentally quoted. Unknown types
 * fall back to their `toString()` as a JSON string — never raw.
 */
fun jsonValue(value: Any?): String =
    when (value) {
        null -> "null"
        is String -> jsonString(value)
        is Boolean -> jsonBoolean(value)
        is Number -> jsonNumber(value)
        else -> jsonString(value.toString())
    }

fun jsonArray(items: Iterable<String>): String =
    items.joinToString(prefix = "[", postfix = "]", separator = ",")

fun jsonObject(fields: List<Pair<String, String>>): String {
    if (fields.isEmpty()) return "{}"
    val sb = StringBuilder(fields.size * 16)
    sb.append('{')
    var first = true
    for ((key, value) in fields) {
        if (!first) sb.append(',')
        first = false
        sb.append(jsonString(key)).append(':').append(value)
    }
    sb.append('}')
    return sb.toString()
}

fun jsonObject(vararg fields: Pair<String, String>): String =
    jsonObject(fields.asList())
