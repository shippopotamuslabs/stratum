package com.stratum.di

import com.stratum.domain.training.DefaultLoggedLiftContextFactory
import com.stratum.domain.training.LoggedLiftContextFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TrainingDomainModule {
    @Provides
    @Singleton
    fun provideLoggedLiftContextFactory(): LoggedLiftContextFactory = DefaultLoggedLiftContextFactory()
}
