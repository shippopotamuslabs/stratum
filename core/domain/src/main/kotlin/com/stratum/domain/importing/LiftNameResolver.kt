package com.stratum.domain.importing

import com.stratum.domain.lift.identity.LiftId
import kotlin.math.roundToInt

data class Bm25LiftCandidate(
    val liftId: LiftId,
    val catalogName: String,
    val score: Double,
    val overlap: Int,
)

/** Token overlap metrics for a query vs catalog name pair. */
data class TokenAlignment(
    val jaccard: Double,
    val queryRecall: Double,
    val docRecall: Double,
)

/**
 * Simple BM25-inspired token overlap + Levenshtein fallback for lift name resolution.
 *
 * Catalog acceptance requires [isStrongTokenAlignment] (near-identical token bags after
 * normalization). Lower scores are for ranking embedding rerank candidates only.
 */
class Bm25LiftNameResolver : LiftNameResolverPort {
    override fun resolve(exerciseNameRaw: String, catalogNames: Map<LiftId, String>): LiftId? =
        evaluate(exerciseNameRaw, catalogNames).matchedLiftId

    override fun evaluate(
        exerciseNameRaw: String,
        catalogNames: Map<LiftId, String>,
    ): LiftNameMatchEvaluation {
        val best = scoreBest(exerciseNameRaw, catalogNames)
        if (best == null) {
            return LiftNameMatchEvaluation(
                matchedLiftId = null,
                bestScore = 0.0,
                bestCatalogName = null,
                matched = false,
            )
        }
        val matched = isStrongTokenAlignment(exerciseNameRaw, best.catalogName)
        return LiftNameMatchEvaluation(
            matchedLiftId = if (matched) best.liftId else null,
            bestScore = best.score,
            bestCatalogName = best.catalogName,
            matched = matched,
        )
    }

    fun rankTopK(
        exerciseNameRaw: String,
        catalogNames: Map<LiftId, String>,
        k: Int = DEFAULT_TOP_K,
    ): List<Bm25LiftCandidate> {
        val queryTokens = queryTokens(exerciseNameRaw)
        if (queryTokens.isEmpty()) return emptyList()

        return catalogNames
            .map { (id, name) ->
                val scored = scoreAgainstCatalogName(exerciseNameRaw, queryTokens, name)
                Bm25LiftCandidate(
                    liftId = id,
                    catalogName = name,
                    score = scored.score,
                    overlap = scored.overlap,
                )
            }
            .sortedWith(
                compareByDescending<Bm25LiftCandidate> { it.score }
                    .thenByDescending { it.overlap }
                    .thenBy { it.catalogName.length },
            )
            .take(k)
    }

    fun matchingQueryTokenCount(exerciseNameRaw: String): Int = queryTokens(exerciseNameRaw).size

    fun isExactNormalizedMatch(exerciseNameRaw: String, catalogName: String): Boolean =
        normalizeForMatching(exerciseNameRaw) == normalizeForMatching(catalogName)

    fun tokenAlignment(exerciseNameRaw: String, catalogName: String): TokenAlignment =
        computeTokenAlignment(queryTokens(exerciseNameRaw), catalogTokens(catalogName))

    /**
     * True when normalized strings match, both token bags align strongly in both directions, or the
     * query is an equipment-aware subset of the catalog name (every query token is covered and the
     * only extra catalog tokens are equipment/variant modifiers like "back", "incline", "cable").
     * Prevents unsafe subset matches (e.g. "leg press" -> "jm press") while accepting equipment-
     * qualified ones (e.g. "squat (barbell)" -> "barbell back squat").
     */
    fun isStrongTokenAlignment(exerciseNameRaw: String, catalogName: String): Boolean {
        if (isExactNormalizedMatch(exerciseNameRaw, catalogName)) {
            return true
        }
        val alignment = tokenAlignment(exerciseNameRaw, catalogName)
        if (alignment.jaccard >= STRONG_JACCARD_MIN &&
            alignment.queryRecall >= STRONG_RECALL_MIN &&
            alignment.docRecall >= STRONG_RECALL_MIN
        ) {
            return true
        }
        return isEquipmentAwareSubsetAlignment(exerciseNameRaw, catalogName, alignment) ||
            isEquipmentAwareQuerySupersetAlignment(exerciseNameRaw, catalogName, alignment)
    }

    /**
     * One-directional relaxation: accepts a match when the query is a subset of the catalog name
     * (queryRecall == 1.0) and every extra catalog token is an equipment/variant modifier. This
     * lets "squat (barbell)" → "barbell squat" match "barbell back squat" (extra "back" is a
     * variant) while rejecting "leg press" → "jm press" ("leg" is in the query but not the catalog,
     * so queryRecall < 1.0).
     */
    private fun isEquipmentAwareSubsetAlignment(
        exerciseNameRaw: String,
        catalogName: String,
        alignment: TokenAlignment,
    ): Boolean {
        if (alignment.queryRecall < FULL_RECALL) return false
        val queryTokenSet = queryTokens(exerciseNameRaw).toSet()
        val extraCatalogTokens = catalogTokens(catalogName).toSet() - queryTokenSet
        return extraCatalogTokens.all { it in MODIFIER_TOKENS }
    }

