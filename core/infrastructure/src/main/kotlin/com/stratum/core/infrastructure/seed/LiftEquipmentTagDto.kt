package com.stratum.core.infrastructure.seed

import kotlinx.serialization.Serializable

/**
 * One entry in the bundled equipment-tags asset
 * (`src/main/assets/lifts/lift-equipment-tags.json`). Maps a catalog lift id (the stable slug
 * shared with [LiftSeedDto]) to the [com.stratum.domain.importing.GymEquipment] required to
 * perform the lift.
 *
 * Built once offline (see `scripts/generate-lift-equipment-tags.ts`) and committed to the repo —
 * every user ships the same asset so no per-user Flash pass is needed to curate the catalog.
 *
 * Equipment names are [com.stratum.domain.importing.GymEquipment] `name` strings; an unknown name
 * is dropped at decode time rather than crashing, so a stale asset on an upgraded app degrades
 * gracefully (same policy as [LiftCatalogSeeder]).
 *
 * **Not persisted in Room.** Read from assets at import time only.
 */
@Serializable
data class LiftEquipmentTagDto(
    val id: String,
    val equipment: List<String> = emptyList(),
)
