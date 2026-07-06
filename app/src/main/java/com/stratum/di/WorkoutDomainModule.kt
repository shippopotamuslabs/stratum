package com.stratum.di

import com.stratum.domain.workout.fatigue.FatigueModel
import com.stratum.domain.workout.fatigue.PopulationFatigueModel
import com.stratum.domain.workout.progression.LinearProgressionConfig
import com.stratum.domain.workout.progression.LinearProgressionDefaults
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WorkoutDomainModule {
    @Provides
    @Singleton
    fun provideLinearProgressionConfig(): LinearProgressionConfig = LinearProgressionDefaults.config

    @Provides
    @Singleton
    fun provideFatigueModel(): FatigueModel = PopulationFatigueModel()
}
