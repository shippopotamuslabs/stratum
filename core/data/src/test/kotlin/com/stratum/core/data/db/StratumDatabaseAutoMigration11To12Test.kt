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
class StratumDatabaseAutoMigration11To12Test {

    @Test
    @DisplayName("AutoMigration 11→12 adds nullable equipment_id to workout_lifts, legacy rows null")
    fun migrate11To12() {
        val helper = MigrationTestUtils.newMigrationHelper()
        val dbName = "stratum-auto-11-12"
        helper.createDatabase(dbName, 11).apply {
            execSQL(
                """
                INSERT INTO programs
                  (id, name, created_at, cycle_length, training_weekdays,
                   current_cycle_number, cycle_position, is_active, rest_slot_indices,
                   updated_at_epoch_millis, pending_sync)
                VALUES ('p-1', 'Program', '2025-01-01T00:00:00Z', 3, '', 1, 0, 0, '',
                        100, 0)
                """.trimIndent(),
            )
            execSQL(
                "INSERT INTO workouts (id, program_id, name, weekday, position, " +
                    "updated_at_epoch_millis, pending_sync) " +
                    "VALUES ('w-1', 'p-1', 'Workout', NULL, 0, 100, 0)",
            )
            execSQL(
                """
                INSERT INTO lifts
                  (id, name, movementPatternsBitmask, primaryMusclesBitmask,
                   secondaryMusclesBitmask, presetTags, isCustom, enabled,
                   muscleLengthBias, mainstream, fatigueProfile, loadType,
                   updated_at_epoch_millis, pending_sync)
                VALUES ('lift-squat', 'Squat', 0, 0, 0, '', 0, 1, 'MID_RANGE', 1,
                        'LOW', 'WEIGHTED', 100, 0)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO workout_lifts
                  (id, workout_id, lift_id, progression_scheme, position,
                   superset_group_id, use_rest_timer_between_lifts, pinned_note,
                   updated_at_epoch_millis, pending_sync)
                VALUES ('wl-1', 'w-1', 'lift-squat', 'Double', 0, NULL, 0, NULL, 100, 0)
                """.trimIndent(),
            )
            close()
        }
        helper.runMigrationsAndValidate(dbName, 12, true).use { migrated ->
            val columns =
                MigrationTestUtils.rows(migrated, "PRAGMA table_info(workout_lifts)").map { it[1] as String }
            assertTrue(columns.contains("equipment_id"))
            assertEquals(
                listOf(listOf("wl-1", null)),
                MigrationTestUtils.rows(migrated, "SELECT id, equipment_id FROM workout_lifts"),
            )
        }
    }
}
