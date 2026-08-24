package com.stratum.core.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        OnboardingProgressEntity::class,
        UserProfileEntity::class,
        SpecializationPreferenceEntity::class,
        EquipmentEntity::class,
        LiftEntity::class,
        IncrementTierEntity::class,
        GymPreferenceEntity::class,
        ImportJobEntity::class,
        LoggedSetEntity::class,
        LiftTrainingInsightsEntity::class,
        SupersetAffinityEntity::class,
        MuscleVolumeBaselineEntity::class,
        ImportAnalysisMetadataEntity::class,
        ResponseEpochEntity::class,
        VolumeResponseObservationEntity::class,
        LiftEffectivenessObservationEntity::class,
        ExperimentBlockEntity::class,
        ResponseProfileMetadataEntity::class,
        MuscleWeeklyStimulusEntity::class,
        LiftWeeklyPerformanceEntity::class,
        MuscleWeeklyOutcomeIndexEntity::class,
        MuscleDoseResponsePointEntity::class,
        MuscleVolumeResponseSnapshotEntity::class,
        MuscleResponseModelDiagnosticEntity::class,
        ProgramEntity::class,
        WorkoutEntity::class,
        WorkoutLiftEntity::class,
        LiftSetEntity::class,
        TrainingSessionEntity::class,
        ProgramChangeEventEntity::class,
        PendingCloudDeleteEntity::class,
        LiftSessionNoteEntity::class,
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
    ],
)
@TypeConverters(WeekdaySetConverter::class, IntSetConverter::class, EnumConverters::class)
@Suppress("TooManyFunctions")
abstract class StratumDatabase : RoomDatabase() {
    abstract fun onboardingProgressDao(): OnboardingProgressDao

    abstract fun userProfileDao(): UserProfileDao

    abstract fun specializationPreferenceDao(): SpecializationPreferenceDao

    abstract fun equipmentDao(): EquipmentDao

    abstract fun liftDao(): LiftDao

    abstract fun incrementTierDao(): IncrementTierDao

    abstract fun gymPreferenceDao(): GymPreferenceDao

    abstract fun importJobDao(): ImportJobDao

    abstract fun liftTrainingInsightsDao(): LiftTrainingInsightsDao

    abstract fun supersetAffinityDao(): SupersetAffinityDao

    abstract fun muscleVolumeBaselineDao(): MuscleVolumeBaselineDao

    abstract fun importAnalysisMetadataDao(): ImportAnalysisMetadataDao

    abstract fun responseEpochDao(): ResponseEpochDao

    abstract fun volumeResponseObservationDao(): VolumeResponseObservationDao

    abstract fun liftEffectivenessObservationDao(): LiftEffectivenessObservationDao

    abstract fun experimentBlockDao(): ExperimentBlockDao

    abstract fun responseProfileMetadataDao(): ResponseProfileMetadataDao

    abstract fun muscleWeeklyStimulusDao(): MuscleWeeklyStimulusDao

    abstract fun liftWeeklyPerformanceDao(): LiftWeeklyPerformanceDao

    abstract fun muscleWeeklyOutcomeIndexDao(): MuscleWeeklyOutcomeIndexDao

    abstract fun muscleDoseResponsePointDao(): MuscleDoseResponsePointDao

    abstract fun muscleVolumeResponseSnapshotDao(): MuscleVolumeResponseSnapshotDao

    abstract fun muscleResponseModelDiagnosticDao(): MuscleResponseModelDiagnosticDao

    abstract fun programDao(): ProgramDao

    abstract fun workoutDao(): WorkoutDao

    abstract fun workoutLiftDao(): WorkoutLiftDao

    abstract fun liftSetDao(): LiftSetDao

    abstract fun trainingSessionDao(): TrainingSessionDao

    abstract fun trainingLoggedSetDao(): TrainingLoggedSetDao

    abstract fun programChangeEventDao(): ProgramChangeEventDao

    abstract fun pendingCloudDeleteDao(): PendingCloudDeleteDao

    abstract fun liftSessionNoteDao(): LiftSessionNoteDao

    companion object {
        const val DATABASE_NAME = "stratum.db"
    }
}
