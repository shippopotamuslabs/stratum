package com.stratum.solver

import com.google.cloud.firestore.FieldValue
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreException
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.firebase.cloud.FirestoreClient
import com.google.firebase.cloud.StorageClient
import com.stratum.domain.program.generation.synthesis.wire.ProgramSynthesisWire
import com.stratum.domain.program.generation.synthesis.wire.ProgramSynthesisWireException
import com.stratum.domain.program.generation.synthesis.SolveStats
import com.stratum.domain.solver.CpSatProgramSynthesizer
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Claims a Firestore synthesis job, runs CP-SAT, uploads result JSON, updates status.
 *
 * Job path: `users/{uid}/synthesisJobs/{jobId}`
 * Result path: `synthesis/{uid}/{jobId}/result.json`
 */
class SynthesisJobWorker(
    private val firestore: Firestore = FirestoreClient.getFirestore(),
    private val storage: Storage = StorageClient.getInstance().bucket().storage,
    private val bucketName: String = StorageClient.getInstance().bucket().name,
    private val deadlineMillis: Long = DEFAULT_SOLVE_DEADLINE_MILLIS,
    private val synthesizerFactory: (Long) -> CpSatProgramSynthesizer = { deadline ->
        CpSatProgramSynthesizer(deadlineMillis = deadline)
    },
) {
    sealed class Outcome {
        data object JobMissing : Outcome()
        data class Skipped(val reason: String) : Outcome()
        data class Ready(val resultStoragePath: String) : Outcome()
        data class Failed(val errorMessage: String) : Outcome()
    }

    fun process(request: ProcessRequest): Outcome {
        val jobPath = "users/${request.uid}/synthesisJobs/${request.jobId}"
        log("start uid=${request.uid} jobId=${request.jobId}")
        // STR-13 trace: mark when the worker received the request.
        recordTrace(request.uid, request.jobId, TRACE_WORKER_START)

        return try {
            val claim = claimJob(request.uid, request.jobId)
            val requestStoragePath = when (claim) {
                is ClaimResult.Missing -> {
                    log("job missing at $jobPath (account deleted?); no-op")
                    return Outcome.JobMissing
                }
                is ClaimResult.NotClaimable -> {
                    log("skip claim at $jobPath status=${claim.status}")
                    return Outcome.Skipped("status=${claim.status}")
                }
                is ClaimResult.Claimed -> {
                    log("claimed $jobPath requestStoragePath=${claim.requestStoragePath}")
                    // STR-13 trace: mark when the claim completed.
                    recordTrace(request.uid, request.jobId, TRACE_WORKER_CLAIMED)
                    claim.requestStoragePath
                }
            }
            runSolveAndPublish(
                uid = request.uid,
                jobId = request.jobId,
                requestStoragePath = requestStoragePath,
            )
        } catch (t: Throwable) {
            val message = sanitizeError(t)
            log("error uid=${request.uid} jobId=${request.jobId}: $message")
            updateJobIfPresent(
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
            Outcome.Failed(message)
        }
    }

    private fun runSolveAndPublish(
        uid: String,
        jobId: String,
        requestStoragePath: String,
    ): Outcome {
        val requestBytes = downloadBytes(requestStoragePath)
        val requestJson = String(requestBytes, StandardCharsets.UTF_8)
        val wireRequest = try {
            ProgramSynthesisWire.decodeRequest(requestJson)
        } catch (e: ProgramSynthesisWireException) {
            throw IllegalArgumentException("Invalid synthesis request JSON: ${e.message}", e)
        }

        log(
            "solving uid=$uid jobId=$jobId " +
                "days=${wireRequest.context.trainingDaysPerWeek} " +
                "deadlineMs=$deadlineMillis",
        )
        // STR-13 trace: mark when the CP-SAT solve begins.
        recordTrace(uid, jobId, TRACE_WORKER_SOLVING)
        val synthesizer = synthesizerFactory(deadlineMillis)
        // Blocking CP-SAT on Default so we don't pin the HTTP pool thread alone.
        val result = runBlocking(Dispatchers.Default) {
            synthesizer.synthesize(
                weeklyTargets = wireRequest.weeklyTargets,
                context = wireRequest.context,
            )
        }

        val resultJson = ProgramSynthesisWire.encodeResult(result)
        val resultStoragePath = "synthesis/$uid/$jobId/result.json"
        uploadJson(resultStoragePath, resultJson)
        log(
            "uploaded result path=$resultStoragePath bytes=${resultJson.length} " +
                "sessions=${result.sessions.size} " +
                "objTotal=${result.objective.total}",
        )

        val traceFields = mutableMapOf<String, Any>(
            // STR-13 trace: upload timestamp + compact solver progression.
            TRACE_WORKER_UPLOADED to FieldValue.serverTimestamp(),
        )
        result.solveStats?.let { stats ->
            traceFields[TRACE_SOLVER] = solveStatsMap(stats)
        }
        val updated = updateJobIfPresent(
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
            ) + traceFields,
        )
        if (!updated) {
            log("job missing after solve uid=$uid jobId=$jobId; result left at $resultStoragePath")
            return Outcome.JobMissing
        }
        return Outcome.Ready(resultStoragePath)
    }

    private sealed class ClaimResult {
        data object Missing : ClaimResult()
        data class NotClaimable(val status: String?) : ClaimResult()
        data class Claimed(val requestStoragePath: String) : ClaimResult()
    }

    private fun claimJob(uid: String, jobId: String): ClaimResult {
        val jobRef = firestore.document("users/$uid/synthesisJobs/$jobId")
        // Prefer a simple get+update over a multi-retry transaction when possible: claim races are
        // rare (one dispatcher, concurrency 1). Fall back to transaction for correctness.
        return try {
            firestore.runTransaction { transaction ->
                val snap = transaction.get(jobRef).get()
                if (!snap.exists()) {
                    return@runTransaction ClaimResult.Missing
                }
                val status = snap.getString("status")
                if (status != CLAIMABLE_STATUS) {
                    return@runTransaction ClaimResult.NotClaimable(status)
                }
                val requestStoragePath = snap.getString("requestStoragePath")
                    ?.trim()
                    .orEmpty()
                require(requestStoragePath.isNotEmpty()) {
                    "Job $uid/$jobId is missing requestStoragePath"
                }
                require(isValidRequestStoragePath(uid, jobId, requestStoragePath)) {
                    "requestStoragePath must be synthesis/$uid/$jobId/request.json"
                }
                transaction.update(
                    jobRef,
                    mapOf(
                        "status" to "running",
                        "progressPhase" to "solving",
                        "progressMessage" to "Solving program…",
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "progressUpdatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
                ClaimResult.Claimed(requestStoragePath)
            }.get()
        } catch (e: Exception) {
            // Surface gRPC / packaging failures as process() catch → job failed (HTTP 200 body),
            // not an uncaught 500 that the Node dispatcher rewrites with a generic message.
            throw IllegalStateException(
                "Failed to claim synthesis job $uid/$jobId: ${e.message}",
                e,
            )
        }
    }

    private fun downloadBytes(storagePath: String): ByteArray {
        val blob = storage.get(BlobId.of(bucketName, storagePath))
            ?: error("Request object not found in Storage")
        val size = blob.size
        require(size > 0) { "Request object is empty" }
        require(size <= MAX_REQUEST_BYTES) {
            "Request object exceeds max size ($MAX_REQUEST_BYTES bytes)"
        }
        val content = blob.getContent()
        require(content.isNotEmpty()) { "Request object is empty" }
        return content
    }

    private fun uploadJson(storagePath: String, json: String) {
        val blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, storagePath))
            .setContentType("application/json; charset=utf-8")
            .build()
        storage.create(blobInfo, json.toByteArray(StandardCharsets.UTF_8))
    }

    /**
     * Job status update that no-ops when the doc was cascade-deleted (account deletion).
     */
    private fun updateJobIfPresent(
        uid: String,
        jobId: String,
        fields: Map<String, Any>,
    ): Boolean {
        val jobRef = firestore.document("users/$uid/synthesisJobs/$jobId")
        return try {
            jobRef.update(fields).get()
            true
        } catch (e: Exception) {
            if (isNotFound(e)) {
                log("job users/$uid/synthesisJobs/$jobId missing; skipping status update")
                false
            } else {
                throw e
            }
        }
    }

    private fun isNotFound(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (isNotFoundSignal(current)) return true
            current = current.cause
        }
        return false
    }

    private fun isNotFoundSignal(error: Throwable): Boolean {
        val message = error.message.orEmpty()
        if (message.contains("NOT_FOUND", ignoreCase = true)) return true
        if (message.contains("No document to update", ignoreCase = true)) return true
        // FirestoreException.code is the gRPC numeric code (5 = NOT_FOUND).
        return error is FirestoreException && error.code == GRPC_NOT_FOUND
    }

    /**
     * User-safe error for the job doc / client. Full detail goes only to Cloud Run logs.
     */
    private fun sanitizeError(t: Throwable): String {
        log("failure detail: ${t::class.java.name}: ${t.message}")
        val root = generateSequence(t) { it.cause }.last()
        val msg = (root.message ?: t.message).orEmpty()
        return when {
            msg.contains("Invalid synthesis request", ignoreCase = true) ||
                t is ProgramSynthesisWireException ||
                t is IllegalArgumentException ->
                "Invalid generation request. Please try again."
            msg.contains("INFEASIBLE", ignoreCase = true) ||
                msg.contains("CP-SAT synthesis returned", ignoreCase = true) ->
                "Could not build a feasible program for this profile. Try relaxing specialization or adding a training day."
            msg.contains("requestStoragePath", ignoreCase = true) ||
                msg.contains("Request object", ignoreCase = true) ->
                "Generation request was missing or invalid. Please try again."
            else -> "Program generation failed. Please try again."
        }
    }

    /**
     * Best-effort trace timestamp write. Never fails the job over tracing.
     */
    private fun recordTrace(uid: String, jobId: String, field: String) {
        try {
            firestore.document("users/$uid/synthesisJobs/$jobId")
                .update(field, FieldValue.serverTimestamp())
                .get()
        } catch (e: Exception) {
            log("trace write failed for $field: ${e.message}")
        }
    }

    /**
     * Compact solver stats map for the job doc.
     */
    private fun solveStatsMap(stats: SolveStats): Map<String, Any?> {
        return mapOf(
            "status" to stats.status,
            "wallTimeSeconds" to stats.wallTimeSeconds,
            "objective" to stats.objective,
            "bestBound" to stats.bestBound,
            "relativeGap" to stats.relativeGap,
            "incumbentCount" to stats.incumbentCount,
            "firstIncumbentAtSeconds" to stats.firstIncumbentAtSeconds,
            "lastIncumbentAtSeconds" to stats.lastIncumbentAtSeconds,
            "firstObjective" to stats.firstObjective,
            "lastObjective" to stats.lastObjective,
            "numBranches" to stats.numBranches,
            "numConflicts" to stats.numConflicts,
        )
    }

    private fun log(message: String) {
        println("SynthesisJobWorker: $message")
    }

    companion object {
        const val CLAIMABLE_STATUS = "pending"
        const val DEFAULT_SOLVE_DEADLINE_MILLIS = 120_000L
        private const val MAX_REQUEST_BYTES = 5L * 1024L * 1024L
        private const val GRPC_NOT_FOUND = 5

        /** STR-13 trace field names for the per-job `trace` map. */
        const val TRACE_WORKER_START = "trace.workerStart"
        const val TRACE_WORKER_CLAIMED = "trace.workerClaimed"
        const val TRACE_WORKER_SOLVING = "trace.workerSolving"
        const val TRACE_WORKER_UPLOADED = "trace.workerUploaded"
        const val TRACE_SOLVER = "trace.solver"

        fun isValidRequestStoragePath(uid: String, jobId: String, path: String): Boolean {
            if (path.contains("..") || path.contains("//")) return false
            if (path.startsWith("/") || path.contains("\\")) return false
            return path == "synthesis/$uid/$jobId/request.json"
        }
    }
}
