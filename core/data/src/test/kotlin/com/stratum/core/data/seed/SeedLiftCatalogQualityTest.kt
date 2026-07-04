package com.stratum.core.data.seed

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Guards against re-introducing noisy secondary muscles and mis-tagged movement patterns in the
 * bundled [LiftCatalogSeeder.ASSET_PATH] catalog.
 */
@ExtendWith(RobolectricExtension::class)
class SeedLiftCatalogQualityTest {

    private val lifts: List<JsonObject> by lazy {
        val stream =
            RuntimeEnvironment.getApplication().assets.open(LiftCatalogSeeder.ASSET_PATH)
        stream.use { input ->
            val root = Json.parseToJsonElement(input.reader().readText())
            when (root) {
                is JsonArray -> root.map { it.jsonObject }
                is JsonObject -> root["lifts"]!!.jsonArray.map { it.jsonObject }
                else -> error("Unexpected seed JSON root")
            }
        }
    }

    @Test
    @DisplayName("Given squat-pattern lifts when audited then hamstring is not a secondary muscle")
    fun squatPatternsDoNotCreditHamstringSecondaries() {
        val offenders =
            lifts.filter { lift ->
                val id = lift.stringField("id")
                val patterns = lift.movementPatterns()
                id != "barbell-hack-squat" && "SquatPattern" in patterns && "Hamstring" in lift.secondaryMuscles()
            }
        assertTrue(
            offenders.isEmpty(),
            offenders.joinToString { it.stringField("name") },
        )
    }

    @Test
    @DisplayName("Given vertical-pull lifts when audited then anterior delt is not a secondary muscle")
    fun verticalPullsDoNotCreditAnteriorDeltoidSecondaries() {
        val offenders =
            lifts.filter { lift ->
                val patterns = lift.movementPatterns()
                "VerticalPull" in patterns && "AnteriorDeltoid" in lift.secondaryMuscles()
            }
        assertTrue(
            offenders.isEmpty(),
            offenders.joinToString { it.stringField("name") },
        )
    }

    @Test
    @DisplayName("Given the seed catalog when audited then squat and hip-extension patterns are not dual-tagged")
    fun squatAndHipExtensionAreNotDualTagged() {
        val offenders =
            lifts.filter { lift ->
                val patterns = lift.movementPatterns()
                "SquatPattern" in patterns && "HipExtension" in patterns
            }
        assertTrue(
            offenders.isEmpty(),
            offenders.joinToString { it.stringField("name") },
        )
    }

    @Test
    @DisplayName("Given known mis-tags when audited then rows and leg raises are not squat patterns")
    fun rowsAndLegRaisesAreNotSquatPatterns() {
        val offenders =
            lifts.filter { lift ->
                val name = lift.stringField("name")
                name in MIS_TAGGED_AS_NON_SQUAT && "SquatPattern" in lift.movementPatterns()
            }
        assertTrue(
            offenders.isEmpty(),
            offenders.joinToString { it.stringField("name") },
        )
    }

    @Test
    @DisplayName("Given secondary muscles when audited then duplicates and primary repeats are absent")
    fun secondaryMusclesAreDedupedAndExcludePrimaries() {
        for (lift in lifts) {
            val primary = lift.stringList("primaryMuscles").toSet()
            val secondary = lift.stringList("secondaryMuscles")
            val liftName = lift.stringField("name")
            assertEquals(
                secondary.size,
                secondary.toSet().size,
                "$liftName has duplicate secondary muscles: $secondary",
            )
            assertFalse(
                secondary.any { muscle -> muscle in primary },
                "$liftName lists primary muscle as secondary: $secondary",
            )
        }
    }

