package com.stratum.application.featureflag

import kotlinx.coroutines.flow.Flow
import com.stratum.domain.featureflag.FeatureFlag

/**
 * Port for accessing and observing feature flags.
 *
 * All flags evaluate to `false` unless explicitly enabled by remote config or local dev overrides.
 */
interface FeatureFlagRepository {
    /**
     * Synchronously checks whether the specified [flag] is currently enabled.
     * Evaluates in-memory state; does not perform network I/O.
     */
    fun isEnabled(flag: FeatureFlag): Boolean

    /**
     * Observes real-time changes to the specified [flag].
     */
    fun observe(flag: FeatureFlag): Flow<Boolean>

    /**
     * Fetches the latest remote configuration and activates it.
     * Returns `true` if fetch and activate succeeded, or `false` otherwise.
     */
    suspend fun syncFlags(): Boolean
}
