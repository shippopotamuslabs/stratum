package com.stratum.core.data.seed

import com.stratum.domain.lift.SeedEquipmentIds
import com.stratum.domain.lift.identity.EquipmentId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Guards drift between Room seed equipment ids and [SeedEquipmentIds] (import loading lattice).
 * Keep in sync with `functions/src/import/customLiftInferenceSchema.ts` `SEED_LOADING_KINDS`.
 */
@ExtendWith(RobolectricExtension::class)
class SeedEquipmentIdsSyncTest {
    @Test
    @DisplayName("SeedEquipmentIds.ALL matches ids in seed-equipment.json")
    fun seedIdsMatchAsset() {
        val text =
            RuntimeEnvironment.getApplication()
                .assets
                .open(EquipmentCatalogSeeder.ASSET_PATH)
                .bufferedReader()
                .use { it.readText() }
        val idsFromAsset =
            Json.parseToJsonElement(text)
                .jsonArray
                .map { it.jsonObject["id"]!!.jsonPrimitive.content }
                .map { EquipmentId(it) }
                .toSet()
        assertEquals(idsFromAsset, SeedEquipmentIds.ALL)
    }
}
