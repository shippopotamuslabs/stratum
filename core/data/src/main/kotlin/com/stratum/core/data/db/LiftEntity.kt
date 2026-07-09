package com.stratum.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.lift.identity.LiftId

/**
 * A catalog lift row (OS-4). The primary catalog entity: seed lifts (from
 * `src/main/assets/lifts/seed-lifts.json`) and user-created lifts share this table,
 * distinguished by [isCustom].
 *
 * Schema authorized for OS-4 (docs/onboarding-vertical-slices.md §OS-4; AGENTS.md §4.5
 * Schema Lock). [id] is a typed [LiftId] value class — stored as its String `.value` via
 * `EnumConverters`, but kept typed at the Kotlin/DAO level so a `LiftId`
 * can't be assigned to another aggregate's id (the pocket-crew identity pattern).
 *
 * Availability lives on this row as [enabled] (no separate flag table — AGENTS.md §4.5: no
 * new tables for transient state; availability is a durable fact about the user's catalog).
 * Increment tiers live in a child [IncrementTierEntity] (no JSON blob — point 1 of the OS-4
 * entity review). [movementPatternsBitmask]/[primaryMusclesBitmask]/[secondaryMusclesBitmask]
 * are integer bitmasks (`1 shl Enum.ordinal`) so the set can be filtered in SQL with a bitwise
 * AND (`movementPatternsBitmask & :bit != 0`); the muscle/pattern set decoding happens in the
 * mapper. [presetTags] is a delimiter-bounded CSV of `GymPreset.name` (also filtered via LIKE).
 * [equipment] is a nullable display string (e.g. "Hammer Strength") — display copy only, not
 * an equipment entity graph (§2.1 lift-first catalog). Increment tier amounts are stored in lb
 * (v1 schema); the mapper re-wraps them with [Weight.WeightUnit.Lb] until a post-release
 * schema adds per-lift unit persistence. [loadType] classifies the loading modality
 * (Weighted / Bodyweight / Assisted) so the progression engine and UI can branch; stored as
 * the enum `.name` via `EnumConverters`. Defaults to [LoadType.Weighted].
 * [defaultRestSeconds] is an optional per-lift rest override (seconds) for new sets when the
 * lift is added/swapped into a workout; null falls back to compound/isolation defaults in
 * domain. v1 reinstall, no migration (AGENTS.md §4.5).
 */
@Entity(tableName = "lifts")
data class LiftEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: LiftId,
    val name: String,
    val movementPatternsBitmask: Int,
    val primaryMusclesBitmask: Int,
    val secondaryMusclesBitmask: Int,
    val presetTags: String,
    val equipment: String? = null,
    val isCustom: Boolean,
    val enabled: Boolean,
    val description: String? = null,
    val imageUrisCsv: String? = null,
    val muscleLengthBias: MuscleLengthBias = MuscleLengthBias.MidRange,
    val mainstream: Boolean = false,
    val kinematicProfile: KinematicProfile = KinematicProfile.Isolation,
    val loadType: LoadType = LoadType.Weighted,
    val defaultRestSeconds: Int? = null,
)
