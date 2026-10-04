package com.stratum.solver

/**
 * Firestore job-document schema shared by [SynthesisJobWorker] and
 * [FirestoreSynthesisJobStore].
 *
 * Neutral home so infrastructure never reaches back into its caller for
 * status values, trace keys, or path validation (STR-52 review): both sides
 * reference this contract, and a rename touches one file.
 */
object SynthesisJobs {
    const val CLAIMABLE_STATUS = "pending"

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
