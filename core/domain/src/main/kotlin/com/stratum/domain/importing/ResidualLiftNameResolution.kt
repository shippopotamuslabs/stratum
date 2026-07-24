package com.stratum.domain.importing

import com.stratum.domain.lift.identity.LiftId

/**
 * Shared residual-only name resolution used by historical and program import.
 *
 * Contract:
 * 1. Clear singleton embedding state (empty [LiftNameResolverPort.prepareBatch]) so a prior import
 *    cannot leak MiniLM vectors into the local pass.
 * 2. Resolve every distinct name locally (exact + BM25; no useful embeddings yet).
 * 3. [prepareBatch] only residual unmatched names (empty → no network, still clears).
 * 4. Re-resolve residuals with embeddings available for gray-zone rescue.
 */
object ResidualLiftNameResolution {
    /**
     * Resolves [distinctNames] against [catalogNames] with residual-only embeddings.
     *
     * @param resolveLocal maps a raw exercise name to a catalog [LiftId] (or null). Callers that
     *   honor imported lift-id hints should fold that logic into [resolveLocal].
     */
    suspend fun resolveNamesLocalThenResidual(
        resolver: LiftNameResolverPort,
        distinctNames: List<String>,
        resolveLocal: (exerciseNameRaw: String) -> LiftId?,
    ): Map<String, LiftId?> {
        // Clear stale singleton query vectors before any local evaluate.
        resolver.prepareBatch(emptyList())
        val resolution = LinkedHashMap<String, LiftId?>(distinctNames.size)
        for (name in distinctNames) {
            resolution[name] = resolveLocal(name)
        }
        val residual = distinctNames.filter { resolution[it] == null }
        resolver.prepareBatch(residual)
        for (name in residual) {
            resolution[name] = resolveLocal(name)
        }
        return resolution
    }

    /**
     * Convenience for simple name → catalog resolve without lift-id hints.
     */
    suspend fun resolveNamesLocalThenResidual(
        resolver: LiftNameResolverPort,
        distinctNames: List<String>,
        catalogNames: Map<LiftId, String>,
    ): Map<String, LiftId?> =
        resolveNamesLocalThenResidual(
            resolver = resolver,
            distinctNames = distinctNames,
            resolveLocal = { name -> resolver.resolve(name, catalogNames) },
        )
}
