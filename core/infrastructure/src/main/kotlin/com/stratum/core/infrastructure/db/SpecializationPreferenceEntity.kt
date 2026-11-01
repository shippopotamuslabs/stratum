package com.stratum.core.infrastructure.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.stratum.core.infrastructure.sync.SyncFlags
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.onboarding.identity.SpecializationPreferenceId

/**
 * One row per explicitly-set muscle in the onboarding specialization preference (OS-3).
 *
 * [id] is deterministic from [muscle] ([SpecializationPreferenceId.forMuscle]) so cloud docs
 * share identity across devices. [muscle] remains a unique column for SQL lookup.
 */
@Entity(
    tableName = "specialization_preference",
    indices = [Index(value = ["muscle"], unique = true)],
)
data class SpecializationPreferenceEntity(
    @PrimaryKey val id: SpecializationPreferenceId,
    val muscle: MuscleGroup,
    val level: SpecializationLevel,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long = 0L,
    @ColumnInfo(name = "pending_sync")
    val pendingSync: Int = SyncFlags.CLEAN,
)
