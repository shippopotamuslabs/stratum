package com.stratum.domain.json

/**
 * Minimal streaming JSON reader (objects, arrays, strings, numbers, booleans, null).
 * Package-visible for hand-rolled domain codecs.
 */
internal class JsonReader(private val text: String) {
    private var pos = 0

    fun readRootObject(): JsonObject {
        skipWhitespace()
        val obj = readObject()
        skipWhitespace()
        if (pos < text.length) error("Trailing content at $pos")
        return obj
    }

    fun readValue(): JsonValue {
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

    private fun readObject(): JsonObject {
        expect('{')
        skipWhitespace()
        val entries = mutableMapOf<String, JsonValue>()
        if (peek() == '}') {
            pos++
            return JsonObject(entries)
        }
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
                ',' -> {
                    pos++
                    continue
                }
                '}' -> {
                    pos++
                    return JsonObject(entries)
                }
                else -> error("Expected ',' or '}' at $pos")
            }
        }
    }

    private fun readArray(): JsonArray {
        expect('[')
        skipWhitespace()
        val values = mutableListOf<JsonValue>()
        if (peek() == ']') {
            pos++
            return JsonArray(values)
        }
        while (true) {
            values.add(readValue())
            skipWhitespace()
            when (peek()) {
                ',' -> {
                    pos++
                    skipWhitespace()
                    continue
                }
                ']' -> {
                    pos++
                    return JsonArray(values)
                }
                else -> error("Expected ',' or ']' at $pos")
            }
        }
    }

    private fun readString(): String {
        expect('"')
        val sb = StringBuilder()
        while (true) {
            require(pos < text.length) { "Unterminated string at $pos" }
            val c = text[pos++]
            if (c == '"') return sb.toString()
            if (c == '\\') {
                require(pos < text.length) { "Unterminated escape at $pos" }
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
                            require(pos + 4 <= text.length) { "Bad unicode escape at $pos" }
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
        if (text.startsWith("true", pos)) {
            pos += 4
            return JsonBoolean(true)
        }
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
