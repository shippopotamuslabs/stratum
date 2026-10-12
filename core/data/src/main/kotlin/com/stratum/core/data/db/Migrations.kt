package com.stratum.core.data.db

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.stratum.core.data.sync.SyncFlags
import java.util.UUID

/**
 * Manual migrations applied by the production database builder. Single source shared by
 * `DataModule` and `DatabaseMigrationChainTest` so a version bump without a registered
 * migration fails the chain test instead of wiping user data via the destructive fallback.
 * Auto-migrations (1→2 … 6→7) come from the `@Database` annotations, not this list.
 */
fun RoomDatabase.Builder<StratumDatabase>.withProductionMigrations(): RoomDatabase.Builder<StratumDatabase> =
    addMigrations(
        MIGRATION_4_5,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_12_13,
        MIGRATION_13_14,
    )

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

/**
 * Backfills [column] on [table] from [parentColumn] on [parentTable] for rows whose
 * keys match ([childKeyColumn] on the child, [parentKeyColumn] on the parent). Rows
 * with no matching parent keep null. Emits the correlated UPDATE previously inlined
 * per migration.
 */
internal fun SupportSQLiteDatabase.backfillColumnFromParent(
    table: String,
    column: String,
    parentTable: String,
    parentColumn: String,
    childKeyColumn: String,
    parentKeyColumn: String = "id",
) {
    execSQL(
        "UPDATE `$table` SET `$column` = (" +
            "SELECT `$parentColumn` FROM `$parentTable` " +
            "WHERE `$parentTable`.`$parentKeyColumn` = `$table`.`$childKeyColumn`)",
    )
}

/**
 * Stamps rows matching [where] as locally-modified sync payload
 * (`pending_sync = PENDING_UPSERT` with a bumped clock) so the upload engine pushes
 * the backfilled data (MIGRATION_7_8 pattern). [where] is a static column predicate
 * over [table].
 */
internal fun SupportSQLiteDatabase.markDirtyWhere(
    table: String,
    where: String,
    now: Long = System.currentTimeMillis(),
) {
    execSQL(
        "UPDATE `$table` SET `pending_sync` = ${SyncFlags.PENDING_UPSERT}, " +
            "`updated_at_epoch_millis` = ? WHERE $where",
        arrayOf<Any?>(now),
    )
}

/**
 * Creates a non-unique index unless it already exists.
 */
internal fun SupportSQLiteDatabase.createIndexIfNotExists(
    indexName: String,
    table: String,
    vararg columns: String,
) {
    execSQL(
        "CREATE INDEX IF NOT EXISTS `$indexName` ON `$table` (${columns.joinToString { "`$it`" }})",
    )
}

/**
 * v9 → v10: set-level equipment logging (STR-118).
 *
 * - Adds nullable `logged_sets.equipmentId` (bare column, no FK — same as `liftId`)
 *   plus a `(liftId, equipmentId)` index for per-pair history lookups.
 * - Backfills every set from its lift's current equipment. Sets with no lift (null
 *   `liftId`) or a lift with no equipment keep null (legacy/unknown).
 *
 * Sync parity: backfilled rows carry new information the cloud lacks, so they are
 * stamped `pending_sync = PENDING_UPSERT` with a bumped clock and the upload engine
 * pushes them (MIGRATION_7_8 pattern). Rows staying null are unchanged versus cloud —
 * a missing `equipment_id` key decodes to null — and stay clean.
 */
val MIGRATION_9_10: Migration =
    object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `logged_sets` ADD COLUMN `equipmentId` TEXT")
            db.backfillColumnFromParent(
                table = "logged_sets",
                column = "equipmentId",
                parentTable = "lifts",
                parentColumn = "equipmentId",
                childKeyColumn = "liftId",
            )
            db.markDirtyWhere(table = "logged_sets", where = "`equipmentId` IS NOT NULL")
            db.createIndexIfNotExists(
                indexName = "index_logged_sets_liftId_equipmentId",
                table = "logged_sets",
                "liftId",
                "equipmentId",
            )
        }
    }

/**
 * v12 → v13: PR unique index follows STR-123's per-(lift, equipment) PR identity (STR-129).
 *
 * - Drops the stale UNIQUE(liftId, isPersonalRecord) index, which crashed workout
 *   finalize with SQLiteConstraintException whenever one session earned PRs for the
 *   same lift on two different equipments.
 * - Creates UNIQUE(liftId, equipmentId, isPersonalRecord) instead.
 * - Safe with no backfill: existing rows satisfy the old index, so they trivially
 *   satisfy the new (strictly more permissive) one.
 */
val MIGRATION_12_13: Migration =
    object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP INDEX IF EXISTS `index_logged_sets_liftId_isPersonalRecord`")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "`index_logged_sets_liftId_equipmentId_isPersonalRecord` " +
                    "ON `logged_sets` (`liftId`, `equipmentId`, `isPersonalRecord`)",
            )
        }
    }

/**
 * v13 → v14: slot pinned notes are scoped to the (slot, lift) pair (STR-127).
 *
 * - Adds nullable `lift_id` to `workout_lift_notes` (plus an FK to `lifts`) via
 *   table rebuild — SQLite cannot add a foreign key with ALTER TABLE.
 * - Backfills every existing row from its slot's current lift: pre-v14 notes were
 *   written for whichever lift occupied the slot, which is exactly that lift.
 *   Rows whose slot vanished keep null (unscoped legacy) instead of dropping.
 * - No sync restamp: the pair scope is derivable on every device from the slot's
 *   lift, and the encoder starts sending `lift_id` with the next write.
 */
val MIGRATION_13_14: Migration =
    object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `workout_lift_notes_new` (
                  `id` TEXT NOT NULL,
                  `workout_lift_id` TEXT NOT NULL,
                  `lift_id` TEXT,
                  `body` TEXT NOT NULL,
                  `position` INTEGER NOT NULL,
                  `created_at_epoch_millis` INTEGER NOT NULL,
                  `updated_at_epoch_millis` INTEGER NOT NULL,
                  `pending_sync` INTEGER NOT NULL,
                  PRIMARY KEY(`id`),
                  FOREIGN KEY(`workout_lift_id`) REFERENCES `workout_lifts`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                  FOREIGN KEY(`lift_id`) REFERENCES `lifts`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO `workout_lift_notes_new`
                  (`id`, `workout_lift_id`, `lift_id`, `body`, `position`,
                   `created_at_epoch_millis`, `updated_at_epoch_millis`, `pending_sync`)
                SELECT n.`id`, n.`workout_lift_id`, wl.`lift_id`, n.`body`, n.`position`,
                       n.`created_at_epoch_millis`, n.`updated_at_epoch_millis`, n.`pending_sync`
                FROM `workout_lift_notes` n
                LEFT JOIN `workout_lifts` wl ON wl.`id` = n.`workout_lift_id`
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE `workout_lift_notes`")
            db.execSQL("ALTER TABLE `workout_lift_notes_new` RENAME TO `workout_lift_notes`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_workout_lift_notes_workout_lift_id` " +
                    "ON `workout_lift_notes` (`workout_lift_id`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_workout_lift_notes_workout_lift_id_position` " +
                    "ON `workout_lift_notes` (`workout_lift_id`, `position`)",
            )
        }
    }
