package com.stratum.core.data.mapper

import com.stratum.core.data.db.SpecializationPreferenceEntity
import com.stratum.core.data.sync.SyncFlags
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.onboarding.SpecializationPreference
import com.stratum.domain.onboarding.identity.SpecializationPreferenceId

/**
 * Maps [SpecializationPreferenceEntity] rows to/from the domain [SpecializationPreference].
 *
 * Each row is one explicitly-set muscle. Absence means
 * [SpecializationLevel.Maintenance], so a persisted empty preference (skip) round-trips to
 * [SpecializationPreference.Empty] with zero rows. [SpecializationLevel.Maintenance] entries
 * are filtered out on persist — they are implied by absence, not stored.
 */
fun List<SpecializationPreferenceEntity>.toDomain(): SpecializationPreference {
    if (isEmpty()) return SpecializationPreference.Empty
    val levels = associate { it.muscle to it.level }
    return SpecializationPreference(levels = levels)
}

fun SpecializationPreference.toEntities(
    nowMillis: Long = System.currentTimeMillis(),
): List<SpecializationPreferenceEntity> =
    levels.entries
        .filter { (_, level) -> level != SpecializationLevel.Maintenance }
        .map { (muscle, level) ->
            SpecializationPreferenceEntity(
                id = SpecializationPreferenceId.forMuscle(muscle),
                muscle = muscle,
                level = level,
                updatedAtEpochMillis = nowMillis,
                pendingSync = SyncFlags.PENDING_UPSERT,
            )
        }
