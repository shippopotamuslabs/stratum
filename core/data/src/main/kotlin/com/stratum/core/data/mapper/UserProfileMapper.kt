package com.stratum.core.data.mapper

import com.stratum.core.data.db.UserProfileEntity
import com.stratum.core.data.sync.SyncFlags
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.onboarding.identity.UserProfileId

/**
 * Maps [UserProfileEntity] ⇄ domain [UserTrainingProfile]. Enum columns are decoded by
 * [com.stratum.core.data.db.EnumConverters], `Set<Weekday>` by
 * [com.stratum.core.data.db.WeekdaySetConverter], and rest indices by
 * [com.stratum.core.data.db.IntSetConverter].
 */
fun UserProfileEntity.toDomain(): UserTrainingProfile =
    UserTrainingProfile(
        trainingAge = trainingAge,
        lastTrained = lastTrained,
        weekdays = weekdays,
        sessionLengthMinutes = sessionLengthMinutes,
        generationTrainingDayCount = generationTrainingDayCount,
        generationCycleLength = generationCycleLength,
        generationRestSlotIndices = generationRestSlotIndices,
    )

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
