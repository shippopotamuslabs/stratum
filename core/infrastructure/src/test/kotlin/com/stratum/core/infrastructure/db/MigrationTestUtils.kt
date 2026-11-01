package com.stratum.core.infrastructure.db

import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Shared harness for Room migration tests: schema-version knowledge, helper
 * wiring, and cursor plumbing live here once instead of per test class.
 */
object MigrationTestUtils {
    /**
     * Current schema version — [STRATUM_SCHEMA_VERSION], the const the `@Database`
     * annotation itself references, so no throwaway database build is required.
     * (The annotation is not runtime-retained, so reflection cannot read it.)
     */
    val currentSchemaVersion: Int = STRATUM_SCHEMA_VERSION

    /**
     * Standard helper: the schema comes from the exported JSON, so no migrations
     * are registered; the framework factory matches production SQLite behavior.
     */
    fun newMigrationHelper(): MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            StratumDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    /**
     * Runs [sql] with optional [args] and maps every row to a cell list
     * (INTEGER → Long, FLOAT → Double, NULL → null, anything else → String).
     */
    fun rows(
        db: SupportSQLiteDatabase,
        sql: String,
        args: Array<Any?> = emptyArray(),
    ): List<List<Any?>> =
        (if (args.isEmpty()) db.query(sql) else db.query(sql, args)).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        List(cursor.columnCount) { index ->
                            when (cursor.getType(index)) {
                                Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                                Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                                Cursor.FIELD_TYPE_NULL -> null
                                else -> cursor.getString(index)
                            }
                        },
                    )
                }
            }
        }

    /**
     * Runs [sql] with optional [args] and returns its single cell.
     */
    fun single(db: SupportSQLiteDatabase, sql: String, args: Array<Any?> = emptyArray()): Any? =
        rows(db, sql, args).single().single()
}
