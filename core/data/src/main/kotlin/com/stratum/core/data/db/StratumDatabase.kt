package com.stratum.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        OnboardingProgressEntity::class,
        UserProfileEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(WeekdaySetConverter::class)
abstract class StratumDatabase : RoomDatabase() {
    abstract fun onboardingProgressDao(): OnboardingProgressDao

    abstract fun userProfileDao(): UserProfileDao

    companion object {
        const val DATABASE_NAME = "stratum.db"
    }
}
