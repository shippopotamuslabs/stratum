package com.stratum.application.importing

import com.stratum.domain.lift.identity.LiftId

/**
 * Bundled catalog lift-name vectors (build-time MiniLM, normalized). Read from APK assets;
 * never hits the network.
 */
interface LiftEmbeddingCatalogPort {
    val dimensions: Int

    fun vectorFor(liftId: LiftId): FloatArray?

    fun allVectors(): Map<LiftId, FloatArray>
}

/**
 * Cloud embedding for distinct import exercise names. Vectors are L2-normalized MiniLM outputs
 * keyed by the original raw exercise name string.
 */
interface LiftNameEmbeddingPort {
    suspend fun embedExerciseNames(names: List<String>): Result<Map<String, FloatArray>>
}
