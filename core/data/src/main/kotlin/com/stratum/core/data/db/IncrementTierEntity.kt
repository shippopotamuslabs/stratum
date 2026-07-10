package com.stratum.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.stratum.core.data.sync.SyncFlags
import com.stratum.domain.lift.identity.IncrementTierId
import com.stratum.domain.lift.identity.LiftId

/**
 * One weight-banded increment tier for a lift (OS-4 §2.2).
 *
 * [id] is a deterministic [IncrementTierId] (`{liftId}_{lowerBound}`) suitable as a Firestore
 * document id. Unique index on `(liftId, lowerBound)` preserves the previous composite-key
 * invariant.
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
    ],
    indices = [
        Index("liftId"),
        Index(value = ["liftId", "lowerBound"], unique = true),
    ],
)
data class IncrementTierEntity(
    @PrimaryKey val id: IncrementTierId,
    @ColumnInfo(name = "liftId") val liftId: LiftId,
    val lowerBound: Double,
    val upperBound: Double?,
    val increment: Double,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long = 0L,
    @ColumnInfo(name = "pending_sync")
    val pendingSync: Int = SyncFlags.CLEAN,
)
