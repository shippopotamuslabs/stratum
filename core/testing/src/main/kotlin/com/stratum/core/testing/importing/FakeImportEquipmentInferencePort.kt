package com.stratum.core.testing.importing

import com.stratum.domain.importing.GymEquipment
import com.stratum.application.importing.ImportEquipmentInferencePort

/**
 * In-memory [ImportEquipmentInferencePort] for domain tests. Returns a fixed name -> equipment
 * map for the requested names (default empty — tests seed via [equipmentByName]).
 *
 * Failure modes: [failure] returns `Result.failure` with that exact throwable (so tests can
 * assert the real cause is carried, e.g. by identity); [fail] returns a generic failure.
 * [failure] takes precedence when both are set.
 */
class FakeImportEquipmentInferencePort(
    private val equipmentByName: Map<String, Set<GymEquipment>> = emptyMap(),
    private val fail: Boolean = false,
    private val failure: Throwable? = null,
) : ImportEquipmentInferencePort {
    val invocations = mutableListOf<List<String>>()

    override suspend fun inferEquipment(
        names: List<String>,
    ): Result<Map<String, Set<GymEquipment>>> {
        invocations += names
        return when {
            failure != null -> Result.failure(failure)
            fail -> Result.failure(IllegalStateException("equipment inference failed"))
            else ->
                Result.success(
                    names.filter { it in equipmentByName }.associateWith { equipmentByName[it]!! },
                )
        }
    }
}
