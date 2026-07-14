package com.stratum.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.stratum.core.data.sync.SyncFlags
import com.stratum.domain.onboarding.OnboardingPath
import com.stratum.domain.onboarding.OnboardingStep
import com.stratum.domain.onboarding.identity.OnboardingProgressId

/**
 * Single-row table tracking onboarding wizard progress.
 *
 * [id] is [OnboardingProgressId.Default] — one progress row per install / cloud document.
 */
@Entity(tableName = "onboarding_progress")
data class OnboardingProgressEntity(
    @PrimaryKey val id: OnboardingProgressId = OnboardingProgressId.Default,
    val currentStep: OnboardingStep,
    val startedAt: Long,
    val completedAt: Long?,
    /** Durable Generate/Import choice; null until ChoosePath is committed. */
    @ColumnInfo(name = "selected_path")
    val selectedPath: OnboardingPath? = null,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long = 0L,
    @ColumnInfo(name = "pending_sync")
    val pendingSync: Int = SyncFlags.CLEAN,
)
