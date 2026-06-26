package com.stratum.core.testing.importing

import com.stratum.domain.importing.LiftEmbeddingCatalogPort
import com.stratum.domain.importing.LiftNameEmbeddingPort
import com.stratum.domain.lift.identity.LiftId

class FakeLiftEmbeddingCatalog(
    private val vectorsByLiftId: Map<LiftId, FloatArray> = emptyMap(),
    override val dimensions: Int = vectorsByLiftId.values.firstOrNull()?.size ?: 0,
) : LiftEmbeddingCatalogPort {
    override fun vectorFor(liftId: LiftId): FloatArray? = vectorsByLiftId[liftId]

    override fun allVectors(): Map<LiftId, FloatArray> = vectorsByLiftId
}

class FakeLiftNameEmbeddingPort(
    private val vectorsByName: Map<String, FloatArray> = emptyMap(),
    private val result: Result<Map<String, FloatArray>>? = null,
) : LiftNameEmbeddingPort {
    var lastRequestedNames: List<String> = emptyList()
        private set

    override suspend fun embedExerciseNames(names: List<String>): Result<Map<String, FloatArray>> {
        lastRequestedNames = names
        return result ?: Result.success(
            names.associateWith { name -> vectorsByName[name] }.filterValues { it != null }.mapValues { it.value!! },
        )
    }
}
