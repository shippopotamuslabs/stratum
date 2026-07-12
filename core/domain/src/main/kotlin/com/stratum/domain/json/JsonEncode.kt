package com.stratum.domain.json

/** Escapes a string for inclusion inside JSON double quotes. */
internal fun escapeJsonString(value: String): String {
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

internal fun jsonString(value: String): String = "\"${escapeJsonString(value)}\""

internal fun jsonStringOrNull(value: String?): String =
    if (value == null) "null" else jsonString(value)

internal fun jsonNumber(value: Number): String =
    when (value) {
        is Double -> if (value.isFinite()) value.toString() else "0.0"
        is Float -> if (value.isFinite()) value.toString() else "0.0"
        else -> value.toString()
    }

internal fun jsonNumberOrNull(value: Number?): String =
    if (value == null) "null" else jsonNumber(value)

internal fun jsonBoolean(value: Boolean): String = if (value) "true" else "false"

internal fun jsonBooleanOrNull(value: Boolean?): String =
    if (value == null) "null" else jsonBoolean(value)

internal fun jsonArray(items: Iterable<String>): String =
    items.joinToString(prefix = "[", postfix = "]", separator = ",")

internal fun jsonObject(fields: List<Pair<String, String>>): String {
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

internal fun jsonObject(vararg fields: Pair<String, String>): String =
    jsonObject(fields.asList())
