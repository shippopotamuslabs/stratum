package com.stratum.core.data.mapper

import com.stratum.core.data.db.SpecializationPreferenceEntity
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.onboarding.SpecializationPreference

/**
 * Maps [SpecializationPreferenceEntity] rows to/from the domain [SpecializationPreference].
 *
 * Each row is one explicitly-set muscle. Absence means
 * [SpecializationLevel.Maintenance], so a persisted empty preference (skip) round-trips to
 * [SpecializationPreference.Empty] with zero rows. Enum columns are decoded by
 * [com.stratum.core.data.db.EnumConverters], not here.
 */
fun List<SpecializationPreferenceEntity>.toDomain(): SpecializationPreference {
    if (isEmpty()) return SpecializationPreference.Empty
    val levels = associate { it.muscle to it.level }
    return SpecializationPreference(levels = levels)
}

fun SpecializationPreference.toEntities(): List<SpecializationPreferenceEntity> =
    levels.entries.map { (muscle, level) ->
        SpecializationPreferenceEntity(muscle = muscle, level = level)
    }