    /**
     * Reverse of [isEquipmentAwareSubsetAlignment]: catalog tokens are fully covered by the query
     * (docRecall == 1.0) and every extra query token is a **position/equipment** qualifier only.
     * Accepts "Standing Smith Machine Calf Raise" → "Smith Machine Calf Raise" without treating
     * grip/variant tokens (underhand, incline, …) as free extras — those can change the lift.
     */
    private fun isEquipmentAwareQuerySupersetAlignment(
        exerciseNameRaw: String,
        catalogName: String,
        alignment: TokenAlignment,
    ): Boolean {
        if (alignment.docRecall < FULL_RECALL) return false
        val catalogTokenSet = catalogTokens(catalogName).toSet()
        val extraQueryTokens = queryTokens(exerciseNameRaw).toSet() - catalogTokenSet
        return extraQueryTokens.isNotEmpty() &&
            extraQueryTokens.all { it in QUERY_SUPERSET_MODIFIER_TOKENS }
    }

    private fun computeTokenAlignment(queryTokens: List<String>, docTokens: List<String>): TokenAlignment {
        if (queryTokens.isEmpty() || docTokens.isEmpty()) {
            return TokenAlignment(jaccard = 0.0, queryRecall = 0.0, docRecall = 0.0)
        }
        val overlap = queryTokens.intersect(docTokens.toSet()).size.toDouble()
        val union = queryTokens.size + docTokens.size - overlap.toInt()
        return TokenAlignment(
            jaccard = overlap / union.coerceAtLeast(1),
            queryRecall = overlap / queryTokens.size.coerceAtLeast(1),
            docRecall = overlap / docTokens.size.coerceAtLeast(1),
        )
    }

    private fun scoreAll(
        exerciseNameRaw: String,
        catalogNames: Map<LiftId, String>,
    ): List<ScoredLiftMatch> {
        val queryTokens = queryTokens(exerciseNameRaw)
        if (queryTokens.isEmpty()) return emptyList()

        return catalogNames.map { (id, name) ->
            val scored = scoreAgainstCatalogName(exerciseNameRaw, queryTokens, name)
            ScoredLiftMatch(
                liftId = id,
                catalogName = name,
                score = scored.score,
                overlap = scored.overlap,
            )
        }
    }

    private fun scoreBest(exerciseNameRaw: String, catalogNames: Map<LiftId, String>): ScoredLiftMatch? =
        scoreAll(exerciseNameRaw, catalogNames).reduceOrNull { current, candidate ->
            if (isBetterMatch(candidate, current)) candidate else current
        }

    private data class ScoredLiftMatch(
        val liftId: LiftId,
        val catalogName: String,
        val score: Double,
        val overlap: Int,
    )

    private data class TokenScore(val score: Double, val overlap: Int)

    private fun scoreAgainstCatalogName(
        exerciseNameRaw: String,
        queryTokens: List<String>,
        catalogName: String,
    ): TokenScore {
        val docTokens = catalogTokens(catalogName)
        if (docTokens.isEmpty()) return TokenScore(score = 0.0, overlap = 0)

        val queryTokenSet = queryTokens.toSet()
        val docTokenSet = docTokens.toSet()
        val overlap = queryTokens.intersect(docTokenSet).size
        val levBonus = levenshteinBonus(exerciseNameRaw, catalogName)
        if (overlap == 0) {
            return TokenScore(score = levBonus, overlap = 0)
        }

        val alignment = computeTokenAlignment(queryTokens, docTokens)
        val tokenScore = alignment.jaccard
        val equipmentPenalty =
            docTokens
                .count { token -> token !in queryTokenSet && token in EQUIPMENT_TOKENS }
                .toDouble() * EQUIPMENT_TOKEN_PENALTY
        return TokenScore(
            score = (tokenScore + levBonus - equipmentPenalty).coerceAtLeast(0.0),
            overlap = overlap,
        )
    }

    private fun isBetterMatch(candidate: ScoredLiftMatch, current: ScoredLiftMatch): Boolean {
        if (candidate.score > current.score + SCORE_EPSILON) return true
        if (current.score > candidate.score + SCORE_EPSILON) return false
        if (candidate.overlap > current.overlap) return true
        if (current.overlap > candidate.overlap) return false
        return candidate.catalogName.length < current.catalogName.length
    }

    private fun queryTokens(exerciseNameRaw: String): List<String> =
        applyQueryMatchingAliases(tokenize(exerciseNameRaw))

