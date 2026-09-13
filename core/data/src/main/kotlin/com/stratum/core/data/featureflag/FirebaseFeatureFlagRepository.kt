package com.stratum.core.data.featureflag

import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.stratum.domain.di.IoDispatcher
import com.stratum.domain.featureflag.FeatureFlag
import com.stratum.domain.featureflag.FeatureFlagRepository
import com.stratum.domain.logging.LoggingPort
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
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
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : FeatureFlagRepository {

    private val devOverrides = ConcurrentHashMap<String, Boolean>()

    override fun isEnabled(flag: FeatureFlag): Boolean {
        // 1. In-memory developer override takes precedence (for QA / dogfooding)
        devOverrides[flag.key]?.let { return it }

        // 2. Firebase Remote Config value (defaults to false if unconfigured)
        return try {
            remoteConfig.getBoolean(flag.key)
        } catch (e: Exception) {
            loggingPort.warning(TAG, "Failed reading flag ${flag.key}: ${e.message}")
            false
        }
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

    override suspend fun syncFlags(): Boolean = withContext(ioDispatcher) {
        try {
            val updated = remoteConfig.fetchAndActivate().await()
            loggingPort.info(TAG, "Remote config sync complete. New params activated: $updated")
            true
        } catch (e: Exception) {
            loggingPort.warning(TAG, "Remote config sync failed: ${e.message}")
            false
        }
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
    }
}
