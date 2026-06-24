package com.stratum.core.data.mapper

import com.stratum.core.data.db.UserProfileEntity
import com.stratum.domain.onboarding.UserTrainingProfile

/**
 * Maps [UserProfileEntity] ⇄ domain [UserTrainingProfile]. Enum columns are decoded by
 * [com.stratum.core.data.db.EnumConverters] and `Set<Weekday>` by
 * [com.stratum.core.data.db.WeekdaySetConverter], so this is now a plain field copy.
 */
fun UserProfileEntity.toDomain(): UserTrainingProfile =
    UserTrainingProfile(
        trainingAge = trainingAge,
        lastTrained = lastTrained,
        weekdays = weekdays,
        sessionLengthMinutes = sessionLengthMinutes,
    )

fun UserTrainingProfile.toEntity(): UserProfileEntity =
    UserProfileEntity(
        id = UserProfileEntity.ID,
        trainingAge = trainingAge,
        lastTrained = lastTrained,
        weekdays = weekdays,
        sessionLengthMinutes = sessionLengthMinutes,
    )
