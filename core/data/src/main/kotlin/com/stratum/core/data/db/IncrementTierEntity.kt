package com.stratum.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.stratum.domain.lift.identity.LiftId

/**
 * One weight-banded increment tier for a lift (OS-4 §2.2). A normalized child table rather
 * than a JSON blob on the lift row (point 1 of the OS-4 entity review): each band is its own
 * row, queryable and individually validatable.
 *
 * Schema authorized for OS-4 (docs/onboarding-vertical-slices.md §OS-4). The composite
 * primary key `(liftId, lowerBound)` prevents duplicate bands for one lift; the FK cascades
 * deletes so removing a lift clears its tiers. [upperBound] is `null` for the final
 * ("and above") band. Tier amounts are stored as plain `Double` in lb (v1); the mapper
 * re-wraps them into domain [Weight] with [Weight.WeightUnit.Lb].
 *
 * [liftId] is the typed [LiftId] FK, so the compiler refuses a wrong-type id here.
 */
@Entity(
    tableName = "increment_tiers",
    primaryKeys = ["liftId", "lowerBound"],
    foreignKeys = [
        ForeignKey(
            entity = LiftEntity::class,
            parentColumns = ["id"],
            childColumns = ["liftId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("liftId")],
)
data class IncrementTierEntity(
    @ColumnInfo(name = "liftId") val liftId: LiftId,
    val lowerBound: Double,
    val upperBound: Double?,
    val increment: Double,
)
