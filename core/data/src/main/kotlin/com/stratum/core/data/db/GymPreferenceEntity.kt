package com.stratum.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.stratum.core.data.sync.SyncFlags
import com.stratum.domain.lift.GymPreset
import com.stratum.domain.onboarding.identity.GymPreferenceId

/**
 * Single-row table holding the user's gym-preset choice from the Gym & Lifts step (OS-4).
 *
 * [id] is [GymPreferenceId.Default] — one row per install / cloud document.
 */
@Entity(tableName = "gym_preference")
data class GymPreferenceEntity(
    @PrimaryKey val id: GymPreferenceId = GymPreferenceId.Default,
    val selectedPreset: GymPreset?,
    val usedHistoricalImport: Boolean = false,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long = 0L,
    @ColumnInfo(name = "pending_sync")
    val pendingSync: Int = SyncFlags.CLEAN,
)
