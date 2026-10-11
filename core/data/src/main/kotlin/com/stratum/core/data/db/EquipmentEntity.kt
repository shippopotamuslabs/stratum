package com.stratum.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.stratum.core.data.sync.SyncFlags
import com.stratum.domain.lift.identity.EquipmentId

/**
 * Shared equipment row used for default weight-increment bands across lifts.
 *
 * Seed rows come from `seed-equipment.json` (`isCustom = false`). User-created rows use UUID
 * ids. Increment tiers for equipment live in [IncrementTierEntity] with `equipmentId` set and
 * `liftId` null.
 */
@Entity(tableName = "equipment")
data class EquipmentEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: EquipmentId,
    val name: String,
    val isCustom: Boolean,
    /**
     * Adder-knob stops as a JSON array of storage-lb doubles (e.g. `"[5.0,10.0]"`).
     * Null (or missing pre-v7) means no adder. Parsed in `LiftMapper`.
     */
    @ColumnInfo(name = "micro_options_json")
    val microOptionsJson: String? = null,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long = 0L,
    @ColumnInfo(name = "pending_sync")
    val pendingSync: Int = SyncFlags.CLEAN,
    /**
     * User-deleted (tombstone) flag. The row, name, and tiers are retained so history
     * keeps resolving; catalog surfaces exclude tombstoned rows while by-id lookups
     * (including the lift join) still return them.
     */
    @ColumnInfo(defaultValue = "0")
    val isDeleted: Boolean = false,
)
