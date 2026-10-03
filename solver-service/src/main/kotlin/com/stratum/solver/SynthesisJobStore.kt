package com.stratum.solver

import com.google.cloud.firestore.FieldValue
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreException
import com.google.firebase.cloud.FirestoreClient

/**
 * Firestore side of a synthesis job: claim once, then record terminal status.
 *
 * Narrow seam so the proxy worker tests run against an in-memory fake instead
 * of the Firebase Admin SDK (third-party boundary).
 */
interface SynthesisJobStore {
    sealed interface Claim {
        data object Missing : Claim
        data class NotClaimable(val status: String?) : Claim
        data class Claimed(val requestStoragePath: String) : Claim
    }

    fun claim(uid: String, jobId: String): Claim

    /**
     * Status update that no-ops (returns false) when the doc was
     * cascade-deleted (account deletion).
     */
    fun updateIfPresent(uid: String, jobId: String, fields: Map<String, Any>): Boolean
}

/** Production [SynthesisJobStore] over Cloud Firestore. */
class FirestoreSynthesisJobStore(
    private val firestore: Firestore = FirestoreClient.getFirestore(),
) : SynthesisJobStore {
    override fun claim(uid: String, jobId: String): SynthesisJobStore.Claim {
        val jobRef = firestore.document("users/$uid/synthesisJobs/$jobId")
        // Prefer a simple get+update over a multi-retry transaction when possible: claim races are
        // rare (one dispatcher, concurrency 1). Fall back to transaction for correctness.
        return try {
            firestore.runTransaction { transaction ->
                val snap = transaction.get(jobRef).get()
                if (!snap.exists()) {
                    return@runTransaction SynthesisJobStore.Claim.Missing
                }
                val status = snap.getString("status")
                if (status != SynthesisJobWorker.CLAIMABLE_STATUS) {
                    return@runTransaction SynthesisJobStore.Claim.NotClaimable(status)
                }
                val requestStoragePath = snap.getString("requestStoragePath")
                    ?.trim()
                    .orEmpty()
                require(requestStoragePath.isNotEmpty()) {
                    "Job $uid/$jobId is missing requestStoragePath"
                }
                require(SynthesisJobWorker.isValidRequestStoragePath(uid, jobId, requestStoragePath)) {
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
                        // STR-13 trace: piggyback worker handoff timestamps on the claim write.
                        // workerStart (request received), workerClaimed, and workerSolving are
                        // co-timed here — the claim is the last Firestore write before the
                        // blocking solver call, avoiding extra round-trips on the critical path.
                        SynthesisJobWorker.TRACE_WORKER_START to FieldValue.serverTimestamp(),
                        SynthesisJobWorker.TRACE_WORKER_CLAIMED to FieldValue.serverTimestamp(),
                        SynthesisJobWorker.TRACE_WORKER_SOLVING to FieldValue.serverTimestamp(),
                    ),
                )
                SynthesisJobStore.Claim.Claimed(requestStoragePath)
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

    override fun updateIfPresent(uid: String, jobId: String, fields: Map<String, Any>): Boolean {
        val jobRef = firestore.document("users/$uid/synthesisJobs/$jobId")
        return try {
            jobRef.update(fields).get()
            true
        } catch (e: Exception) {
            if (isNotFound(e)) {
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

    private companion object {
        const val GRPC_NOT_FOUND = 5
    }
}
