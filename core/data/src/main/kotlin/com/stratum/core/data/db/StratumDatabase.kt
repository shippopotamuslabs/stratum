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

    companion object {
        const val DATABASE_NAME = "stratum.db"
    }
}
