package com.stratum.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.LiftId

/**
 * A catalog lift row (OS-4). The primary catalog entity: seed lifts (from
 * `src/main/assets/lifts/seed-lifts.json`) and user-created lifts share this table,
 * distinguished by [isCustom].
 *
 * Schema authorized for OS-4 (docs/onboarding-vertical-slices.md §OS-4; AGENTS.md §4.5
 * Schema Lock). [id] is a typed [LiftId] value class — stored as its String `.value` via
 * `EnumConverters`/`IdConverters`, but kept typed at the Kotlin/DAO level so a `LiftId`
 * can't be assigned to another aggregate's id (the pocket-crew identity pattern).
 *
 * Availability lives on this row as [enabled] (no separate flag table — AGENTS.md §4.5: no
 * new tables for transient state; availability is a durable fact about the user's catalog).
 * Increment tiers live in a child [IncrementTierEntity] (no JSON blob — point 1 of the OS-4
 * entity review). [primaryMusclesBitmask]/[secondaryMusclesBitmask] are `1 shl MuscleGroup.ordinal`;
 * [presetTags] is a CSV of `GymPreset.name`. Muscle set + preset decoding happens in the mapper.
 * [equipment] is a nullable display string (e.g. "Hammer Strength") — display copy only, not
 * an equipment entity graph (§2.1 lift-first catalog).
 */
@Entity(tableName = "lifts")
data class LiftEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: LiftId,
    val name: String,
    val movementPattern: MovementPattern,
    val primaryMusclesBitmask: Int,
    val secondaryMusclesBitmask: Int,
    val allowWeightChangeBetweenSets: Boolean,
    val presetTags: String,
    val equipment: String? = null,
    val isCustom: Boolean,
    val enabled: Boolean,
)
