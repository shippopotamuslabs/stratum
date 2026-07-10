package com.stratum.core.data.sync.appliers

import com.stratum.core.data.db.ProgramDao
import com.stratum.core.data.sync.codecs.SyncEntityDecoders
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies remote program documents.
 *
 * **Invariant side-effects of applying a remote winner mark PENDING so they upload and heal
 * cloud.** When the remote program is active, other local actives are demoted with a
 * non-regressing clock + [com.stratum.core.data.sync.SyncFlags.PENDING_UPSERT] so dual-active
 * cloud state (e.g. two devices each activating a different program) is corrected on the next
 * upload pass. Same rule as PR demotion in [LoggedSetRemoteApplier].
 */
@Singleton
class ProgramRemoteApplier @Inject constructor(
    private val programDao: ProgramDao,
    private val decoders: SyncEntityDecoders,
) {
    suspend fun upsertFromRemote(id: String, data: Map<String, Any?>): Boolean {
        val entity = decoders.program(id, data) ?: return false
        if (entity.isActive && !entity.id.isImportHistory) {
            val floor =
                maxOf(
                    entity.updatedAtEpochMillis,
                    System.currentTimeMillis(),
                )
            programDao.demoteOtherActivesPending(
                exceptProgramId = entity.id,
                minUpdatedAtEpochMillis = floor,
            )
        }
        programDao.upsert(entity)
        return true
    }
}
