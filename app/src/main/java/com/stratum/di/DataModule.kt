package com.stratum.di

import android.content.Context
import androidx.room.Room
import com.stratum.core.infrastructure.db.EquipmentDao
import com.stratum.core.infrastructure.db.GymPreferenceDao
import com.stratum.core.infrastructure.db.IncrementTierDao
import com.stratum.core.infrastructure.db.LiftDao
import com.stratum.core.infrastructure.db.OnboardingProgressDao
import com.stratum.core.infrastructure.db.SpecializationPreferenceDao
import com.stratum.core.infrastructure.db.StratumDatabase
import com.stratum.core.infrastructure.db.withProductionMigrations
import com.stratum.application.onboarding.UserTrainingProfileRepository
import com.stratum.core.infrastructure.db.UserProfileDao
import com.stratum.core.infrastructure.repository.RoomEquipmentRepository
import com.stratum.core.infrastructure.repository.RoomLiftRepository
import com.stratum.core.infrastructure.repository.RoomDeleteLiftPort
import com.stratum.core.infrastructure.repository.RoomMergeLiftsPort
import com.stratum.core.infrastructure.repository.RoomOnboardingRepository
import com.stratum.core.infrastructure.repository.RoomSpecializationPreferenceRepository
import com.stratum.core.infrastructure.repository.RoomUserTrainingProfileRepository
import com.stratum.core.infrastructure.seed.AssetLiftEmbeddingCatalog
import com.stratum.core.infrastructure.seed.AssetLiftEquipmentCatalog
import com.stratum.core.infrastructure.seed.EquipmentCatalogSeeder
import com.stratum.core.infrastructure.seed.LiftCatalogSeeder
import com.stratum.domain.logging.LoggingPort
import com.stratum.application.lift.EquipmentRepository
import com.stratum.application.lift.LiftRepository
import com.stratum.application.lift.MergeLiftsPort
import com.stratum.application.onboarding.OnboardingRepository
import com.stratum.application.onboarding.SpecializationPreferenceRepository
import com.stratum.core.infrastructure.db.ImportAnalysisMetadataDao
import com.stratum.core.infrastructure.db.ImportJobDao
import com.stratum.core.infrastructure.db.ExperimentBlockDao
import com.stratum.core.infrastructure.db.LiftEffectivenessObservationDao
import com.stratum.core.infrastructure.db.LiftTrainingInsightsDao
import com.stratum.core.infrastructure.db.LiftWeeklyPerformanceDao
import com.stratum.core.infrastructure.db.MuscleDoseResponsePointDao
import com.stratum.core.infrastructure.db.MuscleResponseModelDiagnosticDao
import com.stratum.core.infrastructure.db.MuscleVolumeBaselineDao
import com.stratum.core.infrastructure.db.MuscleVolumeResponseSnapshotDao
import com.stratum.core.infrastructure.db.MuscleWeeklyOutcomeIndexDao
import com.stratum.core.infrastructure.db.MuscleWeeklyStimulusDao
import com.stratum.core.infrastructure.db.ResponseEpochDao
import com.stratum.core.infrastructure.db.ResponseProfileMetadataDao
import com.stratum.core.infrastructure.db.SupersetAffinityDao
import com.stratum.core.infrastructure.db.VolumeResponseObservationDao
import com.stratum.core.infrastructure.repository.RoomExperimentBlockRepository
import com.stratum.core.infrastructure.repository.RoomLiftEffectivenessObservationRepository
import com.stratum.core.infrastructure.repository.RoomLiftWeeklyPerformanceRepository
import com.stratum.core.infrastructure.work.WorkManagerResponseModelingScheduler
import com.stratum.core.infrastructure.repository.RoomResponseEpochRepository
import com.stratum.core.infrastructure.repository.RoomResponseProfileMetadataRepository
import com.stratum.core.infrastructure.repository.RoomResponseProfilePersistenceRepository
import com.stratum.core.infrastructure.repository.RoomVolumeResponseObservationRepository
import com.stratum.core.infrastructure.repository.RoomImportAnalysisMetadataRepository
import com.stratum.core.infrastructure.repository.RoomImportJobRepository
import com.stratum.core.infrastructure.repository.RoomProgramChangeEventRepository
import com.stratum.core.infrastructure.repository.RoomTrainingLogRepository
import com.stratum.core.infrastructure.repository.ProgressionHistoryRepository
import com.stratum.application.training.ProgramChangeEventRepository
import com.stratum.application.training.TrainingLogRepository
import com.stratum.application.workout.ProgressionHistoryPort
import com.stratum.application.workout.WorkoutOverlayStore
import com.stratum.core.infrastructure.repository.DataStoreCheckInPreferencesRepository
import com.stratum.core.infrastructure.repository.DataStoreDashboardPreferencesRepository
import com.stratum.core.infrastructure.repository.DataStoreEquipmentProposalPreferences
import com.stratum.core.infrastructure.repository.DataStoreProgramsUiPreferencesRepository
import com.stratum.core.infrastructure.repository.DataStoreRepRangeShiftProposalPreferences
import com.stratum.core.infrastructure.repository.DataStoreThemePreferencesRepository
import com.stratum.core.infrastructure.repository.DataStoreWeightDisplayPreferencesRepository
import com.stratum.core.infrastructure.repository.DataStoreWorkoutOverlayStore
import com.stratum.core.infrastructure.repository.RoomLiftTrainingInsightsRepository
import com.stratum.application.checkin.CheckInPreferencesRepository
import com.stratum.application.dashboard.DashboardPreferencesRepository
import com.stratum.application.lift.EquipmentProposalPreferences
import com.stratum.application.lift.WeightDisplayPreferencesRepository
import com.stratum.application.program.ProgramsUiPreferencesRepository
import com.stratum.application.theme.ThemePreferencesRepository
import com.stratum.application.workout.RepRangeShiftProposalPreferences
import com.stratum.core.infrastructure.repository.RoomMuscleVolumeResponseSnapshotRepository
import com.stratum.core.infrastructure.repository.RoomMuscleWeeklyOutcomeIndexRepository
import com.stratum.core.infrastructure.repository.RoomMuscleWeeklyStimulusRepository
import com.stratum.core.infrastructure.repository.RoomMuscleVolumeBaselineRepository
import com.stratum.core.infrastructure.repository.RoomSupersetAffinityRepository
import com.stratum.core.infrastructure.repository.DataStoreHealthConnectPreferencesRepository
import com.stratum.core.infrastructure.repository.HealthConnectSyncStatusRepository
import com.stratum.core.infrastructure.healthconnect.HealthConnectManager
import com.stratum.core.infrastructure.healthconnect.HealthConnectMetricsReader
import com.stratum.core.infrastructure.work.WorkManagerHealthConnectScheduler
import com.stratum.application.healthconnect.HealthConnectMetricsPort
import com.stratum.application.healthconnect.HealthConnectPreferencesPort
import com.stratum.application.healthconnect.HealthConnectSchedulerPort
import com.stratum.application.healthconnect.HealthConnectSyncPort
import com.stratum.application.healthconnect.HealthConnectSyncStatusPort
import com.stratum.application.importing.Bm25LiftNameResolver
import com.stratum.application.importing.HybridLiftNameResolver
import com.stratum.application.importing.ImportAnalysisMetadataRepository
import com.stratum.application.importing.ImportJobRepository
import com.stratum.application.importing.LiftEmbeddingCatalogPort
import com.stratum.application.importing.LiftEquipmentCatalogPort
import com.stratum.application.importing.LiftNameEmbeddingPort
import com.stratum.application.importing.LiftNameResolverPort
import com.stratum.application.importing.LiftTrainingInsightsRepository
import com.stratum.application.importing.MuscleVolumeBaselineRepository
import com.stratum.application.importing.SupersetAffinityRepository
import com.stratum.domain.response.DefaultResponseObservationExtractor
import com.stratum.application.response.EmptyExperimentRegistry
import com.stratum.application.response.ExperimentBlockRepository
import com.stratum.application.response.ExperimentRegistry
import com.stratum.application.response.LiftEffectivenessObservationRepository
import com.stratum.application.response.LiftWeeklyPerformanceRepository
import com.stratum.application.response.MuscleWeeklyOutcomeIndexRepository
import com.stratum.application.response.MuscleWeeklyStimulusRepository
import com.stratum.application.response.ResponseEpochRepository
import com.stratum.application.response.ResponseModelingSchedulerPort
import com.stratum.domain.response.ResponseObservationExtractor
import com.stratum.application.response.ResponseProfileMetadataRepository
import com.stratum.application.response.MuscleVolumeResponseSnapshotRepository
import com.stratum.application.response.ResponseProfilePersistencePort
import com.stratum.application.response.VolumeResponseObservationRepository
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
        seedGate: com.stratum.core.infrastructure.sync.LiftCatalogSeedGate,
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
    fun provideTrainingSessionDao(database: StratumDatabase): com.stratum.core.infrastructure.db.TrainingSessionDao =
        database.trainingSessionDao()

    @Provides
    fun provideTrainingLoggedSetDao(database: StratumDatabase): com.stratum.core.infrastructure.db.TrainingLoggedSetDao =
        database.trainingLoggedSetDao()

    @Provides
    fun provideProgramChangeEventDao(database: StratumDatabase): com.stratum.core.infrastructure.db.ProgramChangeEventDao =
        database.programChangeEventDao()

    @Provides
    fun providePendingCloudDeleteDao(database: StratumDatabase): com.stratum.core.infrastructure.db.PendingCloudDeleteDao =
        database.pendingCloudDeleteDao()

    @Provides
    fun provideLiftSessionNoteDao(database: StratumDatabase): com.stratum.core.infrastructure.db.LiftSessionNoteDao =
        database.liftSessionNoteDao()

    @Provides
    fun provideLiftNoteDao(database: StratumDatabase): com.stratum.core.infrastructure.db.LiftNoteDao =
        database.liftNoteDao()

    @Provides
    fun provideWorkoutLiftNoteDao(database: StratumDatabase): com.stratum.core.infrastructure.db.WorkoutLiftNoteDao =
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
    fun provideProgramDao(database: StratumDatabase): com.stratum.core.infrastructure.db.ProgramDao =
        database.programDao()

    @Provides
    fun provideWorkoutDao(database: StratumDatabase): com.stratum.core.infrastructure.db.WorkoutDao =
        database.workoutDao()

    @Provides
    fun provideWorkoutLiftDao(database: StratumDatabase): com.stratum.core.infrastructure.db.WorkoutLiftDao =
        database.workoutLiftDao()

    @Provides
    fun provideLiftSetDao(database: StratumDatabase): com.stratum.core.infrastructure.db.LiftSetDao =
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
        impl: com.stratum.core.infrastructure.repository.RoomLiftNotesRepository,
    ): com.stratum.application.lift.LiftNotesRepository

    @Binds
    @Singleton
    abstract fun bindMergeLiftsPort(impl: RoomMergeLiftsPort): MergeLiftsPort

    @Binds
    @Singleton
    abstract fun bindDeleteLiftPort(impl: RoomDeleteLiftPort): com.stratum.application.lift.DeleteLiftPort

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
        impl: com.stratum.application.training.ForceRefreshDerivedTrainingDataUseCase,
    ): com.stratum.application.training.ForceRefreshDerivedTrainingData

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
        impl: com.stratum.core.infrastructure.repository.RoomProgramRepository,
    ): com.stratum.application.program.ProgramRepository

    @Binds
    @Singleton
    abstract fun bindProgramGenerationContextLoader(
        impl: com.stratum.application.program.LoadProgramGenerationContextUseCase,
    ): com.stratum.application.program.ProgramGenerationContextLoader

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
