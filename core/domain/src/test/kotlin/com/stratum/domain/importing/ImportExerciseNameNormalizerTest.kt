package com.stratum.domain.importing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ImportExerciseNameNormalizerTest {
    @Test
    fun `strips trailing superset suffix`() {
        assertEquals(
            "katana triceps extension",
            ImportExerciseNameNormalizer.normalizeForMatching("Katana Triceps Extension ∈ SS1"),
        )
    }

    @Test
    fun `strips mojibake macrofactor superset suffix`() {
        // Real MacroFactor exports often double-encode ∈ as Ã¢ÂˆÂˆ (or similar).
        val raw = "Katana Triceps Extension Ã¢ÂˆÂˆ SS1"
        assertEquals(
            "katana triceps extension",
            ImportExerciseNameNormalizer.normalizeForMatching(raw),
        )
        assertEquals(
            "Katana Triceps Extension",
            ImportExerciseNameNormalizer.displayName(raw),
        )
    }

    @Test
    fun `strips bare trailing SSn without element-of marker`() {
        assertEquals(
            "pec deck fly",
            ImportExerciseNameNormalizer.normalizeForMatching("Pec Deck Fly SS2"),
        )
    }

    @Test
    fun `display name preserves casing and strips only superset suffix`() {
        assertEquals(
            "Katana Triceps Extension",
            ImportExerciseNameNormalizer.displayName("Katana Triceps Extension ∈ SS1"),
        )
    }

    @Test
    fun `display name preserves trailing equipment paren`() {
        assertEquals(
            "Bench Press (Dumbbell)",
            ImportExerciseNameNormalizer.displayName("Bench Press (Dumbbell)"),
        )
    }

    @Test
    fun `matching moves trailing equipment paren to front`() {
        assertEquals(
            "dumbbell bench press",
            ImportExerciseNameNormalizer.normalizeForMatching("Bench Press (Dumbbell)"),
        )
    }

    @Test
    fun `matching moves dash variant suffix to front`() {
        assertEquals(
            "close grip bench press",
            ImportExerciseNameNormalizer.normalizeForMatching("Bench Press - Close Grip"),
        )
    }

    @Test
    fun `matching moves dash variant after paren equipment is moved front`() {
        // "Lat Pulldown - Underhand (Cable)" -> paren moved front -> "Cable Lat Pulldown - Underhand"
        // -> dash suffix moved front -> "Underhand Cable Lat Pulldown".
        assertEquals(
            "underhand cable lat pulldown",
            ImportExerciseNameNormalizer.normalizeForMatching("Lat Pulldown - Underhand (Cable)"),
        )
    }

    @Test
    fun `matching normalizes unicode hyphens to spaces`() {
        assertEquals(
            "low to high cable fly",
            ImportExerciseNameNormalizer.normalizeForMatching("Low-To-High Cable Fly"),
        )
    }

    @Test
    fun `matching collapses repeated whitespace`() {
        assertEquals(
            "back squat",
            ImportExerciseNameNormalizer.normalizeForMatching("  Back   Squat  "),
        )
    }
}
