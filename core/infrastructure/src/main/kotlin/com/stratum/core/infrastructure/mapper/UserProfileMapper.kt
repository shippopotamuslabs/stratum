package com.stratum.core.infrastructure.mapper

import com.stratum.core.infrastructure.db.UserProfileEntity
import com.stratum.core.infrastructure.sync.SyncFlags
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.onboarding.identity.UserProfileId

/**
 * Maps [UserProfileEntity] ⇄ domain [UserTrainingProfile]. Enum columns are decoded by
 * [com.stratum.core.infrastructure.db.EnumConverters], `Set<Weekday>` by
 * [com.stratum.core.infrastructure.db.WeekdaySetConverter], and rest indices by
 * [com.stratum.core.infrastructure.db.IntSetConverter].
 */
fun UserProfileEntity.toDomain(): UserTrainingProfile {
    // Heal rows written before the UserTrainingProfile constructor invariants existed:
    // a non-positive stored value means the field was never meaningfully set, so it
    // hydrates as absent; rest indices outside the healed cycle length are dropped.
    val healedCycleLength = generationCycleLength?.takeIf { it > 0 }
    return UserTrainingProfile(
        trainingAge = trainingAge,
        lastTrained = lastTrained,
        weekdays = weekdays,
        sessionLengthMinutes = sessionLengthMinutes?.takeIf { it > 0 },
        generationTrainingDayCount = generationTrainingDayCount?.takeIf { it > 0 },
        generationCycleLength = healedCycleLength,
        generationRestSlotIndices = generationRestSlotIndices.filter { index ->
            index >= 0 && (healedCycleLength == null || index < healedCycleLength)
        }.toSet(),
    )
}

/** Local write path — marks the singleton dirty for cloud upload. */
fun UserTrainingProfile.toEntity(
    nowMillis: Long = System.currentTimeMillis(),
): UserProfileEntity =
    UserProfileEntity(
        id = UserProfileId.Default,
        trainingAge = trainingAge,
        lastTrained = lastTrained,
        weekdays = weekdays,
        sessionLengthMinutes = sessionLengthMinutes,
        generationTrainingDayCount = generationTrainingDayCount,
        generationCycleLength = generationCycleLength,
        generationRestSlotIndices = generationRestSlotIndices,
        updatedAtEpochMillis = nowMillis,
        pendingSync = SyncFlags.PENDING_UPSERT,
    )
