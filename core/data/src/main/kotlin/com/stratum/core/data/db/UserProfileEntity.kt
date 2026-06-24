package com.stratum.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.stratum.domain.onboarding.LastTrainedBucket
import com.stratum.domain.onboarding.TrainingAge
import com.stratum.domain.onboarding.Weekday

/**
 * Single-row table holding the onboarding Profile + Schedule draft (OS-2).
 *
 * Schema authorized for OS-2 (docs/onboarding-vertical-slices.md §OS-2;
 * AGENTS.md §4.5 Schema Lock). [id] is fixed at [ID] — one profile per install.
 *
 * Columns are typed enums (decoded by [EnumConverters]); [weekdays] is a `Set<Weekday>`
 * stored as a CSV via [WeekdaySetConverter]. On disk these remain TEXT columns, so the
 * OS-3 enum-column refactor needs no ALTER TABLE — only the Kotlin types and mappers change.
 */
@Entity(tableName = "user_training_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = ID,
    val trainingAge: TrainingAge?,
    val lastTrained: LastTrainedBucket?,
    val weekdays: Set<Weekday>,
    val sessionLengthMinutes: Int?,
) {
    companion object {
        const val ID = 0
    }
}
