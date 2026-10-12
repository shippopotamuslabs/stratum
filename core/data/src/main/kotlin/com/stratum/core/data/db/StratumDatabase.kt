package com.stratum.core.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Current Room schema version — the single source referenced by the `@Database`
 * annotation below and by migration tests. Bump this (and only this) with every
 * entity change.
 */
const val STRATUM_SCHEMA_VERSION = 13

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
        LiftNoteEntity::class,
        WorkoutLiftNoteEntity::class,
    ],
    version = STRATUM_SCHEMA_VERSION,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        // 4 → 5 is a manual migration (MIGRATION_4_5: backfill + index rebuild).
        // 5 → 6 adds the nullable JSON event-payload column on program_change_events
        // (payload_json); pure column add, no backfill. Pre-v6 rows keep null and read
        // through the frozen flat columns; new writes are JSON-only.
        AutoMigration(from = 5, to = 6),
        // 6 → 7 adds nullable adder-stop columns (micro_options_json) on equipment
        // and lifts; pure column adds, no backfill. Pre-v7 rows keep null: no adder
        // on equipment, inherit-equipment on lifts.
        AutoMigration(from = 6, to = 7),
        // 10 → 11 adds the tombstone flag (isDeleted) on equipment; pure column add
        // defaulting to false. Pre-v11 rows are all live (no delete path existed).
        AutoMigration(from = 10, to = 11),
        // 11 → 12 adds the nullable template-assignment column (equipment_id) on
        // workout_lifts; pure column add. Null means "inherit the lift's equipment".
        AutoMigration(from = 11, to = 12),
        // 12 → 13 is a manual migration (MIGRATION_12_13: PR unique index rebuild).
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

    abstract fun liftNoteDao(): LiftNoteDao

    abstract fun workoutLiftNoteDao(): WorkoutLiftNoteDao

    companion object {
        const val DATABASE_NAME = "stratum.db"
    }
}
