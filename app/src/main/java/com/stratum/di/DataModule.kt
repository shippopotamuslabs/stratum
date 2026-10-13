package com.stratum.di

import android.content.Context
import androidx.room.Room
import com.stratum.core.data.db.EquipmentDao
import com.stratum.core.data.db.GymPreferenceDao
import com.stratum.core.data.db.IncrementTierDao
import com.stratum.core.data.db.LiftDao
import com.stratum.core.data.db.OnboardingProgressDao
import com.stratum.core.data.db.SpecializationPreferenceDao
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.data.db.withProductionMigrations
import com.stratum.domain.onboarding.UserTrainingProfileRepository
import com.stratum.core.data.db.UserProfileDao
import com.stratum.core.data.repository.RoomEquipmentRepository
import com.stratum.core.data.repository.RoomLiftRepository
import com.stratum.core.data.repository.RoomDeleteLiftPort
import com.stratum.core.data.repository.RoomMergeLiftsPort
import com.stratum.core.data.repository.RoomOnboardingRepository
import com.stratum.core.data.repository.RoomSpecializationPreferenceRepository
import com.stratum.core.data.repository.RoomUserTrainingProfileRepository
import com.stratum.core.data.seed.AssetLiftEmbeddingCatalog
import com.stratum.core.data.seed.AssetLiftEquipmentCatalog
import com.stratum.core.data.seed.EquipmentCatalogSeeder
import com.stratum.core.data.seed.LiftCatalogSeeder
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.lift.EquipmentRepository
import com.stratum.domain.lift.LiftRepository
import com.stratum.domain.lift.MergeLiftsPort
import com.stratum.domain.onboarding.OnboardingRepository
import com.stratum.domain.onboarding.SpecializationPreferenceRepository
import com.stratum.core.data.db.ImportAnalysisMetadataDao
import com.stratum.core.data.db.ImportJobDao
import com.stratum.core.data.db.ExperimentBlockDao
import com.stratum.core.data.db.LiftEffectivenessObservationDao
import com.stratum.core.data.db.LiftTrainingInsightsDao
import com.stratum.core.data.db.LiftWeeklyPerformanceDao
import com.stratum.core.data.db.MuscleDoseResponsePointDao
import com.stratum.core.data.db.MuscleResponseModelDiagnosticDao
import com.stratum.core.data.db.MuscleVolumeBaselineDao
import com.stratum.core.data.db.MuscleVolumeResponseSnapshotDao
import com.stratum.core.data.db.MuscleWeeklyOutcomeIndexDao
import com.stratum.core.data.db.MuscleWeeklyStimulusDao
import com.stratum.core.data.db.ResponseEpochDao
import com.stratum.core.data.db.ResponseProfileMetadataDao
import com.stratum.core.data.db.SupersetAffinityDao
import com.stratum.core.data.db.VolumeResponseObservationDao
import com.stratum.core.data.repository.RoomExperimentBlockRepository
import com.stratum.core.data.repository.RoomLiftEffectivenessObservationRepository
import com.stratum.core.data.repository.RoomLiftWeeklyPerformanceRepository
import com.stratum.core.data.work.WorkManagerResponseModelingScheduler
import com.stratum.core.data.repository.RoomResponseEpochRepository
import com.stratum.core.data.repository.RoomResponseProfileMetadataRepository
import com.stratum.core.data.repository.RoomResponseProfilePersistenceRepository
import com.stratum.core.data.repository.RoomVolumeResponseObservationRepository
import com.stratum.core.data.repository.RoomImportAnalysisMetadataRepository
import com.stratum.core.data.repository.RoomImportJobRepository
import com.stratum.core.data.repository.RoomProgramChangeEventRepository
import com.stratum.core.data.repository.RoomTrainingLogRepository
import com.stratum.core.data.repository.ProgressionHistoryRepository
import com.stratum.domain.training.ProgramChangeEventRepository
import com.stratum.domain.training.TrainingLogRepository
import com.stratum.domain.workout.ProgressionHistoryPort
import com.stratum.domain.workout.WorkoutOverlayStore
import com.stratum.core.data.repository.DataStoreCheckInPreferencesRepository
import com.stratum.core.data.repository.DataStoreDashboardPreferencesRepository
import com.stratum.core.data.repository.DataStoreEquipmentProposalPreferences
import com.stratum.core.data.repository.DataStoreProgramsUiPreferencesRepository
import com.stratum.core.data.repository.DataStoreRepRangeShiftProposalPreferences
import com.stratum.core.data.repository.DataStoreThemePreferencesRepository
import com.stratum.core.data.repository.DataStoreWeightDisplayPreferencesRepository
import com.stratum.core.data.repository.DataStoreWorkoutOverlayStore
import com.stratum.core.data.repository.RoomLiftTrainingInsightsRepository
import com.stratum.domain.checkin.CheckInPreferencesRepository
import com.stratum.domain.dashboard.DashboardPreferencesRepository
import com.stratum.domain.lift.EquipmentProposalPreferences
import com.stratum.domain.lift.WeightDisplayPreferencesRepository
import com.stratum.domain.program.ProgramsUiPreferencesRepository
import com.stratum.domain.theme.ThemePreferencesRepository
import com.stratum.domain.workout.RepRangeShiftProposalPreferences
import com.stratum.core.data.repository.RoomMuscleVolumeResponseSnapshotRepository
import com.stratum.core.data.repository.RoomMuscleWeeklyOutcomeIndexRepository
import com.stratum.core.data.repository.RoomMuscleWeeklyStimulusRepository
import com.stratum.core.data.repository.RoomMuscleVolumeBaselineRepository
import com.stratum.core.data.repository.RoomSupersetAffinityRepository
import com.stratum.core.data.repository.DataStoreHealthConnectPreferencesRepository
import com.stratum.core.data.repository.HealthConnectSyncStatusRepository
import com.stratum.core.data.healthconnect.HealthConnectManager
import com.stratum.core.data.healthconnect.HealthConnectMetricsReader
import com.stratum.core.data.work.WorkManagerHealthConnectScheduler
import com.stratum.domain.healthconnect.HealthConnectMetricsPort
import com.stratum.domain.healthconnect.HealthConnectPreferencesPort
import com.stratum.domain.healthconnect.HealthConnectSchedulerPort
import com.stratum.domain.healthconnect.HealthConnectSyncPort
import com.stratum.domain.healthconnect.HealthConnectSyncStatusPort
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
import com.stratum.domain.response.LiftWeeklyPerformanceRepository
import com.stratum.domain.response.MuscleWeeklyOutcomeIndexRepository
import com.stratum.domain.response.MuscleWeeklyStimulusRepository
import com.stratum.domain.response.ResponseEpochRepository
import com.stratum.domain.response.ResponseModelingSchedulerPort
import com.stratum.domain.response.ResponseObservationExtractor
import com.stratum.domain.response.ResponseProfileMetadataRepository
import com.stratum.domain.response.MuscleVolumeResponseSnapshotRepository
import com.stratum.domain.response.ResponseProfilePersistencePort
import com.stratum.domain.response.VolumeResponseObservationRepository
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
import kotlin.coroutines.cancellation.CancellationException

