package com.stratum.core.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.stratum.core.data.sync.SyncFlags
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * 6 → 7 adds nullable adder-stop columns (`micro_options_json`) on `equipment` and
 * `lifts`. Pure column adds: pre-v7 rows keep null (no adder on equipment,
 * inherit-equipment on lifts) and the auto-migration must validate.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class StratumDatabaseAutoMigration6To7Test {

    @Test
    @DisplayName("Given v6 rows when auto-migrated to 7 then micro columns exist and stay null")
    fun microColumnsAddedAsNull() {
        // Helper per test (not a field): instrumentation registers after construction,
        // mirroring StratumDatabaseAutoMigration5To6Test.
        val helper =
            MigrationTestHelper(
                InstrumentationRegistry.getInstrumentation(),
                StratumDatabase::class.java,
            )
        helper.createDatabase(TEST_DB, 6).use { db ->
            db.execSQL(
                """
                INSERT INTO equipment (id, name, isCustom, updated_at_epoch_millis, pending_sync)
                VALUES ('pin-loaded-machine', 'Pin-Loaded Machine', 0, 1, ${SyncFlags.CLEAN})
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO lifts (id, name, movementPatternsBitmask, primaryMusclesBitmask,
                    secondaryMusclesBitmask, presetTags, equipmentId, isCustom, enabled,
                    description, imageUrisCsv, muscleLengthBias, mainstream, fatigueProfile,
                    loadType, defaultRestSeconds, pinned_note, updated_at_epoch_millis, pending_sync)
                VALUES ('lat-pulldown', 'Lat pulldown', 0, 0, 0, '', 'pin-loaded-machine',
                    0, 1, NULL, NULL, 'MidRange', 0, 'Low', 'Weighted', NULL, NULL, 1, ${SyncFlags.CLEAN})
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 7, true).use { db ->
            assertHasColumn(db, "equipment", "micro_options_json")
            assertHasColumn(db, "lifts", "micro_options_json")
            db.query("SELECT micro_options_json FROM equipment").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
            }
            db.query("SELECT micro_options_json FROM lifts").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
            }
        }
    }

    @Test
    @DisplayName("Given an empty v6 database when auto-migrated to 7 then columns exist")
    fun emptyDatabaseMigrates() {
        val helper =
            MigrationTestHelper(
                InstrumentationRegistry.getInstrumentation(),
                StratumDatabase::class.java,
            )
        helper.createDatabase(TEST_DB_EMPTY, 6).close()
        helper.runMigrationsAndValidate(TEST_DB_EMPTY, 7, true).use { db ->
            assertHasColumn(db, "equipment", "micro_options_json")
            assertHasColumn(db, "lifts", "micro_options_json")
        }
    }

    private fun assertHasColumn(db: SupportSQLiteDatabase, table: String, column: String) {
        db.query("PRAGMA table_info($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == column) {
                    found = true
                }
            }
            assertTrue(found, "expected $table to have column $column")
        }
    }

    private companion object {
        const val TEST_DB = "migration-6-7"
        const val TEST_DB_EMPTY = "migration-6-7-empty"
    }
}
