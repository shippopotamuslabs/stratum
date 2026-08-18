package com.stratum.core.data.seed

import androidx.room.Room
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.onboarding.MuscleGroup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
class LiftCatalogSeederTest {

    private lateinit var database: StratumDatabase
    private lateinit var subject: LiftCatalogSeeder
    private lateinit var equipmentSeeder: EquipmentCatalogSeeder

    @BeforeEach
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StratumDatabase::class.java,
        ).allowMainThreadQueries().build()
        equipmentSeeder = EquipmentCatalogSeeder(
            equipmentDao = database.equipmentDao(),
            incrementTierDao = database.incrementTierDao(),
            loggingPort = FakeLoggingPort(),
        )
        val tags =
            AssetLiftEquipmentCatalog(
                RuntimeEnvironment.getApplication(),
                loggingPort = FakeLoggingPort(),
            ).all()
        subject = LiftCatalogSeeder(
            liftDao = database.liftDao(),
            incrementTierDao = database.incrementTierDao(),
            equipmentTagsByLiftId = tags,
            loggingPort = FakeLoggingPort(),
        )
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    private suspend fun seedAll() {
        equipmentSeeder.seed(RuntimeEnvironment.getApplication())
        subject.seed(RuntimeEnvironment.getApplication())
    }

    @Test
    @DisplayName("Given the bundled asset when seeded then the catalog loads with many lifts")
    fun seedLoadsCatalog() = runTest {
        seedAll()
        val written = database.liftDao().countSeedLifts()

        assertTrue(written > 10, "Expected a sizeable seed catalog, got $written")
        val lifts = database.liftDao().observeLiftsWithTiers().first()
        assertEquals(written, lifts.size)
        // Seed lifts start disabled — the user opts in via preset/toggle.
        assertTrue(lifts.all { !it.lift.enabled })
        assertTrue(lifts.all { !it.lift.isCustom })
    }

    @Test
    @DisplayName("Given the bundled asset when seeded then lifts carry decoded muscles + preset tags")
    fun seedDecodesMusclesAndTags() = runTest {
        seedAll()

        val lifts = database.liftDao().observeLiftsWithTiers().first()
        val curl = lifts.first { it.lift.id.value == "barbell-curl" }.lift
        assertEquals(MuscleGroup.Bicep.ordinal.let { 1 shl it }, curl.primaryMusclesBitmask)

        val commercialCount = database.liftDao().idsForPresetTag("%,${GymPreset.CommercialGym.name},%").size
        assertTrue(commercialCount > 0, "Expected lifts tagged CommercialGym")
    }

    @Test
    @DisplayName("Given tiers omitted in the asset when seeded then lift inherits equipment defaults (no override rows)")
    fun seedInheritsEquipmentTiers() = runTest {
        seedAll()

        val lifts = database.liftDao().observeLiftsWithTiers().first()
        val curl = lifts.first { it.lift.id.value == "barbell-curl" }
        assertTrue(curl.tiers.isEmpty(), "seed lifts should not write override tiers when inheriting")
        assertEquals(EquipmentId("double-weight-plates"), curl.lift.equipmentId)

        val equipmentTiers =
            database.incrementTierDao().getForEquipment(EquipmentId("double-weight-plates"))
        assertTrue(equipmentTiers.isNotEmpty())
        assertEquals(5.0, equipmentTiers.single().increment)

        val bentRow = lifts.first { it.lift.id.value == "bent-over-barbell-row" }.lift
        assertEquals(EquipmentId("double-weight-plates"), bentRow.equipmentId)

        val reverseGrip = lifts.first { it.lift.id.value == "reverse-grip-bent-over-rows" }.lift
        assertEquals(EquipmentId("double-weight-plates"), reverseGrip.equipmentId)

        val tBar = lifts.first { it.lift.id.value == "t-bar-row-with-handle" }.lift
        assertEquals(EquipmentId("single-weight-plate"), tBar.equipmentId)

        // Generic / sled / plate-loaded leg press → dual-horn plates; machine variant → pin stack.
        val sledLegPress = lifts.first { it.lift.id.value == "sled-leg-press" }.lift
        assertEquals(EquipmentId("double-weight-plates"), sledLegPress.equipmentId)
        val plateLoadedLegPress =
            lifts.first { it.lift.id.value == "plate-loaded-single-leg-press" }.lift
        assertEquals(EquipmentId("double-weight-plates"), plateLoadedLegPress.equipmentId)
        val machineLegPress = lifts.first { it.lift.id.value == "machine-single-leg-press" }.lift
        assertEquals(EquipmentId("pin-loaded-machine"), machineLegPress.equipmentId)
    }

    @Test
    @DisplayName("Given the bundled asset when seeded then no resistance-band variants are loaded")
    fun seedExcludesBandVariants() = runTest {
        seedAll()

        val bandPattern = Regex("(^|[-\\s])band($|[-\\s])", RegexOption.IGNORE_CASE)
        val lifts = database.liftDao().observeLiftsWithTiers().first()
        for (entry in lifts) {
            assertFalse(
                bandPattern.containsMatchIn(entry.lift.id.value),
                "seed catalog must not include band variant id ${entry.lift.id.value}",
            )
            assertFalse(
                bandPattern.containsMatchIn(entry.lift.name),
                "seed catalog must not include band variant name ${entry.lift.name}",
            )
        }
    }

    @Test
    @DisplayName("Given the catalog already seeded when seeded again then it is idempotent")
    fun seedIsIdempotent() = runTest {
        seedAll()
        val first = database.liftDao().countSeedLifts()
        subject.seed(RuntimeEnvironment.getApplication())
        val second = database.liftDao().countSeedLifts()

        assertEquals(first, second)
        val lifts = database.liftDao().observeLiftsWithTiers().first()
        assertEquals(first, lifts.size)
    }

    @Test
    @DisplayName("Given the bundled asset when seeded then loadType is parsed and persisted per entry")
    fun seedParsesLoadType() = runTest {
        seedAll()

        val lifts = database.liftDao().observeLiftsWithTiers().first()
        val pullUp = lifts.first { it.lift.id.value == "pull-up" }.lift
        assertEquals(LoadType.Bodyweight, pullUp.loadType)

        val assisted = lifts.first { it.lift.id.value == "assisted-pull-up" }.lift
        assertEquals(LoadType.Assisted, assisted.loadType)

        // Entries that omit loadType in the asset default to Weighted.
        val bench = lifts.first { it.lift.id.value == "barbell-back-squat" }.lift
        assertEquals(LoadType.Weighted, bench.loadType)
        assertNotNull(bench.equipmentId)
    }
}
