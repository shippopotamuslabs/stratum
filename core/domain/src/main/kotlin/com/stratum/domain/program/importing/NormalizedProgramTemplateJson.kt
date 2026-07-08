package com.stratum.domain.program.importing

/**
 * Parses the normalized program-template JSON produced by the server (deterministic parser or LLM
 * conversion) into a [NormalizedProgramTemplate].
 *
 * Hand-rolled rather than kotlinx.serialization so the pure-JVM `:core:domain` module stays free of
 * serialization framework dependencies (ARCHITECTURE.md §2 — stdlib, coroutines, javax.inject only).
 * The shape is narrow and controlled by this codebase's server side, so a focused parser is robust.
 *
 * Expected shape:
 * ```
 * { "programName": "..." | null, "cycleLength": N | null,
 *   "workouts": [{ "name": "...", "weekday": "..." | null, "position": N,
 *     "lifts": [{ "name": "...", "position": N, "supersetGroupKey": "..." | null,
 *       "sets": [{ "position": N, "setTypeLabel": "..." | null,
 *                  "repMin": N | null, "repMax": N | null, "rirGoal": N | null,
 *                  "restSeconds": N | null, "isTopSet": bool }] }] }] }
 * ```
 */
internal object NormalizedProgramTemplateJson {
    fun parse(json: String): NormalizedProgramTemplate {
        val reader = JsonReader(json)
        val root = reader.readObject()
        return parseTemplate(root)
    }

    private fun parseTemplate(obj: JsonObject): NormalizedProgramTemplate {
        val workouts = (obj["workouts"] as? JsonArray)
            ?.values
            ?.mapNotNull { it as? JsonObject }
            ?.mapIndexed { index, workout -> parseWorkout(workout, index) }
            ?: emptyList()
        return NormalizedProgramTemplate(
            programName = obj["programName"]?.asStringOrNull(),
            cycleLength = obj["cycleLength"]?.asIntOrNull(),
            workouts = workouts,
        )
    }

    private fun parseWorkout(obj: JsonObject, fallbackIndex: Int): NormalizedProgramTemplateWorkout =
        NormalizedProgramTemplateWorkout(
            name = obj["name"]?.asStringOrNull().orEmpty(),
            weekday = obj["weekday"]?.asStringOrNull(),
            position = obj["position"]?.asIntOrNull() ?: fallbackIndex,
            lifts = (obj["lifts"] as? JsonArray)
                ?.values
                ?.mapNotNull { it as? JsonObject }
                ?.mapIndexed { index, lift -> parseLift(lift, index) }
                ?: emptyList(),
            restDay = obj["restDay"]?.asBooleanOrNull() ?: false,
        )

    private fun parseLift(obj: JsonObject, fallbackIndex: Int): NormalizedProgramTemplateLift =
        NormalizedProgramTemplateLift(
            name = obj["name"]?.asStringOrNull().orEmpty(),
            position = obj["position"]?.asIntOrNull() ?: fallbackIndex,
            sets = (obj["sets"] as? JsonArray)
                ?.values
                ?.mapNotNull { it as? JsonObject }
                ?.mapIndexed { index, set -> parseSet(set, index) }
                ?: emptyList(),
            supersetGroupKey = obj["supersetGroupKey"]?.asStringOrNull(),
        )

    private fun parseSet(obj: JsonObject, fallbackIndex: Int): NormalizedProgramTemplateSet =
        NormalizedProgramTemplateSet(
            position = obj["position"]?.asIntOrNull() ?: fallbackIndex,
            setTypeLabel = obj["setTypeLabel"]?.asStringOrNull(),
            repMin = obj["repMin"]?.asIntOrNull(),
            repMax = obj["repMax"]?.asIntOrNull(),
            rirGoal = obj["rirGoal"]?.asIntOrNull(),
            restSeconds = obj["restSeconds"]?.asIntOrNull(),
            isTopSet = obj["isTopSet"]?.asBooleanOrNull() ?: false,
        )
}

// --- Minimal JSON reader (objects, arrays, strings, numbers, booleans, null) ---

