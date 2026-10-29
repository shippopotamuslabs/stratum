package com.stratum.application.importing

import com.stratum.domain.lift.identity.LiftId
import java.util.concurrent.ConcurrentHashMap
import com.stratum.domain.importing.ImportExerciseNameNormalizer
import com.stratum.domain.importing.LiftNameMatchEvaluation
import com.stratum.domain.lift.residual

/**
 * BM25-first resolver with embedding rerank in the gray zone and for low-confidence rescue.
 *
 * Callers should [prepareBatch] **only residual names** that failed high-confidence local match
 * (exact / strong BM25). Prefetching every import name forces a cloud MiniLM round-trip even when
 * all names already exist in the catalog.
 *
 * Catalog vectors come from the APK asset. Catalog lifts are accepted only on exact normalized
 * match, strong BM25 token alignment, or a high-confidence embedding win with margin — otherwise
 * the name becomes a custom lift.
 */
class HybridLiftNameResolver(
    private val bm25: Bm25LiftNameResolver,
    private val catalogEmbeddings: LiftEmbeddingCatalogPort,
    private val nameEmbedding: LiftNameEmbeddingPort,
) : LiftNameResolverPort {
    // ConcurrentHashMap: this resolver is a process-wide singleton; concurrent imports must not
    // tear the query-vector map during residual re-resolve.
    private val queryEmbeddings = ConcurrentHashMap<String, FloatArray>()
    @Volatile private var embeddingBatchError: String? = null

    /**
     * Prefetch query embeddings for residual [exerciseNames] only. Always clears prior batch state
     * first (including when [exerciseNames] is empty) so callers can isolate the local pass from
     * stale singleton vectors.
     */
    override suspend fun prepareBatch(exerciseNames: List<String>) {
        queryEmbeddings.clear()
        embeddingBatchError = null
        val rawToTrimmed =
            exerciseNames
                .map { raw -> raw to raw.trim() }
                .filter { (_, trimmed) -> trimmed.isNotEmpty() }
        val distinctTrimmed = rawToTrimmed.map { it.second }.distinct()
        if (distinctTrimmed.isEmpty()) return
        nameEmbedding.embedExerciseNames(distinctTrimmed)
            .onSuccess { embeddings ->
                queryEmbeddings.putAll(embeddings)
                // Cloud keys are trimmed; also index original raw strings so evaluate lookups
                // that still carry padding hit the same vector.
                for ((raw, trimmed) in rawToTrimmed) {
                    if (raw != trimmed) {
                        embeddings[trimmed]?.let { queryEmbeddings[raw] = it }
                    }
                }
            }
            .onFailure { error -> embeddingBatchError = error.message ?: error::class.simpleName }
    }

    override fun embeddingBatchFailure(): String? = embeddingBatchError

    override fun resolve(exerciseNameRaw: String, catalogNames: Map<LiftId, String>): LiftId? =
        evaluate(exerciseNameRaw, catalogNames).matchedLiftId

    override fun evaluate(
        exerciseNameRaw: String,
        catalogNames: Map<LiftId, String>,
    ): LiftNameMatchEvaluation {
        val bm25Eval = bm25.evaluate(exerciseNameRaw, catalogNames)
        if (bm25Eval.matched && isHighConfidenceCatalogMatch(exerciseNameRaw, bm25Eval)) {
            return bm25Eval
        }
        val reranked = rerankWithEmbeddings(exerciseNameRaw, catalogNames, bm25Eval)
        if (reranked != null) {
            return reranked
        }
        return rejectLowConfidenceBm25(exerciseNameRaw, bm25Eval)
    }

    private fun isHighConfidenceCatalogMatch(
        exerciseNameRaw: String,
        evaluation: LiftNameMatchEvaluation,
    ): Boolean {
        if (!evaluation.matched) return false
        val catalogName = evaluation.bestCatalogName ?: return false
        // Exact normalized equality is never ambiguous — including short 1–2 token names.
        if (bm25.isExactNormalizedMatch(exerciseNameRaw, catalogName)) {
            return true
        }
        if (isAmbiguousShortQuery(exerciseNameRaw)) {
            return false
        }
        return bm25.isStrongTokenAlignment(exerciseNameRaw, catalogName)
    }

    private fun rejectLowConfidenceBm25(
        exerciseNameRaw: String,
        bm25Eval: LiftNameMatchEvaluation,
    ): LiftNameMatchEvaluation {
        if (!bm25Eval.matched) {
            return bm25Eval
        }
        val catalogName = bm25Eval.bestCatalogName
        if (catalogName != null && bm25.isStrongTokenAlignment(exerciseNameRaw, catalogName)) {
            return bm25Eval
        }
        if (isAmbiguousShortQuery(exerciseNameRaw)) {
            return unmatchedEvaluation(bm25Eval)
        }
        return unmatchedEvaluation(bm25Eval)
    }

    private fun isAmbiguousShortQuery(exerciseNameRaw: String): Boolean {
        val tokenCount = bm25.matchingQueryTokenCount(exerciseNameRaw)
        return tokenCount <= SHORT_QUERY_MAX_TOKENS
    }

    private fun unmatchedEvaluation(
        bm25Eval: LiftNameMatchEvaluation,
    ): LiftNameMatchEvaluation =
        LiftNameMatchEvaluation(
            matchedLiftId = null,
            bestScore = bm25Eval.bestScore,
            bestCatalogName = bm25Eval.bestCatalogName,
            matched = false,
        )

    private fun rerankWithEmbeddings(
        exerciseNameRaw: String,
        catalogNames: Map<LiftId, String>,
        bm25Eval: LiftNameMatchEvaluation,
    ): LiftNameMatchEvaluation? {
        val topK = bm25.rankTopK(exerciseNameRaw, catalogNames, RERANK_CANDIDATE_COUNT)
        val needsRerank =
            topK.isNotEmpty() &&
                (
                    !bm25Eval.matched ||
                        isBm25TopTied(topK)
                    )
        if (!needsRerank) {
            return null
        }

        val queryVector =
            queryEmbeddings[exerciseNameRaw]
                ?: queryEmbeddings[exerciseNameRaw.trim()]
        if (queryVector == null || catalogEmbeddings.dimensions != queryVector.size) {
            return null
        }

        val embeddingScores = scoreEmbeddingCandidates(exerciseNameRaw, topK, queryVector)
        if (embeddingScores.isEmpty()) {
            return null
        }

        val (bestCandidate, bestSimilarity) = embeddingScores.first()
        val secondSimilarity = embeddingScores.getOrNull(1)?.second ?: 0.0
        val margin = bestSimilarity - secondSimilarity
        val acceptByEmbedding =
            bestSimilarity >= EMBEDDING_ACCEPT_THRESHOLD &&
                margin >= EMBEDDING_MARGIN
        if (!acceptByEmbedding) {
            return null
        }

        return LiftNameMatchEvaluation(
            matchedLiftId = bestCandidate.liftId,
            bestScore = maxOf(bm25Eval.bestScore, bestCandidate.score),
            bestCatalogName = bestCandidate.catalogName,
            matched = true,
        )
    }

    private fun scoreEmbeddingCandidates(
        exerciseNameRaw: String,
        topK: List<Bm25LiftCandidate>,
        queryVector: FloatArray,
    ): List<Pair<Bm25LiftCandidate, Double>> =
        topK
            .mapNotNull { candidate ->
                if (!passesImplementVeto(exerciseNameRaw, candidate.catalogName)) {
                    return@mapNotNull null
                }
                val catalogVector = catalogEmbeddings.vectorFor(candidate.liftId) ?: return@mapNotNull null
                candidate to cosineSimilarity(queryVector, catalogVector)
            }
            .sortedByDescending { it.second }

    private fun isBm25TopTied(topK: List<Bm25LiftCandidate>): Boolean {
        if (topK.size < 2) return false
        return kotlin.math.abs(topK[0].score - topK[1].score) <= Bm25LiftNameResolver.SCORE_EPSILON
    }

    private fun passesImplementVeto(exerciseNameRaw: String, catalogName: String): Boolean {
        val queryTokens = tokenizeForVeto(exerciseNameRaw)
        val catalogTokens = tokenizeForVeto(catalogName)
        val queryHasImplement = queryTokens.any { it in IMPLEMENT_PENALTY_TOKENS }
        val catalogHasImplement = catalogTokens.any { it in IMPLEMENT_PENALTY_TOKENS }
        return (queryHasImplement || !catalogHasImplement) &&
            passesDescriptorVeto(queryTokens, catalogTokens)
    }

    private fun passesDescriptorVeto(queryTokens: Set<String>, catalogTokens: Set<String>): Boolean =
        VARIANT_DESCRIPTOR_TOKENS.none { descriptor ->
            descriptor in queryTokens && descriptor !in catalogTokens
        }

    private fun tokenizeForVeto(value: String): Set<String> =
        ImportExerciseNameNormalizer.normalizeForMatching(value)
            .split(TOKEN_SPLIT_REGEX)
            .filter { it.isNotBlank() }
            .toSet()

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Double {
        if (a.size != b.size) return 0.0
        var dot = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
        }
        return dot
    }

    companion object {
        const val EMBEDDING_ACCEPT_THRESHOLD = 0.80
        const val EMBEDDING_MARGIN = 0.05
        const val RERANK_CANDIDATE_COUNT = 5
        const val SHORT_QUERY_MAX_TOKENS = 2
        private val TOKEN_SPLIT_REGEX = Regex("\\s+")
        private val IMPLEMENT_PENALTY_TOKENS =
            setOf(
                "ball",
                "swiss",
                "slider",
            )
        private val VARIANT_DESCRIPTOR_TOKENS =
            setOf(
                "scapular",
                "rear",
                "rocky",
                "assisted",
                // "weighted" intentionally omitted: load-only qualifier should not block embedding
                // match to the unweighted catalog row (see QUERY_SUPERSET_MODIFIER_TOKENS).
            )
    }
}
