package com.stratum.core.infrastructure.sync

/**
 * Thrown when a sync pass ends with residual work (upload backlog, pull incomplete).
 * Callers must surface this as [Result.failure] so [com.stratum.core.infrastructure.work.CloudSyncWorker]
 * returns [androidx.work.ListenableWorker.Result.retry] — do not REPLACE unique work from inside
 * the running worker.
 */
class CloudSyncResidualException(message: String) : IllegalStateException(message)
