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
class PrIndexMigration12To13Test {

    @Test
    @DisplayName("12→13 swaps the PR unique index to (liftId, equipmentId, isPersonalRecord)")
    fun migrate12To13() {
        val helper = MigrationTestUtils.newMigrationHelper()
        val dbName = "stratum-pr-index-12-13"
        helper.createDatabase(dbName, 12).use { db ->
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
                  (id, sessionId, exerciseNameRaw, liftId, equipmentId, setIndex, weightAmount,
                   weightUnit, reps, rir, partialReps, completedAtEpochMillis, liftName,
                   movementPatternsBitmask, primaryMusclesBitmask, secondaryMusclesBitmask,
                   fatigueProfile, muscleLengthBias, workoutLiftId, progressionScheme,
                   setType, repMin, repMax, rirGoal, isPersonalRecord, estimatedOneRm,
                   updated_at_epoch_millis, pending_sync)
                VALUES
                  ('pr-a', 'ts-1', 'Bench', 'lift-bench', 'eq-barbell', 0, 135.0,
                   'Lb', 5, 2, NULL, 1500, 'Bench',
                   0, 0, 0, 'Medium', 'MidRange', NULL, NULL,
                   NULL, NULL, NULL, NULL, 1, 150.0,
                   100, 0),
                  ('plain-a', 'ts-1', 'Bench', 'lift-bench', 'eq-barbell', 1, 135.0,
                   'Lb', 5, 2, NULL, 1600, 'Bench',
                   0, 0, 0, 'Medium', 'MidRange', NULL, NULL,
                   NULL, NULL, NULL, NULL, NULL, 150.0,
                   100, 0)
                """.trimIndent(),
            )
        }
        // validate=true checks the migrated schema against the v13 entities —
        // the hardcoded index names in MIGRATION_12_13 must match Room exactly.
        helper.runMigrationsAndValidate(dbName, 13, true, MIGRATION_12_13).use { migrated ->
            // Old index dropped, new per-pair unique index present.
            val indexes =
                MigrationTestUtils.rows(
                    migrated,
                    "SELECT name FROM sqlite_master WHERE type = 'index' " +
                        "AND tbl_name = 'logged_sets' AND name NOT LIKE 'sqlite_%' " +
                        "ORDER BY name",
                ).map { it.single() as String }
            assertTrue(
                indexes.contains("index_logged_sets_liftId_equipmentId_isPersonalRecord"),
                "expected new per-pair unique index, got: $indexes",
            )
            assertTrue(
                indexes.none { it == "index_logged_sets_liftId_isPersonalRecord" },
                "old lift-only unique index must be gone, got: $indexes",
            )

            // Seeded rows survive byte-identical.
            assertEquals(
                listOf(listOf("pr-a", 1L), listOf("plain-a", null)),
                MigrationTestUtils.rows(
                    migrated,
                    "SELECT id, isPersonalRecord FROM logged_sets ORDER BY id",
                ),
            )

            // Same-pair duplicate PR is still rejected.
            var duplicateRejected = false
            try {
                migrated.execSQL(
                    """
                    INSERT INTO logged_sets
                      (id, sessionId, exerciseNameRaw, liftId, equipmentId, setIndex,
                       weightAmount, weightUnit, reps, completedAtEpochMillis,
                       isPersonalRecord, updated_at_epoch_millis, pending_sync)
                    VALUES
                      ('pr-dup', 'ts-1', 'Bench', 'lift-bench', 'eq-barbell', 2,
                       140.0, 'Lb', 5, 1700, 1, 100, 0)
                    """.trimIndent(),
                )
            } catch (_: Exception) {
                duplicateRejected = true
            }
            assertTrue(duplicateRejected, "duplicate PR for the same (lift, equipment) pair must be rejected")

            // Same lift, different equipment: allowed (STR-129 — this is the hotfix).
            migrated.execSQL(
                """
                INSERT INTO logged_sets
                  (id, sessionId, exerciseNameRaw, liftId, equipmentId, setIndex,
                   weightAmount, weightUnit, reps, completedAtEpochMillis,
                   isPersonalRecord, updated_at_epoch_millis, pending_sync)
                VALUES
                  ('pr-other-eq', 'ts-1', 'Bench', 'lift-bench', 'eq-dumbbell', 3,
                   70.0, 'Lb', 8, 1800, 1, 100, 0)
                """.trimIndent(),
            )
            assertEquals(
                2L,
                MigrationTestUtils.single(
                    migrated,
                    "SELECT COUNT(*) FROM logged_sets WHERE isPersonalRecord = 1",
                ),
            )
        }
    }
}
