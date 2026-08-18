package com.stratum.core.data.seed

import android.content.Context
import com.stratum.domain.importing.GymEquipment
import com.stratum.domain.importing.LiftEquipmentCatalogPort
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
import kotlinx.serialization.json.Json

/**
 * Reads the bundled `lift-equipment-tags.json` asset and exposes it as a [LiftEquipmentCatalogPort]
 * for the import enablement pipeline. Asset is decoded once and cached in memory for the app
 * lifetime; it never touches Room.
 *
 * Missing or corrupt asset yields an empty catalog — import still works, Tier B enablement just
 * falls back to the sparse-import preset path (no equipment closure).
 */
class AssetLiftEquipmentCatalog(
    private val context: Context,
    private val assetPath: String = ASSET_PATH,
    private val loggingPort: LoggingPort,
) : LiftEquipmentCatalogPort {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Volatile
    private var cached: Map<LiftId, Set<GymEquipment>>? = null

    override fun equipmentFor(liftId: LiftId): Set<GymEquipment> =
        all()[liftId] ?: emptySet()

    override fun all(): Map<LiftId, Set<GymEquipment>> {
        cached?.let { return it }
        val loaded = decode()
        cached = loaded
        return loaded
    }

    private fun decode(): Map<LiftId, Set<GymEquipment>> =
        runCatching {
            context.assets.open(assetPath).use { stream ->
                val dtos = json.decodeFromString<List<LiftEquipmentTagDto>>(
                    stream.readBytes().decodeToString(),
                )
                dtos.mapNotNull { dto ->
                    val equipment = dto.equipment.mapNotNull { name ->
                        runCatching { GymEquipment.valueOf(name) }.getOrNull()
                    }.toSet()
                    if (equipment.isEmpty()) return@mapNotNull null
                    LiftId(dto.id) to equipment
                }.toMap()
            }
        }.getOrElse { error ->
            loggingPort.error(TAG, "Failed to decode lift equipment tags: ${error.message}", error)
            emptyMap()
        }

    companion object {
        const val ASSET_PATH = "lifts/lift-equipment-tags.json"
        private const val TAG = "LiftEquipmentCatalog"
    }
}
