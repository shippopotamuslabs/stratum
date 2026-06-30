package com.stratum.di

import android.content.Context
import androidx.room.Room
import com.stratum.core.data.db.GymPreferenceDao
import com.stratum.core.data.db.IncrementTierDao
import com.stratum.core.data.db.LiftDao
import com.stratum.core.data.db.OnboardingProgressDao
import com.stratum.core.data.db.SpecializationPreferenceDao
import com.stratum.core.data.db.StratumDatabase
import com.stratum.domain.onboarding.UserTrainingProfileRepository
import com.stratum.core.data.db.UserProfileDao
import com.stratum.core.data.repository.RoomLiftRepository
import com.stratum.core.data.repository.RoomOnboardingRepository
import com.stratum.core.data.repository.RoomSpecializationPreferenceRepository
import com.stratum.core.data.repository.RoomUserTrainingProfileRepository
import com.stratum.core.data.seed.LiftCatalogSeeder
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisPort
import com.stratum.domain.solver.CpSatProgramSynthesizer
import com.stratum.domain.lift.LiftRepository
import com.stratum.domain.onboarding.OnboardingRepository
import com.stratum.domain.onboarding.SpecializationPreferenceRepository
import com.stratum.core.data.db.ExperimentBlockDao
import com.stratum.core.data.db.HistoricalSessionDao
import com.stratum.core.data.db.ImportAnalysisMetadataDao
import com.stratum.core.data.db.ImportJobDao
import com.stratum.core.data.db.LiftEffectivenessObservationDao
import com.stratum.core.data.db.LiftTrainingInsightsDao
import com.stratum.core.data.db.LoggedSetDao
import com.stratum.core.data.db.LiftWeeklyPerformanceDao
import com.stratum.core.data.db.MuscleDoseResponsePointDao
import com.stratum.core.data.db.MuscleResponseModelDiagnosticDao
import com.stratum.core.data.db.MuscleVolumeBaselineDao
import com.stratum.core.data.db.MuscleVolumeLandmarkEstimateDao
import com.stratum.core.data.db.MuscleWeeklyOutcomeIndexDao
import com.stratum.core.data.db.MuscleWeeklyStimulusDao
import com.stratum.core.data.db.ResponseEpochDao
import com.stratum.core.data.db.ResponseProfileMetadataDao
import com.stratum.core.data.db.SupersetAffinityDao
import com.stratum.core.data.db.VolumeResponseObservationDao
import com.stratum.core.data.repository.RoomExperimentBlockRepository
import com.stratum.core.data.repository.RoomLiftEffectivenessObservationRepository
import com.stratum.core.data.repository.RoomResponseEpochRepository
import com.stratum.core.data.repository.RoomResponseProfileMetadataRepository
import com.stratum.core.data.repository.RoomResponseProfilePersistenceRepository
import com.stratum.core.data.repository.RoomVolumeResponseObservationRepository
import com.stratum.core.data.repository.RoomImportAnalysisMetadataRepository
import com.stratum.core.data.repository.RoomImportJobRepository
import com.stratum.domain.importing.TrainingHistoryRepository
import com.stratum.core.data.repository.RoomLiftTrainingInsightsRepository
import com.stratum.core.data.repository.RoomMuscleVolumeLandmarkEstimateRepository
import com.stratum.core.data.repository.RoomMuscleVolumeBaselineRepository
import com.stratum.core.data.repository.RoomSupersetAffinityRepository
import com.stratum.core.data.repository.RoomTrainingHistoryRepository
import com.stratum.domain.importing.Bm25LiftNameResolver
import com.stratum.domain.importing.HybridLiftNameResolver
import com.stratum.domain.importing.ImportAnalysisMetadataRepository
import com.stratum.domain.importing.ImportJobRepository
import com.stratum.domain.importing.LiftEmbeddingCatalogPort
import com.stratum.domain.importing.LiftEquipmentCatalogPort
import com.stratum.domain.importing.LiftNameEmbeddingPort
import com.stratum.domain.importing.LiftNameResolverPort
import com.stratum.domain.importing.LiftTrainingInsightsRepository
import com.stratum.domain.importing.MuscleVolumeBaselineRepository
import com.stratum.domain.importing.SupersetAffinityRepository
import com.stratum.domain.response.DefaultResponseObservationExtractor
import com.stratum.domain.response.EmptyExperimentRegistry
import com.stratum.domain.response.ExperimentBlockRepository
import com.stratum.domain.response.ExperimentRegistry
import com.stratum.domain.response.LiftEffectivenessObservationRepository
import com.stratum.domain.response.ResponseEpochRepository
import com.stratum.domain.response.ResponseObservationExtractor
import com.stratum.domain.response.ResponseProfileMetadataRepository
import com.stratum.domain.response.MuscleVolumeLandmarkEstimateRepository
import com.stratum.domain.response.ResponseProfilePersistencePort
import com.stratum.domain.response.VolumeResponseObservationRepository
import com.stratum.core.data.seed.AssetLiftEmbeddingCatalog
import com.stratum.core.data.seed.AssetLiftEquipmentCatalog
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
            // Seed when no bundled catalog exists. Import may create custom lifts before seed
            // finishes; countAll() would then block seeding forever and BM25 would have nothing
            // to match against.
            if (liftDao.countSeedLifts() == 0) {
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

    @Provides
    fun provideImportJobDao(database: StratumDatabase): ImportJobDao = database.importJobDao()

    @Provides
    fun provideHistoricalSessionDao(database: StratumDatabase): HistoricalSessionDao =
        database.historicalSessionDao()

    @Provides
    fun provideLoggedSetDao(database: StratumDatabase): LoggedSetDao = database.loggedSetDao()

    @Provides
    fun provideLiftTrainingInsightsDao(database: StratumDatabase): LiftTrainingInsightsDao =
        database.liftTrainingInsightsDao()

    @Provides
    fun provideSupersetAffinityDao(database: StratumDatabase): SupersetAffinityDao =
        database.supersetAffinityDao()

    @Provides
    fun provideMuscleVolumeBaselineDao(database: StratumDatabase): MuscleVolumeBaselineDao =
        database.muscleVolumeBaselineDao()

    @Provides
    fun provideImportAnalysisMetadataDao(database: StratumDatabase): ImportAnalysisMetadataDao =
        database.importAnalysisMetadataDao()

    @Provides
    @Singleton
    fun provideBm25LiftNameResolver(): Bm25LiftNameResolver = Bm25LiftNameResolver()

    @Provides
    @Singleton
    fun provideLiftEmbeddingCatalog(
        @ApplicationContext context: Context,
    ): LiftEmbeddingCatalogPort = AssetLiftEmbeddingCatalog(context)

    @Provides
    @Singleton
    fun provideLiftNameResolver(
        bm25: Bm25LiftNameResolver,
        catalogEmbeddings: LiftEmbeddingCatalogPort,
        nameEmbedding: LiftNameEmbeddingPort,
    ): LiftNameResolverPort = HybridLiftNameResolver(bm25, catalogEmbeddings, nameEmbedding)

    @Provides
    @Singleton
    fun provideLiftEquipmentCatalog(
        @ApplicationContext context: Context,
    ): LiftEquipmentCatalogPort = AssetLiftEquipmentCatalog(context)
}

