package com.stratum.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [OnboardingProgressEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class StratumDatabase : RoomDatabase() {
    abstract fun onboardingProgressDao(): OnboardingProgressDao

    companion object {
        const val DATABASE_NAME = "stratum.db"
    }
}
