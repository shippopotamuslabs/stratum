package com.stratum.core.infrastructure.sync

import com.stratum.core.infrastructure.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runtime kill-switch for cloud sync. Defaults to [BuildConfig.CLOUD_SYNC_ENABLED].
 * Tests may override via [setEnabledForTests].
 */
interface CloudSyncFeatureGate {
    fun isEnabled(): Boolean
}

@Singleton
class DefaultCloudSyncFeatureGate @Inject constructor() : CloudSyncFeatureGate {
    @Volatile
    private var testOverride: Boolean? = null

    override fun isEnabled(): Boolean = testOverride ?: BuildConfig.CLOUD_SYNC_ENABLED

    /** JVM/unit-test hook only — production code must not call this. */
    fun setEnabledForTests(enabled: Boolean?) {
        testOverride = enabled
    }
}
