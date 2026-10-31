package com.stratum.core.infrastructure.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.stratum.core.infrastructure.sync.SyncFlags
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.IncrementTierId

/**
 * One weight-banded increment tier of an equipment's defaults.
 *
 * Every row is equipment-scoped ([equipmentId] non-null). Per-lift overrides were removed
 * (STR-82/84): lifts that need their own bands get a "<lift name> Stack" equipment clone.
 *
 * [id] is deterministic: [IncrementTierId.forEquipmentBand].
 */
@Entity(
    tableName = "increment_tiers",
    foreignKeys = [
        ForeignKey(
            entity = EquipmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["equipmentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("equipmentId"),
        Index(value = ["equipmentId", "lowerBound"], unique = true),
    ],
)
data class IncrementTierEntity(
    @PrimaryKey val id: IncrementTierId,
    @ColumnInfo(name = "equipmentId") val equipmentId: EquipmentId,
    val lowerBound: Double,
    val upperBound: Double?,
    val increment: Double,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long = 0L,
    @ColumnInfo(name = "pending_sync")
    val pendingSync: Int = SyncFlags.CLEAN,
)
