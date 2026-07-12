package com.stratum.solver

import com.stratum.domain.json.JsonReader

/**
 * Body of `POST /process` from the Node dispatcher:
 * `{ "uid": "...", "jobId": "..." }`.
 */
data class ProcessRequest(
    val uid: String,
    val jobId: String,
)

/**
 * Parses the process body via shared domain [JsonReader] (no second mini-parser).
 */
object ProcessRequestParser {
    fun parse(body: String): ProcessRequest =
        try {
            val root = JsonReader(body).readRootObject()
            val uid = root.requireString("uid").trim()
            val jobId = root.requireString("jobId").trim()
            require(uid.isNotEmpty()) { "uid must be non-empty" }
            require(jobId.isNotEmpty()) { "jobId must be non-empty" }
            require(!uid.contains('/')) { "uid must not contain '/'" }
            require(!jobId.contains('/')) { "jobId must not contain '/'" }
            ProcessRequest(uid = uid, jobId = jobId)
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid process body: ${e.message}", e)
        }
}
