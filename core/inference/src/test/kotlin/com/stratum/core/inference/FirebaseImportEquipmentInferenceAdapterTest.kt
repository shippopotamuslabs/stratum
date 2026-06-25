package com.stratum.core.inference

import com.stratum.domain.importing.GymEquipment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FirebaseImportEquipmentInferenceAdapterTest {
    @Test
    fun `parses name to equipment set from callable response`() {
        val data =
            mapOf(
                "lifts" to
                    listOf(
                        mapOf(
                            "name" to "Lat Pulldown - Underhand (Cable)",
                            "equipment" to listOf("CableStation", "LatPulldownMachine"),
                            "confidence" to 0.9,
                        ),
                        mapOf(
                            "name" to "Bench Press (Dumbbell)",
                            "equipment" to listOf("Dumbbell", "FlatBench"),
                            "confidence" to 0.8,
                        ),
                    ),
            )
        val parsed = FirebaseImportEquipmentInferenceAdapter.parseResponse(data)
        assertEquals(
            setOf(GymEquipment.CableStation, GymEquipment.LatPulldownMachine),
            parsed["Lat Pulldown - Underhand (Cable)"],
        )
        assertEquals(
            setOf(GymEquipment.Dumbbell, GymEquipment.FlatBench),
            parsed["Bench Press (Dumbbell)"],
        )
    }

    @Test
    fun `drops rows with unknown equipment enums and keeps valid ones`() {
        val data =
            mapOf(
                "lifts" to
                    listOf(
                        mapOf("name" to "Mystery", "equipment" to listOf("NotAnEnum"), "confidence" to 0.5),
                        mapOf("name" to "Cable Fly", "equipment" to listOf("CableStation"), "confidence" to 0.7),
                    ),
            )
        val parsed = FirebaseImportEquipmentInferenceAdapter.parseResponse(data)
        assertTrue("Mystery" !in parsed)
        assertEquals(setOf(GymEquipment.CableStation), parsed["Cable Fly"])
    }

    @Test
    fun `returns empty map for malformed payloads`() {
        assertEquals(emptyMap<String, Set<GymEquipment>>(), FirebaseImportEquipmentInferenceAdapter.parseResponse(null))
        assertEquals(emptyMap<String, Set<GymEquipment>>(), FirebaseImportEquipmentInferenceAdapter.parseResponse("nope"))
        assertEquals(emptyMap<String, Set<GymEquipment>>(), FirebaseImportEquipmentInferenceAdapter.parseResponse(mapOf("lifts" to "nope")))
    }

    @Test
    fun `inference batch size matches server limit`() {
        assertEquals(100, FirebaseImportEquipmentInferenceAdapter.INFERENCE_BATCH_SIZE)
    }
}
