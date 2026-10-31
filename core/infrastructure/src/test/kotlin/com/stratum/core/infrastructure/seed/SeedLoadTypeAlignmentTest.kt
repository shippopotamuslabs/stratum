package com.stratum.core.infrastructure.seed

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * STR-250: the bundled seed catalog must satisfy the domain load-type/equipment
 * alignment invariant (`requireLoadTypeEquipmentAlignment`) on every row. The
 * seeder enforces the rule at import and fails loudly on a contradictory row —
 * ab-roller shipped as Weighted against Bodyweight equipment because no such
 * check existed. This test runs the REAL bundled assets through the REAL mapping
 * path and collects every offender so a bad row names itself.
 */
@ExtendWith(RobolectricExtension::class)
class SeedLoadTypeAlignmentTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val application get() = RuntimeEnvironment.getApplication()

    private val seedDtos: List<LiftSeedDto> by lazy {
        application.assets.open(LiftCatalogSeeder.ASSET_PATH).use { stream ->
            json.decodeFromString<List<LiftSeedDto>>(stream.readBytes().decodeToString())
        }
    }

    private val equipmentTagsByLiftId by lazy {
        AssetLiftEquipmentCatalog(application, loggingPort = FakeLoggingPort()).all()
    }

    /** The seeder's own band-variant exclusion (resistance-band rows never seed). */
    private fun isBandVariant(dto: LiftSeedDto): Boolean =
        LiftCatalogSeeder.isBandVariantLift(dto.id, dto.name)

    @Test
    @DisplayName("every bundled seed row satisfies the load-type/equipment alignment invariant")
    fun everySeedRowAlignsLoadTypeWithEquipment() {
        val offenders = mutableListOf<String>()
        for (dto in seedDtos) {
            if (isBandVariant(dto)) continue
            try {
                LiftCatalogSeeder.mapSeedDto(dto, equipmentTagsByLiftId)
            } catch (error: IllegalArgumentException) {
                offenders += "${dto.id}: ${error.message}"
            }
        }
        assertTrue(
            offenders.isEmpty(),
            "Seed rows violating load-type/equipment alignment:\n" +
                offenders.joinToString("\n"),
        )
    }

    @Test
    @DisplayName("ab-roller seeds as Bodyweight, matching its Bodyweight equipment")
    fun abRollerSeedsAsBodyweight() {
        val dto = seedDtos.single { it.id == "ab-roller" }
        val entity = LiftCatalogSeeder.mapSeedDto(dto, equipmentTagsByLiftId)
        assertEquals(LoadType.Bodyweight, entity?.loadType)
    }

    @Test
    @DisplayName("mapSeedDto throws on a contradictory row instead of persisting it")
    fun mapSeedDtoThrowsOnContradiction() {
        val weightedOnBodyweightEquipment = LiftSeedDto(
            id = "synthetic-contradiction",
            name = "Synthetic Contradiction",
            movementPatterns = listOf(MovementPattern.ElbowFlexion),
            primaryMuscles = listOf(MuscleGroup.Bicep),
            equipmentId = "bodyweight",
            // loadType omitted → seeder defaults to Weighted, contradicting the equipment.
        )
        assertThrows(IllegalArgumentException::class.java) {
            LiftCatalogSeeder.mapSeedDto(weightedOnBodyweightEquipment)
        }

        val bodyweightOnLoadedEquipment = LiftSeedDto(
            id = "synthetic-contradiction-2",
            name = "Synthetic Contradiction 2",
            movementPatterns = listOf(MovementPattern.ElbowFlexion),
            primaryMuscles = listOf(MuscleGroup.Bicep),
            equipmentId = "barbell",
            loadType = LoadType.Bodyweight,
        )
        assertThrows(IllegalArgumentException::class.java) {
            LiftCatalogSeeder.mapSeedDto(bodyweightOnLoadedEquipment)
        }
    }

    @Test
    @DisplayName("the flagged-row collector sees the ab-roller lift id")
    fun collectorSanityCheck() {
        // Guards the validation test itself: the tags catalog must resolve ab-roller
        // so a future silent empty-tags regression cannot make the audit vacuous.
        assertTrue(equipmentTagsByLiftId.containsKey(LiftId("ab-roller")))
    }
}