@Module
@InstallIn(SingletonComponent::class)
@Suppress("TooManyFunctions")
object DatabaseModule {

    // Application-scoped scope for one-shot catalog seeding. Detached from any ViewModel so the
    // seed completes even if the UI that triggered first-run leaves.
    private val seedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun provideStratumDatabase(
        @ApplicationContext context: Context,
        seedGate: com.stratum.core.data.sync.LiftCatalogSeedGate,
        loggingPort: LoggingPort,
    ): StratumDatabase {
        // Manual migrations (v4→v5, v7→v8, v8→v9, v9→v10) are registered centrally via
        // withProductionMigrations so DatabaseMigrationChainTest guards the same list.
        // Destructive fallback remains for unrecoverable paths (missing migration, identity skew).
        val database = Room.databaseBuilder(context, StratumDatabase::class.java, StratumDatabase.DATABASE_NAME)
            .withProductionMigrations()
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
        // Never call DAOs from RoomDatabase.Callback.onCreate — Room holds the DB lock during
        // init and DAO work there deadlocks (white screen on startup). Seed asynchronously once
        // the database is fully open; idempotent upserts make re-runs safe.
        // Always complete [LiftCatalogSeedGate] so cloud hydrate can wait for catalog readiness.
        seedScope.launch {
            try {
                val liftDao = database.liftDao()
                // Seed when no bundled catalog exists. Import may create custom lifts before seed
                // finishes; countAll() would then block seeding forever and BM25 would have nothing
                // to match against.
                val equipmentDao = database.equipmentDao()
                if (equipmentDao.countSeedEquipment() == 0) {
                    EquipmentCatalogSeeder(
                        equipmentDao = equipmentDao,
                        incrementTierDao = database.incrementTierDao(),
                        loggingPort = loggingPort,
                    ).seed(context)
                }
                if (liftDao.countSeedLifts() == 0) {
                    val tags = AssetLiftEquipmentCatalog(context, loggingPort = loggingPort).all()
                    LiftCatalogSeeder(
                        liftDao = liftDao,
                        equipmentTagsByLiftId = tags,
                        loggingPort = loggingPort,
                    ).seed(context)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                loggingPort.error("CatalogSeed", "Catalog seed failed: ${error.message}", error)
            } finally {
                seedGate.complete()
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
    fun provideEquipmentDao(database: StratumDatabase): EquipmentDao = database.equipmentDao()

    @Provides
    fun provideLiftDao(database: StratumDatabase): LiftDao = database.liftDao()

    @Provides
    @Singleton
    fun provideEquipmentCatalogSeeder(
        equipmentDao: EquipmentDao,
        incrementTierDao: IncrementTierDao,
        loggingPort: LoggingPort,
    ): EquipmentCatalogSeeder = EquipmentCatalogSeeder(equipmentDao, incrementTierDao, loggingPort)

    @Provides
    @Singleton
    fun provideLiftCatalogSeeder(
        liftDao: LiftDao,
        @ApplicationContext context: Context,
        loggingPort: LoggingPort,
    ): LiftCatalogSeeder =
        LiftCatalogSeeder(
            liftDao = liftDao,
            equipmentTagsByLiftId = AssetLiftEquipmentCatalog(context, loggingPort = loggingPort).all(),
            loggingPort = loggingPort,
        )

    @Provides
    fun provideIncrementTierDao(database: StratumDatabase): IncrementTierDao =
        database.incrementTierDao()

    @Provides
    fun provideGymPreferenceDao(database: StratumDatabase): GymPreferenceDao =
        database.gymPreferenceDao()

    @Provides
    fun provideImportJobDao(database: StratumDatabase): ImportJobDao = database.importJobDao()

    @Provides
    fun provideTrainingSessionDao(database: StratumDatabase): com.stratum.core.data.db.TrainingSessionDao =
        database.trainingSessionDao()

    @Provides
    fun provideTrainingLoggedSetDao(database: StratumDatabase): com.stratum.core.data.db.TrainingLoggedSetDao =
        database.trainingLoggedSetDao()

    @Provides
    fun provideProgramChangeEventDao(database: StratumDatabase): com.stratum.core.data.db.ProgramChangeEventDao =
        database.programChangeEventDao()

    @Provides
    fun providePendingCloudDeleteDao(database: StratumDatabase): com.stratum.core.data.db.PendingCloudDeleteDao =
        database.pendingCloudDeleteDao()

    @Provides
    fun provideLiftSessionNoteDao(database: StratumDatabase): com.stratum.core.data.db.LiftSessionNoteDao =
        database.liftSessionNoteDao()

    @Provides
    fun provideLiftNoteDao(database: StratumDatabase): com.stratum.core.data.db.LiftNoteDao =
        database.liftNoteDao()

    @Provides
    fun provideWorkoutLiftNoteDao(database: StratumDatabase): com.stratum.core.data.db.WorkoutLiftNoteDao =
        database.workoutLiftNoteDao()

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
        loggingPort: LoggingPort,
    ): LiftEmbeddingCatalogPort = AssetLiftEmbeddingCatalog(context, loggingPort = loggingPort)

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
        loggingPort: LoggingPort,
    ): LiftEquipmentCatalogPort = AssetLiftEquipmentCatalog(context, loggingPort = loggingPort)
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
    fun provideMuscleVolumeResponseSnapshotDao(database: StratumDatabase): MuscleVolumeResponseSnapshotDao =
        database.muscleVolumeResponseSnapshotDao()

    @Provides
    fun provideMuscleResponseModelDiagnosticDao(database: StratumDatabase): MuscleResponseModelDiagnosticDao =
        database.muscleResponseModelDiagnosticDao()
}

@Module
@InstallIn(SingletonComponent::class)
@Suppress("TooManyFunctions")
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
    abstract fun bindLiftNotesRepository(
        impl: com.stratum.core.data.repository.RoomLiftNotesRepository,
    ): com.stratum.domain.lift.LiftNotesRepository

    @Binds
    @Singleton
    abstract fun bindMergeLiftsPort(impl: RoomMergeLiftsPort): MergeLiftsPort

    @Binds
    @Singleton
    abstract fun bindDeleteLiftPort(impl: RoomDeleteLiftPort): com.stratum.domain.lift.DeleteLiftPort

    @Binds
    @Singleton
    abstract fun bindEquipmentRepository(impl: RoomEquipmentRepository): EquipmentRepository

    @Binds
    @Singleton
    abstract fun bindImportJobRepository(impl: RoomImportJobRepository): ImportJobRepository

    @Binds
    @Singleton
    abstract fun bindTrainingLogRepository(
        impl: RoomTrainingLogRepository,
    ): TrainingLogRepository

    @Binds
    @Singleton
    abstract fun bindProgressionHistoryPort(
        impl: ProgressionHistoryRepository,
    ): ProgressionHistoryPort

    @Binds
    @Singleton
    abstract fun bindProgramChangeEventRepository(
        impl: RoomProgramChangeEventRepository,
    ): ProgramChangeEventRepository

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
    abstract fun bindMuscleVolumeResponseSnapshotRepository(
        impl: RoomMuscleVolumeResponseSnapshotRepository,
    ): MuscleVolumeResponseSnapshotRepository

    @Binds
    @Singleton
    abstract fun bindMuscleWeeklyStimulusRepository(
        impl: RoomMuscleWeeklyStimulusRepository,
    ): MuscleWeeklyStimulusRepository

    @Binds
    @Singleton
    abstract fun bindLiftWeeklyPerformanceRepository(
        impl: RoomLiftWeeklyPerformanceRepository,
    ): LiftWeeklyPerformanceRepository

    @Binds
    @Singleton
    abstract fun bindMuscleWeeklyOutcomeIndexRepository(
        impl: RoomMuscleWeeklyOutcomeIndexRepository,
    ): MuscleWeeklyOutcomeIndexRepository

    @Binds
    @Singleton
    abstract fun bindResponseModelingSchedulerPort(
        impl: WorkManagerResponseModelingScheduler,
    ): ResponseModelingSchedulerPort

    @Binds
    abstract fun bindForceRefreshDerivedTrainingData(
        impl: com.stratum.domain.training.ForceRefreshDerivedTrainingDataUseCase,
    ): com.stratum.domain.training.ForceRefreshDerivedTrainingData

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

    @Binds
    @Singleton
    abstract fun bindWorkoutOverlayStore(impl: DataStoreWorkoutOverlayStore): WorkoutOverlayStore

    @Binds
    @Singleton
    abstract fun bindThemePreferencesRepository(
        impl: DataStoreThemePreferencesRepository,
    ): ThemePreferencesRepository

    @Binds
    @Singleton
    abstract fun bindWeightDisplayPreferencesRepository(
        impl: DataStoreWeightDisplayPreferencesRepository,
    ): WeightDisplayPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindDashboardPreferencesRepository(
        impl: DataStoreDashboardPreferencesRepository,
    ): DashboardPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindProgramsUiPreferencesRepository(
        impl: DataStoreProgramsUiPreferencesRepository,
    ): ProgramsUiPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindCheckInPreferencesRepository(
        impl: DataStoreCheckInPreferencesRepository,
    ): CheckInPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindEquipmentProposalPreferences(
        impl: DataStoreEquipmentProposalPreferences,
    ): EquipmentProposalPreferences

    @Binds
    @Singleton
    abstract fun bindRepRangeShiftProposalPreferences(
        impl: DataStoreRepRangeShiftProposalPreferences,
    ): RepRangeShiftProposalPreferences

    @Binds
    @Singleton
    abstract fun bindHealthConnectPreferencesPort(
        impl: DataStoreHealthConnectPreferencesRepository,
    ): HealthConnectPreferencesPort

    @Binds
    @Singleton
    abstract fun bindHealthConnectSyncPort(
        impl: HealthConnectManager,
    ): HealthConnectSyncPort

    @Binds
    @Singleton
    abstract fun bindHealthConnectMetricsPort(
        impl: HealthConnectMetricsReader,
    ): HealthConnectMetricsPort

    @Binds
    @Singleton
    abstract fun bindHealthConnectSchedulerPort(
        impl: WorkManagerHealthConnectScheduler,
    ): HealthConnectSchedulerPort

    @Binds
    @Singleton
    abstract fun bindHealthConnectSyncStatusPort(
        impl: HealthConnectSyncStatusRepository,
    ): HealthConnectSyncStatusPort
}
