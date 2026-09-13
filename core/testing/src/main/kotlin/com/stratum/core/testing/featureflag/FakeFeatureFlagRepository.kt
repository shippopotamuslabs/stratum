package com.stratum.core.testing.featureflag

import com.stratum.domain.featureflag.FeatureFlag
import com.stratum.domain.featureflag.FeatureFlagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory test fake for [FeatureFlagRepository].
 *
 * All flags evaluate to `false` by default.
 * Use [setFlag] to toggle flags during tests.
 */
class FakeFeatureFlagRepository : FeatureFlagRepository {

    private val overrides = ConcurrentHashMap<String, Boolean>()
    private val stateFlows = ConcurrentHashMap<String, MutableStateFlow<Boolean>>()

    var syncFlagsResult: Boolean = true

    fun setFlag(flag: FeatureFlag, isEnabled: Boolean) {
        overrides[flag.key] = isEnabled
        getOrCreateStateFlow(flag.key).value = isEnabled
    }

    fun clear() {
        overrides.clear()
        stateFlows.values.forEach { it.value = false }
    }

    override fun isEnabled(flag: FeatureFlag): Boolean {
        return overrides[flag.key] ?: false
    }

    override fun observe(flag: FeatureFlag): Flow<Boolean> {
        return getOrCreateStateFlow(flag.key).asStateFlow()
    }

    override suspend fun syncFlags(): Boolean {
        return syncFlagsResult
    }

    private fun getOrCreateStateFlow(key: String): MutableStateFlow<Boolean> {
        return stateFlows.computeIfAbsent(key) {
            MutableStateFlow(overrides[key] ?: false)
        }
    }
}
