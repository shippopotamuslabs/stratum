package com.stratum.core.data.seed

import androidx.room.Room
import com.stratum.core.data.db.StratumDatabase
import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.LoadType
import com.stratum.domain.onboarding.MuscleGroup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
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

    @BeforeEach
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StratumDatabase::class.java,
        ).allowMainThreadQueries().build()
        subject = LiftCatalogSeeder(
            liftDao = database.liftDao(),
            incrementTierDao = database.incrementTierDao(),
        )
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    @DisplayName("Given the bundled asset when seeded then the catalog loads with many lifts")
    fun seedLoadsCatalog() = runTest {
        val written = subject.seed(RuntimeEnvironment.getApplication())

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
        subject.seed(RuntimeEnvironment.getApplication())

        val lifts = database.liftDao().observeLiftsWithTiers().first()
        val curl = lifts.first { it.lift.id.value == "barbell-curl" }.lift
        assertEquals(MuscleGroup.Bicep.ordinal.let { 1 shl it }, curl.primaryMusclesBitmask)

        val commercialCount = database.liftDao().idsForPresetTag("%,${GymPreset.CommercialGym.name},%").size
        assertTrue(commercialCount > 0, "Expected lifts tagged CommercialGym")
    }

    @Test
    @DisplayName("Given tiers omitted in the asset when seeded then default tiers are applied")
    fun seedAppliesDefaultTiers() = runTest {
        subject.seed(RuntimeEnvironment.getApplication())

        val lifts = database.liftDao().observeLiftsWithTiers().first()
        val curl = lifts.first { it.lift.id.value == "barbell-curl" }
        assertEquals(4, curl.tiers.size)
        // Final band is open-ended.
        assertEquals(null, curl.tiers.last().upperBound)
    }

    @Test
    @DisplayName("Given the bundled asset when seeded then no resistance-band variants are loaded")
    fun seedExcludesBandVariants() = runTest {
        subject.seed(RuntimeEnvironment.getApplication())

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
        val first = subject.seed(RuntimeEnvironment.getApplication())
        val second = subject.seed(RuntimeEnvironment.getApplication())

        assertEquals(first, second)
        val lifts = database.liftDao().observeLiftsWithTiers().first()
        assertEquals(first, lifts.size)
    }

    @Test
    @DisplayName("Given the bundled asset when seeded then loadType is parsed and persisted per entry")
    fun seedParsesLoadType() = runTest {
        subject.seed(RuntimeEnvironment.getApplication())

        val lifts = database.liftDao().observeLiftsWithTiers().first()
        val pullUp = lifts.first { it.lift.id.value == "pull-up" }.lift
        assertEquals(LoadType.Bodyweight, pullUp.loadType)

        val assisted = lifts.first { it.lift.id.value == "assisted-pull-up" }.lift
        assertEquals(LoadType.Assisted, assisted.loadType)

        // Entries that omit loadType in the asset default to Weighted.
        val bench = lifts.first { it.lift.id.value == "barbell-back-squat" }.lift
        assertEquals(LoadType.Weighted, bench.loadType)
    }
}
