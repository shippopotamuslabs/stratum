package com.stratum.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.stratum.core.data.sync.SyncFlags
import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId

/**
 * A catalog lift row (OS-4). The primary catalog entity: seed lifts (from
 * `src/main/assets/lifts/seed-lifts.json`) and user-created lifts share this table,
 * distinguished by [isCustom].
 *
 * Availability lives on this row as [enabled]. Increment tiers may live as lift-scoped override
 * rows in [IncrementTierEntity]; when none exist, effective tiers resolve from the linked
 * [equipmentId]'s equipment-owned tiers (or a global fallback).
 *
 * [equipmentId] is a nullable FK to [EquipmentEntity] for shared default increments and display
 * name. Import-time [com.stratum.domain.importing.GymEquipment] tags remain asset-only and are
 * not this column.
 *
 * v1 reinstall, no migration (AGENTS.md §4.5).
 */
@Entity(
    tableName = "lifts",
    foreignKeys = [
        ForeignKey(
            entity = EquipmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["equipmentId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("equipmentId"),
    ],
)
data class LiftEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: LiftId,
    val name: String,
    val movementPatternsBitmask: Int,
    val primaryMusclesBitmask: Int,
    val secondaryMusclesBitmask: Int,
    val presetTags: String,
    @ColumnInfo(name = "equipmentId") val equipmentId: EquipmentId? = null,
    val isCustom: Boolean,
    val enabled: Boolean,
    val description: String? = null,
    val imageUrisCsv: String? = null,
    val muscleLengthBias: MuscleLengthBias = MuscleLengthBias.MidRange,
    val mainstream: Boolean = false,
    val fatigueProfile: FatigueProfile = FatigueProfile.Low,
    val loadType: LoadType = LoadType.Weighted,
    val defaultRestSeconds: Int? = null,
    @ColumnInfo(name = "pinned_note")
    val pinnedNote: String? = null,
    /**
     * Lift-scoped adder override as a JSON array of storage-lb doubles. Null means
     * inherit the linked equipment's stops; `"[]"` means explicitly no adder.
     * Parsed in `LiftMapper`.
     */
    @ColumnInfo(name = "micro_options_json")
    val microOptionsJson: String? = null,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long = 0L,
    @ColumnInfo(name = "pending_sync")
    val pendingSync: Int = SyncFlags.CLEAN,
    /**
     * Tombstone flag (STR-131): the row is retained so logged history keeps
     * resolving by id, but the lift is deleted — catalog reads filter it out
     * and no live pointer may reference it. Mirrors [EquipmentEntity.isDeleted].
     */
    @ColumnInfo(defaultValue = "0")
    val isDeleted: Boolean = false,
)