    private fun catalogTokens(catalogName: String): List<String> = tokenize(catalogName)

    private fun applyQueryMatchingAliases(tokens: List<String>): List<String> {
        if (!tokens.contains(CURL_TOKEN) || !tokens.contains(HAMSTRING_TOKEN)) {
            return tokens
        }
        return tokens.map { token -> if (token == HAMSTRING_TOKEN) LEG_TOKEN else token }
    }

    private fun tokenize(value: String): List<String> =
        normalizeForMatching(value)
            .split(TOKEN_SPLIT_REGEX)
            .filter { it.isNotBlank() }
            .map { stemMatchingToken(it) }

    private fun stemMatchingToken(token: String): String =
        if (token.length > MIN_STEM_TOKEN_LENGTH && token.endsWith('s')) {
            token.dropLast(1)
        } else {
            token
        }

    private fun levenshteinBonus(exerciseNameRaw: String, catalogName: String): Double {
        val normalizedQuery = normalizeForMatching(exerciseNameRaw)
        val normalizedName = normalizeForMatching(catalogName)
        return if (levenshtein(normalizedQuery, normalizedName) <= LEVENSHTEIN_DISTANCE_THRESHOLD) {
            LEVENSHTEIN_BONUS
        } else {
            0.0
        }
    }

    private fun normalizeForMatching(value: String): String =
        ImportExerciseNameNormalizer.normalizeForMatching(value)

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val costs = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var last = i - 1
            costs[0] = i
            for (j in 1..b.length) {
                val temp = costs[j]
                costs[j] = minOf(
                    costs[j] + 1,
                    costs[j - 1] + 1,
                    last + if (a[i - 1] == b[j - 1]) 0 else 1,
                )
                last = temp
            }
        }
        return costs[b.length]
    }

    companion object {
        /** Minimum ranking score for near-miss diagnostics in import summaries. */
        const val MATCH_THRESHOLD = 0.6
        const val SCORE_EPSILON = 0.000_1
        const val STRONG_JACCARD_MIN = 0.85
        const val STRONG_RECALL_MIN = 0.9
        private const val FULL_RECALL = 1.0
        private const val DEFAULT_TOP_K = 5
        private const val LEVENSHTEIN_DISTANCE_THRESHOLD = 3
        private const val LEVENSHTEIN_BONUS = 0.5
        private const val EQUIPMENT_TOKEN_PENALTY = 0.2
        private const val MIN_STEM_TOKEN_LENGTH = 3
        private const val HAMSTRING_TOKEN = "hamstring"
        private const val LEG_TOKEN = "leg"
        private const val CURL_TOKEN = "curl"
        private val TOKEN_SPLIT_REGEX = Regex("\\s+")
        private val EQUIPMENT_TOKENS =
            setOf(
                "band",
                "barbell",
                "cable",
                "dumbbell",
                "kettlebell",
                "machine",
                "plate",
                "rope",
                "smith",
            )

        /**
         * Equipment tokens plus variant descriptors that commonly appear in catalog names as
         * qualifiers the user may omit (e.g. "back" in "barbell back squat", "incline" in
         * "incline dumbbell press"). Used by [isEquipmentAwareSubsetAlignment] to accept subset
         * matches where the query covers all core movement tokens.
         */
        private val MODIFIER_TOKENS =
            EQUIPMENT_TOKENS +
                setOf(
                    "back",
                    "front",
                    "incline",
                    "decline",
                    "close",
                    "wide",
                    "neutral",
                    "standing",
                    "seated",
                    "lying",
                    "overhead",
                    "reverse",
                    "stiff",
                    "romanian",
                    "sumo",
                    "split",
                    "single",
                    "one",
                    "arm",
                    "leg",
                    "low",
                    "high",
                    "pin",
                    "loaded",
                    "plate",
                    "iso",
                    "lateral",
                    "v",
                    "bar",
                    "ez",
                    "hack",
                    "zercher",
                    "narrow",
                    "alternate",
                    "concentration",
                    "spider",
                    "prone",
                    "isolation",
                    "preacher",
                    "hammer",
                    "underhand",
                    "overhand",
                    "neutral",
                )

        /**
         * Tokens allowed as *extra on the import name* when every catalog token is already present.
         * Stricter than [MODIFIER_TOKENS]: grip/plane variants (underhand, incline, …) are excluded
         * so "Lat Pulldown - Underhand (Cable)" does not collapse to bare "Lat Pulldown".
         */
        private val QUERY_SUPERSET_MODIFIER_TOKENS =
            EQUIPMENT_TOKENS +
                setOf(
                    "standing",
                    "seated",
                    "lying",
                    "prone",
                    "single",
                    "one",
                    "arm",
                    "leg",
                    "low",
                    "high",
                    "pin",
                    "loaded",
                    "plate",
                    "iso",
                )
    }
}
