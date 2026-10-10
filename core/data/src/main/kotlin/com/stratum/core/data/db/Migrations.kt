package com.stratum.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

/**
 * v4 → v5: multi-note storage.
 *
 * - New `lift_notes` / `workout_lift_notes` tables, backfilled (position 0) from every
 *   non-blank legacy `pinned_note`. Backfilled rows are `pending_sync = CLEAN` — they
 *   already synced as part of their parent row.
 * - `lift_session_notes` gains `position` / `created_at_epoch_millis` (existing rows keep
 *   `created_at = updated_at`); the unique `(session_id, workout_lift_id)` index becomes
 *   non-unique so a slot can hold an ordered list.
 * - Legacy `pinned_note` columns are kept as first-note mirrors (dual-read fallback).
 */
val MIGRATION_4_5: Migration =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `lift_notes` (
                  `id` TEXT NOT NULL,
                  `lift_id` TEXT NOT NULL,
                  `body` TEXT NOT NULL,
                  `position` INTEGER NOT NULL,
                  `created_at_epoch_millis` INTEGER NOT NULL,
                  `updated_at_epoch_millis` INTEGER NOT NULL,
                  `pending_sync` INTEGER NOT NULL,
                  PRIMARY KEY(`id`),
                  FOREIGN KEY(`lift_id`) REFERENCES `lifts`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_lift_notes_lift_id` ON `lift_notes` (`lift_id`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_lift_notes_lift_id_position` " +
                    "ON `lift_notes` (`lift_id`, `position`)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `workout_lift_notes` (
                  `id` TEXT NOT NULL,
                  `workout_lift_id` TEXT NOT NULL,
                  `body` TEXT NOT NULL,
                  `position` INTEGER NOT NULL,
                  `created_at_epoch_millis` INTEGER NOT NULL,
                  `updated_at_epoch_millis` INTEGER NOT NULL,
                  `pending_sync` INTEGER NOT NULL,
                  PRIMARY KEY(`id`),
                  FOREIGN KEY(`workout_lift_id`) REFERENCES `workout_lifts`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_workout_lift_notes_workout_lift_id` " +
                    "ON `workout_lift_notes` (`workout_lift_id`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_workout_lift_notes_workout_lift_id_position` " +
                    "ON `workout_lift_notes` (`workout_lift_id`, `position`)",
            )

            db.execSQL("ALTER TABLE `lift_session_notes` ADD COLUMN `position` INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                "ALTER TABLE `lift_session_notes` " +
                    "ADD COLUMN `created_at_epoch_millis` INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL(
                "UPDATE `lift_session_notes` " +
                    "SET `created_at_epoch_millis` = `updated_at_epoch_millis`",
            )
            db.execSQL("DROP INDEX IF EXISTS `index_lift_session_notes_session_id_workout_lift_id`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_lift_session_notes_session_id_workout_lift_id` " +
                    "ON `lift_session_notes` (`session_id`, `workout_lift_id`)",
            )

            db.execSQL(
                """
                INSERT INTO `lift_notes`
                  (`id`, `lift_id`, `body`, `position`,
                   `created_at_epoch_millis`, `updated_at_epoch_millis`, `pending_sync`)
                SELECT `id` || '::0', `id`, `pinned_note`, 0,
                       `updated_at_epoch_millis`, `updated_at_epoch_millis`, 0
                FROM `lifts`
                WHERE `pinned_note` IS NOT NULL AND `pinned_note` != ''
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO `workout_lift_notes`
                  (`id`, `workout_lift_id`, `body`, `position`,
                   `created_at_epoch_millis`, `updated_at_epoch_millis`, `pending_sync`)
                SELECT `id` || '::0', `id`, `pinned_note`, 0,
                       `updated_at_epoch_millis`, `updated_at_epoch_millis`, 0
                FROM `workout_lifts`
                WHERE `pinned_note` IS NOT NULL AND `pinned_note` != ''
                """.trimIndent(),
            )
        }
    }

/**
 * v8 → v9: per-lift increment overrides were removed (STR-82/84), so the now-unused
 * `liftId` column — plus its foreign key to `lifts` and its two indexes — is dropped
 * from `increment_tiers` via table rebuild.
 *
 * STR-85's 7→8 migration already converted every override into a "<lift name> Stack"
 * equipment clone, so no lift-scoped rows should remain; the copy filters defensively
 * on `liftId IS NULL` rather than resurrecting them as parentless rows.
 */
