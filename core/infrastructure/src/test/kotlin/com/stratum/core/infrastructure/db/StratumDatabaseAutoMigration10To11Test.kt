package com.stratum.core.infrastructure.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class StratumDatabaseAutoMigration10To11Test {

    @Test
    @DisplayName("AutoMigration 10→11 adds the isDeleted tombstone flag to equipment, default live")
    fun migrate10To11() {
        val helper = MigrationTestUtils.newMigrationHelper()
        val dbName = "stratum-auto-10-11"
        helper.createDatabase(dbName, 10).apply {
            execSQL(
                """
                INSERT INTO equipment
                  (id, name, isCustom, micro_options_json, updated_at_epoch_millis, pending_sync)
                VALUES ('eq-1', 'Cables', 0, NULL, 100, 0)
                """.trimIndent(),
            )
            close()
        }
        helper.runMigrationsAndValidate(dbName, 11, true).use { migrated ->
            val columns =
                MigrationTestUtils.rows(migrated, "PRAGMA table_info(equipment)").map { it[1] as String }
            assertTrue(columns.contains("isDeleted"))
            assertEquals(
                listOf(listOf("eq-1", "Cables", 0L)),
                MigrationTestUtils.rows(migrated, "SELECT id, name, isDeleted FROM equipment"),
            )
        }
    }
}
