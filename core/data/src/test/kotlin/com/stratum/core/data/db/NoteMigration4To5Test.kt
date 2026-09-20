package com.stratum.core.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class NoteMigration4To5Test {

    @Test
    @DisplayName("4→5 backfills note tables, keeps legacy pins, and relaxes the session slot")
    fun migrate4To5() {
        val helper =
            MigrationTestHelper(
                InstrumentationRegistry.getInstrumentation(),
                StratumDatabase::class.java,
                emptyList(),
                FrameworkSQLiteOpenHelperFactory(),
            )
        val dbName = "stratum-notes-4-5"
        helper.createDatabase(dbName, 4).use { db ->
            db.execSQL(
                """
                INSERT INTO lifts
                  (id, name, movementPatternsBitmask, primaryMusclesBitmask,
                   secondaryMusclesBitmask, presetTags, equipmentId, isCustom, enabled,
                   description, imageUrisCsv, muscleLengthBias, mainstream, fatigueProfile,
                   loadType, defaultRestSeconds, pinned_note,
                   updated_at_epoch_millis, pending_sync)
                VALUES
                  ('lift-1', 'Squat', 0, 0, 0, '', NULL, 0, 1,
                   NULL, NULL, 'MidRange', 0, 'Low',
                   'Weighted', NULL, 'catalog pin',
                   100, 0),
                  ('lift-2', 'Bench', 0, 0, 0, '', NULL, 0, 1,
                   NULL, NULL, 'MidRange', 0, 'Low',
                   'Weighted', NULL, NULL,
                   100, 0),
                  ('lift-3', 'Row', 0, 0, 0, '', NULL, 0, 1,
                   NULL, NULL, 'MidRange', 0, 'Low',
                   'Weighted', NULL, '',
                   100, 0)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO programs
                  (id, name, created_at, cycle_length, training_weekdays,
                   current_cycle_number, cycle_position, is_active, rest_slot_indices,
                   cycle_start_epoch_day, updated_at_epoch_millis, pending_sync)
                VALUES ('p-1', 'P', '2026-01-01', 1, '[]', 1, 0, 1, '[]', NULL, 100, 0)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO workouts
                  (id, program_id, name, weekday, position,
                   updated_at_epoch_millis, pending_sync)
                VALUES ('w-1', 'p-1', 'Day', NULL, 0, 100, 0)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO workout_lifts
                  (id, workout_id, lift_id, progression_scheme, position,
                   superset_group_id, use_rest_timer_between_lifts, pinned_note,
                   updated_at_epoch_millis, pending_sync)
                VALUES
                  ('wl-1', 'w-1', 'lift-1', 'Double', 0, NULL, 0, 'slot pin', 200, 0),
                  ('wl-2', 'w-1', 'lift-1', 'Double', 1, NULL, 0, NULL, 200, 0)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO training_sessions
                  (id, program_id, workout_id, status, kind, origin,
                   cycle_number_snapshot, weekday_snapshot, cycle_slot_index_snapshot,
                   started_at_epoch_millis, ended_at_epoch_millis, source_app,
                   program_name_snapshot, workout_name_snapshot,
                   updated_at_epoch_millis, pending_sync)
                VALUES ('ts-1', NULL, NULL, 'COMPLETED', 'PROGRAM_WORKOUT', 'LIVE',
                        1, NULL, NULL, 10, 11, NULL, NULL, 'Day', 100, 0)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO lift_session_notes
                  (id, session_id, lift_id, workout_lift_id, body,
                   updated_at_epoch_millis, pending_sync)
                VALUES ('n-1', 'ts-1', 'lift-1', 'wl-1', 'session body', 50, 0)
                """.trimIndent(),
            )
        }
        helper.runMigrationsAndValidate(dbName, 5, true, MIGRATION_4_5).use { migrated ->
            assertEquals(
                listOf(listOf("lift-1::0", "lift-1", "catalog pin", 0L, 100L, 100L, 0L)),
                rows(migrated, "SELECT id, lift_id, body, position, created_at_epoch_millis, updated_at_epoch_millis, pending_sync FROM lift_notes"),
            )
            assertEquals(
                listOf(listOf("wl-1::0", "wl-1", "slot pin", 0L, 200L, 200L, 0L)),
                rows(migrated, "SELECT id, workout_lift_id, body, position, created_at_epoch_millis, updated_at_epoch_millis, pending_sync FROM workout_lift_notes"),
            )
            // Legacy mirrors stay readable.
            assertEquals(
                "catalog pin",
                single(migrated, "SELECT pinned_note FROM lifts WHERE id = 'lift-1'"),
            )
            assertEquals(
                "slot pin",
                single(migrated, "SELECT pinned_note FROM workout_lifts WHERE id = 'wl-1'"),
            )
            // Session row gains position + created_at (= updated_at for pre-v5 rows).
            assertEquals(
                listOf(listOf("n-1", "session body", 0L, 50L)),
                rows(migrated, "SELECT id, body, position, created_at_epoch_millis FROM lift_session_notes"),
            )
            // The slot index is now non-unique: a second row for the slot inserts cleanly.
            migrated.execSQL(
                "INSERT INTO lift_session_notes VALUES ('n-2', 'ts-1', 'lift-1', 'wl-1', 'second', 1, 60, 60, 0)",
            )
            assertEquals(
                2,
                (single(migrated, "SELECT COUNT(*) FROM lift_session_notes") as Long).toInt(),
            )
            assertSlotIndexNonUnique(migrated)
        }
    }

    private fun assertSlotIndexNonUnique(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.query("PRAGMA index_list(lift_session_notes)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val uniqueIndex = cursor.getColumnIndex("unique")
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == "index_lift_session_notes_session_id_workout_lift_id") {
                    found = true
                    assertEquals(0, cursor.getInt(uniqueIndex))
                }
            }
            assertTrue(found, "slot index missing after migration")
        }
    }

    private fun rows(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        sql: String,
    ): List<List<Any?>> =
        db.query(sql).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        List(cursor.columnCount) { index ->
                            when (cursor.getType(index)) {
                                android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                                android.database.Cursor.FIELD_TYPE_NULL -> null
                                else -> cursor.getString(index)
                            }
                        },
                    )
                }
            }
        }

    private fun single(db: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): Any? =
        rows(db, sql).single().single()
}