private sealed interface JsonValue {
    fun asStringOrNull(): String? = null
    fun asIntOrNull(): Int? = null
    fun asBooleanOrNull(): Boolean? = null
}

private class JsonObject(val entries: Map<String, JsonValue>) : JsonValue {
    operator fun get(key: String): JsonValue? = entries[key]
}

private class JsonArray(val values: List<JsonValue>) : JsonValue

private class JsonString(val value: String) : JsonValue {
    override fun asStringOrNull(): String? = value
}

private class JsonNumber(val raw: String) : JsonValue {
    override fun asIntOrNull(): Int? = raw.toIntOrNull()
}

private class JsonBoolean(val value: Boolean) : JsonValue {
    override fun asBooleanOrNull(): Boolean? = value
}

private object JsonNull : JsonValue

private class JsonReader(private val text: String) {
    private var pos = 0

    fun readObject(): JsonObject {
        skipWhitespace()
        expect('{')
        skipWhitespace()
        val entries = mutableMapOf<String, JsonValue>()
        if (peek() == '}') { pos++; return JsonObject(entries) }
        while (true) {
            skipWhitespace()
            val key = readString()
            skipWhitespace()
            expect(':')
            skipWhitespace()
            val value = readValue()
            entries[key] = value
            skipWhitespace()
            when (peek()) {
                ',' -> { pos++; continue }
                '}' -> { pos++; return JsonObject(entries) }
                else -> error("Expected ',' or '}' at $pos")
            }
        }
    }

    private fun readValue(): JsonValue {
        skipWhitespace()
        val c = peek()
        return when {
            c == '{' -> readObject()
            c == '[' -> readArray()
            c == '"' -> JsonString(readString())
            c == 't' || c == 'f' -> readBoolean()
            c == 'n' -> readNull()
            c == '-' || c.isDigit() -> readNumber()
            else -> error("Unexpected '$c' at $pos")
        }
    }

    private fun readArray(): JsonArray {
        expect('[')
        skipWhitespace()
        val values = mutableListOf<JsonValue>()
        if (peek() == ']') { pos++; return JsonArray(values) }
        while (true) {
            values.add(readValue())
            skipWhitespace()
            when (peek()) {
                ',' -> { pos++; skipWhitespace(); continue }
                ']' -> { pos++; return JsonArray(values) }
                else -> error("Expected ',' or ']' at $pos")
            }
        }
    }

    private fun readString(): String {
        expect('"')
        val sb = StringBuilder()
        while (true) {
            val c = text[pos++]
            if (c == '"') return sb.toString()
            if (c == '\\') {
                val escaped = text[pos++]
                sb.append(
                    when (escaped) {
                        '"' -> '"'
                        '\\' -> '\\'
                        '/' -> '/'
                        'n' -> '\n'
                        't' -> '\t'
                        'r' -> '\r'
                        'b' -> '\b'
                        'u' -> {
                            val hex = text.substring(pos, pos + 4)
                            pos += 4
                            hex.toInt(16).toChar()
                        }
                        else -> escaped
                    },
                )
            } else {
                sb.append(c)
            }
        }
    }

    private fun readNumber(): JsonNumber {
        val start = pos
        if (peek() == '-') pos++
        val numericChars = setOf('.', 'e', 'E', '+', '-')
        while (pos < text.length && (text[pos].isDigit() || text[pos] in numericChars)) {
            pos++
        }
        return JsonNumber(text.substring(start, pos))
    }

    private fun readBoolean(): JsonBoolean {
        if (text.startsWith("true", pos)) { pos += 4; return JsonBoolean(true) }
        require(text.startsWith("false", pos)) { "Invalid literal at $pos" }
        pos += 5
        return JsonBoolean(false)
    }

    private fun readNull(): JsonValue {
        require(text.startsWith("null", pos)) { "Invalid literal at $pos" }
        pos += 4
        return JsonNull
    }

    private fun peek(): Char {
        require(pos < text.length) { "Unexpected EOF" }
        return text[pos]
    }

    private fun expect(c: Char) {
        require(peek() == c) { "Expected '$c' at $pos" }
        pos++
    }

    private fun skipWhitespace() {
        while (pos < text.length && text[pos].isWhitespace()) pos++
    }
}
