package com.stratum.solver

import com.google.cloud.firestore.FieldValue
import com.stratum.domain.program.generation.synthesis.wire.ProgramSynthesisWire
import com.stratum.domain.program.generation.synthesis.wire.ProgramSynthesisWireException
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Claims a Firestore synthesis job, proxies the stored request to the Python
 * solver over HTTP, uploads the raw result JSON, updates status.
 *
 * Version-normalizing proxy (STR-52): the stored `request.json` is validated
 * with [ProgramSynthesisWire.decodeRequest] (unknown majors fail the job
 * before any HTTP call — never guessed), stamped with `modelVersion: v1`
 * when the field is absent (missing means v1), and POSTed byte-preserving to
 * `$STRATUM_SOLVER_URL/solve/v1`. The Python response body is validated with
 * `decodeResult`, then uploaded verbatim as `result.json` — never re-encoded
 * through domain models.
 *
 * Job path: `users/{uid}/synthesisJobs/{jobId}`
 * Result path: `synthesis/{uid}/{jobId}/result.json`
 *
 * The request path holds zero OR-Tools references: CP-SAT runs in the Python
 * service. (`:core:domain-solver` stays a dependency for Phase 3 / STR-46.)
 */
class SynthesisJobWorker(
    private val jobs: SynthesisJobStore = FirestoreSynthesisJobStore(),
    private val blobs: SynthesisBlobStore = StorageSynthesisBlobStore(),
    private val solverBaseUrl: String = resolveSolverBaseUrl(),
    private val deadlineMillis: Long = DEFAULT_SOLVE_DEADLINE_MILLIS,
    private val httpClient: SolverHttpClient = JdkSolverHttpClient(solverBaseUrl, deadlineMillis),
    private val logger: StructuredLogger = StructuredLogger(StructuredLog.SERVICE_NAME),
) {
    sealed class Outcome {
        data object JobMissing : Outcome()
        data class Skipped(val reason: String) : Outcome()
        data class Ready(val resultStoragePath: String) : Outcome()
        data class Failed(val errorMessage: String) : Outcome()
    }

    fun process(request: ProcessRequest): Outcome {
        val jobPath = "users/${request.uid}/synthesisJobs/${request.jobId}"
        logger.info("job started", uid = request.uid, jobId = request.jobId)

        return try {
            val claim = jobs.claim(request.uid, request.jobId)
            val requestStoragePath = when (claim) {
                is SynthesisJobStore.Claim.Missing -> {
                    logger.info(
                        "job missing; no-op",
                        uid = request.uid,
                        jobId = request.jobId,
                        extra = listOf("jobPath" to jobPath),
                    )
                    return Outcome.JobMissing
                }
                is SynthesisJobStore.Claim.NotClaimable -> {
                    logger.info(
                        "skip claim",
                        uid = request.uid,
                        jobId = request.jobId,
                        extra = listOf("status" to claim.status.orEmpty()),
                    )
                    return Outcome.Skipped("status=${claim.status}")
                }
                is SynthesisJobStore.Claim.Claimed -> {
                    logger.info(
                        "job claimed",
                        uid = request.uid,
                        jobId = request.jobId,
                        extra = listOf("requestStoragePath" to claim.requestStoragePath),
                    )
                    claim.requestStoragePath
                }
            }
            runProxyAndPublish(
                uid = request.uid,
                jobId = request.jobId,
                requestStoragePath = requestStoragePath,
            )
        } catch (t: Throwable) {
            // Full type + stack go to Error Reporting; the job doc keeps the user-safe message.
            logger.reportError("job failed", t, uid = request.uid, jobId = request.jobId)
            val message = sanitizeError(t)
            val updated = jobs.updateIfPresent(
                uid = request.uid,
                jobId = request.jobId,
                fields = mapOf(
                    "status" to "failed",
                    "progressPhase" to "failed",
                    "progressMessage" to "Program synthesis failed",
                    "errorMessage" to message,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "progressUpdatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            if (!updated) {
                logger.info("job missing; skipping status update", uid = request.uid, jobId = request.jobId)
            }
            Outcome.Failed(message)
        }
    }

    private fun runProxyAndPublish(
        uid: String,
        jobId: String,
        requestStoragePath: String,
    ): Outcome {
        val requestBytes = blobs.download(requestStoragePath)
        val requestJson = String(requestBytes, StandardCharsets.UTF_8)
        // Full validation first (unknown majors/shapes fail before any HTTP
        // call — the fake HTTP client in tests asserts it is never reached
        // for v2), then a byte-preserving splice below: the payload is never
        // re-encoded, so these two steps cannot collapse into one
        // decode-then-encode helper without breaking the byte-identical
        // proxy guarantee. wireRequest also feeds the proxy log line.
        // Missing means v1: splice the authoritative version signal onto the
        // stored bytes without touching anything else. Stamp BEFORE validation
        // because the v1 schema requires modelVersion.
        val stamped = ProgramSynthesisWire.stampModelVersionV1(requestJson)
        val wireRequest = try {
            ProgramSynthesisWire.decodeRequest(stamped)
        } catch (e: ProgramSynthesisWireException) {
            throw IllegalArgumentException("Invalid synthesis request JSON: ${e.message}", e)
        }

        logger.info(
            "proxying solve",
            uid = uid,
            jobId = jobId,
            extra = listOf(
                "trainingDaysPerWeek" to wireRequest.context.trainingDaysPerWeek,
                "deadlineMs" to deadlineMillis,
            ),
        )
        val solveStartMs = System.currentTimeMillis()
        // Forward the raw Python body: the stored result is byte-identical to
        // what the solver emitted, never re-encoded through domain models.
        val resultBody = postToSolver(stamped)
        val resultStoragePath = "synthesis/$uid/$jobId/result.json"
        blobs.uploadJson(resultStoragePath, resultBody)
        logger.info(
            "uploaded result",
            uid = uid,
            jobId = jobId,
            durationMs = System.currentTimeMillis() - solveStartMs,
            extra = listOf(
                "resultStoragePath" to resultStoragePath,
                "bytes" to resultBody.length,
            ),
        )

        val updated = jobs.updateIfPresent(
            uid = uid,
            jobId = jobId,
            fields = mapOf(
                "status" to "ready",
                "resultStoragePath" to resultStoragePath,
                "progressPhase" to "complete",
                "progressMessage" to "Program synthesis complete",
                "errorMessage" to FieldValue.delete(),
                "updatedAt" to FieldValue.serverTimestamp(),
                "progressUpdatedAt" to FieldValue.serverTimestamp(),
                // STR-13 trace: upload timestamp on the ready write.
                SynthesisJobs.TRACE_WORKER_UPLOADED to FieldValue.serverTimestamp(),
                // Proxy mode runs no local solver: clear any pre-migration
                // solver stats so a reused jobId never shows stale values.
                SynthesisJobs.TRACE_SOLVER to FieldValue.delete(),
            ),
        )
        if (!updated) {
            logger.info(
                "job missing after solve; result left in storage",
                uid = uid,
                jobId = jobId,
                extra = listOf("resultStoragePath" to resultStoragePath),
            )
            return Outcome.JobMissing
        }
        return Outcome.Ready(resultStoragePath)
    }

    /**
     * POSTs the stamped request and returns the validated raw result body.
     * 4xx means the solver rejected our request (client-shape failure); any
     * other non-2xx, an empty body, or an undecodable body is a solver-side
     * failure. All failures surface as IllegalArgument/IllegalState so
     * [sanitizeError] maps them to the right user-safe message.
     */
    private fun postToSolver(stamped: String): String {
        val response = try {
            httpClient.postSolveV1(stamped)
        } catch (e: IOException) {
            throw IllegalStateException("Solver service call failed: ${e.message}", e)
        }
        require(response.statusCode !in HTTP_CLIENT_ERROR_MIN..HTTP_CLIENT_ERROR_MAX) {
            "Invalid synthesis request: solver rejected it (HTTP ${response.statusCode})"
        }
        check(response.statusCode in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX) {
            "Solver service returned HTTP ${response.statusCode}"
        }
        val body = response.body
        check(body.isNotBlank()) { "Solver service returned empty body" }
        try {
            ProgramSynthesisWire.decodeResult(body)
        } catch (e: ProgramSynthesisWireException) {
            throw IllegalStateException("Invalid solver response: ${e.message}", e)
        }
        return body
    }

    /**
     * User-safe error for the job doc / client. Full detail goes only to Cloud Run logs.
     */
    private fun sanitizeError(t: Throwable): String {
        val root = generateSequence(t) { it.cause }.last()
        val msg = (root.message ?: t.message).orEmpty()
        return when {
            msg.contains("Invalid synthesis request", ignoreCase = true) ||
                t is ProgramSynthesisWireException ||
                t is IllegalArgumentException ->
                "Invalid generation request. Please try again."
            msg.contains("requestStoragePath", ignoreCase = true) ||
                msg.contains("Request object", ignoreCase = true) ->
                "Generation request was missing or invalid. Please try again."
            else -> "Program generation failed. Please try again."
        }
    }

    companion object {
        /** Python solver base URL, e.g. `https://stratum-solver-xyz.run.app` (no path). */
        const val SOLVER_BASE_URL_ENV = "STRATUM_SOLVER_URL"

        /**
         * Worker deadline, applied as the solver HTTP connect+request timeout
         * so a hung solver fails the job instead of pinning a request thread.
         */
        const val DEFAULT_SOLVE_DEADLINE_MILLIS = 120_000L

        private const val HTTP_CLIENT_ERROR_MIN = 400
        private const val HTTP_CLIENT_ERROR_MAX = 499
        private const val HTTP_SUCCESS_MIN = 200
        private const val HTTP_SUCCESS_MAX = 299

        /** Resolves the Python solver base URL from the environment (testable seam). */
        fun resolveSolverBaseUrl(env: Map<String, String> = System.getenv()): String {
            val raw = env[SOLVER_BASE_URL_ENV].orEmpty().trim().trimEnd('/')
            require(raw.isNotEmpty()) {
                "Missing $SOLVER_BASE_URL_ENV environment variable (Python solver base URL)"
            }
            return raw
        }

    }
}
