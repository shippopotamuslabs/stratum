package com.stratum.core.data.sync.appliers

import androidx.room.Room
import com.stratum.core.data.db.LiftEntity
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.data.sync.FirestoreSyncMetadata
import com.stratum.core.data.sync.SyncFlags
import com.stratum.core.data.sync.codecs.SyncEntityDecoders
import com.stratum.core.data.sync.codecs.SyncEntityEncoders
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class LiftRemoteApplierMatrixTest {
    private lateinit var database: StratumDatabase
    private lateinit var subject: LiftRemoteApplier

    @BeforeEach
    fun setup() {
        database =
            Room.inMemoryDatabaseBuilder(
                RuntimeEnvironment.getApplication(),
                StratumDatabase::class.java,
            ).allowMainThreadQueries().build()
        val encoders = SyncEntityEncoders()
        val decoders = SyncEntityDecoders()
        subject = LiftRemoteApplier(database, database.liftDao(), encoders, decoders)
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    @DisplayName("overlay + catalog seed applies enabled/rest CLEAN")
    fun overlayOnCatalogSeed() = runTest {
        seedCatalog("bench")
        val ok =
            subject.upsertFromRemote(
                "bench",
                mapOf(
                    "catalog_overlay" to true,
                    "is_custom" to false,
                    "enabled" to false,
                    "default_rest_seconds" to 120,
                    FirestoreSyncMetadata.UPDATED_AT to 50L,
                ),
            )
        assertTrue(ok)
        val row = database.liftDao().getById(LiftId("bench"))!!
        assertFalse(row.enabled)
        assertEquals(120, row.defaultRestSeconds)
        assertEquals(SyncFlags.CLEAN, row.pendingSync)
        assertEquals(50L, row.updatedAtEpochMillis)
    }

    @Test
    @DisplayName("overlay without local seed fails for retry after catalog seed")
    fun overlayMissingSeedFails() = runTest {
        val ok =
            subject.upsertFromRemote(
                "missing",
                mapOf(
                    "catalog_overlay" to true,
                    "enabled" to true,
                    FirestoreSyncMetadata.UPDATED_AT to 1L,
                ),
            )
        assertFalse(ok)
    }

    @Test
    @DisplayName("custom full path decodes when payload is complete")
    fun customFullUpsert() = runTest {
        val ok =
            subject.upsertFromRemote(
                "custom-1",
                mapOf(
                    "is_custom" to true,
                    "name" to "My Lift",
                    "movement_patterns" to listOf(MovementPattern.HorizontalPress.name),
                    "primary_muscles" to listOf(MuscleGroup.Chest.name),
                    "secondary_muscles" to emptyList<String>(),
                    "preset_tags" to emptyList<String>(),
                    "enabled" to true,
                    "default_rest_seconds" to 90,
                    FirestoreSyncMetadata.UPDATED_AT to 10L,
                ),
            )
        assertTrue(ok)
        val row = database.liftDao().getById(LiftId("custom-1"))!!
        assertTrue(row.isCustom)
        assertEquals("My Lift", row.name)
    }

    @Test
    @DisplayName("catalog encode is overlay-only")
    fun catalogEncodeIsOverlay() = runTest {
        val entity =
            seedCatalog("squat").copy(enabled = false, defaultRestSeconds = 180)
        val payload = subject.encode(entity)
        assertEquals(true, payload["catalog_overlay"])
        assertEquals(false, payload["is_custom"])
        assertEquals(false, payload["enabled"])
        assertEquals(180, payload["default_rest_seconds"])
        assertEquals("", payload["pinned_note"])
        assertFalse(payload.containsKey("name"))
    }

    @Test
    @DisplayName("unknown remote shape (neither overlay nor custom) is rejected")
    fun unknownShapeRejected() = runTest {
        seedCatalog("bench")
        val ok =
            subject.upsertFromRemote(
                "bench",
                mapOf(
                    "name" to "Bench Press",
                    "enabled" to false,
                    FirestoreSyncMetadata.UPDATED_AT to 99L,
                ),
            )
        assertFalse(ok)
        val row = database.liftDao().getById(LiftId("bench"))!!
        assertTrue(row.enabled) // unchanged seed
    }

    @Test
    @DisplayName("overlay without pinned_note keeps the local catalog pin")
    fun overlayMissingPinnedNotePreservesLocal() = runTest {
        seedCatalog("bench").let { row ->
            database.liftDao().upsert(row.copy(pinnedNote = "Belt on"))
        }
        val ok =
            subject.upsertFromRemote(
                "bench",
                mapOf(
                    "catalog_overlay" to true,
                    "is_custom" to false,
                    "enabled" to true,
                    FirestoreSyncMetadata.UPDATED_AT to 50L,
                ),
            )
        assertTrue(ok)
        assertEquals("Belt on", database.liftDao().getById(LiftId("bench"))!!.pinnedNote)
    }

    @Test
    @DisplayName("overlay empty pinned_note clears the local catalog pin")
    fun overlayEmptyPinnedNoteClearsLocal() = runTest {
        seedCatalog("bench").let { row ->
            database.liftDao().upsert(row.copy(pinnedNote = "Belt on"))
        }
        val ok =
            subject.upsertFromRemote(
                "bench",
                mapOf(
                    "catalog_overlay" to true,
                    "is_custom" to false,
                    "pinned_note" to "",
                    FirestoreSyncMetadata.UPDATED_AT to 50L,
                ),
            )
        assertTrue(ok)
        assertEquals(null, database.liftDao().getById(LiftId("bench"))!!.pinnedNote)
    }

    @Test
    @DisplayName("overlay micro_options is ignored (per-lift overrides removed, STR-83)")
    fun overlayMicroOptionsIgnored() = runTest {
        seedCatalog("bench")
        val ok =
            subject.upsertFromRemote(
                "bench",
                mapOf(
                    "catalog_overlay" to true,
                    "is_custom" to false,
                    "micro_options" to listOf(2.5),
                    FirestoreSyncMetadata.UPDATED_AT to 50L,
                ),
            )
        assertTrue(ok)
        val row = database.liftDao().getById(LiftId("bench"))!!
        assertEquals(null, row.microOptionsJson)
        assertEquals(null, subject.encode(row)["micro_options"])
    }

    @Test
    @DisplayName("overlay without micro_options leaves the column untouched")
    fun overlayMissingMicroOptionsLeavesColumnUntouched() = runTest {
        seedCatalog("bench").let { row ->
            database.liftDao().upsert(row.copy(microOptionsJson = "[2.5]"))
        }
        val ok =
            subject.upsertFromRemote(
                "bench",
                mapOf(
                    "catalog_overlay" to true,
                    "is_custom" to false,
                    "enabled" to true,
                    FirestoreSyncMetadata.UPDATED_AT to 50L,
                ),
            )
        assertTrue(ok)
        assertEquals("[2.5]", database.liftDao().getById(LiftId("bench"))!!.microOptionsJson)
    }

    @Test
    @DisplayName("STR-131: received seed tombstone applies the flag and sweeps local template slots")
    fun overlayTombstoneSweepsLocalSlots() = runTest {
        seedCatalog("bench")
        seedProgramWithSlot("bench")
        val ok =
            subject.upsertFromRemote(
                "bench",
                mapOf(
                    "catalog_overlay" to true,
                    "is_custom" to false,
                    "is_deleted" to true,
                    FirestoreSyncMetadata.UPDATED_AT to 60L,
                ),
            )
        assertTrue(ok)
        val row = database.liftDao().getById(LiftId("bench"))!!
        assertTrue(row.isDeleted)
        // The local slot pointing at the tombstoned lift is swept (Scope 5).
        assertTrue(database.workoutLiftDao().getAll().isEmpty())
        // Re-applying the same tombstone is idempotent.
        assertTrue(
            subject.upsertFromRemote(
                "bench",
                mapOf(
                    "catalog_overlay" to true,
                    "is_custom" to false,
                    "is_deleted" to true,
                    FirestoreSyncMetadata.UPDATED_AT to 61L,
                ),
            ),
        )
        assertTrue(database.liftDao().getById(LiftId("bench"))!!.isDeleted)
    }

    @Test
    @DisplayName("STR-131: old-payload upsert without is_deleted never un-deletes a tombstoned custom")
    fun oldPayloadDoesNotUndelete() = runTest {
        val custom =
            LiftEntity(
                id = LiftId("mine"),
                name = "Mine",
                movementPatternsBitmask = 0,
                primaryMusclesBitmask = 0,
                secondaryMusclesBitmask = 0,
                presetTags = ",",
                isCustom = true,
                enabled = true,
                isDeleted = true,
                updatedAtEpochMillis = 5L,
                pendingSync = SyncFlags.CLEAN,
            )
        database.liftDao().upsert(custom)
        val ok =
            subject.upsertFromRemote(
                "mine",
                mapOf(
                    "is_custom" to true,
                    "name" to "Mine",
                    "movement_patterns" to emptyList<String>(),
                    "primary_muscles" to emptyList<String>(),
                    "secondary_muscles" to emptyList<String>(),
                    "preset_tags" to emptyList<String>(),
                    "enabled" to true,
                    FirestoreSyncMetadata.UPDATED_AT to 90L,
                ),
            )
        assertTrue(ok)
        assertTrue(database.liftDao().getById(LiftId("mine"))!!.isDeleted)
    }

    private suspend fun seedProgramWithSlot(liftId: String) {
        database.programDao().upsert(
            com.stratum.core.data.db.ProgramEntity(
                id = com.stratum.domain.program.identity.ProgramId("p-1"),
                name = "Main",
                createdAt = "2026-01-01T00:00:00Z",
                cycleLength = 1,
                isActive = true,
            ),
        )
        database.workoutDao().upsertAll(
            listOf(
                com.stratum.core.data.db.WorkoutEntity(
                    id = com.stratum.domain.program.identity.WorkoutId("w-1"),
                    programId = com.stratum.domain.program.identity.ProgramId("p-1"),
                    name = "Push",
                    weekday = null,
                    position = 0,
                ),
            ),
        )
        database.workoutLiftDao().upsertAll(
            listOf(
                com.stratum.core.data.db.WorkoutLiftEntity(
                    id = com.stratum.domain.program.identity.WorkoutLiftId("wl-1"),
                    workoutId = com.stratum.domain.program.identity.WorkoutId("w-1"),
                    liftId = LiftId(liftId),
                    progressionScheme = com.stratum.domain.program.ProgressionScheme.Linear,
                    position = 0,
                ),
            ),
        )
    }

    private suspend fun seedCatalog(id: String): LiftEntity {
        val entity =
            LiftEntity(
                id = LiftId(id),
                name = id.replaceFirstChar { it.uppercase() },
                movementPatternsBitmask = 1 shl MovementPattern.HorizontalPress.ordinal,
                primaryMusclesBitmask = 1 shl MuscleGroup.Chest.ordinal,
                secondaryMusclesBitmask = 0,
                presetTags = ",",
                isCustom = false,
                enabled = true,
                defaultRestSeconds = 90,
                updatedAtEpochMillis = 1L,
                pendingSync = SyncFlags.CLEAN,
            )
        database.liftDao().upsert(entity)
        return entity
    }
}
