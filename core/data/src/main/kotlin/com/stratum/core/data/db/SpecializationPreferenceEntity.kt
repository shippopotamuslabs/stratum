package com.stratum.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel

/**
 * One row per explicitly-set muscle in the onboarding specialization preference (OS-3).
 *
 * Schema authorized for OS-3 (docs/onboarding-vertical-slices.md §OS-3; AGENTS.md §4.5
 * Schema Lock). [muscle] is the primary key — one preference per muscle. OS-6 is expected to
 * widen this to a composite `(muscle, program_id)` key once programs exist; until then there
 * is a single specialization preference per install.
 *
 * Absence of a row means [SpecializationLevel.Maintenance] — the implicit default — so a
 * skipped step stores nothing. Columns are typed enums; [EnumConverters] handles the
 * on-disk String mapping.
 */
@Entity(tableName = "specialization_preference")
data class SpecializationPreferenceEntity(
    @PrimaryKey val muscle: MuscleGroup,
    val level: SpecializationLevel,
)
