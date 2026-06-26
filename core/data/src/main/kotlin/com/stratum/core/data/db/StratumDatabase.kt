package com.stratum.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        OnboardingProgressEntity::class,
        UserProfileEntity::class,
        SpecializationPreferenceEntity::class,
        LiftEntity::class,
        IncrementTierEntity::class,
        GymPreferenceEntity::class,
        ImportJobEntity::class,
        HistoricalSessionEntity::class,
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
        MuscleVolumeLandmarkEstimateEntity::class,
        MuscleResponseModelDiagnosticEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(WeekdaySetConverter::class, EnumConverters::class)
abstract class StratumDatabase : RoomDatabase() {
    abstract fun onboardingProgressDao(): OnboardingProgressDao

    abstract fun userProfileDao(): UserProfileDao

    abstract fun specializationPreferenceDao(): SpecializationPreferenceDao

    abstract fun liftDao(): LiftDao

    abstract fun incrementTierDao(): IncrementTierDao

    abstract fun gymPreferenceDao(): GymPreferenceDao

    abstract fun importJobDao(): ImportJobDao

    abstract fun historicalSessionDao(): HistoricalSessionDao

    abstract fun loggedSetDao(): LoggedSetDao

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

    abstract fun muscleVolumeLandmarkEstimateDao(): MuscleVolumeLandmarkEstimateDao

    abstract fun muscleResponseModelDiagnosticDao(): MuscleResponseModelDiagnosticDao

    companion object {
        const val DATABASE_NAME = "stratum.db"
    }
}
