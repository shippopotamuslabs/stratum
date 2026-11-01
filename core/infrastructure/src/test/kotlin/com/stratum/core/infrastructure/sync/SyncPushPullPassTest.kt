package com.stratum.core.infrastructure.sync
import com.stratum.core.infrastructure.sync.CloudSyncResidualException

import com.stratum.domain.sync.SyncStatus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Orchestration contract for R2 F1–F3: upload → pull → post-pull residual upload;
 * residual / incomplete pull never claims Idle and throws [CloudSyncResidualException].
 */
class SyncPushPullPassTest {
    @Test
    @DisplayName("clean upload+pull with no dirty residual claims Idle")
    fun cleanPassIdle() = runTest {
        val statuses = mutableListOf<SyncStatus>()
        var uploads = 0
        runSyncPushPullPass(
            awaitSeed = {},
            setStatus = { statuses += it },
            isHydrated = { true },
            uploadUntilIdle = {
                uploads++
                false
            },
            hasDirtyResidual = { false },
            pull = { PullResult(hadFailures = false, hydrated = true) },
            logError = {},
        )
        assertEquals(1, uploads)
        assertEquals(listOf(SyncStatus.Syncing, SyncStatus.Idle), statuses)
    }

    @Test
    @DisplayName("post-pull dirty residual runs a second uploadUntilIdle then Idle")
    fun postPullDirtyDrains() = runTest {
        val statuses = mutableListOf<SyncStatus>()
        var uploads = 0
        runSyncPushPullPass(
            awaitSeed = {},
            setStatus = { statuses += it },
            isHydrated = { true },
            uploadUntilIdle = {
                uploads++
                false
            },
            hasDirtyResidual = { uploads == 1 }, // dirty only after first upload (post-pull demotion)
            pull = { PullResult(hadFailures = false, hydrated = true) },
            logError = {},
        )
        assertEquals(2, uploads)
        assertEquals(SyncStatus.Idle, statuses.last())
    }

    @Test
    @DisplayName("upload residual after max passes throws and sets Error — no Idle")
    fun prePullUploadResidualFails() = runTest {
        val statuses = mutableListOf<SyncStatus>()
        val outcome =
            runCatching {
                runSyncPushPullPass(
                    awaitSeed = {},
                    setStatus = { statuses += it },
                    isHydrated = { true },
                    uploadUntilIdle = { true },
                    hasDirtyResidual = { false },
                    pull = { error("pull must not run") },
                    logError = {},
                )
            }
        val ex = outcome.exceptionOrNull()
        assertInstanceOf(CloudSyncResidualException::class.java, ex)
        assertTrue(ex!!.message!!.contains("max passes"))
        assertInstanceOf(SyncStatus.Error::class.java, statuses.last())
        assertTrue(statuses.none { it is SyncStatus.Idle })
    }

    @Test
    @DisplayName("post-pull second upload residual throws Error — no Idle")
    fun postPullUploadResidualFails() = runTest {
        val statuses = mutableListOf<SyncStatus>()
        var uploads = 0
        val outcome =
            runCatching {
                runSyncPushPullPass(
                    awaitSeed = {},
                    setStatus = { statuses += it },
                    isHydrated = { true },
                    uploadUntilIdle = {
                        uploads++
                        // First drain ok; second (post-pull) residual
                        uploads > 1
                    },
                    hasDirtyResidual = { true },
                    pull = { PullResult(hadFailures = false, hydrated = true) },
                    logError = {},
                )
            }
        val ex = outcome.exceptionOrNull()
        assertInstanceOf(CloudSyncResidualException::class.java, ex)
        assertTrue(ex!!.message!!.contains("post-pull"))
        assertEquals(2, uploads)
        assertInstanceOf(SyncStatus.Error::class.java, statuses.last())
    }

    @Test
    @DisplayName("pull hadFailures does not claim Idle even after residual drain")
    fun pullFailuresFailResult() = runTest {
        val statuses = mutableListOf<SyncStatus>()
        val outcome =
            runCatching {
                runSyncPushPullPass(
                    awaitSeed = {},
                    setStatus = { statuses += it },
                    isHydrated = { true },
                    uploadUntilIdle = { false },
                    hasDirtyResidual = { false },
                    pull = { PullResult(hadFailures = true, hydrated = true) },
                    logError = {},
                )
            }
        val ex = outcome.exceptionOrNull()
        assertInstanceOf(CloudSyncResidualException::class.java, ex)
        assertTrue(ex!!.message!!.contains("hadFailures=true"))
        assertInstanceOf(SyncStatus.Error::class.java, statuses.last())
        assertTrue(statuses.none { it is SyncStatus.Idle })
    }

    @Test
    @DisplayName("incomplete hydrate (!hydrated) does not claim Idle")
    fun incompleteHydrateFails() = runTest {
        val statuses = mutableListOf<SyncStatus>()
        val outcome =
            runCatching {
                runSyncPushPullPass(
                    awaitSeed = {},
                    setStatus = { statuses += it },
                    isHydrated = { false },
                    uploadUntilIdle = { false },
                    hasDirtyResidual = { false },
                    pull = { PullResult(hadFailures = true, hydrated = false) },
                    logError = {},
                )
            }
        val ex = outcome.exceptionOrNull()
        assertInstanceOf(CloudSyncResidualException::class.java, ex)
        assertTrue(ex!!.message!!.contains("hydrated=false"))
        assertTrue(statuses.any { it is SyncStatus.Hydrating })
        assertInstanceOf(SyncStatus.Error::class.java, statuses.last())
    }

    @Test
    @DisplayName("afterSuccessfulPull runs before Idle on clean pass")
    fun afterSuccessfulPullBeforeIdle() = runTest {
        val statuses = mutableListOf<SyncStatus>()
        var rebuildAtStatus: SyncStatus? = null
        runSyncPushPullPass(
            awaitSeed = {},
            setStatus = { statuses += it },
            isHydrated = { true },
            uploadUntilIdle = { false },
            hasDirtyResidual = { false },
            pull = { PullResult(hadFailures = false, hydrated = true) },
            logError = {},
            afterSuccessfulPull = {
                rebuildAtStatus = statuses.lastOrNull()
            },
        )
        assertEquals(SyncStatus.Syncing, rebuildAtStatus, "rebuild before Idle claim")
        assertEquals(listOf(SyncStatus.Syncing, SyncStatus.Idle), statuses)
    }

    @Test
    @DisplayName("afterSuccessfulPull is skipped when pull residual fails")
    fun afterSuccessfulPullSkippedOnPullFailure() = runTest {
        var rebuildRan = false
        val outcome =
            runCatching {
                runSyncPushPullPass(
                    awaitSeed = {},
                    setStatus = {},
                    isHydrated = { true },
                    uploadUntilIdle = { false },
                    hasDirtyResidual = { false },
                    pull = { PullResult(hadFailures = true, hydrated = true) },
                    logError = {},
                    afterSuccessfulPull = { rebuildRan = true },
                )
            }
        assertInstanceOf(CloudSyncResidualException::class.java, outcome.exceptionOrNull())
        assertTrue(!rebuildRan, "do not rebuild derived tables on incomplete pull")
    }
}
