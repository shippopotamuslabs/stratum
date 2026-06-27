package com.stratum.core.data.db

import androidx.room.Room
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * OS-7 schema invariants and converter tests. Scenarios PROG-GEN-301 (DB stays at v1
 * with the four program tables, no migration) and PROG-GEN-302 (EnumConverters round-trips the
 * OS-7 enums and typed ids).
 *
 * PROG-GEN-301 observes the invariant at runtime through a real in-memory database (no reflection):
 * the four tables exist and a `2.json` schema export is absent. These pin the contract against a
 * later version bump.
 */
@ExtendWith(RobolectricExtension::class)
class ProgramSchemaContractTest {
    @Nested
    @DisplayName("PROG-GEN-301 DB version is 1 and 4 program tables exist")
    inner class SchemaInvariants {
        @Test
        fun `the four OS-7 program tables exist in the database`() {
            val database = Room.inMemoryDatabaseBuilder(
                RuntimeEnvironment.getApplication(),
                StratumDatabase::class.java,
            ).allowMainThreadQueries().build()
            val tableNames = database.openHelper.writableDatabase.query(
                "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name",
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
            }.toSet()
            database.close()

            assertTrue("programs" in tableNames, "programs table missing")
            assertTrue("workouts" in tableNames, "workouts table missing")
            assertTrue("workout_lifts" in tableNames, "workout_lifts table missing")
            assertTrue("sets" in tableNames, "sets table missing")
        }

        @Test
        fun `no 2_json schema export exists yet`() {
            // The source schema dir lives at core/data/schemas/...; a 2.json would only appear after
            // a version bump + build. Assert it is absent (version stays 1, no migration authored).
            val schemasDir = java.io.File("schemas/com.stratum.core.data.db.StratumDatabase")
            assertFalse(schemasDir.resolve("2.json").exists())
        }
    }

    @Nested
    @DisplayName("PROG-GEN-302 EnumConverters round-trip OS-7 enums and typed ids")
    inner class ConverterRoundTrips {
        private val converters = EnumConverters()

        @Test
        fun `ProgressionScheme round-trips and rejects unknown values`() {
            assertEquals("Linear", converters.fromProgressionScheme(ProgressionScheme.Linear))
            assertEquals(ProgressionScheme.Linear, converters.toProgressionScheme("Linear"))
            assertEquals("Double", converters.fromProgressionScheme(ProgressionScheme.Double))
            assertEquals(ProgressionScheme.Double, converters.toProgressionScheme("Double"))
            assertNull(converters.toProgressionScheme("Wave"))
            assertNull(converters.toProgressionScheme(null))
        }

        @Test
        fun `SetType round-trips and rejects unknown values`() {
            assertEquals("MyoRep", converters.fromSetType(SetType.MyoRep))
            assertEquals(SetType.MyoRep, converters.toSetType("MyoRep"))
            assertEquals(SetType.DropSet, converters.toSetType("DropSet"))
            assertNull(converters.toSetType("Drop"))
        }

        @Test
        fun `typed program ids round-trip and reject blank values`() {
            assertEquals("p-1", converters.fromProgramId(ProgramId("p-1")))
            assertEquals(ProgramId("p-1"), converters.toProgramId("p-1"))
            assertNotNull(converters.toWorkoutId("w-1"))
            assertNotNull(converters.toWorkoutLiftId("wl-1"))
            assertNotNull(converters.toLiftSetId("ls-1"))
            assertEquals(WorkoutId("w-1"), converters.toWorkoutId("w-1"))
            assertEquals(WorkoutLiftId("wl-1"), converters.toWorkoutLiftId("wl-1"))
            assertEquals(LiftSetId("ls-1"), converters.toLiftSetId("ls-1"))
            // A blank stored value cannot re-wrap (value classes reject blank), so it decodes null.
            assertNull(converters.toProgramId(""))
            assertNull(converters.toProgramId(null))
        }
    }
}
