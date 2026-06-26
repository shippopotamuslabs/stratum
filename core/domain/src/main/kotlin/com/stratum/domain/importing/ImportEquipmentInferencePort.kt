package com.stratum.domain.importing

/**
 * Transiently infers the [GymEquipment] required to perform each distinct imported exercise name.
 *
 * Backed by the `inferImportEquipment` Firebase Callable (Flash model). The returned name -> equipment
 * map lives only for the duration of `ResolveImportLiftsUseCase` (equipment closure + sparse-import
 * fallback) and is **never persisted to Room** — equipment is a transient import-enablement signal,
 * not a permanent attribute of a `Lift` (ARCHITECTURE.md §2.1, lift-first contract).
 *
 * Implementations should degrade gracefully: a failed/inference-less result yields an empty map so
 * the import pipeline falls back to Tier B sparse-import preset enablement.
 */
interface ImportEquipmentInferencePort {
    suspend fun inferEquipment(names: List<String>): Result<Map<String, Set<GymEquipment>>>
}
