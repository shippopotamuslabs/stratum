package com.stratum.core.data.featureflag

import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.FirebaseRemoteConfigFetchThrottledException
import com.stratum.domain.di.IoDispatcher
import com.stratum.domain.featureflag.FeatureFlag
import com.stratum.application.featureflag.FeatureFlagRepository
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.telemetry.AnalyticsPort
import com.stratum.domain.telemetry.CrashTelemetryPort
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseFeatureFlagRepository @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig,
    private val loggingPort: LoggingPort,
    private val analyticsPort: AnalyticsPort,
    private val crashTelemetryPort: CrashTelemetryPort,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : FeatureFlagRepository {

    private val devOverrides = ConcurrentHashMap<String, Boolean>()
    private val exposedKeys = ConcurrentHashMap.newKeySet<String>()

    override fun isEnabled(flag: FeatureFlag): Boolean {
        // 1. In-memory developer override takes precedence (for QA / dogfooding)
        devOverrides[flag.key]?.let { return recordExposure(flag.key, it) }

        // 2. Firebase Remote Config value (defaults to false if unconfigured)
        return try {
            recordExposure(flag.key, remoteConfig.getBoolean(flag.key))
        } catch (e: Exception) {
            loggingPort.warning(TAG, "Failed reading flag ${flag.key}: ${e.message}")
            recordExposure(flag.key, false)
        }
    }

    /**
     * Records the first evaluation of [key] per process as an analytics exposure event and a
     * Crashlytics flag key, so error spikes can be attributed to flag-gated paths. Repeat
     * evaluations return the value without re-emitting.
     */
    private fun recordExposure(
        key: String,
        enabled: Boolean,
    ): Boolean {
        if (exposedKeys.add(key)) {
            analyticsPort.logEvent(
                FLAG_EXPOSURE_EVENT,
                mapOf(PARAM_FLAG_KEY to key, PARAM_ENABLED to enabled.toString()),
            )
            crashTelemetryPort.setFlagState(key, enabled)
        }
        return enabled
    }

    override fun observe(flag: FeatureFlag): Flow<Boolean> = callbackFlow {
        trySend(isEnabled(flag))

        val registration = remoteConfig.addOnConfigUpdateListener(object : ConfigUpdateListener {
            override fun onUpdate(configUpdate: ConfigUpdate) {
                if (configUpdate.updatedKeys.contains(flag.key)) {
                    remoteConfig.activate().addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            trySend(isEnabled(flag))
                        }
                    }
                }
            }

            override fun onError(error: FirebaseRemoteConfigException) {
                loggingPort.warning(TAG, "ConfigUpdateListener error for ${flag.key}: ${error.message}")
            }
        })

        awaitClose { registration.remove() }
    }.flowOn(ioDispatcher)

    /**
     * Fetches and activates Remote Config with bounded retries. Transient failures retry with
     * linear backoff ([SYNC_RETRY_DELAY_BASE_MS] × attempt); server throttling is never
     * retried (it would burn the throttle budget), and coroutine cancellation propagates.
     *
     * On JVM, [CancellationException] is shared between coroutine cancellation and a
     * cancelled Remote Config task, so [ensureActive] disambiguates: a cancelled coroutine
     * rethrows (structured cancellation), while a task cancelled underneath a live
     * coroutine is a terminal false, not a retry.
     */
    override suspend fun syncFlags(): Boolean = withContext(ioDispatcher) {
        for (attempt in 1..MAX_SYNC_ATTEMPTS) {
            try {
                val updated = remoteConfig.fetchAndActivate().await()
                loggingPort.info(TAG, "Remote config sync complete. New params activated: $updated")
                return@withContext true
            } catch (e: CancellationException) {
                ensureActive()
                loggingPort.warning(TAG, "Remote config sync cancelled: ${e.message}")
                return@withContext false
            } catch (e: FirebaseRemoteConfigFetchThrottledException) {
                loggingPort.warning(TAG, "Remote config sync throttled; not retrying: ${e.message}")
                return@withContext false
            } catch (e: Exception) {
                if (attempt >= MAX_SYNC_ATTEMPTS) {
                    loggingPort.warning(TAG, "Remote config sync failed after $attempt attempts: ${e.message}")
                    return@withContext false
                }
                loggingPort.info(TAG, "Remote config sync attempt $attempt failed; retrying: ${e.message}")
                delay(SYNC_RETRY_DELAY_BASE_MS * attempt)
            }
        }
        false
    }

    fun setDevOverride(flag: FeatureFlag, enabled: Boolean?) {
        if (enabled != null) {
            devOverrides[flag.key] = enabled
        } else {
            devOverrides.remove(flag.key)
        }
    }

    fun clearDevOverrides() {
        devOverrides.clear()
    }

    companion object {
        private const val TAG = "FirebaseFeatureFlagRepository"
        private const val MAX_SYNC_ATTEMPTS = 3
        private const val SYNC_RETRY_DELAY_BASE_MS = 1_000L
        const val FLAG_EXPOSURE_EVENT = "flag_exposure"
        private const val PARAM_FLAG_KEY = "flag_key"
        private const val PARAM_ENABLED = "enabled"
    }
}
