package com.stratum.domain.importing

import com.stratum.domain.lift.Equipment

/**
 * Normalizes imported exercise names for catalog matching and custom-lift deduplication.
 *
 * App-specific noise that hurts BM25 token overlap is normalized:
 * - MacroFactor superset markers (`∈ SS1`, `∈ SS2`, ...) stripped. Also strips mojibake /
 *   double-encoded forms of `∈` and any trailing `SSn` so exports that corrupt the marker still
 *   match (e.g. `Katana Triceps Extension Ã¢ÂˆÂˆ SS1` → `katana triceps extension`).
 * - Strong/Hevy trailing equipment parentheses (`Bench Press (Dumbbell)`) rewritten to leading
 *   equipment (`Dumbbell Bench Press`) for matching only — display names keep the original word
 *   order and parentheses.
 * - Strong/Hevy dash variants (` - Close Grip`, ` - Behind Back`, ` - Underhand`) rewritten to
 *   leading qualifiers (`Bench Press - Close Grip` -> `Close Grip Bench Press`), mirroring the
 *   paren rewrite so the variant token participates in matching instead of being dropped.
 * - Unicode/hyphen punctuation normalized to spaces so token splitting is consistent.
 *
 * [displayName] preserves the user's original casing and word order — only the superset suffix is
 * removed, since that is pure app metadata, not part of the exercise identity.
 */
object ImportExerciseNameNormalizer {
    /**
     * MacroFactor appends `∈ SSn` (or a corrupted encoding of that marker) after the exercise
     * name. Mojibake of `∈` often lands as Latin-1 letters (e.g. `Ã¢ÂˆÂˆ`), so we strip:
     * - optional non-ASCII / punctuation junk
     * - optional canonical `∈`
     * - trailing `SSn`
     * so BM25 does not treat `ss1` as a content token.
     */
    private val SUPERSET_SUFFIX =
        Regex(
            """(?:\s|[^\x00-\x7F]|[^\p{L}\p{N}])*(?:∈\s*)?SS\d+\s*$""",
            RegexOption.IGNORE_CASE,
        )

    private val TRAILING_EQUIPMENT_PAREN =
        Regex("""\s*\(([^)]+)\)\s*$""")

    private val DASH_VARIANT_SUFFIX =
        Regex("""\s+-\s+([^-]+)$""")

    private val NON_ALNUM = Regex("""[^a-z0-9\s]""")

    /**
     * Canonical form for BM25 matching and custom-lift deduplication: superset suffix stripped,
     * trailing `(Equipment)` paren moved to the front, trailing `- Variant` suffix moved to the
     * front, punctuation normalized to spaces, whitespace collapsed, lowercased.
     */
    fun normalizeForMatching(raw: String): String {
        val noSuperset = stripSupersetSuffix(raw)
        val parenMoved = moveTrailingEquipmentParenToFront(noSuperset)
        val dashMoved = moveDashVariantToFront(parenMoved)
        val lowercased = dashMoved.lowercase()
        val punctuationToSpaces = lowercased.replace(NON_ALNUM, " ")
        val collapsed = punctuationToSpaces.trim().replace(Regex("""\s+"""), " ")
        // Second pass: if mojibake was turned into spaces, a bare `ss1` may remain.
        return stripSupersetSuffix(collapsed)
    }

    /**
     * Canonical display name for custom lifts — superset suffix removed, original casing and word
     * order preserved (no `(Equipment)` rewrite, no dash-variant rewrite).
     */
    fun displayName(raw: String): String = stripSupersetSuffix(raw).trim()

    private fun stripSupersetSuffix(value: String): String =
        value.replace(SUPERSET_SUFFIX, "").trimEnd()

    private fun moveTrailingEquipmentParenToFront(value: String): String {
        val match = TRAILING_EQUIPMENT_PAREN.find(value) ?: return value
        val equipment = match.groupValues[1].trim()
        val base = value.substring(0, match.range.first).trim()
        if (base.isEmpty()) return value
        return "$equipment $base"
    }

    private fun moveDashVariantToFront(value: String): String {
        val match = DASH_VARIANT_SUFFIX.find(value) ?: return value
        val variant = match.groupValues[1].trim()
        val base = value.substring(0, match.range.first).trim()
        if (base.isEmpty() || variant.isEmpty()) return value
        return "$variant $base"
    }
}
