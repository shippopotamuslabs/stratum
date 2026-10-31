package com.stratum.core.infrastructure.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.stratum.core.infrastructure.sync.SyncFlags
import com.stratum.domain.onboarding.LastTrainedBucket
import com.stratum.domain.onboarding.TrainingAge
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.onboarding.identity.UserProfileId

/**
 * Single-row table holding the onboarding Profile + Schedule draft (OS-2).
 *
 * [id] is the well-known [UserProfileId.Default] — one profile per install; shared with
 * Firestore as the document id under `users/{uid}/user_profile/`.
 *
 * Sync columns ([updatedAtEpochMillis], [pendingSync]) are local dirty-tracking for cloud
 * upload; not domain model fields.
 */
@Entity(tableName = "user_training_profile")
data class UserProfileEntity(
    @PrimaryKey val id: UserProfileId = UserProfileId.Default,
    val trainingAge: TrainingAge?,
    val lastTrained: LastTrainedBucket?,
    val weekdays: Set<Weekday>,
    val sessionLengthMinutes: Int?,
    /** Persisted generate train-day count N (schedule identity). */
    @ColumnInfo(name = "generation_training_day_count")
    val generationTrainingDayCount: Int? = null,
    @ColumnInfo(name = "generation_cycle_length")
    val generationCycleLength: Int? = null,
    @ColumnInfo(name = "generation_rest_slot_indices")
    val generationRestSlotIndices: Set<Int> = emptySet(),
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long = 0L,
    @ColumnInfo(name = "pending_sync")
    val pendingSync: Int = SyncFlags.CLEAN,
)
