package com.stratum.application.importing

import com.stratum.domain.lift.identity.LiftId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.stratum.domain.importing.ImportExerciseNameNormalizer

/**
 * Golden-fixture driven checks: the three bundled sample exports (MacroFactor, Hevy, Strong) cover
 * the naming landmines (superset suffixes, trailing equipment parens, dash variants, machine
 * descriptors). These tests pin the normalizer + resolver behavior against representative names
 * drawn from each fixture so regressions are caught without re-running a full import.
 */
class ImportFixtureNormalizationTest {
    private val resolver = Bm25LiftNameResolver()

    @Test
    fun `macrofactor superset suffixes are stripped for matching`() {
        val raw = "Katana Triceps Extension ∈ SS1"
        assertEquals("katana triceps extension", ImportExerciseNameNormalizer.normalizeForMatching(raw))
        assertEquals("Katana Triceps Extension", ImportExerciseNameNormalizer.displayName(raw))
    }

    @Test
    fun `macrofactor machine descriptors normalize to tokens`() {
        assertEquals(
            "seated neutral grip plate loaded machine chest press",
            ImportExerciseNameNormalizer.normalizeForMatching(
                "Seated Neutral Grip Plate-Loaded Machine Chest Press ∈ SS2",
            ),
        )
    }

    @Test
    fun `hevy trailing equipment paren rewrites to leading for matching`() {
        assertEquals(
            "dumbbell incline bench press",
            ImportExerciseNameNormalizer.normalizeForMatching("Incline Bench Press (Dumbbell)"),
        )
    }

    @Test
    fun `hevy custom names without paren stay intact for matching`() {
        assertEquals(
            "katana extension",
            ImportExerciseNameNormalizer.normalizeForMatching("Katana Extension"),
        )
    }

    @Test
    fun `hevy dash variant suffix is moved to front for matching`() {
        assertEquals(
            "behind back cable lateral raise",
            ImportExerciseNameNormalizer.normalizeForMatching("Cable Lateral Raise - Behind Back"),
        )
    }

    @Test
    fun `strong semicolon export names resolve against leading-equipment catalog`() {
        val catalog =
            mapOf(
                LiftId("db-bench") to "Dumbbell Bench Press",
                LiftId("cable-lat") to "Cable Lat Pulldown",
            )
        assertTrue(resolver.resolve("Bench Press (Dumbbell)", catalog) == LiftId("db-bench"))
        assertTrue(resolver.resolve("Lat Pulldown - Underhand (Cable)", catalog) == null)
    }

    @Test
    fun `strong bare custom name does not match unrelated catalog lift`() {
        val catalog = mapOf(LiftId("bench") to "Barbell Bench Press")
        // "Cable Crossover" shares no tokens with "Barbell Bench Press" -> no match.
        assertTrue(resolver.resolve("Cable Crossover", catalog) == null)
    }

    @Test
    fun `fixtures are present on the classpath`() {
        listOf(
            "import-fixtures/macrofactor.sample.csv",
            "import-fixtures/hevy.sample.csv",
            "import-fixtures/strong.sample.csv",
        ).forEach { resource ->
            val stream = ClassLoader.getSystemClassLoader().getResourceAsStream(resource)
            checkNotNull(stream) { "Missing fixture resource: $resource" }
            stream.use { it.readBytes().decodeToString() }
        }
    }

    @Test
    fun `macrofactor fixture rows parse and normalize without superset suffix in display`() {
        val csv = resource("import-fixtures/macrofactor.sample.csv")
        val exercises = csv.lines().drop(1).filter { it.isNotBlank() }.map { it.split(",").let { it[3] } }
        assertTrue(exercises.any { it.contains("∈ SS1") })
        exercises.filter { it.contains("∈") }.forEach {
            assertTrue(!ImportExerciseNameNormalizer.displayName(it).contains("∈"))
        }
    }

    private fun resource(path: String): String =
        ClassLoader.getSystemClassLoader().getResourceAsStream(path)!!.use { it.readBytes().decodeToString() }
}
