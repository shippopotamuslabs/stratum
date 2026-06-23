package com.stratum.di

import android.content.Context
import androidx.room.Room
import com.stratum.core.data.db.OnboardingProgressDao
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.data.repository.RoomOnboardingRepository
import com.stratum.domain.onboarding.OnboardingRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideStratumDatabase(@ApplicationContext context: Context): StratumDatabase =
        Room.databaseBuilder(context, StratumDatabase::class.java, StratumDatabase.DATABASE_NAME)
            .build()

    @Provides
    fun provideOnboardingProgressDao(database: StratumDatabase): OnboardingProgressDao =
        database.onboardingProgressDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindOnboardingRepository(impl: RoomOnboardingRepository): OnboardingRepository
}
