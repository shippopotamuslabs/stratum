package com.stratum.core.data.di

import com.stratum.core.infrastructure.telemetry.DataStorePendingClientLogStore
import com.stratum.domain.telemetry.PendingClientLogStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TelemetryBindsModule {
    @Binds
    @Singleton
    abstract fun bindPendingClientLogStore(impl: DataStorePendingClientLogStore): PendingClientLogStore
}
