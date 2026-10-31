package com.stratum.core.infrastructure.db

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class StratumDatabaseAutoMigration2To3Test {

    @Test
    @DisplayName("AutoMigration 2→3 adds pinned_note columns and lift_session_notes")
    fun migrate2To3() {
        val helper = MigrationTestUtils.newMigrationHelper()
        val dbName = "stratum-auto-2-3"
        helper.createDatabase(dbName, 2).close()
        helper.runMigrationsAndValidate(dbName, 3, true).use { migrated ->
            assertTrue(columnNames(migrated, "lifts").contains("pinned_note"))
            assertTrue(columnNames(migrated, "workout_lifts").contains("pinned_note"))
            val tables =
                migrated.query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='lift_session_notes'",
                ).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) add(cursor.getString(0))
                    }
                }
            assertTrue("lift_session_notes" in tables)
            val indexes =
                migrated.query("PRAGMA index_list(lift_session_notes)").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    val uniqueIndex = cursor.getColumnIndex("unique")
                    buildList {
                        while (cursor.moveToNext()) {
                            add(cursor.getString(nameIndex) to cursor.getInt(uniqueIndex))
                        }
                    }
                }
            assertTrue(indexes.any { (_, unique) -> unique == 1 })
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
