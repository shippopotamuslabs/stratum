package com.stratum.solver

/**
 * Body of `POST /process` from the Node dispatcher:
 * `{ "uid": "...", "jobId": "..." }`.
 */
data class ProcessRequest(
    val uid: String,
    val jobId: String,
)

/**
 * Minimal JSON object parser for the two-string process body.
 * Avoids pulling kotlinx.serialization into the fat jar for a single DTO.
 */
object ProcessRequestParser {
    fun parse(body: String): ProcessRequest {
        val trimmed = body.trim()
        require(trimmed.startsWith("{") && trimmed.endsWith("}")) {
            "Body must be a JSON object"
        }
        val uid = readStringField(trimmed, "uid")
        val jobId = readStringField(trimmed, "jobId")
        require(uid.isNotEmpty()) { "uid must be non-empty" }
        require(jobId.isNotEmpty()) { "jobId must be non-empty" }
        require(!uid.contains('/')) { "uid must not contain '/'" }
        require(!jobId.contains('/')) { "jobId must not contain '/'" }
        return ProcessRequest(uid = uid, jobId = jobId)
    }

    private fun readStringField(json: String, name: String): String {
        val key = "\"$name\""
        val keyIdx = json.indexOf(key)
        require(keyIdx >= 0) { "Missing field: $name" }
        var i = keyIdx + key.length
        while (i < json.length && json[i].isWhitespace()) i++
        require(i < json.length && json[i] == ':') { "Expected ':' after $name" }
        i++
        while (i < json.length && json[i].isWhitespace()) i++
        require(i < json.length && json[i] == '"') { "Expected string value for $name" }
        i++
        val sb = StringBuilder()
        while (i < json.length) {
            val c = json[i]
            when (c) {
                '"' -> return sb.toString()
                '\\' -> {
                    require(i + 1 < json.length) { "Invalid escape in $name" }
                    val next = json[i + 1]
                    sb.append(
                        when (next) {
                            '"', '\\', '/' -> next
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            else -> next
                        },
                    )
                    i += 2
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        error("Unterminated string for $name")
    }
}
