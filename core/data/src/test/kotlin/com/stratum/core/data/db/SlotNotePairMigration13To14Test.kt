package com.stratum.core.data.db

import androidx.room.Room
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * STR-127 migration coverage: v13 → v14 adds `workout_lift_notes.lift_id` and
 * backfills it from each slot's current lift.
 *
 * No MigrationTestHelper validate-to-current (the v14 schema JSON is
 * KSP-generated during the build, not checked in), so this creates the
 * database at v12 from the checked-in export, seeds rows, and upgrades
 * through the production migration chain with the real database.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class SlotNotePairMigration13To14Test {

    @Test
    @DisplayName("13→14 backfills workout_lift_notes.lift_id from the slot's current lift")
    fun migrateBackfillsPairLiftId() {
        val context = RuntimeEnvironment.getApplication()
        val name = "stratum-slot-note-pair-13-14"
        context.deleteDatabase(name)
        val helper = MigrationTestUtils.newMigrationHelper()
        helper.createDatabase(name, 12).use { db ->
            db.execSQL(
                """
                INSERT INTO lifts
                  (id, name, movementPatternsBitmask, primaryMusclesBitmask,
                   secondaryMusclesBitmask, presetTags, equipmentId, isCustom, enabled,
                   description, imageUrisCsv, muscleLengthBias, mainstream, fatigueProfile,
                   loadType, defaultRestSeconds, pinned_note, micro_options_json,
                   updated_at_epoch_millis, pending_sync)
                VALUES ('lift-a', 'Bench Press', 0, 0, 0, '', NULL, 0, 1,
                        NULL, NULL, 'MidRange', 1, 'Medium', 'Weighted', NULL, NULL, NULL,
                        100, 0)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO programs
                  (id, name, created_at, cycle_length, training_weekdays,
                   current_cycle_number, cycle_position, is_active, rest_slot_indices,
                   cycle_start_epoch_day, updated_at_epoch_millis, pending_sync)
                VALUES ('p-1', 'Program', '2026-01-01', 1, '', 1, 0, 1, '', NULL, 100, 0)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO workouts
                  (id, program_id, name, weekday, position, updated_at_epoch_millis, pending_sync)
                VALUES ('w-1', 'p-1', 'Push Day', NULL, 0, 100, 0)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO workout_lifts
                  (id, workout_id, lift_id, equipment_id, progression_scheme, position,
                   superset_group_id, use_rest_timer_between_lifts, pinned_note,
                   updated_at_epoch_millis, pending_sync)
                VALUES ('wl-1', 'w-1', 'lift-a', NULL, 'Double', 0, NULL, 0, 'bench cue', 100, 0)
                """.trimIndent(),
            )
            // v12 workout_lift_notes has no lift_id column yet.
            db.execSQL(
                """
                INSERT INTO workout_lift_notes
                  (id, workout_lift_id, body, position, created_at_epoch_millis,
                   updated_at_epoch_millis, pending_sync)
                VALUES ('wn-1', 'wl-1', 'bench cue', 0, 100, 100, 0),
                       ('wn-2', 'wl-1', 'brace hard', 1, 100, 100, 0)
                """.trimIndent(),
            )
        }

        val database =
            Room.databaseBuilder(context, StratumDatabase::class.java, name)
                .withProductionMigrations()
                .build()
        try {
            val sqlite = database.openHelper.readableDatabase
            // Upgrades through the production chain to current (v15 since
            // STR-131); the 13→14 backfill assertions below are version-agnostic.
            assertEquals(MigrationTestUtils.currentSchemaVersion, sqlite.version)
            val columns =
                MigrationTestUtils.rows(sqlite, "PRAGMA table_info(workout_lift_notes)")
                    .map { it[1] as String }
            assertTrue(columns.contains("lift_id"))
            assertEquals(
                listOf(
                    listOf("wn-1", "lift-a", "bench cue", 0L),
                    listOf("wn-2", "lift-a", "brace hard", 1L),
                ),
                MigrationTestUtils.rows(
                    sqlite,
                    "SELECT id, lift_id, body, position FROM workout_lift_notes ORDER BY position",
                ),
            )
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
