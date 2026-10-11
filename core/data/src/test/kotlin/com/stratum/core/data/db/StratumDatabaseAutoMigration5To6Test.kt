package com.stratum.core.data.db

import org.junit.jupiter.api.Assertions.assertEquals
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
    @DisplayName("AutoMigration 5→6 adds the JSON payload column to program_change_events")
    fun migrate5To6() {
        val helper = MigrationTestUtils.newMigrationHelper()
        val dbName = "stratum-auto-5-6"
        helper.createDatabase(dbName, 5).apply {
            // Pre-v6 row written with flat payload columns only.
            execSQL(
                """
                INSERT INTO program_change_events
                    (id, program_id, event_type, occurred_at_epoch_millis,
                     workout_lift_id, previous_lift_id, new_lift_id,
                     workout_id, completed_cycle_number,
                     updated_at_epoch_millis, pending_sync)
                VALUES
                    ('pce-legacy', 'prog-1', 'LIFT_SWAPPED', 1234567890,
                     'wl-1', 'lift-old', 'lift-new',
                     NULL, NULL,
                     1234567890, 0)
                """.trimIndent(),
            )
            close()
        }
        helper.runMigrationsAndValidate(dbName, 6, true).use { migrated ->
            val columns = columnNames(migrated, "program_change_events")
            assertTrue(columns.contains("payload_json"))

            migrated.query("SELECT * FROM program_change_events WHERE id = 'pce-legacy'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(
                    "wl-1",
                    cursor.getString(cursor.getColumnIndex("workout_lift_id")),
                )
                assertTrue(cursor.isNull(cursor.getColumnIndex("payload_json")))
            }
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
