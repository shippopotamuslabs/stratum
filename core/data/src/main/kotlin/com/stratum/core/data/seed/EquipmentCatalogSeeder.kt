package com.stratum.core.data.seed

import android.content.Context
import com.stratum.core.data.db.EquipmentDao
import com.stratum.core.data.db.EquipmentEntity
import com.stratum.core.data.db.IncrementTierDao
import com.stratum.core.data.db.IncrementTierEntity
import com.stratum.core.data.sync.SyncFlags
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.IncrementTierId
import kotlinx.serialization.json.Json

/**
 * Loads bundled equipment + default increment tiers. Run **before** [LiftCatalogSeeder] so
 * lift FKs resolve. Idempotent upsert by stable seed id; never clears custom equipment.
 */
class EquipmentCatalogSeeder(
    private val equipmentDao: EquipmentDao,
    private val incrementTierDao: IncrementTierDao,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun seed(context: Context, assetPath: String = ASSET_PATH): Int {
        val dtos = decode(context, assetPath) ?: return 0
        if (dtos.isEmpty()) return 0
        for (dto in dtos) {
            val id = EquipmentId(dto.id)
            equipmentDao.upsert(
                EquipmentEntity(
                    id = id,
                    name = dto.name,
                    isCustom = false,
                    updatedAtEpochMillis = 0L,
                    pendingSync = SyncFlags.CLEAN,
                ),
            )
            incrementTierDao.deleteForEquipment(id)
            val tiers =
                dto.incrementTiers.map { t ->
                    IncrementTierEntity(
                        id = IncrementTierId.forEquipmentBand(id, t.lowerBound),
                        liftId = null,
                        equipmentId = id,
                        lowerBound = t.lowerBound,
                        upperBound = t.upperBound,
                        increment = t.increment,
                        updatedAtEpochMillis = 0L,
                        pendingSync = SyncFlags.CLEAN,
                    )
                }
            if (tiers.isNotEmpty()) incrementTierDao.upsertAll(tiers)
        }
        return dtos.size
    }

    private fun decode(context: Context, assetPath: String): List<EquipmentSeedDto>? =
        runCatching {
            context.assets.open(assetPath).use { stream ->
                json.decodeFromString<List<EquipmentSeedDto>>(stream.readBytes().decodeToString())
            }
        }.getOrNull()

    companion object {
        const val ASSET_PATH = "lifts/seed-equipment.json"
    }
}
