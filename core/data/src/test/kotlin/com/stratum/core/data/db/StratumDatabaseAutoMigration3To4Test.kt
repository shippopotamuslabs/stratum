package com.stratum.core.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class StratumDatabaseAutoMigration3To4Test {

    @Test
    @DisplayName("AutoMigration 3→4 adds partialReps to logged_sets")
    fun migrate3To4() {
        val helper =
            MigrationTestHelper(
                InstrumentationRegistry.getInstrumentation(),
                StratumDatabase::class.java,
                emptyList(),
                FrameworkSQLiteOpenHelperFactory(),
            )
        val dbName = "stratum-auto-3-4"
        helper.createDatabase(dbName, 3).close()
        helper.runMigrationsAndValidate(dbName, 4, true).use { migrated ->
            assertTrue(columnNames(migrated, "logged_sets").contains("partialReps"))
        }
    }

    private fun columnNames(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        table: String,
    ): Set<String> =
        db.query("PRAGMA table_info($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }
}