val MIGRATION_8_9: Migration =
    object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE increment_tiers_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    equipmentId TEXT NOT NULL,
                    lowerBound REAL NOT NULL,
                    upperBound REAL,
                    increment REAL NOT NULL,
                    updated_at_epoch_millis INTEGER NOT NULL,
                    pending_sync INTEGER NOT NULL,
                    FOREIGN KEY(equipmentId) REFERENCES equipment(id)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO increment_tiers_new
                    (id, equipmentId, lowerBound, upperBound, increment,
                     updated_at_epoch_millis, pending_sync)
                SELECT id, equipmentId, lowerBound, upperBound, increment,
                       updated_at_epoch_millis, pending_sync
                FROM increment_tiers
                WHERE liftId IS NULL
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE increment_tiers")
            db.execSQL("ALTER TABLE increment_tiers_new RENAME TO increment_tiers")
            db.execSQL(
                "CREATE INDEX index_increment_tiers_equipmentId " +
                    "ON increment_tiers(equipmentId)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX index_increment_tiers_equipmentId_lowerBound " +
                    "ON increment_tiers(equipmentId, lowerBound)",
            )
            // Per-lift micro overrides were removed (STR-82/84): no code writes
            // lifts.micro_options_json anymore. Clear any stale values so lifts
            // re-inherit their equipment's adder stops, and mark them dirty so
            // the clear syncs.
            db.execSQL(
                "UPDATE lifts SET micro_options_json = NULL, pending_sync = 1, " +
                    "updated_at_epoch_millis = ${System.currentTimeMillis()} " +
                    "WHERE micro_options_json IS NOT NULL",
            )
        }
    }
/**
 * v7 → v8: per-lift increment overrides become equipment clones (STR-85).
 *
 * For every lift with lift-scoped tier rows (`increment_tiers.liftId` set) and/or a
 * lift-scoped micro override (`lifts.micro_options_json` non-null):
 * - creates equipment "<lift name> Stack" (name deduped) carrying the override bands and
 *   micro stops,
 * - reassigns the lift to the clone and clears its micro override,
 * - deletes the lift-scoped tier rows.
 *
 * Sync parity: new/changed rows are stamped `pending_sync = PENDING_UPSERT` so the upload
 * engine pushes them; deleted lift-scoped tier doc ids are enqueued in
 * `pending_cloud_deletes` so the stale cloud docs are tombstoned.
 *
 * New tier ids match `IncrementTierId.forEquipmentBand` by reusing the bound suffix of the
 * old id: 'e_' || newEquipmentId || '_' || substr(old_id, length(liftId)+2).
 * The `liftId` column itself is kept in this version; STR-84 drops it.
 */
val MIGRATION_7_8: Migration =
    object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            db.query(
                """
                SELECT l.id, l.name, l.micro_options_json
                FROM lifts l
                WHERE EXISTS (SELECT 1 FROM increment_tiers t WHERE t.liftId = l.id)
                   OR l.micro_options_json IS NOT NULL
                """.trimIndent(),
            ).use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow("id")
                val nameIdx = cursor.getColumnIndexOrThrow("name")
                val microIdx = cursor.getColumnIndexOrThrow("micro_options_json")
                while (cursor.moveToNext()) {
                    val liftId = cursor.getString(idIdx)
                    val liftName = cursor.getString(nameIdx)
                    val microJson = if (cursor.isNull(microIdx)) null else cursor.getString(microIdx)
                    migrateLiftOverride(db, liftId, liftName, microJson, now)
                }
            }
        }

        private fun migrateLiftOverride(
            db: SupportSQLiteDatabase,
            liftId: String,
            liftName: String,
            microJson: String?,
            now: Long,
        ) {
            val equipmentId = UUID.randomUUID().toString()
            val cloneName = uniqueCloneName(db, "$liftName Stack")
            db.execSQL(
                """
                INSERT INTO equipment
                  (id, name, isCustom, micro_options_json, updated_at_epoch_millis, pending_sync)
                VALUES (?, ?, 1, ?, ?, 1)
                """.trimIndent(),
                arrayOf<Any?>(equipmentId, cloneName, microJson, now),
            )
            db.execSQL(
                """
                INSERT INTO increment_tiers
                  (id, equipmentId, lowerBound, upperBound, increment,
                   updated_at_epoch_millis, pending_sync)
                SELECT 'e_' || ? || '_' || substr(id, length(?) + 2), ?,
                       lowerBound, upperBound, increment, ?, 1
                FROM increment_tiers
                WHERE liftId = ?
                """.trimIndent(),
                arrayOf<Any?>(equipmentId, liftId, equipmentId, now, liftId),
            )
            db.execSQL(
                """
                INSERT OR REPLACE INTO pending_cloud_deletes
                  (row_id, collection_path, entity_id, enqueued_at_epoch_millis)
                SELECT 'increment_tiers/' || id, 'increment_tiers', id, ?
                FROM increment_tiers
                WHERE liftId = ?
                """.trimIndent(),
                arrayOf<Any?>(now, liftId),
            )
            db.execSQL("DELETE FROM increment_tiers WHERE liftId = ?", arrayOf<Any?>(liftId))
            db.execSQL(
                """
                UPDATE lifts
                SET equipmentId = ?, micro_options_json = NULL,
                    updated_at_epoch_millis = ?, pending_sync = 1
                WHERE id = ?
                """.trimIndent(),
                arrayOf<Any?>(equipmentId, now, liftId),
            )
        }

        private fun uniqueCloneName(db: SupportSQLiteDatabase, base: String): String {
            var name = base
            var suffix = 2
            while (
                db.query(
                    "SELECT 1 FROM equipment WHERE name = ? LIMIT 1",
                    arrayOf<Any?>(name),
                ).use { it.count > 0 }
            ) {
                name = "$base $suffix"
                suffix++
            }
            return name
        }
    }
