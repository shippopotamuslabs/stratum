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
class StratumDatabaseAutoMigration14To15Test {

    @Test
    @DisplayName("AutoMigration 14→15 adds the isDeleted tombstone flag to lifts, default live")
    fun migrate14To15() {
        val helper = MigrationTestUtils.newMigrationHelper()
        val dbName = "stratum-auto-14-15"
        helper.createDatabase(dbName, 14).apply {
            execSQL(
                """
                INSERT INTO lifts
                  (id, name, movementPatternsBitmask, primaryMusclesBitmask,
                   secondaryMusclesBitmask, presetTags, equipmentId, isCustom, enabled,
                   description, imageUrisCsv, muscleLengthBias, mainstream, fatigueProfile,
                   loadType, defaultRestSeconds, pinned_note, micro_options_json,
                   updated_at_epoch_millis, pending_sync)
                VALUES ('lift-1', 'Bench Press', 0, 0, 0, '', NULL, 0, 0,
                        NULL, NULL, 'MidRange', 1, 'Medium', 'Weighted', NULL, NULL, NULL,
                        100, 0)
                """.trimIndent(),
            )
            close()
        }
        helper.runMigrationsAndValidate(dbName, 15, true).use { migrated ->
            val columns =
                MigrationTestUtils.rows(migrated, "PRAGMA table_info(lifts)").map { it[1] as String }
            assertTrue(columns.contains("isDeleted"))
            // The pre-v15 row was merge-hidden long ago (enabled = 0): Scope 7 —
            // it stays merely disabled, NOT retroactively tombstoned.
            assertEquals(
                listOf(listOf("lift-1", "Bench Press", 0L, 0L)),
                MigrationTestUtils.rows(migrated, "SELECT id, name, enabled, isDeleted FROM lifts"),
            )
        }
    }
}
