package com.stratum.core.data.repository

import androidx.room.Room
import app.cash.turbine.test
import com.stratum.core.data.db.EquipmentEntity
import com.stratum.core.data.db.LiftEntity
import com.stratum.core.data.mapper.toBitmask
import com.stratum.core.data.mapper.toPresetCsv
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.data.sync.SyncCollection
import com.stratum.core.data.sync.SyncFlags
import com.stratum.domain.lift.CatalogNoteInput
import com.stratum.domain.lift.CustomLiftDefinition
import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class RoomLiftRepositoryTest {

    private lateinit var database: StratumDatabase
    private lateinit var subject: RoomLiftRepository

    @BeforeEach
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StratumDatabase::class.java,
        ).allowMainThreadQueries().build()
        val syncTouch =
            com.stratum.core.data.sync.testSyncTouch(
                RuntimeEnvironment.getApplication(),
                database.pendingCloudDeleteDao(),
            )
        val equipmentRepository =
            RoomEquipmentRepository(
                database = database,
                equipmentDao = database.equipmentDao(),
                incrementTierDao = database.incrementTierDao(),
                liftDao = database.liftDao(),
                syncTouch = syncTouch,
            )
        subject = RoomLiftRepository(
            database = database,
            liftDao = database.liftDao(),
            incrementTierDao = database.incrementTierDao(),
            equipmentDao = database.equipmentDao(),
            equipmentRepository = equipmentRepository,
            gymPreferenceDao = database.gymPreferenceDao(),
            syncTouch = syncTouch,
        )
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Nested
    @DisplayName("observe / toggle")
    inner class ObserveAndToggle {
        @Test
        @DisplayName("Given an empty catalog when observed then an empty list is emitted")
        fun emptyCatalogEmitsEmpty() = runTest {
            subject.observeLifts().test {
                assertEquals(emptyList<Any>(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("Given a seeded lift when its toggle flips then observe emits the new enabled state")
        fun togglePersists() = runTest {
            val id = LiftId("leg-press")
            seedLift(id, enabled = false)

            subject.setLiftEnabled(id, enabled = true)

            subject.observeLifts().test {
                val emitted = awaitItem()
                assertEquals(1, emitted.size)
                assertTrue(emitted.single().enabled)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    @DisplayName("preset")
    inner class Preset {
        @Test
        @DisplayName("Given lifts tagged for a preset when applyPreset runs then all tagged lifts enable")
        fun applyPresetEnablesTagged() = runTest {
            seedLift(LiftId("bench-press"), tags = setOf(GymPreset.CommercialGym, GymPreset.GarageGym), enabled = false)
            seedLift(LiftId("leg-press"), tags = setOf(GymPreset.CommercialGym), enabled = false)
            seedLift(LiftId("cable-fly"), tags = setOf(GymPreset.CommercialGym), enabled = false)

            subject.applyPreset(GymPreset.CommercialGym)

            val lifts = firstLifts()
            assertEquals(3, lifts.count { it.enabled })
        }

        @Test
        @DisplayName("Given a preset when applyPreset runs then it does not enable untagged lifts")
        fun applyPresetLeavesUntaggedAlone() = runTest {
            seedLift(LiftId("bench-press"), tags = setOf(GymPreset.GarageGym), enabled = false)

            subject.applyPreset(GymPreset.CommercialGym)

            val lifts = firstLifts()
            assertTrue(lifts.none { it.enabled })
        }

        @Test
        @DisplayName("Given a preset choice when saved then observe emits it")
        fun presetChoicePersists() = runTest {
            subject.setSelectedPreset(GymPreset.CommercialGym)

            subject.observeSelectedPreset().test {
                assertEquals(GymPreset.CommercialGym, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("Given a cleared preset when saved then observe emits null")
        fun presetChoiceClears() = runTest {
            subject.setSelectedPreset(GymPreset.CommercialGym)
            subject.setSelectedPreset(null)

            subject.observeSelectedPreset().test {
                assertEquals(null, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    @DisplayName("custom lift")
    inner class CustomLift {
        @Test
        @DisplayName("Given a definition with increment when a custom lift is added then it appears enabled with isCustom")
        fun customLiftAppearsEnabled() = runTest {
            val id = subject.addCustomLift(
                CustomLiftDefinition(
                    name = "Landmine Press",
                    movementPatterns = setOf(MovementPattern.HorizontalPress),
                    primaryMuscles = setOf(MuscleGroup.Chest),
                    increment = Weight(5.0, Weight.WeightUnit.Lb),
                    muscleLengthBias = MuscleLengthBias.Lengthened,
                    fatigueProfile = FatigueProfile.Medium,
                    loadType = LoadType.Weighted,
                    defaultRestSeconds = 150,
                ),
            )

            assertNotNull(id)
            val lifts = firstLifts()
            val custom = lifts.single { it.id == id }
            assertEquals("Landmine Press", custom.name)
            assertTrue(custom.enabled)
            assertTrue(custom.isCustom)
            assertEquals(MuscleLengthBias.Lengthened, custom.muscleLengthBias)
            assertEquals(FatigueProfile.Medium, custom.fatigueProfile)
            assertEquals(LoadType.Weighted, custom.loadType)
            assertEquals(150, custom.defaultRestSeconds)
        }

        @Test
        @DisplayName("Given no increment when a custom lift is added then it still gets default tiers")
        fun customLiftWithoutIncrementGetsDefaultTiers() = runTest {
            val id = subject.addCustomLift(
                CustomLiftDefinition(
                    name = "Custom Move",
                    movementPatterns = setOf(MovementPattern.SquatPattern),
                    primaryMuscles = emptySet(),
                ),
            )

            val custom = firstLifts().single { it.id == id }
            assertEquals(4, custom.incrementTiers.size)
        }

        @Test
        @DisplayName("Given free-text 'cable' when custom lift is added then it links seed Cables not a custom row")
        fun freeTextCableLinksSeedEquipment() = runTest {
            val cableId = EquipmentId("cable")
            database.equipmentDao().upsert(
                EquipmentEntity(
                    id = cableId,
                    name = "Cables",
                    isCustom = false,
                    updatedAtEpochMillis = 0L,
                    pendingSync = SyncFlags.CLEAN,
                ),
            )

            val id = subject.addCustomLift(
                CustomLiftDefinition(
                    name = "Cable Fly",
                    movementPatterns = setOf(MovementPattern.HorizontalPress),
                    primaryMuscles = setOf(MuscleGroup.Chest),
                    equipmentName = "cable",
                ),
            )

            val custom = firstLifts().single { it.id == id }
            assertEquals(cableId, custom.equipmentId)
            assertEquals("Cables", custom.equipmentName)
            assertTrue(custom.usesEquipmentDefaultIncrements)
            // Must not mint a second equipment row for the free-text label.
            assertEquals(1, database.equipmentDao().getAll().size)
        }

        @Test
        @DisplayName("Given unknown free-text equipment when custom lift is added then equipment and lift are created together")
        fun freeTextCreatesEquipmentWithLiftAtomically() = runTest {
            val id = subject.addCustomLift(
                CustomLiftDefinition(
                    name = "Pro2 Pec Fly",
                    movementPatterns = setOf(MovementPattern.HorizontalPress),
                    primaryMuscles = setOf(MuscleGroup.Chest),
                    equipmentName = "Life Fitness Pro2 Stack",
                ),
            )

            val custom = firstLifts().single { it.id == id }
            val equipmentId = requireNotNull(custom.equipmentId)
            val equipment = database.equipmentDao().getById(equipmentId)
            assertNotNull(equipment)
            assertEquals("Life Fitness Pro2 Stack", equipment!!.name)
            assertTrue(equipment.isCustom)
            assertEquals(equipmentId, custom.equipmentId)
            // Single custom equipment row — no orphaned extras from a separate write.
            assertEquals(1, database.equipmentDao().getAll().size)
        }

        @Test
        @DisplayName("Given seed equipmentId when custom lift is added then it links that row without minting equipment")
        fun seedEquipmentIdLinksDirectly() = runTest {
            val platesId = EquipmentId("double-weight-plates")
            database.equipmentDao().upsert(
                EquipmentEntity(
                    id = platesId,
                    name = "Double Weight Plates",
                    isCustom = false,
                    updatedAtEpochMillis = 0L,
                    pendingSync = SyncFlags.CLEAN,
                ),
            )

            val id = subject.addCustomLift(
                CustomLiftDefinition(
                    name = "Seated Neutral Grip Plate-Loaded Machine Press",
                    movementPatterns = setOf(MovementPattern.HorizontalPress),
                    primaryMuscles = setOf(MuscleGroup.Chest),
                    equipmentId = platesId,
                ),
            )

            val custom = firstLifts().single { it.id == id }
            assertEquals(platesId, custom.equipmentId)
            assertEquals("Double Weight Plates", custom.equipmentName)
            assertTrue(custom.usesEquipmentDefaultIncrements)
            assertEquals(1, database.equipmentDao().getAll().size)
        }

        @Test
        @DisplayName("Given equipmentCreateIncrement when custom equipment is minted then open band uses that step")
        fun customEquipmentCreateIncrementApplied() = runTest {
            val id = subject.addCustomLift(
                CustomLiftDefinition(
                    name = "Pro2 Pec Fly",
                    movementPatterns = setOf(MovementPattern.HorizontalPress),
                    primaryMuscles = setOf(MuscleGroup.Chest),
                    equipmentName = "Life Fitness Pro2 Stack",
                    equipmentCreateIncrement = Weight(12.5, Weight.WeightUnit.Lb),
                ),
            )

            val custom = firstLifts().single { it.id == id }
            val equipmentId = requireNotNull(custom.equipmentId)
            val tiers = database.incrementTierDao().getForEquipment(equipmentId)
            assertEquals(1, tiers.size)
            assertEquals(12.5, tiers.single().increment)
            assertEquals(0.0, tiers.single().lowerBound)
            assertEquals(null, tiers.single().upperBound)
            assertTrue(custom.usesEquipmentDefaultIncrements)
        }

        @Test
        @DisplayName("Given Default equipment name when custom lift is added then shared Default row is find-or-created at +5")
        fun defaultEquipmentFindOrCreate() = runTest {
            val firstId =
                subject.addCustomLift(
                    CustomLiftDefinition(
                        name = "Unknown A",
                        movementPatterns = setOf(MovementPattern.ElbowExtension),
                        primaryMuscles = setOf(MuscleGroup.Tricep),
                        equipmentName = "Default",
                        equipmentCreateIncrement = Weight(5.0, Weight.WeightUnit.Lb),
                    ),
                )
            val secondId =
                subject.addCustomLift(
                    CustomLiftDefinition(
                        name = "Unknown B",
                        movementPatterns = setOf(MovementPattern.ElbowFlexion),
                        primaryMuscles = setOf(MuscleGroup.Bicep),
                        equipmentName = "Default",
                        equipmentCreateIncrement = Weight(5.0, Weight.WeightUnit.Lb),
                    ),
                )

            val first = firstLifts().single { it.id == firstId }
            val second = firstLifts().single { it.id == secondId }
            assertEquals(first.equipmentId, second.equipmentId)
            assertEquals("Default", first.equipmentName)
            assertEquals(1, database.equipmentDao().getAll().size)
            val tiers = database.incrementTierDao().getForEquipment(requireNotNull(first.equipmentId))
            assertEquals(5.0, tiers.single().increment)
        }
    }

    @Nested
    @DisplayName("observe by pattern")
    inner class ObserveByPattern {
        @Test
        @DisplayName("Given lifts with distinct patterns when observed by pattern then only matching lifts emit")
        fun observeLiftsContainingPatternFiltersByBitmask() = runTest {
            seedLift(LiftId("back-squat"), patterns = setOf(MovementPattern.SquatPattern))
            seedLift(LiftId("romanian-deadlift"), patterns = setOf(MovementPattern.HipHinge))
            // Multi-pattern lift carrying SquatPattern among others — must still match.
            seedLift(
                LiftId("front-squat"),
                patterns = setOf(MovementPattern.SquatPattern, MovementPattern.HipExtension),
            )

            subject.observeLiftsContainingPattern(MovementPattern.SquatPattern).test {
                val emitted = awaitItem().map { it.id }
                assertEquals(setOf(LiftId("back-squat"), LiftId("front-squat")), emitted.toSet())
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("Given no lifts carrying a pattern when observed by pattern then an empty list emits")
        fun observeLiftsContainingPatternEmptyWhenNoMatch() = runTest {
            seedLift(LiftId("back-squat"), patterns = setOf(MovementPattern.SquatPattern))

            subject.observeLiftsContainingPattern(MovementPattern.ElbowFlexion).test {
                assertEquals(emptyList<Any>(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    private suspend fun firstLifts(): List<com.stratum.domain.lift.Lift> =
        subject.observeLifts().first()

    private suspend fun seedLift(
        id: LiftId,
        tags: Set<GymPreset> = emptySet(),
        enabled: Boolean = false,
        patterns: Set<MovementPattern> = setOf(MovementPattern.SquatPattern),
    ) {
        // Use the production mapper so the test encodes exactly as the seeder/repository do.
        val tagCsv = tags.toPresetCsv()
        database.liftDao().upsert(
            LiftEntity(
                id = id,
                name = id.value,
                movementPatternsBitmask = patterns.toBitmask(),
                primaryMusclesBitmask = 1 shl MuscleGroup.Quad.ordinal,
                secondaryMusclesBitmask = 0,
                presetTags = tagCsv,
                isCustom = false,
                enabled = enabled,
            ),
        )
    }

    @Nested
    @DisplayName("pinned note lists")
    inner class PinnedNoteLists {
        @Test
        @DisplayName("Given two drafts when set then both persist ordered with a legacy mirror")
        fun setTwoNotesOrdered() = runTest {
            val id = LiftId("squat")
            seedLift(id)

            subject.setPinnedNotes(
                id,
                listOf(
                    CatalogNoteInput(body = "belt on"),
                    CatalogNoteInput(body = "knees out"),
                ),
            )

            val stored = subject.getPinnedNotes(id)
            assertEquals(listOf("belt on", "knees out"), stored.map { it.body })
            assertEquals(listOf(0, 1), stored.map { it.position })
            assertEquals("belt on", database.liftDao().getById(id)?.pinnedNote)
        }

        @Test
        @DisplayName("Given existing notes when re-saved then known ids are stable and blanks drop")
        fun resaveKeepsIds() = runTest {
            val id = LiftId("squat")
            seedLift(id)
            subject.setPinnedNotes(
                id,
                listOf(
                    CatalogNoteInput(body = "keep"),
                    CatalogNoteInput(body = "drop"),
                ),
            )
            val keptId = subject.getPinnedNotes(id).first().id

            subject.setPinnedNotes(
                id,
                listOf(
                    CatalogNoteInput(id = keptId, body = "keep edited"),
                    CatalogNoteInput(body = "   "),
                ),
            )

            val stored = subject.getPinnedNotes(id)
            assertEquals(listOf("keep edited"), stored.map { it.body })
            assertEquals(keptId, stored.single().id)
        }

        @Test
        @DisplayName("Given a missing lift when read or written then empty result and no-op")
        fun missingLiftIgnored() = runTest {
            val missing = LiftId("ghost")

            assertTrue(subject.getPinnedNotes(missing).isEmpty())
            subject.setPinnedNotes(
                missing,
                listOf(CatalogNoteInput(body = "orphan")),
            )

            assertTrue(subject.getPinnedNotes(missing).isEmpty())
        }

        @Test
        @DisplayName("Given notes when the lift is deleted then its rows cascade")
        fun deleteCascades() = runTest {
            val id = LiftId("squat")
            seedLift(id)
            subject.setPinnedNotes(
                id,
                listOf(CatalogNoteInput(body = "pin")),
            )

            subject.deleteLifts(listOf(id))

            assertTrue(subject.getPinnedNotes(id).isEmpty())
        }

        @Test
        @DisplayName("Given only a legacy pin when read then it surfaces as a single note")
        fun legacyFallback() = runTest {
            val id = LiftId("squat")
            seedLift(id)
            database.liftDao().setPinnedNote(id, "legacy", System.currentTimeMillis())

            val stored = subject.getPinnedNotes(id)
            assertEquals(listOf("legacy"), stored.map { it.body })
        }

        @Test
        @DisplayName("Given a backfilled row when scalar-edited then the table row updates, not just the mirror")
        fun scalarEditUpdatesTableRow() = runTest {
            val id = LiftId("squat")
            seedLift(id)
            database.liftDao().setPinnedNote(id, "legacy", 1L)
            subject.setPinnedNotes(id, listOf(CatalogNoteInput(body = "migrated")))
            val rowId = subject.getPinnedNotes(id).single().id

            subject.setPinnedNote(id, "edited")

            val stored = subject.getPinnedNotes(id)
            assertEquals(listOf("edited"), stored.map { it.body })
            assertEquals(rowId, stored.single().id)
            assertEquals("edited", database.liftDao().getById(id)?.pinnedNote)
        }

        @Test
        @DisplayName("Given note rows when scalar-cleared then rows and mirror are removed")
        fun scalarClearRemovesRows() = runTest {
            val id = LiftId("squat")
            seedLift(id)
            subject.setPinnedNotes(
                id,
                listOf(CatalogNoteInput(body = "one"), CatalogNoteInput(body = "two")),
            )

            subject.setPinnedNote(id, null)

            assertTrue(subject.getPinnedNotes(id).isEmpty())
            assertEquals(null, database.liftDao().getById(id)?.pinnedNote)
        }

        @Test
        @DisplayName("Given a removed row when replaced then its tombstone is enqueued")
        fun removedRowTombstoned() = runTest {
            val id = LiftId("squat")
            seedLift(id)
            subject.setPinnedNotes(
                id,
                listOf(CatalogNoteInput(body = "keep"), CatalogNoteInput(body = "drop")),
            )
            val droppedId = subject.getPinnedNotes(id).last().id

            subject.setPinnedNotes(id, listOf(CatalogNoteInput(body = "keep")))

            val tombstones = database.pendingCloudDeleteDao().peekPending()
            assertTrue(
                tombstones.any {
                    it.collectionPath == SyncCollection.LiftCatalogNotes.firestorePath &&
                        it.entityId == droppedId.value
                },
            )
        }
    }
}
