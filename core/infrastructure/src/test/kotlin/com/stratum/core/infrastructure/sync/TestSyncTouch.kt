package com.stratum.core.infrastructure.sync

import android.content.Context
import com.stratum.core.infrastructure.db.PendingCloudDeleteDao
import com.stratum.core.infrastructure.work.CloudSyncScheduler

/** Builds a no-network [SyncTouch] for Robolectric repository tests. */
fun testSyncTouch(context: Context, pendingDao: PendingCloudDeleteDao): SyncTouch {
    val gate = DefaultCloudSyncFeatureGate().apply { setEnabledForTests(false) }
    val scheduler = CloudSyncScheduler(context)
    val queue = CloudDeleteQueue(pendingDao)
    return SyncTouch(
        cloudDeleteQueue = queue,
        cloudSyncScheduler = scheduler,
        featureGate = gate,
        writeCoordinator = UserOwnedWriteCoordinator(),
    )
}
