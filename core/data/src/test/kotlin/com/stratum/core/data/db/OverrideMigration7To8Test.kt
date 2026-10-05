package com.stratum.core.data.db

import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.testing.MigrationTestHelper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class OverrideMigration7To8Test {

    @Test
    @DisplayName("7→8 converts lift overrides to '<lift name> Stack' clones with sync parity")
    fun migrate7To8() {
        val helper =
            MigrationTestHelper(
                InstrumentationRegistry.getInstrumentation(),
                StratumDatabase::class.java,
                emptyList(),
                FrameworkSQLiteOpenHelperFactory(),
            )
        val dbName = "stratum-overrides-7-8"
        helper.createDatabase(dbName, 7).use { db ->
            db.execSQL(
                """
                INSERT INTO equipment
                  (id, name, isCustom, micro_options_json, updated_at_epoch_millis, pending_sync)
                VALUES
                  ('eq-default', 'Default', 0, NULL, 100, 0),
                  ('eq-clash', 'Squat Stack', 1, NULL, 100, 0)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO increment_tiers
                  (id, liftId, equipmentId, lowerBound, upperBound, increment,
                   updated_at_epoch_millis, pending_sync)
                VALUES
                  ('e_eq-default_0', NULL, 'eq-default', 0, NULL, 5, 100, 0),
                  ('lift-squat_0', 'lift-squat', NULL, 0, 100, 5, 100, 0),
                  ('lift-squat_100', 'lift-squat', NULL, 100, NULL, 10, 100, 0)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO lifts
                  (id, name, movementPatternsBitmask, primaryMusclesBitmask,
                   secondaryMusclesBitmask, presetTags, equipmentId, isCustom, enabled,
                   description, imageUrisCsv, muscleLengthBias, mainstream, fatigueProfile,
                   loadType, defaultRestSeconds, pinned_note, micro_options_json,
                   updated_at_epoch_millis, pending_sync)
                VALUES
                  ('lift-squat', 'Squat', 0, 0, 0, '', 'eq-default', 0, 1,
                   NULL, NULL, 'MidRange', 0, 'Low',
                   'Weighted', NULL, NULL, '[2.5]',
                   100, 0),
                  ('lift-bench', 'Bench', 0, 0, 0, '', 'eq-default', 0, 1,
                   NULL, NULL, 'MidRange', 0, 'Low',
                   'Weighted', NULL, NULL, NULL,
                   100, 0),
                  ('lift-row', 'Row', 0, 0, 0, '', NULL, 0, 1,
                   NULL, NULL, 'MidRange', 0, 'Low',
                   'Weighted', NULL, NULL, '[1.25]',
                   100, 0)
                """.trimIndent(),
            )
        }
        helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8).use { migrated ->
            // Clone names: "Squat Stack" is taken, so the Squat clone dedupes.
            val squatCloneId = single(migrated, "SELECT id FROM equipment WHERE name = 'Squat Stack 2'") as String
            val rowCloneId = single(migrated, "SELECT id FROM equipment WHERE name = 'Row Stack'") as String
            assertEquals(
                listOf(listOf("Squat Stack 2", 1L, "[2.5]", 1L)),
                rows(migrated, "SELECT name, isCustom, micro_options_json, pending_sync FROM equipment WHERE id = '$squatCloneId'"),
            )
            assertEquals(
                listOf(listOf("Row Stack", 1L, "[1.25]", 1L)),
                rows(migrated, "SELECT name, isCustom, micro_options_json, pending_sync FROM equipment WHERE id = '$rowCloneId'"),
            )
            // Override bands copied as equipment-scoped rows with deterministic ids.
            assertEquals(
                listOf(
                    listOf("e_${squatCloneId}_0", squatCloneId, null, 0.0, 100.0, 5.0, 1L),
                    listOf("e_${squatCloneId}_100", squatCloneId, null, 100.0, null, 10.0, 1L),
                ),
                rows(migrated, "SELECT id, equipmentId, liftId, lowerBound, upperBound, increment, pending_sync FROM increment_tiers WHERE equipmentId = '$squatCloneId' ORDER BY lowerBound"),
            )
            // Old lift-scoped rows are gone; shared rows untouched and still clean.
            assertEquals(0L, single(migrated, "SELECT COUNT(*) FROM increment_tiers WHERE liftId IS NOT NULL"))
            assertEquals(
                listOf(listOf("e_eq-default_0", "eq-default", 0L)),
                rows(migrated, "SELECT id, equipmentId, pending_sync FROM increment_tiers WHERE id = 'e_eq-default_0'"),
            )
            // Lifts reassigned to their clones, micro overrides cleared, marked dirty.
            assertEquals(
                listOf(listOf(squatCloneId, null, 1L)),
                rows(migrated, "SELECT equipmentId, micro_options_json, pending_sync FROM lifts WHERE id = 'lift-squat'"),
            )
            assertEquals(
                listOf(listOf(rowCloneId, null, 1L)),
                rows(migrated, "SELECT equipmentId, micro_options_json, pending_sync FROM lifts WHERE id = 'lift-row'"),
            )
            assertEquals(
                listOf(listOf("eq-default", null, 0L)),
                rows(migrated, "SELECT equipmentId, micro_options_json, pending_sync FROM lifts WHERE id = 'lift-bench'"),
            )
            // Stale cloud docs for the deleted override rows are tombstoned.
            assertEquals(
                listOf(
                    listOf("increment_tiers/lift-squat_0", "increment_tiers", "lift-squat_0"),
                    listOf("increment_tiers/lift-squat_100", "increment_tiers", "lift-squat_100"),
                ),
                rows(migrated, "SELECT row_id, collection_path, entity_id FROM pending_cloud_deletes ORDER BY entity_id"),
            )
            // No other equipment was cloned.
            assertEquals(4L, single(migrated, "SELECT COUNT(*) FROM equipment"))
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
                                android.database.Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
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
