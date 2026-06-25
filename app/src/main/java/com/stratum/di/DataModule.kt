package com.stratum.di

import android.content.Context
import androidx.room.Room
import com.stratum.core.data.db.GymPreferenceDao
import com.stratum.core.data.db.IncrementTierDao
import com.stratum.core.data.db.LiftDao
import com.stratum.core.data.db.OnboardingProgressDao
import com.stratum.core.data.db.SpecializationPreferenceDao
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.data.db.UserProfileDao
import com.stratum.core.data.repository.RoomLiftRepository
import com.stratum.core.data.repository.RoomOnboardingRepository
import com.stratum.core.data.repository.RoomSpecializationPreferenceRepository
import com.stratum.core.data.repository.RoomUserTrainingProfileRepository
import com.stratum.core.data.seed.LiftCatalogSeeder
import com.stratum.domain.lift.LiftRepository
import com.stratum.domain.onboarding.OnboardingRepository
import com.stratum.domain.onboarding.SpecializationPreferenceRepository
import com.stratum.domain.onboarding.UserTrainingProfileRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // Application-scoped scope for one-shot catalog seeding. Detached from any ViewModel so the
    // seed completes even if the UI that triggered first-run leaves.
    private val seedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun provideStratumDatabase(
        @ApplicationContext context: Context,
    ): StratumDatabase {
        val database = Room.databaseBuilder(context, StratumDatabase::class.java, StratumDatabase.DATABASE_NAME)
            .build()
        // Never call DAOs from RoomDatabase.Callback.onCreate — Room holds the DB lock during
        // init and DAO work there deadlocks (white screen on startup). Seed asynchronously once
        // the database is fully open; idempotent upserts make re-runs safe.
        seedScope.launch {
            val liftDao = database.liftDao()
            if (liftDao.countAll() == 0) {
                LiftCatalogSeeder(
                    liftDao = liftDao,
                    incrementTierDao = database.incrementTierDao(),
                ).seed(context)
            }
        }
        return database
    }

    @Provides
    fun provideOnboardingProgressDao(database: StratumDatabase): OnboardingProgressDao =
        database.onboardingProgressDao()

    @Provides
    fun provideUserProfileDao(database: StratumDatabase): UserProfileDao =
        database.userProfileDao()

    @Provides
    fun provideSpecializationPreferenceDao(database: StratumDatabase): SpecializationPreferenceDao =
        database.specializationPreferenceDao()

    @Provides
    fun provideLiftDao(database: StratumDatabase): LiftDao = database.liftDao()

    @Provides
    fun provideIncrementTierDao(database: StratumDatabase): IncrementTierDao =
        database.incrementTierDao()

    @Provides
    fun provideGymPreferenceDao(database: StratumDatabase): GymPreferenceDao =
        database.gymPreferenceDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindOnboardingRepository(impl: RoomOnboardingRepository): OnboardingRepository

    @Binds
    @Singleton
    abstract fun bindUserTrainingProfileRepository(
        impl: RoomUserTrainingProfileRepository,
    ): UserTrainingProfileRepository

    @Binds
    @Singleton
    abstract fun bindSpecializationPreferenceRepository(
        impl: RoomSpecializationPreferenceRepository,
    ): SpecializationPreferenceRepository

    @Binds
    @Singleton
    abstract fun bindLiftRepository(impl: RoomLiftRepository): LiftRepository
}
