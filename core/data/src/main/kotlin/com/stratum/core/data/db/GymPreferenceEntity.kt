package com.stratum.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.stratum.domain.lift.GymPreset

/**
 * Single-row table holding the user's gym-preset choice from the Gym & Lifts step (OS-4).
 *
 * The preset is a *global onboarding choice* (which bundled set to start from), not a
 * per-lift attribute — so it gets its own row rather than a flag on each lift (the
 * "no single-boolean table" rule targets per-entity transient state, which this is not).
 * OS-5 Review reads [selectedPreset] to render the preset's name. `null` means the user
 * skipped the preset and curated manually.
 *
 * Schema authorized for OS-4 (docs/onboarding-vertical-slices.md §OS-4). [selectedPreset]
 * is a typed enum via [EnumConverters]; [ID] pins the single row.
 */
@Entity(tableName = "gym_preference")
data class GymPreferenceEntity(
    @PrimaryKey val id: Int = ID,
    val selectedPreset: GymPreset?,
) {
    companion object {
        const val ID = 0
    }
}
