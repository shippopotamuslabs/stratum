package com.stratum.core.data.db

import androidx.room.Room
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutSupersetGroupId
import org.junit.jupiter.api.Assertions.assertEquals
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
 * Schema invariants and converter tests. Room v4 adds `partialReps` on logged sets
 * via AutoMigration(3→4). v3 added lift/workout pinned notes and `lift_session_notes`.
 */
@ExtendWith(RobolectricExtension::class)
class ProgramSchemaContractTest {
    @Nested
    @DisplayName("DB version is 4 and program tables exist")
    inner class SchemaInvariants {
        @Test
        fun `the four program tables exist in the database`() {
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
            assertTrue("lift_session_notes" in tableNames, "lift_session_notes table missing")
        }

        @Test
        fun `v1 through v4 schema exports exist`() {
            val schemasDir = java.io.File("schemas/com.stratum.core.data.db.StratumDatabase")
            assertTrue(schemasDir.resolve("1.json").exists(), "1.json schema export missing")
            assertTrue(schemasDir.resolve("2.json").exists(), "2.json schema export missing")
            assertTrue(schemasDir.resolve("3.json").exists(), "3.json schema export missing")
            assertTrue(schemasDir.resolve("4.json").exists(), "4.json schema export missing")
        }

        @Test
        fun `v4 schema includes partialReps on logged_sets`() {
            val schema = java.io.File("schemas/com.stratum.core.data.db.StratumDatabase/4.json").readText()
            assertTrue(schema.contains("\"partialReps\""), "v4 schema must include partialReps")
            assertTrue(schema.contains("\"version\": 4"), "schema file must declare version 4")
        }

        @Test
        fun `v3 schema includes pinned_note and lift_session_notes`() {
            val schema = java.io.File("schemas/com.stratum.core.data.db.StratumDatabase/3.json").readText()
            assertTrue(schema.contains("\"pinned_note\""), "v3 schema must include pinned_note")
            assertTrue(schema.contains("\"lift_session_notes\""), "v3 schema must include lift_session_notes")
            assertTrue(schema.contains("\"version\": 3"), "schema file must declare version 3")
        }

        @Test
        fun `v2 schema includes myo_rep_kind on sets`() {
            val schema = java.io.File("schemas/com.stratum.core.data.db.StratumDatabase/2.json").readText()
            assertTrue(schema.contains("\"myo_rep_kind\""), "v2 schema must include myo_rep_kind")
            assertTrue(schema.contains("\"version\": 2"), "schema file must declare version 2")
        }
    }

    @Nested
    @DisplayName("EnumConverters round-trip OS-7 enums and typed ids")
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
        fun `MyoRepKind round-trips and rejects unknown values`() {
            assertEquals(
                "RepFloor",
                converters.fromMyoRepKind(com.stratum.domain.workout.myorep.MyoRepKind.RepFloor),
            )
            assertEquals(
                com.stratum.domain.workout.myorep.MyoRepKind.RepMatching,
                converters.toMyoRepKind("RepMatching"),
            )
            assertNull(converters.toMyoRepKind("Match"))
            assertNull(converters.toMyoRepKind(null))
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
            assertNull(converters.toProgramId(""))
            assertNull(converters.toProgramId(null))
        }

        @Test
        fun `WorkoutSupersetGroupId round-trips and rejects blank values`() {
            assertEquals("sg-1", converters.fromWorkoutSupersetGroupId(WorkoutSupersetGroupId("sg-1")))
            assertEquals(
                WorkoutSupersetGroupId("sg-1"),
                converters.toWorkoutSupersetGroupId("sg-1"),
            )
            assertNull(converters.toWorkoutSupersetGroupId(""))
            assertNull(converters.toWorkoutSupersetGroupId(null))
        }
    }
}