@Module
@InstallIn(SingletonComponent::class)
object SolverModule {
    @Provides
    @Singleton
    fun provideProgramSynthesisPort(): ProgramSynthesisPort =
        CpSatProgramSynthesizer()
}

@Module
@InstallIn(SingletonComponent::class)
object ProgramDatabaseModule {
    @Provides
    fun provideProgramDao(database: StratumDatabase): com.stratum.core.data.db.ProgramDao =
        database.programDao()

    @Provides
    fun provideWorkoutDao(database: StratumDatabase): com.stratum.core.data.db.WorkoutDao =
        database.workoutDao()

    @Provides
    fun provideWorkoutLiftDao(database: StratumDatabase): com.stratum.core.data.db.WorkoutLiftDao =
        database.workoutLiftDao()

    @Provides
    fun provideLiftSetDao(database: StratumDatabase): com.stratum.core.data.db.LiftSetDao =
        database.liftSetDao()
}

@Module
@InstallIn(SingletonComponent::class)
object ResponseDatabaseModule {
    @Provides
    fun provideResponseEpochDao(database: StratumDatabase): ResponseEpochDao =
        database.responseEpochDao()

    @Provides
    fun provideVolumeResponseObservationDao(database: StratumDatabase): VolumeResponseObservationDao =
        database.volumeResponseObservationDao()

    @Provides
    fun provideLiftEffectivenessObservationDao(database: StratumDatabase): LiftEffectivenessObservationDao =
        database.liftEffectivenessObservationDao()

    @Provides
    fun provideExperimentBlockDao(database: StratumDatabase): ExperimentBlockDao =
        database.experimentBlockDao()

