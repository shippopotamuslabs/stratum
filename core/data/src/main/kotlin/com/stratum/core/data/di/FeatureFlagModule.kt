package com.stratum.core.data.di

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.stratum.core.data.BuildConfig
import com.stratum.core.data.featureflag.FirebaseFeatureFlagRepository
import com.stratum.domain.featureflag.FeatureFlagRepository
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
