package com.stratum.core.testing.importing

import com.stratum.domain.importing.LiftNameMatchEvaluation
import com.stratum.domain.importing.LiftNameResolverPort
import com.stratum.domain.lift.identity.LiftId

/**
 * Fake [LiftNameResolverPort]. Resolves names via an explicit [matches] map (normalized exact
 * match); any name not in the map returns null (the caller then creates a custom lift).
 */
class FakeLiftNameResolverPort(
    private val matches: Map<String, LiftId> = emptyMap(),
) : LiftNameResolverPort {
    val preparedBatches = mutableListOf<List<String>>()

    override suspend fun prepareBatch(exerciseNames: List<String>) {
        preparedBatches += exerciseNames
    }

    override fun resolve(
        exerciseNameRaw: String,
        catalogNames: Map<LiftId, String>,
    ): LiftId? = matches[exerciseNameRaw.trim()]

    override fun evaluate(
        exerciseNameRaw: String,
        catalogNames: Map<LiftId, String>,
    ): LiftNameMatchEvaluation = LiftNameMatchEvaluation(
        matchedLiftId = matches[exerciseNameRaw.trim()],
        bestScore = if (matches.containsKey(exerciseNameRaw.trim())) 1.0 else 0.0,
        bestCatalogName = null,
        matched = matches.containsKey(exerciseNameRaw.trim()),
    )
}
