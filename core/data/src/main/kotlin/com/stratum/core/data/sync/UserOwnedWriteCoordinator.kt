package com.stratum.core.data.sync

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes user-owned Room mutations with account wipes so an in-flight write cannot land
 * after [UserOwnedDataClearer.clearUserOwnedData] and upload under a different Firebase uid.
 *
 * All repository write paths that touch syncable tables should use [withWriteLock].
 * Account ownership transfer and logout wipe use the same lock exclusively around wipe + seed.
 */
@Singleton
class UserOwnedWriteCoordinator @Inject constructor() {
    private val mutex = Mutex()

    suspend fun <T> withWriteLock(block: suspend () -> T): T =
        mutex.withLock {
            block()
        }
}
