package com.stratum.application.importing

import com.stratum.domain.importing.GymEquipment
import com.stratum.domain.lift.Lift

/**
 * Transiently infers the [GymEquipment] required to perform each distinct imported exercise name.
 *
 * Backed by the `inferImportEquipment` Firebase Callable (Flash model). The returned name -> equipment
 * map lives only for the duration of `ResolveImportLiftsUseCase` (equipment closure + sparse-import
 * fallback) and is **never persisted to Room** — equipment is a transient import-enablement signal,
 * not a permanent attribute of a `Lift` (ARCHITECTURE.md §2.1, lift-first contract).
 *
 * A failed result degrades the import to an empty inferred map (Tier B sparse-import preset
 * enablement still applies), but the failure is not silent: `ResolveImportLiftsUseCase`
 * carries the real cause on the import summary as a diagnostic, recorded exactly once at
 * the import boundary.
 */
interface ImportEquipmentInferencePort {
    suspend fun inferEquipment(names: List<String>): Result<Map<String, Set<GymEquipment>>>
}