    /**
     * Regression for G12/D6: four rear-delt lifts were mislabeled `AnteriorDeltoid`-primary (and
     * the two flys used the chest-fly `ShoulderAdduction` pattern). By name, description, and
     * pattern they are rear-delt movements and must be `PosteriorDeltoid`-primary; the flys must
     * use `ShoulderHorizontalAbduction`. Guards the bundled asset against reintroduction.
     */
    @Test
    @DisplayName("Given rear-delt lifts when audited then PosteriorDeltoid is primary and flys abduct")
    fun rearDeltLiftsArePosteriorPrimary() {
        val flyExpectedPattern = "ShoulderHorizontalAbduction"
        for (liftId in REAR_DELT_FLY_IDS) {
            val lift = lifts.firstOrNull { it.stringField("id") == liftId }
                ?: error("missing seed lift: $liftId")
            val primary = lift.stringList("primaryMuscles")
            assertEquals(
                listOf("PosteriorDeltoid"),
                primary,
                "${lift.stringField("name")} ($liftId) should be PosteriorDeltoid-primary (G12)",
            )
            assertTrue(
                flyExpectedPattern in lift.movementPatterns(),
                "${lift.stringField("name")} ($liftId) should use $flyExpectedPattern (D6)",
            )
        }
        for (liftId in REAR_DELT_ROW_IDS) {
            val lift = lifts.firstOrNull { it.stringField("id") == liftId }
                ?: error("missing seed lift: $liftId")
            assertEquals(
                listOf("PosteriorDeltoid"),
                lift.stringList("primaryMuscles"),
                "${lift.stringField("name")} ($liftId) should be PosteriorDeltoid-primary (G12)",
            )
            assertTrue(
                "HorizontalPull" in lift.movementPatterns(),
                "${lift.stringField("name")} ($liftId) should keep HorizontalPull",
            )
        }
    }

    /**
     * Structural invariant (G12): a lift labeled `AnteriorDeltoid`-primary must not use a
     * horizontal-pull or horizontal-abduction/adduction pattern — those patterns target the
     * rear delt or chest, not the front delt as a primary mover. The audit found 4 rear-delt
     * lifts mislabeled this way and corrected them; this guard catches any regression or new
     * mislabel of the same shape. `VerticalPull` (upright rows) is intentionally excluded —
     * anterior-delt co-primary there is a defensible judgment call, not a mislabel.
     * `low-pulley-row-to-neck` is a known borderline case (row-to-neck targeting rear delts)
     * that the audit deferred; it is allowlisted here so the guard stays meaningful for new
     * mislabels without falsely failing on the one acknowledged exception.
     */
    @Test
    @DisplayName("Given anterior-delt-primary lifts when audited then no horizontal pull or fly pattern is used")
    fun anteriorDeltPrimaryLiftsUseNoHorizontalPullOrFlyPatterns() {
        val forbiddenForAnterior = setOf(
            "HorizontalPull",
            "ShoulderHorizontalAbduction",
            "ShoulderAdduction",
        )
        val offenders =
            lifts.filter { lift ->
                val id = lift.stringField("id")
                "AnteriorDeltoid" in lift.stringList("primaryMuscles") &&
                    id !in KNOWN_ANTERIOR_PULL_EXCEPTIONS &&
                    lift.movementPatterns().any { it in forbiddenForAnterior }
            }
        assertTrue(
            offenders.isEmpty(),
            offenders.joinToString { "${it.stringField("name")} (${it.stringField("id")}): ${it.movementPatterns()}" },
        )
    }

    private fun JsonObject.movementPatterns(): List<String> = stringList("movementPatterns")

    private fun JsonObject.secondaryMuscles(): List<String> = stringList("secondaryMuscles")

    private fun JsonObject.stringList(field: String): List<String> =
        this[field]?.jsonArray?.map { element -> element.jsonPrimitive.content }.orEmpty()

    private fun JsonObject.stringField(field: String): String =
        this[field]?.jsonPrimitive?.content.orEmpty()

    private companion object {
        val MIS_TAGGED_AS_NON_SQUAT =
            setOf(
                "Barbell Rear Delt Row",
                "Cable Rope Rear-Delt Rows",
                "Flat Bench Lying Leg Raise",
                "Low Pulley Row To Neck",
            )

        val REAR_DELT_FLY_IDS = setOf("cable-rear-delt-fly", "reverse-flyes")
        val REAR_DELT_ROW_IDS = setOf("barbell-rear-delt-row", "cable-rope-rear-delt-rows")

        /** Audit-deferred borderline: row-to-neck targets rear delts but was left anterior-primary. */
        val KNOWN_ANTERIOR_PULL_EXCEPTIONS = setOf("low-pulley-row-to-neck")
    }
}