    @Provides
    fun provideResponseProfileMetadataDao(database: StratumDatabase): ResponseProfileMetadataDao =
        database.responseProfileMetadataDao()

    @Provides
    fun provideMuscleWeeklyStimulusDao(database: StratumDatabase): MuscleWeeklyStimulusDao =
        database.muscleWeeklyStimulusDao()

    @Provides
    fun provideLiftWeeklyPerformanceDao(database: StratumDatabase): LiftWeeklyPerformanceDao =
        database.liftWeeklyPerformanceDao()

    @Provides
    fun provideMuscleWeeklyOutcomeIndexDao(database: StratumDatabase): MuscleWeeklyOutcomeIndexDao =
        database.muscleWeeklyOutcomeIndexDao()

    @Provides
    fun provideMuscleDoseResponsePointDao(database: StratumDatabase): MuscleDoseResponsePointDao =
        database.muscleDoseResponsePointDao()

    @Provides
    fun provideMuscleVolumeLandmarkEstimateDao(database: StratumDatabase): MuscleVolumeLandmarkEstimateDao =
        database.muscleVolumeLandmarkEstimateDao()

    @Provides
    fun provideMuscleResponseModelDiagnosticDao(database: StratumDatabase): MuscleResponseModelDiagnosticDao =
        database.muscleResponseModelDiagnosticDao()
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

    @Binds
    @Singleton
    abstract fun bindImportJobRepository(impl: RoomImportJobRepository): ImportJobRepository

    @Binds
    @Singleton
    abstract fun bindTrainingHistoryRepository(
        impl: RoomTrainingHistoryRepository,
    ): TrainingHistoryRepository

    @Binds
    @Singleton
    abstract fun bindLiftTrainingInsightsRepository(
        impl: RoomLiftTrainingInsightsRepository,
    ): LiftTrainingInsightsRepository

    @Binds
    @Singleton
    abstract fun bindSupersetAffinityRepository(
        impl: RoomSupersetAffinityRepository,
    ): SupersetAffinityRepository

    @Binds
    @Singleton
    abstract fun bindMuscleVolumeBaselineRepository(
        impl: RoomMuscleVolumeBaselineRepository,
    ): MuscleVolumeBaselineRepository

    @Binds
    @Singleton
    abstract fun bindImportAnalysisMetadataRepository(
        impl: RoomImportAnalysisMetadataRepository,
    ): ImportAnalysisMetadataRepository

    @Binds
    @Singleton
    abstract fun bindResponseEpochRepository(
        impl: RoomResponseEpochRepository,
    ): ResponseEpochRepository

    @Binds
    @Singleton
    abstract fun bindVolumeResponseObservationRepository(
        impl: RoomVolumeResponseObservationRepository,
    ): VolumeResponseObservationRepository

    @Binds
    @Singleton
    abstract fun bindLiftEffectivenessObservationRepository(
        impl: RoomLiftEffectivenessObservationRepository,
    ): LiftEffectivenessObservationRepository

    @Binds
    @Singleton
    abstract fun bindExperimentBlockRepository(
        impl: RoomExperimentBlockRepository,
    ): ExperimentBlockRepository

    @Binds
    @Singleton
    abstract fun bindResponseProfilePersistencePort(
        impl: RoomResponseProfilePersistenceRepository,
    ): ResponseProfilePersistencePort

    @Binds
    @Singleton
    abstract fun bindResponseProfileMetadataRepository(
        impl: RoomResponseProfileMetadataRepository,
    ): ResponseProfileMetadataRepository

    @Binds
    @Singleton
    abstract fun bindMuscleVolumeLandmarkEstimateRepository(
        impl: RoomMuscleVolumeLandmarkEstimateRepository,
    ): MuscleVolumeLandmarkEstimateRepository

    @Binds
    @Singleton
    abstract fun bindExperimentRegistry(
        impl: EmptyExperimentRegistry,
    ): ExperimentRegistry

    @Binds
    @Singleton
    abstract fun bindResponseObservationExtractor(
        impl: DefaultResponseObservationExtractor,
    ): ResponseObservationExtractor

    @Binds
    @Singleton
    abstract fun bindProgramRepository(
        impl: com.stratum.core.data.repository.RoomProgramRepository,
    ): com.stratum.domain.program.ProgramRepository

    @Binds
    @Singleton
    abstract fun bindProgramGenerationContextLoader(
        impl: com.stratum.domain.program.LoadProgramGenerationContextUseCase,
    ): com.stratum.domain.program.ProgramGenerationContextLoader
}
