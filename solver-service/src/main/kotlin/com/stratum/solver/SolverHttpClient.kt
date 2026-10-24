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
    private companion object {
        const val HTTP_OK = 200
        const val METADATA_TIMEOUT_SECONDS = 5L
    }

    private val endpoint: URI = URI.create("${baseUrl.trimEnd('/')}/solve/v1")
    private val audience: String = baseUrl.trimEnd('/')
    private val timeout: Duration = Duration.ofMillis(timeoutMillis)
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(timeout)
        .build()

    // Seam for tests: production fetches the ID token from the GCE metadata
    // server (fetchIdToken); tests substitute a fixed token so the HTTP
    // behavior tests run on any machine, not only on GCP.
    internal var idTokenProvider: () -> String = { fetchIdToken() }

    override fun postSolveV1(requestJson: String): SolverHttpClient.SolveResponse {
        val idToken = idTokenProvider()
        val request = HttpRequest.newBuilder(endpoint)
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $idToken")
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

    /**
     * Fetches an OIDC ID token for the solver audience from the GCE metadata
     * server. Works on Cloud Run (and GCE/GKE); throws IOException otherwise.
     */
    private fun fetchIdToken(): String {
        val metadataUrl = URI.create(
            "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/identity?audience=$audience"
        )
        val request = HttpRequest.newBuilder(metadataUrl)
            .timeout(Duration.ofSeconds(METADATA_TIMEOUT_SECONDS))
            .header("Metadata-Flavor", "Google")
            .GET()
            .build()
        try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            if (response.statusCode() != HTTP_OK) {
                throw IOException("Metadata server returned HTTP ${response.statusCode()}")
            }
            return response.body().trim()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("ID token fetch interrupted: ${e.message}", e)
        }
    }
}
