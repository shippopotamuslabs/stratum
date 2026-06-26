package com.stratum.domain.importing

import com.stratum.domain.lift.identity.LiftId

/**
 * Simple BM25-inspired token overlap + Levenshtein fallback for lift name resolution.
 */
class Bm25LiftNameResolver : LiftNameResolverPort {
    override fun resolve(exerciseNameRaw: String, catalogNames: Map<LiftId, String>): LiftId? {
        val best = scoreBest(exerciseNameRaw, catalogNames)
        return best?.takeIf { it.score >= MATCH_THRESHOLD }?.liftId
    }

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
        val matched = best.score >= MATCH_THRESHOLD
        return LiftNameMatchEvaluation(
            matchedLiftId = if (matched) best.liftId else null,
            bestScore = best.score,
            bestCatalogName = best.catalogName,
            matched = matched,
        )
    }

    private data class ScoredLiftMatch(
        val liftId: LiftId,
        val catalogName: String,
        val score: Double,
    )

    private fun scoreBest(exerciseNameRaw: String, catalogNames: Map<LiftId, String>): ScoredLiftMatch? {
        val queryTokens = queryTokens(exerciseNameRaw)
        if (queryTokens.isEmpty()) return null

        var best: ScoredLiftMatch? = null
        catalogNames.forEach { (id, name) ->
            val score = scoreAgainstCatalogName(exerciseNameRaw, queryTokens, name)
            val candidate = ScoredLiftMatch(liftId = id, catalogName = name, score = score)
            if (best == null || isBetterMatch(candidate, best)) {
                best = candidate
            }
        }
        return best
    }

    private fun isBetterMatch(candidate: ScoredLiftMatch, current: ScoredLiftMatch): Boolean {
        if (candidate.score > current.score + SCORE_EPSILON) return true
        if (current.score > candidate.score + SCORE_EPSILON) return false
        return candidate.catalogName.length < current.catalogName.length
    }

    private fun scoreAgainstCatalogName(
        exerciseNameRaw: String,
        queryTokens: List<String>,
        catalogName: String,
    ): Double {
        val docTokens = catalogTokens(catalogName)
        if (docTokens.isEmpty()) return 0.0

        val queryTokenSet = queryTokens.toSet()
        val overlap = queryTokens.intersect(docTokens.toSet()).size.toDouble()
        val levBonus = levenshteinBonus(exerciseNameRaw, catalogName)
        if (overlap == 0.0) {
            return levBonus
        }

        val union = queryTokens.size + docTokens.size - overlap.toInt()
        val jaccard = overlap / union.coerceAtLeast(1)
        val queryRecall = overlap / queryTokens.size.coerceAtLeast(1)
        val docRecall = overlap / docTokens.size.coerceAtLeast(1)
        val tokenScore = maxOf(jaccard, queryRecall, docRecall)
        val equipmentPenalty =
            docTokens
                .count { token -> token !in queryTokenSet && token in EQUIPMENT_TOKENS }
                .toDouble() * EQUIPMENT_TOKEN_PENALTY
        return (tokenScore + levBonus - equipmentPenalty).coerceAtLeast(0.0)
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
        const val MATCH_THRESHOLD = 0.6
        private const val LEVENSHTEIN_DISTANCE_THRESHOLD = 3
        private const val LEVENSHTEIN_BONUS = 0.5
        private const val EQUIPMENT_TOKEN_PENALTY = 0.2
        private const val SCORE_EPSILON = 0.000_1
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
    }
}

/**
 * Brzycki for reps ≤ 10, Epley otherwise (OS-6 open decision locked here).
 */
object EstimatedOneRm {
    private const val BRZYCKI_REP_THRESHOLD = 10
    private const val BRZYCKI_DENOMINATOR_BASE = 1.0278
    private const val BRZYCKI_DENOMINATOR_FACTOR = 0.0278
    private const val EPLEY_NUMERATOR_FACTOR = 1.0 / 30.0

    fun calculate(weight: Double, reps: Int): Double {
        require(reps > 0) { "reps must be positive" }
        return if (reps <= BRZYCKI_REP_THRESHOLD) {
            weight * (BRZYCKI_DENOMINATOR_BASE / (BRZYCKI_DENOMINATOR_BASE - BRZYCKI_DENOMINATOR_FACTOR * reps))
        } else {
            weight * (1 + reps * EPLEY_NUMERATOR_FACTOR)
        }
    }
}
