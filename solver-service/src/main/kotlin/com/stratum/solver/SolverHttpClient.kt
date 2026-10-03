package com.stratum.solver

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * Versioned Python-solver HTTP boundary (STR-52).
 *
 * The worker is a version-normalizing proxy: it stamps `modelVersion: v1` onto
 * the stored request (missing means v1) and POSTs it to the versioned
 * endpoint. The authoritative version signal is the payload's `modelVersion`,
 * not the URL (`solver-contract/VERSIONING.md` §3).
 */
fun interface SolverHttpClient {
    /** Raw solver response: status plus body bytes as text. Never blank on success. */
    data class SolveResponse(val statusCode: Int, val body: String)

    /**
     * POSTs the stamped request JSON to `/solve/v1`.
     *
     * @throws IOException on transport failure or timeout.
     */
    @Throws(IOException::class)
    fun postSolveV1(requestJson: String): SolveResponse
}

/**
 * JDK-only [SolverHttpClient] (no new dependencies). Connect and request
 * timeouts both equal the worker deadline so a hung solver fails the job
 * instead of pinning a Cloud Run request thread past its budget.
 */
class JdkSolverHttpClient(
    baseUrl: String,
    timeoutMillis: Long,
) : SolverHttpClient {
    private val endpoint: URI = URI.create("${baseUrl.trimEnd('/')}/solve/v1")
    private val timeout: Duration = Duration.ofMillis(timeoutMillis)
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(timeout)
        .build()

    override fun postSolveV1(requestJson: String): SolverHttpClient.SolveResponse {
        val request = HttpRequest.newBuilder(endpoint)
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
            .build()
        try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            return SolverHttpClient.SolveResponse(response.statusCode(), response.body())
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Solver request interrupted: ${e.message}", e)
        }
    }
}
