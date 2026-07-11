package com.stratum.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.stratum.core.data.sync.SyncFlags
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.IncrementTierId
import com.stratum.domain.lift.identity.LiftId

/**
 * One weight-banded increment tier for either a lift override or equipment defaults.
 *
 * Exactly one of [liftId] / [equipmentId] must be non-null (enforced in the repository layer).
 * Lift-owned rows are per-lift overrides; equipment-owned rows are shared defaults.
 *
 * [id] is deterministic: [IncrementTierId.forBand] for lifts,
 * [IncrementTierId.forEquipmentBand] for equipment.
 */
@Entity(
    tableName = "increment_tiers",
    foreignKeys = [
        ForeignKey(
            entity = LiftEntity::class,
            parentColumns = ["id"],
            childColumns = ["liftId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = EquipmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["equipmentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("liftId"),
        Index("equipmentId"),
        Index(value = ["liftId", "lowerBound"], unique = true),
        Index(value = ["equipmentId", "lowerBound"], unique = true),
    ],
)
data class IncrementTierEntity(
    @PrimaryKey val id: IncrementTierId,
    @ColumnInfo(name = "liftId") val liftId: LiftId? = null,
    @ColumnInfo(name = "equipmentId") val equipmentId: EquipmentId? = null,
    val lowerBound: Double,
    val upperBound: Double?,
    val increment: Double,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long = 0L,
    @ColumnInfo(name = "pending_sync")
    val pendingSync: Int = SyncFlags.CLEAN,
)
