package com.stratum.domain.program.generation.universe

import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.domain.program.generation.volume.planWeeklyVolume
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * Golden snapshots of universe slot keys for representative scenarios.
 *
 * Captured from pre–Phase-E `buildSlotUniverse` (union of SOURCE_SESSION_TYPES) so the
 * explicit [com.stratum.domain.program.generation.slots.baseSlotLibrary] rewrite stays
 * candidate-equivalent. Assert set equality of keys (order ignored).
 *
 * Intentional diffs:
 * - 2026-07-12: core volume aim surface (no PosteriorDeltoid aim) drops rear-delt slots
 *   from the default universe unless Posterior is specialized (it is not in CORE aims).
 * - 2026-07-12: Calf/Glute are opt-in aims only (Specialize/MV). Maintenance universes drop
 *   PlantarFlexion|Calf|Optional — calf slots reappear when Calf is specialized.
 */
class SlotUniverseGoldenTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenScenarios")
    @DisplayName("universe slot keys match golden snapshot")
    fun universeKeysMatchGolden(scenario: GoldenScenario) {
        val context = parseScenarioContext(scenario.payload)
        val actual = buildSlotUniverse(context, planWeeklyVolume(context)).map { it.id }.toSet()
        assertEquals(
            scenario.expectedKeys,
            actual,
            "universe key set mismatch for ${scenario.name}\n" +
                "missing: ${(scenario.expectedKeys - actual).sorted()}\n" +
                "extra:   ${(actual - scenario.expectedKeys).sorted()}",
        )
    }

    @Test
    @DisplayName("base library groups cover press/pull/lower families without inventing slots")
    fun libraryGroupsAreNonEmptySubsetsOfBaseLibrary() {
        val baseKeys =
            com.stratum.domain.program.generation.slots.baseSlotLibrary()
                .map { com.stratum.domain.program.generation.slots.librarySlotKey(it) }
                .toSet()
        val groups =
            listOf(
                com.stratum.domain.program.generation.slots.LOWER_ANCHORS,
                com.stratum.domain.program.generation.slots.LOWER_ISOLATIONS,
                com.stratum.domain.program.generation.slots.UPPER_PRESS,
                com.stratum.domain.program.generation.slots.UPPER_PULL,
                com.stratum.domain.program.generation.slots.UPPER_ISOLATIONS,
            )
        for (group in groups) {
            assert(group.isNotEmpty()) { "library group should not be empty" }
            for (slot in group) {
                val key = com.stratum.domain.program.generation.slots.librarySlotKey(slot)
                assert(key in baseKeys) {
                    "group slot $key must appear in baseSlotLibrary() (no invented slots)"
                }
            }
        }
    }

    data class GoldenScenario(
        val name: String,
        val payload: String,
        val expectedKeys: Set<String>,
    )

    companion object {
        /**
         * Pre-rewrite snapshots (2026-07-12). Keys are pattern|muscle|role|touch|variant.
         */
        @JvmStatic
        fun goldenScenarios(): Stream<GoldenScenario> =
            Stream.of(
                GoldenScenario(
                    name = "4-day sequential maintenance",
                    payload = scenarioPayload(4, emptyMap(), weekdayLayout = "sequential"),
                    expectedKeys = MAINTENANCE_KEYS,
                ),
                GoldenScenario(
                    name = "3-day mwf maintenance",
                    payload = scenarioPayload(3, emptyMap(), weekdayLayout = "mwf"),
                    expectedKeys = MAINTENANCE_KEYS,
                ),
                GoldenScenario(
                    name = "3-day mwf arms",
                    payload =
                        scenarioPayload(
                            3,
                            mapOf("Bicep" to "Specialize", "Tricep" to "Specialize"),
                            weekdayLayout = "mwf",
                        ),
                    expectedKeys = ARMS_KEYS,
                ),
                GoldenScenario(
                    name = "5-day mtwfs arm+calf specialization",
                    payload =
                        scenarioPayload(
                            5,
                            mapOf(
                                "Bicep" to "Specialize",
                                "Tricep" to "Specialize",
                                "Calf" to "Specialize",
                            ),
                            weekdayLayout = "mtwfs",
                        ),
                    expectedKeys = ARM_CALF_5DAY_KEYS,
                ),
                GoldenScenario(
                    name = "sean5 maintenance",
                    payload = scenarioPayload(5, emptyMap(), weekdayLayout = "sean5"),
                    expectedKeys = MAINTENANCE_KEYS,
                ),
                GoldenScenario(
                    name = "sean5 glute specialization",
                    payload =
                        scenarioPayload(
                            5,
                            mapOf("Glute" to "Specialize"),
                            weekdayLayout = "sean5",
                        ),
                    expectedKeys = SEAN5_GLUTE_KEYS,
                ),
                GoldenScenario(
                    name = "4-day sequential arms",
                    payload =
                        scenarioPayload(
                            4,
                            mapOf("Bicep" to "Specialize", "Tricep" to "Specialize"),
                            weekdayLayout = "sequential",
                        ),
                    expectedKeys = ARMS_KEYS,
                ),
            )

        /** Shared across 3/4/5-day maintenance (same catalog + core aims). */
        private val MAINTENANCE_KEYS =
            setOf(
                "ElbowExtension|Tricep|Isolation|false|Any",
                "ElbowFlexion|Bicep|Isolation|false|Any",
                "HipHinge|Hamstring|Anchor|false|Any",
                "HipHinge|Hamstring|SecondaryCompound|false|Any",
                "HorizontalPress|Chest|Anchor|false|Any",
                "HorizontalPull|Back|SecondaryCompound|false|A",
                "HorizontalPull|Back|SecondaryCompound|false|Any",
                "InclinePress|Chest|SecondaryCompound|false|Any",
                "InclinePress|Chest|SecondaryCompound|false|B",
                "KneeExtension|Quad|Isolation|false|Any",
                "KneeFlexion|Hamstring|Isolation|false|Any",
                "ShoulderAbduction|LateralDeltoid|Isolation|false|Any",
                "SquatPattern|Quad|Anchor|false|Any",
                "VerticalPull|Back|Anchor|false|Any",
                "VerticalPull|Back|SecondaryCompound|false|A",
            )

        private val ARMS_KEYS =
            MAINTENANCE_KEYS +
                setOf(
                    "ElbowExtension|Tricep|Isolation|true|Any",
                    "ElbowFlexion|Bicep|Isolation|true|Any",
                )

        private val ARM_CALF_5DAY_KEYS =
            MAINTENANCE_KEYS +
                setOf(
                    "ElbowExtension|Tricep|Isolation|true|Any",
                    "ElbowFlexion|Bicep|Isolation|true|Any",
                    "PlantarFlexion|Calf|Isolation|false|Any",
                    "PlantarFlexion|Calf|Isolation|false|B",
                )

        private val SEAN5_GLUTE_KEYS =
            MAINTENANCE_KEYS +
                setOf(
                    "HipExtension|Glute|Anchor|false|Any",
                    "HipExtension|Glute|Isolation|true|Any",
                )
    }
}
