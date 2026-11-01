package com.stratum.core.infrastructure.sync
import com.stratum.core.infrastructure.sync.UserOwnedWriteCoordinator

import android.content.Context
import com.stratum.core.infrastructure.db.StratumDatabase
import com.stratum.core.infrastructure.seed.EquipmentCatalogSeeder
import com.stratum.core.infrastructure.seed.LiftCatalogSeeder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wipes user-owned Room state (account switch or logout) so device data cannot leak across
 * UIDs. Re-seeds equipment + lift catalog after clear (deterministic seed ids).
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
    private val equipmentCatalogSeeder: EquipmentCatalogSeeder,
    private val liftCatalogSeeder: LiftCatalogSeeder,
) {
    suspend fun clearUserOwnedData() {
        database.clearAllTables()
        equipmentCatalogSeeder.seed(context)
        liftCatalogSeeder.seed(context)
    }
}
