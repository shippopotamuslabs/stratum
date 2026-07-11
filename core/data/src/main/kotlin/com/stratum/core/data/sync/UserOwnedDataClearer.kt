package com.stratum.core.data.sync

import android.content.Context
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.data.seed.LiftCatalogSeeder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wipes user-owned Room state (account switch or logout) so device data cannot leak across
 * UIDs. Re-seeds the lift catalog after clear (deterministic seed ids).
 *
 * [StratumDatabase.clearAllTables] already drops outbox rows (`pending_cloud_deletes`);
 * no separate outbox clear is required.
 *
 * Callers must hold [UserOwnedWriteCoordinator.withWriteLock] around this wipe.
 */
@Singleton
class UserOwnedDataClearer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: StratumDatabase,
    private val liftCatalogSeeder: LiftCatalogSeeder,
) {
    suspend fun clearUserOwnedData() {
        database.clearAllTables()
        liftCatalogSeeder.seed(context)
    }
}
