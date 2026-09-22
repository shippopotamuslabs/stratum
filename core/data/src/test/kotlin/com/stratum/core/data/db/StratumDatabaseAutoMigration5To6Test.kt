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
class StratumDatabaseAutoMigration5To6Test {

    @Test
    @DisplayName("AutoMigration 5→6 adds SchemeChanged payload columns to program_change_events")
    fun migrate5To6() {
        val helper =
            MigrationTestHelper(
                InstrumentationRegistry.getInstrumentation(),
                StratumDatabase::class.java,
                emptyList(),
                FrameworkSQLiteOpenHelperFactory(),
            )
        val dbName = "stratum-auto-5-6"
        helper.createDatabase(dbName, 5).close()
        helper.runMigrationsAndValidate(dbName, 6, true).use { migrated ->
            val columns = columnNames(migrated, "program_change_events")
            assertTrue(columns.contains("scheme_lift_id"))
            assertTrue(columns.contains("previous_scheme"))
            assertTrue(columns.contains("current_scheme"))
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
