package com.stratum.feature.workout.notification

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WorkoutNotificationModule {
    @Provides
    @Singleton
    fun provideEpochMilliClock(): EpochMilliClock = EpochMilliClock { System.currentTimeMillis() }
}
