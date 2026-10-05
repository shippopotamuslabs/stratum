package com.stratum.core.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class IncrementTierLiftColumnMigration8To9Test {

    @Test
    @DisplayName("8→9 drops liftId column, its FK and indexes; keeps equipment-scoped rows")
    fun migrate8To9() {
        val helper =
            MigrationTestHelper(
                InstrumentationRegistry.getInstrumentation(),
                StratumDatabase::class.java,
                emptyList(),
                FrameworkSQLiteOpenHelperFactory(),
            )
        val dbName = "stratum-increment-tiers-8-9"
        helper.createDatabase(dbName, 8).use { db ->
            // createDatabase builds the CURRENT (v9) DDL, so reshape increment_tiers
            // back to its v8 shape: re-add the liftId column and its two indexes.
            db.execSQL("ALTER TABLE increment_tiers ADD COLUMN liftId TEXT")
            db.execSQL("CREATE INDEX index_increment_tiers_liftId ON increment_tiers(liftId)")
            db.execSQL(
                "CREATE UNIQUE INDEX index_increment_tiers_liftId_lowerBound " +
                    "ON increment_tiers(liftId, lowerBound)",
            )
            db.execSQL(
                """
                INSERT INTO equipment
                  (id, name, isCustom, micro_options_json, updated_at_epoch_millis, pending_sync)
                VALUES ('eq-1', 'Cables', 0, NULL, 100, 0)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO lifts
                  (id, name, movementPatternsBitmask, primaryMusclesBitmask,
                   secondaryMusclesBitmask, presetTags, equipmentId, isCustom, enabled,
                   micro_options_json, updated_at_epoch_millis, pending_sync)
                VALUES
                  ('lift-stale-micro', 'Stale Micro', 0, 0, 0, '', 'eq-1', 0, 1,
                   '[2.5]', 100, 0)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO increment_tiers
                  (id, equipmentId, lowerBound, upperBound, increment,
                   updated_at_epoch_millis, pending_sync, liftId)
                VALUES
                  ('e_eq-1_0', 'eq-1', 0, NULL, 5, 100, 0, NULL),
                  ('e_eq-1_100', 'eq-1', 100, NULL, 10, 100, 0, NULL),
                  ('lift-x_0', NULL, 0, 100, 5, 100, 0, 'lift-x')
                """.trimIndent(),
            )
        }
        helper.runMigrationsAndValidate(dbName, 9, true, MIGRATION_8_9).use { migrated ->
            // liftId column is gone.
            val columns =
                rows(migrated, "PRAGMA table_info(increment_tiers)").map { it[1] as String }
            assertFalse(columns.contains("liftId"))
            assertEquals(
                listOf(
                    "id",
                    "equipmentId",
                    "lowerBound",
                    "upperBound",
                    "increment",
                    "updated_at_epoch_millis",
                    "pending_sync",
                ),
                columns,
            )
            // Equipment-scoped rows survive with values intact; the lift-scoped row is dropped.
            assertEquals(
                listOf(
                    listOf("e_eq-1_0", "eq-1", 0.0, null, 5.0, 100L, 0L),
                    listOf("e_eq-1_100", "eq-1", 100.0, null, 10.0, 100L, 0L),
                ),
                rows(
                    migrated,
                    "SELECT id, equipmentId, lowerBound, upperBound, increment, " +
                        "updated_at_epoch_millis, pending_sync FROM increment_tiers ORDER BY id",
                ),
            )
            // Only the equipment indexes exist.
            val indexes =
                rows(
                    migrated,
                    "SELECT name FROM sqlite_master WHERE type = 'index' " +
                        "AND tbl_name = 'increment_tiers' AND name NOT LIKE 'sqlite_%' " +
                        "ORDER BY name",
                ).map { it.single() as String }
            assertEquals(
                listOf(
                    "index_increment_tiers_equipmentId",
                    "index_increment_tiers_equipmentId_lowerBound",
                ),
                indexes,
            )
            // The equipment FK survived the rebuild.
            val fkList = rows(migrated, "PRAGMA foreign_key_list(increment_tiers)")
            assertTrue(fkList.any { it[2] == "equipment" && it[3] == "equipmentId" })
            // Stale lift-scoped micro overrides are cleared and marked dirty.
            assertEquals(
                listOf(listOf(null, 1L)),
                rows(
                    migrated,
                    "SELECT micro_options_json, pending_sync FROM lifts WHERE id = 'lift-stale-micro'",
                ),
            )
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
}
