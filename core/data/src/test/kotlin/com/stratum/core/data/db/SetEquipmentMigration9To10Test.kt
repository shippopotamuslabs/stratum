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
class SetEquipmentMigration9To10Test {

    @Test
    @DisplayName("9→10 backfills logged_sets.equipmentId from the set's lift; nulls stay null and clean")
    fun migrate9To10() {
        val helper = MigrationTestUtils.newMigrationHelper()
        val dbName = "stratum-logged-sets-equipment-9-10"
        helper.createDatabase(dbName, 9).use { db ->
            // createDatabase builds the v9 schema from the exported 9.json, so
            // logged_sets has no equipmentId column yet.
            db.execSQL(
                """
                INSERT INTO equipment
                  (id, name, isCustom, micro_options_json, updated_at_epoch_millis, pending_sync)
                VALUES
                  ('eq-cable', 'Cable Stack', 0, NULL, 100, 0),
                  ('eq-barbell', 'Barbell', 0, NULL, 100, 0)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO lifts
                  (id, name, movementPatternsBitmask, primaryMusclesBitmask,
                   secondaryMusclesBitmask, presetTags, equipmentId, isCustom, enabled,
                   muscleLengthBias, mainstream, fatigueProfile, loadType,
                   micro_options_json, updated_at_epoch_millis, pending_sync)
                VALUES
                  ('lift-cable', 'Cable Curl', 0, 0, 0, '', 'eq-cable', 0, 1,
                   'MidRange', 1, 'Low', 'Weighted', NULL, 100, 0),
                  ('lift-bare', 'Bodyweight Dip', 0, 0, 0, '', NULL, 0, 1,
                   'MidRange', 1, 'Low', 'Weighted', NULL, 100, 0)
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
                VALUES
                  ('ts-1', NULL, NULL, 'COMPLETED', 'PROGRAM_WORKOUT', 'LIVE',
                   1, NULL, NULL, 1000, 2000, NULL, NULL, NULL, 100, 0)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO logged_sets
                  (id, sessionId, exerciseNameRaw, liftId, setIndex, weightAmount,
                   weightUnit, reps, rir, partialReps, completedAtEpochMillis, liftName,
                   movementPatternsBitmask, primaryMusclesBitmask, secondaryMusclesBitmask,
                   fatigueProfile, muscleLengthBias, workoutLiftId, progressionScheme,
                   setType, repMin, repMax, rirGoal, isPersonalRecord, estimatedOneRm,
                   updated_at_epoch_millis, pending_sync)
                VALUES
                  ('set-a', 'ts-1', 'Cable Curl', 'lift-cable', 0, 42.5,
                   'Lb', 10, 2, NULL, 1500, 'Cable Curl',
                   0, 0, 0, 'Medium', 'MidRange', NULL, NULL,
                   NULL, NULL, NULL, NULL, NULL, 55.0, 100, 0),
                  ('set-b', 'ts-1', 'Cable Curl', 'lift-cable', 1, 50.0,
                   'Lb', 8, 1, 2, 1600, 'Cable Curl',
                   0, 0, 0, 'Medium', 'MidRange', NULL, NULL,
                   NULL, NULL, NULL, NULL, 1, 62.5, 100, 0),
                  ('set-c', 'ts-1', 'Bodyweight Dip', 'lift-bare', 0, 0.0,
                   'Lb', 12, NULL, NULL, 1700, 'Bodyweight Dip',
                   0, 0, 0, 'Medium', 'MidRange', NULL, NULL,
                   NULL, NULL, NULL, NULL, NULL, NULL, 100, 0),
                  ('set-d', 'ts-1', 'Mystery Press', NULL, 0, 95.0,
                   'Lb', 5, 3, NULL, 1800, 'Mystery Press',
                   0, 0, 0, 'Medium', 'MidRange', NULL, NULL,
                   NULL, NULL, NULL, NULL, NULL, 110.0, 100, 0)
                """.trimIndent(),
            )
        }
        val beforeMigrate = System.currentTimeMillis()
        helper.runMigrationsAndValidate(dbName, 10, true, MIGRATION_9_10).use { migrated ->
            // equipmentId column exists.
            val columns =
                MigrationTestUtils.rows(migrated, "PRAGMA table_info(logged_sets)").map { it[1] as String }
            assertTrue(columns.contains("equipmentId"))

            // Backfill: lift-linked sets inherit; bare-lift and liftless sets stay null.
            assertEquals(
                listOf(
                    listOf("set-a", "eq-cable"),
                    listOf("set-b", "eq-cable"),
                    listOf("set-c", null),
                    listOf("set-d", null),
                ),
                MigrationTestUtils.rows(migrated, "SELECT id, equipmentId FROM logged_sets ORDER BY id"),
            )

            // All other columns byte-identical, including the PR flag and e1RM.
            assertEquals(
                listOf(
                    listOf("set-a", 42.5, 10L, 2L, null, 1500L, null, 55.0),
                    listOf("set-b", 50.0, 8L, 1L, 2L, 1600L, 1L, 62.5),
                    listOf("set-c", 0.0, 12L, null, null, 1700L, null, null),
                    listOf("set-d", 95.0, 5L, 3L, null, 1800L, null, 110.0),
                ),
                MigrationTestUtils.rows(
                    migrated,
                    "SELECT id, weightAmount, reps, rir, partialReps, completedAtEpochMillis, " +
                        "isPersonalRecord, estimatedOneRm FROM logged_sets ORDER BY id",
                ),
            )

            // Backfilled rows are dirty with a bumped clock; null-staying rows untouched.
            val syncState =
                MigrationTestUtils.rows(
                    migrated,
                    "SELECT id, pending_sync, updated_at_epoch_millis FROM logged_sets ORDER BY id",
                )
            assertEquals("set-a", syncState[0][0])
            assertEquals(1L, syncState[0][1])
            assertTrue((syncState[0][2] as Long) >= beforeMigrate)
            assertEquals("set-b", syncState[1][0])
            assertEquals(1L, syncState[1][1])
            assertTrue((syncState[1][2] as Long) >= beforeMigrate)
            assertEquals(listOf("set-c", 0L, 100L), syncState[2])
            assertEquals(listOf("set-d", 0L, 100L), syncState[3])

            // New (liftId, equipmentId) index exists alongside the established indexes.
            val indexes =
                MigrationTestUtils.rows(
                    migrated,
                    "SELECT name FROM sqlite_master WHERE type = 'index' " +
                        "AND tbl_name = 'logged_sets' AND name NOT LIKE 'sqlite_%' " +
                        "ORDER BY name",
                ).map { it.single() as String }
            assertEquals(
                listOf(
                    "index_logged_sets_completedAtEpochMillis",
                    "index_logged_sets_liftId_equipmentId",
                    "index_logged_sets_liftId_isPersonalRecord",
                    "index_logged_sets_sessionId",
                    "index_logged_sets_workoutLiftId_liftId_setIndex",
                ),
                indexes,
            )

            // No FK added on equipmentId — only the session cascade remains.
            val fkList = MigrationTestUtils.rows(migrated, "PRAGMA foreign_key_list(logged_sets)")
            assertEquals(1, fkList.size)
            assertTrue(fkList.any { it[2] == "training_sessions" && it[3] == "sessionId" })
        }
    }

}
