package com.stratum.core.inference

import com.stratum.domain.importing.InferredEquipmentLoading
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.SeedEquipmentIds
import com.stratum.domain.onboarding.MuscleGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Fixtures match the **callable response** shape returned by `inferCustomLifts`
 * (`loading: { type, ... }`), not the raw LLM schema fields.
 */
class FirebaseCustomLiftInferenceAdapterTest {
    @Test
    @DisplayName("parseResponse maps seed loading from callable payload")
    fun parsesSeedLoading() {
        val data =
            mapOf(
                "lifts" to
                    listOf(
                        mapOf(
                            "name" to "Katana Triceps Extension",
                            "movementPattern" to "ElbowExtension",
                            "primaryMuscles" to listOf("Tricep"),
                            "secondaryMuscles" to emptyList<String>(),
                            "loading" to
                                mapOf(
                                    "type" to "seed",
                                    "equipmentId" to "cable",
                                ),
                        ),
                    ),
            )
        val parsed = FirebaseCustomLiftInferenceAdapter.parseResponse(data)
        assertEquals(1, parsed.size)
        assertEquals("Katana Triceps Extension", parsed.single().exerciseNameRaw)
        assertEquals(MovementPattern.ElbowExtension, parsed.single().movementPattern)
        assertEquals(setOf(MuscleGroup.Tricep), parsed.single().primaryMuscles)
        assertEquals(
            InferredEquipmentLoading.Seed(SeedEquipmentIds.Cable),
            parsed.single().loading,
        )
    }

    @Test
    @DisplayName("parseResponse maps plate-loaded dual-horn seed to double-weight-plates")
    fun parsesDoubleWeightPlates() {
        val name = "Seated Neutral Grip Plate-Loaded Machine Press"
        val data =
            mapOf(
                "lifts" to
                    listOf(
                        mapOf(
                            "name" to name,
                            "movementPattern" to "HorizontalPress",
                            "primaryMuscles" to listOf("Chest"),
                            "secondaryMuscles" to emptyList<String>(),
                            "loading" to
                                mapOf(
                                    "type" to "seed",
                                    "equipmentId" to "double-weight-plates",
                                ),
                        ),
                    ),
            )
        val parsed = FirebaseCustomLiftInferenceAdapter.parseResponse(data)
        assertEquals(
            InferredEquipmentLoading.Seed(SeedEquipmentIds.DoubleWeightPlates),
            parsed.single().loading,
        )
        assertTrue(MuscleGroup.Tricep in parsed.single().secondaryMuscles)
    }

    @Test
    @DisplayName("parseResponse maps custom loading with name and increment")
    fun parsesCustomLoading() {
        val data =
            mapOf(
                "lifts" to
                    listOf(
                        mapOf(
                            "name" to "Pro2 Pec Fly",
                            "movementPattern" to "HorizontalPress",
                            "primaryMuscles" to listOf("Chest"),
                            "secondaryMuscles" to emptyList<String>(),
                            "loading" to
                                mapOf(
                                    "type" to "custom",
                                    "name" to "Life Fitness Pro2 Stack",
                                    "incrementLb" to 12.5,
                                ),
                        ),
                    ),
            )
        val parsed = FirebaseCustomLiftInferenceAdapter.parseResponse(data)
        assertEquals(
            InferredEquipmentLoading.Custom(
                name = "Life Fitness Pro2 Stack",
                incrementLb = 12.5,
            ),
            parsed.single().loading,
        )
    }

    @Test
    @DisplayName("parseResponse uses shared Default equipment when loading is missing")
    fun missingLoadingUsesDefault() {
        val data =
            mapOf(
                "lifts" to
                    listOf(
                        mapOf(
                            "name" to "Mystery Brand Press",
                            "movementPattern" to "HorizontalPress",
                            "primaryMuscles" to listOf("Chest"),
                            "secondaryMuscles" to emptyList<String>(),
                        ),
                    ),
            )
        val parsed = FirebaseCustomLiftInferenceAdapter.parseResponse(data)
        assertEquals(InferredEquipmentLoading.defaultEquipment(), parsed.single().loading)
    }

    @Test
    @DisplayName("parseResponse uses Default when seed equipmentId is unknown")
    fun unknownSeedIdUsesDefault() {
        val data =
            mapOf(
                "lifts" to
                    listOf(
                        mapOf(
                            "name" to "Unknown Gizmo",
                            "movementPattern" to "ElbowExtension",
                            "primaryMuscles" to listOf("Tricep"),
                            "secondaryMuscles" to emptyList<String>(),
                            "loading" to
                                mapOf(
                                    "type" to "seed",
                                    "equipmentId" to "not-a-real-kind",
                                ),
                        ),
                    ),
            )
        val parsed = FirebaseCustomLiftInferenceAdapter.parseResponse(data)
        assertEquals(InferredEquipmentLoading.defaultEquipment(), parsed.single().loading)
    }

    @Test
    @DisplayName("parseResponse blank custom name becomes Default")
    fun blankCustomNameUsesDefault() {
        val data =
            mapOf(
                "lifts" to
                    listOf(
                        mapOf(
                            "name" to "Some Lift",
                            "movementPattern" to "ElbowExtension",
                            "primaryMuscles" to listOf("Tricep"),
                            "secondaryMuscles" to emptyList<String>(),
                            "loading" to
                                mapOf(
                                    "type" to "custom",
                                    "name" to "  ",
                                    "incrementLb" to 5.0,
                                ),
                        ),
                    ),
            )
        val parsed = FirebaseCustomLiftInferenceAdapter.parseResponse(data)
        assertEquals(
            InferredEquipmentLoading.Custom(
                name = InferredEquipmentLoading.DEFAULT_EQUIPMENT_NAME,
                incrementLb = 5.0,
            ),
            parsed.single().loading,
        )
    }

    @Test
    @DisplayName("parseResponse clamps absurd custom increments to default 5")
    fun clampsAbsurdIncrement() {
        val data =
            mapOf(
                "lifts" to
                    listOf(
                        mapOf(
                            "name" to "Wild Stack",
                            "movementPattern" to "HorizontalPress",
                            "primaryMuscles" to listOf("Chest"),
                            "secondaryMuscles" to emptyList<String>(),
                            "loading" to
                                mapOf(
                                    "type" to "custom",
                                    "name" to "Wild Stack",
                                    "incrementLb" to 1_000_000.0,
                                ),
                        ),
                    ),
            )
        val parsed = FirebaseCustomLiftInferenceAdapter.parseResponse(data)
        assertEquals(
            InferredEquipmentLoading.Custom(
                name = "Wild Stack",
                incrementLb = InferredEquipmentLoading.DEFAULT_INCREMENT_LB,
            ),
            parsed.single().loading,
        )
    }

    @Test
    @DisplayName("parseResponse still applies bicep secondary for pulls")
    fun pullAddsBicepSecondary() {
        val data =
            mapOf(
                "lifts" to
                    listOf(
                        mapOf(
                            "name" to "Old Cable Row",
                            "movementPattern" to "HorizontalPull",
                            "primaryMuscles" to listOf("Back"),
                            "secondaryMuscles" to emptyList<String>(),
                            "loading" to
                                mapOf(
                                    "type" to "seed",
                                    "equipmentId" to "cable",
                                ),
                        ),
                    ),
            )
        val parsed = FirebaseCustomLiftInferenceAdapter.parseResponse(data)
        assertTrue(MuscleGroup.Bicep in parsed.single().secondaryMuscles)
    }
}
