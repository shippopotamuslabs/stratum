package com.stratum.core.inference

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.stratum.domain.importing.GymEquipment
import com.stratum.application.importing.ImportEquipmentInferencePort
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Calls the `inferImportEquipment` Firebase Callable (Flash) and parses the response into a
 * transient name -> equipment map. Mirrors [FirebaseCustomLiftInferenceAdapter] for auth + callable
 * shape; equipment strings are enum-bound to [GymEquipment] and dropped if not recognized.
 *
 * The result is never persisted — the caller uses it only for Tier B equipment-closure enablement
 * during import and then discards it.
 */
@Singleton
class FirebaseImportEquipmentInferenceAdapter @Inject constructor() : ImportEquipmentInferencePort {
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    override suspend fun inferEquipment(
        names: List<String>,
    ): Result<Map<String, Set<GymEquipment>>> = runCatching {
        requireAuth()
        val distinct = names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (distinct.isEmpty()) return@runCatching emptyMap()
        val merged = mutableMapOf<String, Set<GymEquipment>>()
        distinct.chunked(INFERENCE_BATCH_SIZE).forEach { batch ->
            merged.putAll(invokeBatch(batch))
        }
        merged
    }

    private suspend fun invokeBatch(names: List<String>): Map<String, Set<GymEquipment>> {
        val callable = functions.getHttpsCallable("inferImportEquipment")
        val payload =
            mapOf(
                "exercises" to names.map { mapOf("name" to it, "loggedSetCount" to 1) },
            )
        val result = callable.call(payload).await()
        return Companion.parseResponse(result.data)
    }

    private fun requireAuth() {
        checkNotNull(auth.currentUser) { "Sign-in required for equipment inference." }
    }

    internal companion object {
        /** Must stay in sync with `IMPORT_EQUIPMENT_INFERENCE_MAX_BATCH` in Cloud Functions. */
        const val INFERENCE_BATCH_SIZE = 100

        @Suppress("UNCHECKED_CAST")
        internal fun parseResponse(data: Any?): Map<String, Set<GymEquipment>> {
            val map = data as? Map<*, *> ?: return emptyMap()
            val lifts = map["lifts"] as? List<*> ?: return emptyMap()
            return lifts.mapNotNull { entry -> parseRow(entry as? Map<*, *>) }.toMap()
        }

        private fun parseRow(entry: Map<*, *>?): Pair<String, Set<GymEquipment>>? {
            if (entry == null) return null
            val name = entry["name"] as? String ?: return null
            val equipmentRaw = entry["equipment"] as? List<*> ?: return null
            val equipment = equipmentRaw.mapNotNull { parseEquipment(it as? String) }.toSet()
            if (equipment.isEmpty()) return null
            return name to equipment
        }

        private fun parseEquipment(value: String?): GymEquipment? =
            value?.let { runCatching { GymEquipment.valueOf(it) }.getOrNull() }
    }
}
