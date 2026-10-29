package com.stratum.domain.json


/**
 * Minimal JSON AST for hand-rolled encode/decode in `:core:domain`.
 * Shared by import templates and program-synthesis wire formats so the module stays free of
 * kotlinx.serialization (ARCHITECTURE.md — stdlib, coroutines, javax.inject only).
 */
sealed interface JsonValue {
    fun asStringOrNull(): String? = null
    fun asIntOrNull(): Int? = null
    fun asLongOrNull(): Long? = null
    fun asDoubleOrNull(): Double? = null
    fun asBooleanOrNull(): Boolean? = null
    fun asObjectOrNull(): JsonObject? = null
    fun asArrayOrNull(): JsonArray? = null
    fun isNull(): Boolean = false
}

class JsonObject(val entries: Map<String, JsonValue>) : JsonValue {
    operator fun get(key: String): JsonValue? = entries[key]
    override fun asObjectOrNull(): JsonObject = this

    fun requireObject(key: String): JsonObject =
        get(key)?.asObjectOrNull()
            ?: error("Expected object field '$key'")

    fun requireArray(key: String): JsonArray =
        get(key)?.asArrayOrNull()
            ?: error("Expected array field '$key'")

    fun requireString(key: String): String =
        get(key)?.asStringOrNull()
            ?: error("Expected string field '$key'")

    fun optionalString(key: String): String? {
        val v = get(key) ?: return null
        if (v.isNull()) return null
        return v.asStringOrNull() ?: error("Expected string or null for '$key'")
    }

    fun requireInt(key: String): Int =
        get(key)?.asIntOrNull()
            ?: error("Expected int field '$key'")

    fun optionalInt(key: String): Int? {
        val v = get(key) ?: return null
        if (v.isNull()) return null
        return v.asIntOrNull() ?: error("Expected int or null for '$key'")
    }

    fun requireLong(key: String): Long =
        get(key)?.asLongOrNull()
            ?: error("Expected long field '$key'")

    fun requireDouble(key: String): Double =
        get(key)?.asDoubleOrNull()
            ?: error("Expected number field '$key'")

    fun optionalDouble(key: String): Double? {
        val v = get(key) ?: return null
        if (v.isNull()) return null
        return v.asDoubleOrNull() ?: error("Expected number or null for '$key'")
    }

    fun requireBoolean(key: String): Boolean =
        get(key)?.asBooleanOrNull()
            ?: error("Expected boolean field '$key'")

    fun optionalBoolean(key: String): Boolean? {
        val v = get(key) ?: return null
        if (v.isNull()) return null
        return v.asBooleanOrNull() ?: error("Expected boolean or null for '$key'")
    }
}

class JsonArray(val values: List<JsonValue>) : JsonValue {
    override fun asArrayOrNull(): JsonArray = this
}

class JsonString(val value: String) : JsonValue {
    override fun asStringOrNull(): String = value
}

class JsonNumber(val raw: String) : JsonValue {
    override fun asIntOrNull(): Int? = raw.toIntOrNull() ?: raw.toDoubleOrNull()?.toInt()
    override fun asLongOrNull(): Long? = raw.toLongOrNull() ?: raw.toDoubleOrNull()?.toLong()
    override fun asDoubleOrNull(): Double? = raw.toDoubleOrNull()
}

class JsonBoolean(val value: Boolean) : JsonValue {
    override fun asBooleanOrNull(): Boolean = value
}

object JsonNull : JsonValue {
    override fun isNull(): Boolean = true
}
