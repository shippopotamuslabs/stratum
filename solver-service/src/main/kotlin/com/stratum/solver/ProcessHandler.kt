package com.stratum.solver

import com.stratum.domain.json.jsonString
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * `POST /process` — body `{ "uid", "jobId" }`.
 *
 * Runs the full claim+solve synchronously so Cloud Run request-based CPU stays allocated.
 * Returns 2xx after the worker finishes so the Node dispatcher can treat non-2xx as dispatch
 * failure only when the job is still active (never clobber `ready`).
 */
class ProcessHandler(
    private val worker: SynthesisJobWorker = SynthesisJobWorker(),
) : HttpHandler {
    override fun handle(exchange: HttpExchange) {
        try {
            if (exchange.requestMethod.equals("OPTIONS", ignoreCase = true)) {
                respond(exchange, HTTP_NO_CONTENT, "")
                return
            }
            if (!exchange.requestMethod.equals("POST", ignoreCase = true)) {
                respond(exchange, HTTP_METHOD_NOT_ALLOWED, """{"error":"Method not allowed"}""")
                return
            }
            val body = exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8)
            val request = try {
                ProcessRequestParser.parse(body)
            } catch (e: IllegalArgumentException) {
                log("bad request: ${e.message}")
                respond(exchange, HTTP_BAD_REQUEST, jsonError("Invalid body"))
                return
            }

            val outcome = worker.process(request)
            val (code, payload) = when (outcome) {
                is SynthesisJobWorker.Outcome.JobMissing ->
                    HTTP_OK to """{"status":"missing"}"""
                is SynthesisJobWorker.Outcome.Skipped ->
                    HTTP_OK to """{"status":"skipped","reason":${jsonString(outcome.reason)}}"""
                is SynthesisJobWorker.Outcome.Ready ->
                    HTTP_OK to """{"status":"ready","resultStoragePath":${jsonString(outcome.resultStoragePath)}}"""
                is SynthesisJobWorker.Outcome.Failed ->
                    // Still 200: failure is recorded on the job doc; Node must not clobber terminal state.
                    HTTP_OK to """{"status":"failed"}"""
            }
            respond(exchange, code, payload)
        } catch (t: Throwable) {
            log("unhandled: ${t::class.java.simpleName}: ${t.message}")
            try {
                respond(exchange, HTTP_INTERNAL_ERROR, jsonError("Internal solver error"))
            } catch (_: IOException) {
                // Client gone.
            }
        }
    }

    private fun respond(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) {
            exchange.responseBody.use { it.write(bytes) }
        } else {
            exchange.responseBody.close()
        }
    }

    private fun jsonError(message: String): String =
        """{"error":${jsonString(message)}}"""

    private fun log(message: String) {
        println("ProcessHandler: $message")
    }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_NO_CONTENT = 204
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_METHOD_NOT_ALLOWED = 405
        const val HTTP_INTERNAL_ERROR = 500
    }
}
