package com.stratum.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
