package com.stratum.core.infrastructure.di

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.stratum.core.infrastructure.BuildConfig
import com.stratum.core.infrastructure.featureflag.FirebaseFeatureFlagRepository
import com.stratum.domain.featureflag.AppFeatureFlag
import com.stratum.application.featureflag.FeatureFlagRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FeatureFlagProvidesModule {

    @Provides
    @Singleton
    fun provideFirebaseRemoteConfig(): FirebaseRemoteConfig {
        val config = FirebaseRemoteConfig.getInstance()
        val settings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(if (BuildConfig.DEBUG) 0L else 3600L)
            .setFetchTimeoutInSeconds(5L)
            .build()
        config.setConfigSettingsAsync(settings)
        // Kill-switch flags default OPEN (backend allowed) so an unconfigured flag or a
        // failed first fetch can never disable its path. The console flips them to false.
        // Launch flags default CLOSED (feature hidden); the console flips them to true.
        config.setDefaultsAsync(
            mapOf(
                AppFeatureFlag.CLOUD_PROGRAM_SYNTHESIS.key to true,
                AppFeatureFlag.INCREMENTS_AI_SCAN.key to false,
            ),
        )
        return config
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FeatureFlagBindsModule {

    @Binds
    @Singleton
    abstract fun bindFeatureFlagRepository(
        impl: FirebaseFeatureFlagRepository,
    ): FeatureFlagRepository
}
