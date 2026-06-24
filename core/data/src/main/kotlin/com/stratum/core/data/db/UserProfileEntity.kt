package com.stratum.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row table holding the onboarding Profile + Schedule draft (OS-2).
 *
 * Schema authorized for OS-2 (docs/onboarding-vertical-slices.md §OS-2;
 * AGENTS.md §4.5 Schema Lock). [id] is fixed at [ID] — one profile per install.
 *
 * Enum fields are stored as their `.name` String (matches OnboardingProgressEntity);
 * [weekdays] is stored as a comma-separated CSV of Weekday names via [WeekdaySetConverter].
 */
@Entity(tableName = "user_training_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = ID,
    val trainingAge: String?,
    val lastTrained: String?,
    val weekdays: String,
    val sessionLengthMinutes: Int?,
) {
    companion object {
        const val ID = 0
    }
}
