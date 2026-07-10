package com.stratum.core.data.sync

import com.stratum.domain.sync.SyncStatus

/**
 * Result of a single [FirestoreRemotePullEngine.pull] pass.
 *
 * @property hadFailures true if any document apply failed (cursor may be frozen mid-collection).
 * @property hydrated post-pass hydrate flag from durable state (true only after a clean zero-failure pass).
 */
data class PullResult(
    val hadFailures: Boolean,
    val hydrated: Boolean,
)

/**
 * Thrown when a sync pass ends with residual work (upload backlog, pull incomplete).
 * Callers must surface this as [Result.failure] so [com.stratum.core.data.work.CloudSyncWorker]
 * returns [androidx.work.ListenableWorker.Result.retry] — do not REPLACE unique work from inside
 * the running worker.
 */
class CloudSyncResidualException(message: String) : IllegalStateException(message)

/**
 * Canonical push → pull → (post-pull residual upload) pass used by [FirestoreCloudSyncAdapter].
 *
 * Residual after max upload passes, dirty residual after post-pull drain, or incomplete pull
 * sets [SyncStatus.Error] and throws [CloudSyncResidualException]. Never claims Idle with work left.
 *
 * Lambdas keep engines out of this pure orchestration so unit tests can drive every branch with fakes.
 */
internal suspend fun runSyncPushPullPass(
    awaitSeed: suspend () -> Unit,
    setStatus: (SyncStatus) -> Unit,
    isHydrated: suspend () -> Boolean,
    uploadUntilIdle: suspend () -> Boolean,
    hasDirtyResidual: suspend () -> Boolean,
    pull: suspend () -> PullResult,
    logError: (String) -> Unit,
) {
    awaitSeed()
    setStatus(SyncStatus.Syncing)

    if (uploadUntilIdle()) {
        failResidual(setStatus, logError, "upload residual remaining after max passes")
    }

    if (!isHydrated()) {
        setStatus(SyncStatus.Hydrating)
    }

    val pullResult = pull()

    // Pull-side demotions (dual-active, dual-PR) dirtied Room after the first upload — drain them.
    if (hasDirtyResidual()) {
        if (uploadUntilIdle()) {
            failResidual(setStatus, logError, "upload residual remaining after post-pull drain")
        }
    }

    if (pullResult.hadFailures || !pullResult.hydrated) {
        failResidual(
            setStatus,
            logError,
            "pull incomplete (hadFailures=${pullResult.hadFailures}, hydrated=${pullResult.hydrated})",
        )
    }

    setStatus(SyncStatus.Idle)
}

private fun failResidual(
    setStatus: (SyncStatus) -> Unit,
    logError: (String) -> Unit,
    message: String,
): Nothing {
    logError(message)
    setStatus(SyncStatus.Error(message))
    throw CloudSyncResidualException(message)
}
