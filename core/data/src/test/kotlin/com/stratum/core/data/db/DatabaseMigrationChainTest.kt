package com.stratum.core.data.db

import android.content.Context
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
 * Guards the production upgrade path: every shipped schema version must reach the current
 * version through [withProductionMigrations] — the exact list `DataModule` registers.
 * A version bump without its migration fails here instead of wiping user data through
 * the production destructive fallback.
 *
 * Deliberately **no** `fallbackToDestructiveMigration` on the test builder: a coverage gap
 * must throw, never silently recreate. The marker row proves each upgrade ran against the
 * seeded file (guarding a vacuous fresh-create pass).
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class DatabaseMigrationChainTest {

    @Test
    @DisplayName("production builder upgrades every shipped schema version to current")
    fun productionBuilderUpgradesEveryShippedVersion() {
        val context = RuntimeEnvironment.getApplication()
        val current = MigrationTestUtils.currentSchemaVersion
        assertTrue(current > 1, "expected a migrated schema, got v$current")
        for (version in 1 until current) {
            assertUpgrade(context, version, current)
        }
    }

    private fun assertUpgrade(context: Context, fromVersion: Int, current: Int) {
        val name = "stratum-chain-$fromVersion"
        val markerId = "marker-$fromVersion"
        context.deleteDatabase(name)
        val helper = MigrationTestUtils.newMigrationHelper()
        // onboarding_progress is untouched by every migration (identical across all
        // shipped versions): a stable marker table for all start versions.
        helper.createDatabase(name, fromVersion).use { db ->
            db.execSQL(
                """
                INSERT INTO onboarding_progress
                  (id, currentStep, startedAt, completedAt, selected_path,
                   updated_at_epoch_millis, pending_sync)
                VALUES (?, 'Profile', 1, NULL, NULL, 1, 0)
                """.trimIndent(),
                arrayOf<Any?>(markerId),
            )
        }
        val database =
            Room.databaseBuilder(context, StratumDatabase::class.java, name)
                .withProductionMigrations()
                .build()
        try {
            val sqlite = database.openHelper.readableDatabase
            assertEquals(current, sqlite.version, "v$fromVersion must upgrade to v$current")
            val surviving =
                MigrationTestUtils.rows(
                    sqlite,
                    "SELECT id FROM onboarding_progress WHERE id = ?",
                    arrayOf<Any?>(markerId),
                )
            assertTrue(
                surviving.size == 1,
                "marker row must survive v$fromVersion → v$current",
            )
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
